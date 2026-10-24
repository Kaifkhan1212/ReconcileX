package com.reconcilex.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.reconcilex.entity.FileImport;
import com.reconcilex.entity.FileImportStatus;
import com.reconcilex.entity.FileImportType;
import com.reconcilex.repository.BankStatementRowRepository;
import com.reconcilex.repository.FileImportRepository;
import com.reconcilex.repository.ImportRowErrorRepository;
import com.reconcilex.repository.InternalTransactionRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@WithMockUser(username = "admin", roles = {"ADMIN"})
class FileImportIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("reconcilex_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FileImportRepository fileImportRepository;

    @Autowired
    private BankStatementRowRepository bankStatementRowRepository;

    @Autowired
    private InternalTransactionRepository internalTransactionRepository;

    @Autowired
    private ImportRowErrorRepository importRowErrorRepository;

    @BeforeEach
    void cleanDatabase() {
        importRowErrorRepository.deleteAllInBatch();
        bankStatementRowRepository.deleteAllInBatch();
        internalTransactionRepository.deleteAllInBatch();
        fileImportRepository.deleteAllInBatch();
    }

    @Test
    void importBankStatementSuccess() throws Exception {
        byte[] fileBytes;
        try (InputStream is = getClass().getResourceAsStream("/sample-data/bank_statement.csv")) {
            fileBytes = is.readAllBytes();
        }

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "bank_statement.csv",
                "text/csv",
                fileBytes
        );

        mockMvc.perform(multipart("/api/imports/bank").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileType").value("BANK_STATEMENT"))
                .andExpect(jsonPath("$.totalRows").value(50))
                .andExpect(jsonPath("$.insertedRows").value(50))
                .andExpect(jsonPath("$.skippedRows").value(0))
                .andExpect(jsonPath("$.invalidRows").value(0))
                .andExpect(jsonPath("$.errors").isEmpty());

        assertEquals(50, bankStatementRowRepository.count());
    }

    @Test
    void importInternalTransactionsSuccess() throws Exception {
        byte[] fileBytes;
        try (InputStream is = getClass().getResourceAsStream("/sample-data/internal_transactions.csv")) {
            fileBytes = is.readAllBytes();
        }

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "internal_transactions.csv",
                "text/csv",
                fileBytes
        );

        mockMvc.perform(multipart("/api/imports/internal").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fileType").value("INTERNAL_TRANSACTIONS"))
                .andExpect(jsonPath("$.totalRows").value(40))
                .andExpect(jsonPath("$.insertedRows").value(40))
                .andExpect(jsonPath("$.skippedRows").value(0))
                .andExpect(jsonPath("$.invalidRows").value(0))
                .andExpect(jsonPath("$.errors").isEmpty());

        assertEquals(40, internalTransactionRepository.count());
        assertTrue(internalTransactionRepository.existsByTransactionId("TXN1036"));
        assertTrue(internalTransactionRepository.existsByTransactionId("TXN1037"));
    }

    @Test
    void uploadSameFileAgainReturns409AndNoNewRows() throws Exception {
        byte[] fileBytes;
        try (InputStream is = getClass().getResourceAsStream("/sample-data/bank_statement.csv")) {
            fileBytes = is.readAllBytes();
        }

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "bank_statement.csv",
                "text/csv",
                fileBytes
        );

        // First upload succeeds
        mockMvc.perform(multipart("/api/imports/bank").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insertedRows").value(50));

        assertEquals(50, bankStatementRowRepository.count());

        // Second upload with same file returns 409 Conflict
        mockMvc.perform(multipart("/api/imports/bank").file(file))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fileType").value("BANK_STATEMENT"))
                .andExpect(jsonPath("$.insertedRows").value(50));

        assertEquals(50, bankStatementRowRepository.count());
        assertEquals(1, fileImportRepository.count());
    }

    @Test
    void sameRowsInModifiedFileSkippedNotDuplicated() throws Exception {
        String baseCsv = "transaction_id,payment_reference,transaction_date,description,amount,currency,payment_status,settlement_reference\n"
                + "TXN-TEST-1,PAY-1,2026-10-01,Test 1,100.00,INR,SUCCESS,SETL-1\n"
                + "TXN-TEST-2,PAY-2,2026-10-01,Test 2,200.00,INR,SUCCESS,SETL-2\n";

        MockMultipartFile file1 = new MockMultipartFile(
                "file",
                "file1.csv",
                "text/csv",
                baseCsv.getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/imports/internal").file(file1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(2))
                .andExpect(jsonPath("$.insertedRows").value(2))
                .andExpect(jsonPath("$.skippedRows").value(0));

        assertEquals(2, internalTransactionRepository.count());

        // File 2 has modified content (different hash) but repeats TXN-TEST-1, TXN-TEST-2 and adds TXN-TEST-3
        String modifiedCsv = baseCsv + "TXN-TEST-3,PAY-3,2026-10-02,Test 3,300.00,INR,SUCCESS,SETL-3\n";

        MockMultipartFile file2 = new MockMultipartFile(
                "file",
                "file2.csv",
                "text/csv",
                modifiedCsv.getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/imports/internal").file(file2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(3))
                .andExpect(jsonPath("$.insertedRows").value(1))
                .andExpect(jsonPath("$.skippedRows").value(2))
                .andExpect(jsonPath("$.invalidRows").value(0));

        assertEquals(3, internalTransactionRepository.count());
    }

    @Test
    void fileWithBadDateNegativeAmountAndInvalidStatusRecordsErrorsAndSavesGoodRows() throws Exception {
        String csv = "transaction_id,payment_reference,transaction_date,description,amount,currency,payment_status,settlement_reference\n"
                + "TXN-GOOD-1,PAY-1,2026-10-01,Good 1,150.00,INR,SUCCESS,SETL-1\n"
                + "TXN-BAD-DATE,PAY-2,2026/13/40,Bad Date,200.00,INR,SUCCESS,SETL-2\n"
                + "TXN-BAD-AMT,PAY-3,2026-10-01,Bad Amount,-50.00,INR,SUCCESS,SETL-3\n"
                + "TXN-BAD-STATUS,PAY-4,2026-10-01,Bad Status,300.00,INR,INVALID_STATUS,SETL-4\n"
                + "TXN-GOOD-2,PAY-5,2026-10-02,Good 2,250.00,INR,PENDING,SETL-5\n";

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "validation_test.csv",
                "text/csv",
                csv.getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/imports/internal").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(5))
                .andExpect(jsonPath("$.insertedRows").value(2))
                .andExpect(jsonPath("$.invalidRows").value(3))
                .andExpect(jsonPath("$.errors.length()").value(3));

        assertEquals(2, internalTransactionRepository.count());
        assertTrue(internalTransactionRepository.existsByTransactionId("TXN-GOOD-1"));
        assertTrue(internalTransactionRepository.existsByTransactionId("TXN-GOOD-2"));
        assertEquals(3, importRowErrorRepository.count());
    }

    @Test
    void wrongHeaderReturns400() throws Exception {
        String badHeaderCsv = "wrong_col1,wrong_col2,wrong_col3\n"
                + "val1,val2,val3\n";

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "bad_header.csv",
                "text/csv",
                badHeaderCsv.getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/imports/bank").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void failedImportCanBeRetriedWithSameFileAndCompletesWhileCompletedReturns409() throws Exception {
        String csv = "transaction_id,payment_reference,transaction_date,description,amount,currency,payment_status,settlement_reference\n"
                + "TXN-RETRY-1,PAY-1,2026-10-01,Retry test,150.00,INR,SUCCESS,SETL-1\n";

        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "retry.csv",
                "text/csv",
                bytes
        );

        // Pre-create a FAILED file_import with the exact same sha256 to simulate crashed/failed previous run
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) sb.append('0');
            sb.append(hex);
        }
        String sha256 = sb.toString();

        FileImport failedImport = new FileImport();
        failedImport.setFileType(FileImportType.INTERNAL_TRANSACTIONS);
        failedImport.setFileName("retry.csv");
        failedImport.setFileSha256(sha256);
        failedImport.setStatus(FileImportStatus.FAILED);
        failedImport.setTotalRows(1);
        failedImport.setImportedRows(0);
        failedImport.setSkippedRows(0);
        failedImport.setRejectedRows(1);
        failedImport.setStartedAt(Instant.now());
        failedImport.setCompletedAt(Instant.now());
        fileImportRepository.saveAndFlush(failedImport);

        // Retrying with the same file should succeed (reuse row, reset counters, complete)
        mockMvc.perform(multipart("/api/imports/internal").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(1))
                .andExpect(jsonPath("$.insertedRows").value(1))
                .andExpect(jsonPath("$.skippedRows").value(0))
                .andExpect(jsonPath("$.invalidRows").value(0));

        FileImport completed = fileImportRepository.findByFileTypeAndFileSha256(
                FileImportType.INTERNAL_TRANSACTIONS,
                sha256
        ).orElseThrow();
        assertEquals(FileImportStatus.COMPLETED, completed.getStatus());

        // Now that it is COMPLETED, trying again must return 409
        mockMvc.perform(multipart("/api/imports/internal").file(file))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fileType").value("INTERNAL_TRANSACTIONS"))
                .andExpect(jsonPath("$.insertedRows").value(1));
    }
}
