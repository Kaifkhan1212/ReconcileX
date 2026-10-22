package com.reconcilex.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.reconcilex.dto.ImportRowErrorDto;
import com.reconcilex.dto.ImportSummaryResponse;
import com.reconcilex.entity.FileImport;
import com.reconcilex.entity.FileImportStatus;
import com.reconcilex.entity.FileImportType;
import com.reconcilex.entity.ImportRowError;
import com.reconcilex.exception.DuplicateFileImportException;
import com.reconcilex.exception.InvalidFileException;
import com.reconcilex.repository.BankStatementRowRepository;
import com.reconcilex.repository.FileImportRepository;
import com.reconcilex.repository.ImportRowErrorRepository;
import com.reconcilex.repository.InternalTransactionRepository;

@Service
public class FileImportService {

    private static final int CHUNK_SIZE = 500;
    private static final int MAX_STORED_ERRORS = 1000;
    private static final int MAX_RESPONSE_ERRORS = 100;

    private static final List<String> BANK_REQUIRED_COLUMNS = Arrays.asList(
            "transaction_date",
            "bank_reference",
            "payment_reference",
            "settlement_reference",
            "description",
            "credit_amount",
            "debit_amount",
            "currency"
    );

    private static final List<String> INTERNAL_REQUIRED_COLUMNS = Arrays.asList(
            "transaction_id",
            "payment_reference",
            "transaction_date",
            "description",
            "amount",
            "currency",
            "payment_status",
            "settlement_reference"
    );

    private final FileImportRepository fileImportRepository;
    private final BankStatementRowRepository bankStatementRowRepository;
    private final InternalTransactionRepository internalTransactionRepository;
    private final ImportRowErrorRepository importRowErrorRepository;
    private final CsvRowValidator csvRowValidator;
    private final BatchInsertService batchInsertService;

    public FileImportService(
            FileImportRepository fileImportRepository,
            BankStatementRowRepository bankStatementRowRepository,
            InternalTransactionRepository internalTransactionRepository,
            ImportRowErrorRepository importRowErrorRepository,
            CsvRowValidator csvRowValidator,
            BatchInsertService batchInsertService) {
        this.fileImportRepository = fileImportRepository;
        this.bankStatementRowRepository = bankStatementRowRepository;
        this.internalTransactionRepository = internalTransactionRepository;
        this.importRowErrorRepository = importRowErrorRepository;
        this.csvRowValidator = csvRowValidator;
        this.batchInsertService = batchInsertService;
    }

    public ImportSummaryResponse importBankStatement(MultipartFile file) {
        return processImport(file, FileImportType.BANK_STATEMENT, BANK_REQUIRED_COLUMNS);
    }

    public ImportSummaryResponse importInternalTransactions(MultipartFile file) {
        return processImport(file, FileImportType.INTERNAL_TRANSACTIONS, INTERNAL_REQUIRED_COLUMNS);
    }

    private ImportSummaryResponse processImport(
            MultipartFile file,
            FileImportType fileType,
            List<String> requiredColumns) {

        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("File is empty or missing");
        }

        // Pass 1: Compute SHA-256 while streaming bytes
        String sha256 = computeSha256(file);

        // Claim hash or retrieve existing import in dedicated committed transaction
        FileImport fileImport = claimOrReuseFileImport(
                fileType,
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown.csv",
                sha256
        );

        Long importId = fileImport.getId();

        // Pass 2: Parse and stream CSV rows in chunks
        try {
            return parseAndImportFile(file, fileImport, requiredColumns);
        } catch (InvalidFileException ex) {
            markFileImportFailed(importId);
            throw ex;
        } catch (Exception ex) {
            markFileImportFailed(importId);
            throw new RuntimeException("Import failed: " + ex.getMessage(), ex);
        }
    }

    private String computeSha256(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            long totalBytes = 0;
            while ((read = is.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
                totalBytes += read;
            }
            if (totalBytes == 0) {
                throw new InvalidFileException("Uploaded file is empty");
            }
            byte[] hash = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm not available", ex);
        } catch (IOException ex) {
            throw new InvalidFileException("Failed to read uploaded file: " + ex.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FileImport claimOrReuseFileImport(
            FileImportType fileType,
            String fileName,
            String sha256) {

        Optional<FileImport> existingOpt =
                fileImportRepository.findByFileTypeAndFileSha256(fileType, sha256);

        if (existingOpt.isPresent()) {
            FileImport existing = existingOpt.get();
            if (existing.getStatus() == FileImportStatus.COMPLETED) {
                ImportSummaryResponse summary = buildCompletedSummary(existing);
                throw new DuplicateFileImportException(
                        "File has already been successfully imported",
                        summary
                );
            }

            // Retryable failed or crashed in-progress import: reuse row and reset counters
            existing.setStatus(FileImportStatus.IN_PROGRESS);
            existing.setTotalRows(0);
            existing.setImportedRows(0);
            existing.setSkippedRows(0);
            existing.setRejectedRows(0);
            existing.setStartedAt(Instant.now());
            existing.setCompletedAt(null);
            importRowErrorRepository.deleteByFileImport_Id(existing.getId());
            return fileImportRepository.saveAndFlush(existing);
        }

        FileImport newImport = new FileImport();
        newImport.setFileType(fileType);
        newImport.setFileName(fileName);
        newImport.setFileSha256(sha256);
        newImport.setStatus(FileImportStatus.IN_PROGRESS);
        newImport.setTotalRows(0);
        newImport.setImportedRows(0);
        newImport.setSkippedRows(0);
        newImport.setRejectedRows(0);
        newImport.setStartedAt(Instant.now());

        try {
            return fileImportRepository.saveAndFlush(newImport);
        } catch (DataIntegrityViolationException ex) {
            // Concurrent duplicate claim detected via unique constraint
            FileImport concurrent = fileImportRepository
                    .findByFileTypeAndFileSha256(fileType, sha256)
                    .orElseThrow(() -> ex);

            if (concurrent.getStatus() == FileImportStatus.COMPLETED) {
                ImportSummaryResponse summary = buildCompletedSummary(concurrent);
                throw new DuplicateFileImportException(
                        "Concurrent duplicate file import detected",
                        summary
                );
            }

            // In progress or failed concurrently: return existing summary
            ImportSummaryResponse summary = buildCompletedSummary(concurrent);
            throw new DuplicateFileImportException(
                    "File import is currently in progress",
                    summary
            );
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFileImportFailed(Long fileImportId) {
        fileImportRepository.findById(fileImportId).ifPresent(fi -> {
            fi.setStatus(FileImportStatus.FAILED);
            fi.setCompletedAt(Instant.now());
            fileImportRepository.saveAndFlush(fi);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFileImportCompleted(
            Long fileImportId,
            int totalRows,
            int insertedRows,
            int skippedRows,
            int invalidRows) {

        fileImportRepository.findById(fileImportId).ifPresent(fi -> {
            fi.setTotalRows(totalRows);
            fi.setImportedRows(insertedRows);
            fi.setSkippedRows(skippedRows);
            fi.setRejectedRows(invalidRows);
            fi.setStatus(FileImportStatus.COMPLETED);
            fi.setCompletedAt(Instant.now());
            fileImportRepository.saveAndFlush(fi);
        });
    }

    private ImportSummaryResponse buildCompletedSummary(FileImport fileImport) {
        List<ImportRowError> errorEntities = importRowErrorRepository
                .findByFileImport_IdOrderByRowNumberAsc(
                        fileImport.getId(),
                        PageRequest.of(0, MAX_RESPONSE_ERRORS)
                );

        List<ImportRowErrorDto> errorDtos = errorEntities.stream()
                .map(e -> new ImportRowErrorDto(
                        e.getRowNumber(),
                        e.getFieldName(),
                        e.getErrorMessage(),
                        e.getRawValue()
                ))
                .collect(Collectors.toList());

        return new ImportSummaryResponse(
                fileImport.getId(),
                fileImport.getFileType(),
                fileImport.getTotalRows(),
                fileImport.getImportedRows(),
                fileImport.getSkippedRows(),
                fileImport.getRejectedRows(),
                errorDtos
        );
    }

    private ImportSummaryResponse parseAndImportFile(
            MultipartFile file,
            FileImport fileImport,
            List<String> requiredColumns) throws IOException {

        PushbackInputStream pbis = new PushbackInputStream(file.getInputStream(), 3);
        byte[] bom = new byte[3];
        int n = pbis.read(bom, 0, 3);
        if (n == 3 && bom[0] == (byte) 0xEF && bom[1] == (byte) 0xBB && bom[2] == (byte) 0xBF) {
            // Stripped UTF-8 BOM
        } else if (n > 0) {
            pbis.unread(bom, 0, n);
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(pbis, StandardCharsets.UTF_8))) {
            CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                    .setHeader()
                    .setSkipHeaderRecord(true)
                    .setIgnoreHeaderCase(true)
                    .setTrim(true)
                    .setIgnoreEmptyLines(true)
                    .build();

            CSVParser parser = csvFormat.parse(reader);
            List<String> headerNames = parser.getHeaderNames();

            if (headerNames == null || headerNames.isEmpty()) {
                throw new InvalidFileException("File is empty or missing headers");
            }

            Set<String> normalizedHeaders = headerNames.stream()
                    .map(h -> h.trim().toLowerCase(Locale.ROOT))
                    .collect(Collectors.toSet());

            for (String requiredCol : requiredColumns) {
                if (!normalizedHeaders.contains(requiredCol.toLowerCase(Locale.ROOT))) {
                    throw new InvalidFileException(
                            "Missing required header column: " + requiredCol
                    );
                }
            }

            int totalRows = 0;
            int insertedRows = 0;
            int skippedRows = 0;
            int invalidRows = 0;
            int storedErrorCount = 0;

            List<ImportRowErrorDto> responseErrors = new ArrayList<>();
            Set<String> seenKeysInFile = new HashSet<>();

            List<CSVRecord> chunkRecords = new ArrayList<>(CHUNK_SIZE);

            for (CSVRecord record : parser) {
                chunkRecords.add(record);
                if (chunkRecords.size() == CHUNK_SIZE) {
                    ImportChunkResult result = processChunk(
                            fileImport.getId(),
                            fileImport.getFileType(),
                            chunkRecords,
                            seenKeysInFile,
                            storedErrorCount,
                            responseErrors
                    );
                    totalRows += chunkRecords.size();
                    insertedRows += result.insertedRows;
                    skippedRows += result.skippedRows;
                    invalidRows += result.invalidRows;
                    storedErrorCount += result.newErrorsStored;
                    chunkRecords.clear();
                }
            }

            if (!chunkRecords.isEmpty()) {
                ImportChunkResult result = processChunk(
                        fileImport.getId(),
                        fileImport.getFileType(),
                        chunkRecords,
                        seenKeysInFile,
                        storedErrorCount,
                        responseErrors
                );
                totalRows += chunkRecords.size();
                insertedRows += result.insertedRows;
                skippedRows += result.skippedRows;
                invalidRows += result.invalidRows;
                storedErrorCount += result.newErrorsStored;
                chunkRecords.clear();
            }

            if (totalRows == 0) {
                throw new InvalidFileException("File contains no data rows");
            }

            // Mark import COMPLETED
            markFileImportCompleted(
                    fileImport.getId(),
                    totalRows,
                    insertedRows,
                    skippedRows,
                    invalidRows
            );

            return new ImportSummaryResponse(
                    fileImport.getId(),
                    fileImport.getFileType(),
                    totalRows,
                    insertedRows,
                    skippedRows,
                    invalidRows,
                    responseErrors
            );
        }
    }

    private static class ImportChunkResult {
        int insertedRows = 0;
        int skippedRows = 0;
        int invalidRows = 0;
        int newErrorsStored = 0;
    }

    private ImportChunkResult processChunk(
            Long fileImportId,
            FileImportType fileType,
            List<CSVRecord> records,
            Set<String> seenKeysInFile,
            int currentStoredErrorCount,
            List<ImportRowErrorDto> responseErrors) {

        ImportChunkResult result = new ImportChunkResult();
        List<RowValidationError> errorsToInsert = new ArrayList<>();

        if (fileType == FileImportType.BANK_STATEMENT) {
            List<BankStatementRowData> validCandidates = new ArrayList<>();

            for (CSVRecord record : records) {
                int rowNumber = (int) record.getRecordNumber() + 1;
                Map<String, String> rowMap = normalizeRow(record.toMap());

                ValidationResult<BankStatementRowData> valResult =
                        csvRowValidator.validateBankStatementRow(rowNumber, rowMap);

                if (!valResult.isValid()) {
                    result.invalidRows++;
                    for (RowValidationError err : valResult.getErrors()) {
                        if (responseErrors.size() < MAX_RESPONSE_ERRORS) {
                            responseErrors.add(new ImportRowErrorDto(
                                    err.getRowNumber(),
                                    err.getFieldName(),
                                    err.getErrorMessage(),
                                    err.getRawValue()
                            ));
                        }
                        if (currentStoredErrorCount + errorsToInsert.size() < MAX_STORED_ERRORS) {
                            errorsToInsert.add(err);
                        }
                    }
                } else {
                    BankStatementRowData rowData = valResult.getData();
                    String businessKey = rowData.getBankReference();
                    if (businessKey != null && seenKeysInFile.contains(businessKey)) {
                        result.skippedRows++;
                    } else {
                        if (businessKey != null) {
                            seenKeysInFile.add(businessKey);
                        }
                        validCandidates.add(rowData);
                    }
                }
            }

            // Batch deduplication against DB using single IN query
            if (!validCandidates.isEmpty()) {
                Set<String> candidateKeys = validCandidates.stream()
                        .map(BankStatementRowData::getBankReference)
                        .filter(ref -> ref != null && !ref.isEmpty())
                        .collect(Collectors.toSet());

                Set<String> existingKeys = candidateKeys.isEmpty()
                        ? Collections.emptySet()
                        : bankStatementRowRepository.findExistingBankReferences(candidateKeys);

                List<BankStatementRowData> rowsToInsert = new ArrayList<>();
                for (BankStatementRowData candidate : validCandidates) {
                    if (candidate.getBankReference() != null && existingKeys.contains(candidate.getBankReference())) {
                        result.skippedRows++;
                    } else {
                        rowsToInsert.add(candidate);
                        result.insertedRows++;
                    }
                }

                if (!rowsToInsert.isEmpty()) {
                    batchInsertService.insertBankStatementRows(fileImportId, rowsToInsert);
                }
            }

        } else {
            List<InternalTransactionRowData> validCandidates = new ArrayList<>();

            for (CSVRecord record : records) {
                int rowNumber = (int) record.getRecordNumber() + 1;
                Map<String, String> rowMap = normalizeRow(record.toMap());

                ValidationResult<InternalTransactionRowData> valResult =
                        csvRowValidator.validateInternalTransactionRow(rowNumber, rowMap);

                if (!valResult.isValid()) {
                    result.invalidRows++;
                    for (RowValidationError err : valResult.getErrors()) {
                        if (responseErrors.size() < MAX_RESPONSE_ERRORS) {
                            responseErrors.add(new ImportRowErrorDto(
                                    err.getRowNumber(),
                                    err.getFieldName(),
                                    err.getErrorMessage(),
                                    err.getRawValue()
                            ));
                        }
                        if (currentStoredErrorCount + errorsToInsert.size() < MAX_STORED_ERRORS) {
                            errorsToInsert.add(err);
                        }
                    }
                } else {
                    InternalTransactionRowData rowData = valResult.getData();
                    String businessKey = rowData.getTransactionId();
                    if (businessKey != null && seenKeysInFile.contains(businessKey)) {
                        result.skippedRows++;
                    } else {
                        if (businessKey != null) {
                            seenKeysInFile.add(businessKey);
                        }
                        validCandidates.add(rowData);
                    }
                }
            }

            // Batch deduplication against DB using single IN query
            if (!validCandidates.isEmpty()) {
                Set<String> candidateKeys = validCandidates.stream()
                        .map(InternalTransactionRowData::getTransactionId)
                        .filter(id -> id != null && !id.isEmpty())
                        .collect(Collectors.toSet());

                Set<String> existingKeys = candidateKeys.isEmpty()
                        ? Collections.emptySet()
                        : internalTransactionRepository.findExistingTransactionIds(candidateKeys);

                List<InternalTransactionRowData> rowsToInsert = new ArrayList<>();
                for (InternalTransactionRowData candidate : validCandidates) {
                    if (candidate.getTransactionId() != null && existingKeys.contains(candidate.getTransactionId())) {
                        result.skippedRows++;
                    } else {
                        rowsToInsert.add(candidate);
                        result.insertedRows++;
                    }
                }

                if (!rowsToInsert.isEmpty()) {
                    batchInsertService.insertInternalTransactions(fileImportId, rowsToInsert);
                }
            }
        }

        if (!errorsToInsert.isEmpty()) {
            batchInsertService.insertRowErrors(fileImportId, errorsToInsert);
            result.newErrorsStored = errorsToInsert.size();
        }

        return result;
    }

    private Map<String, String> normalizeRow(Map<String, String> rawMap) {
        Map<String, String> normalized = new HashMap<>();
        for (Map.Entry<String, String> entry : rawMap.entrySet()) {
            if (entry.getKey() != null) {
                normalized.put(
                        entry.getKey().trim().toLowerCase(Locale.ROOT),
                        entry.getValue()
                );
            }
        }
        return normalized;
    }
}
