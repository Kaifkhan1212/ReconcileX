package com.reconcilex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.reconcilex.entity.BankStatementRow;
import com.reconcilex.entity.FileImport;
import com.reconcilex.entity.FileImportStatus;
import com.reconcilex.entity.FileImportType;
import com.reconcilex.entity.InternalTransaction;
import com.reconcilex.entity.PaymentStatus;
import com.reconcilex.entity.ReconciliationResult;
import com.reconcilex.entity.ReconciliationResultType;
import com.reconcilex.entity.ReconciliationRun;
import com.reconcilex.entity.ReconciliationRunStatus;

import com.reconcilex.repository.BankStatementRowRepository;
import com.reconcilex.repository.FileImportRepository;
import com.reconcilex.repository.InternalTransactionRepository;
import com.reconcilex.repository.ReconciliationResultRepository;
import com.reconcilex.repository.ReconciliationRunRepository;

@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Testcontainers
class ReconcilexRepositoryTests {

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
    private FileImportRepository fileImportRepository;

    @Autowired
    private BankStatementRowRepository bankStatementRowRepository;

    @Autowired
    private InternalTransactionRepository internalTransactionRepository;

    @Autowired
    private ReconciliationRunRepository reconciliationRunRepository;

    @Autowired
    private ReconciliationResultRepository reconciliationResultRepository;

    @Test
    void fileImportCanBeFoundByTypeAndHash() {
        FileImport saved = saveImport(
                FileImportType.BANK_STATEMENT,
                "bank_statement.csv",
                'a'
        );

        assertTrue(
                fileImportRepository.existsByFileTypeAndFileSha256(
                        FileImportType.BANK_STATEMENT,
                        saved.getFileSha256()
                )
        );

        FileImport found =
                fileImportRepository.findByFileTypeAndFileSha256(
                        FileImportType.BANK_STATEMENT,
                        saved.getFileSha256()
                ).orElseThrow();

        assertEquals(saved.getId(), found.getId());
    }

    @Test
    void bankRowsCanBeRetrievedInSourceOrder() {
        FileImport fileImport = saveImport(
                FileImportType.BANK_STATEMENT,
                "bank_statement.csv",
                'b'
        );

        saveBankRow(fileImport, 3, "BANK-002");
        saveBankRow(fileImport, 2, "BANK-001");

        List<BankStatementRow> rows =
                bankStatementRowRepository
                        .findByFileImport_IdOrderBySourceRowNumberAsc(
                                fileImport.getId()
                        );

        assertEquals(2, rows.size());
        assertEquals(2, rows.get(0).getSourceRowNumber());
        assertEquals(3, rows.get(1).getSourceRowNumber());
    }

    @Test
    void internalTransactionsCanBeSavedAndRetrieved() {
        FileImport fileImport = saveImport(
                FileImportType.INTERNAL_TRANSACTIONS,
                "internal_transactions.csv",
                'c'
        );

        InternalTransaction saved = saveInternalTransaction(
                fileImport,
                2,
                "TXN-001",
                "PAY-001"
        );

        List<InternalTransaction> rows =
                internalTransactionRepository
                        .findByFileImport_IdOrderBySourceRowNumberAsc(
                                fileImport.getId()
                        );

        assertEquals(1, rows.size());
        assertEquals("TXN-001", rows.get(0).getTransactionId());

        assertEquals(
                0,
                new BigDecimal("125.50").compareTo(rows.get(0).getAmount())
        );

        assertNotNull(saved.getId());
    }

    @Test
    void duplicatePaymentReferencesCanBeStoredInBankAndInternal() {
        FileImport bankImport = saveImport(
                FileImportType.BANK_STATEMENT,
                "bank_statement_dup.csv",
                'm'
        );
        FileImport internalImport = saveImport(
                FileImportType.INTERNAL_TRANSACTIONS,
                "internal_transactions_dup.csv",
                'd'
        );

        // Bank rows with same payment_reference
        BankStatementRow bankRow1 = new BankStatementRow();
        bankRow1.setFileImport(bankImport);
        bankRow1.setSourceRowNumber(1);
        bankRow1.setTransactionDate(LocalDate.of(2026, 10, 1));
        bankRow1.setBankReference("BANK-PAY-DUP-1");
        bankRow1.setPaymentReference("PAY-COMMON-REF");
        bankRow1.setCreditAmount(new BigDecimal("100.00"));
        bankRow1.setDebitAmount(BigDecimal.ZERO);
        bankRow1.setCurrency("INR");
        bankStatementRowRepository.saveAndFlush(bankRow1);

        BankStatementRow bankRow2 = new BankStatementRow();
        bankRow2.setFileImport(bankImport);
        bankRow2.setSourceRowNumber(2);
        bankRow2.setTransactionDate(LocalDate.of(2026, 10, 1));
        bankRow2.setBankReference("BANK-PAY-DUP-2");
        bankRow2.setPaymentReference("PAY-COMMON-REF");
        bankRow2.setCreditAmount(new BigDecimal("100.00"));
        bankRow2.setDebitAmount(BigDecimal.ZERO);
        bankRow2.setCurrency("INR");
        bankStatementRowRepository.saveAndFlush(bankRow2);

        List<BankStatementRow> bankDuplicates =
                bankStatementRowRepository.findByPaymentReference("PAY-COMMON-REF");
        assertEquals(2, bankDuplicates.size());

        // Internal transactions with same payment_reference
        saveInternalTransaction(
                internalImport, 1, "TXN-101", "PAY-COMMON-REF"
        );
        saveInternalTransaction(
                internalImport, 2, "TXN-102", "PAY-COMMON-REF"
        );

        List<InternalTransaction> internalDuplicates =
                internalTransactionRepository
                        .findByPaymentReference("PAY-COMMON-REF");
        assertEquals(2, internalDuplicates.size());
    }

    @Test
    void duplicateBankReferenceThrowsException() {
        FileImport fileImport = saveImport(
                FileImportType.BANK_STATEMENT,
                "bank_statement_unique.csv",
                'g'
        );

        saveBankRow(fileImport, 1, "BANK-UNIQUE-REF");

        assertThrows(
                DataIntegrityViolationException.class,
                () -> saveBankRow(fileImport, 2, "BANK-UNIQUE-REF")
        );
    }

    @Test
    void duplicateFileTypeAndHashThrowsException() {
        saveImport(
                FileImportType.BANK_STATEMENT,
                "bank_statement_1.csv",
                'h'
        );

        assertThrows(
                DataIntegrityViolationException.class,
                () -> saveImport(
                        FileImportType.BANK_STATEMENT,
                        "bank_statement_2.csv",
                        'h'
                )
        );
    }

    @Test
    void customRepositoryQueriesWorkCorrectly() {
        FileImport bankImport = saveImport(
                FileImportType.BANK_STATEMENT,
                "bank_statement_query.csv",
                'j'
        );
        FileImport internalImport = saveImport(
                FileImportType.INTERNAL_TRANSACTIONS,
                "internal_transactions_query.csv",
                'k'
        );

        BankStatementRow bankRow = saveBankRow(bankImport, 1, "BANK-REF-QUERY");
        bankRow.setSettlementReference("SET-QUERY-1");
        bankStatementRowRepository.saveAndFlush(bankRow);

        InternalTransaction txn = saveInternalTransaction(
                internalImport, 1, "TXN-QUERY-1", "PAY-QUERY-1"
        );
        txn.setSettlementReference("SET-QUERY-1");
        internalTransactionRepository.saveAndFlush(txn);

        // existsByBankReference
        assertTrue(bankStatementRowRepository.existsByBankReference("BANK-REF-QUERY"));
        assertFalse(bankStatementRowRepository.existsByBankReference("BANK-NON-EXISTENT"));

        // existsByTransactionId
        assertTrue(internalTransactionRepository.existsByTransactionId("TXN-QUERY-1"));
        assertFalse(internalTransactionRepository.existsByTransactionId("TXN-NON-EXISTENT"));

        // findBySettlementReference
        List<BankStatementRow> bankSettlementRows =
                bankStatementRowRepository.findBySettlementReference("SET-QUERY-1");
        assertEquals(1, bankSettlementRows.size());

        List<InternalTransaction> internalSettlementRows =
                internalTransactionRepository.findBySettlementReference("SET-QUERY-1");
        assertEquals(1, internalSettlementRows.size());
    }

    @Test
    void reconciliationResultCanReferenceBankAndInternalRecords() {
        FileImport bankImport = saveImport(
                FileImportType.BANK_STATEMENT,
                "bank_statement.csv",
                'e'
        );

        FileImport internalImport = saveImport(
                FileImportType.INTERNAL_TRANSACTIONS,
                "internal_transactions.csv",
                'f'
        );

        BankStatementRow bankRow =
                saveBankRow(bankImport, 2, "BANK-100");

        InternalTransaction internalTransaction =
                saveInternalTransaction(
                        internalImport, 2, "TXN-100", "PAY-100"
                );

        ReconciliationRun run = new ReconciliationRun();
        run.setBankImport(bankImport);
        run.setInternalImport(internalImport);
        run.setStatus(ReconciliationRunStatus.COMPLETED);

        ReconciliationRun savedRun =
                reconciliationRunRepository.saveAndFlush(run);

        ReconciliationResult result = new ReconciliationResult();
        result.setRun(savedRun);
        result.setResultType(ReconciliationResultType.EXACT_MATCH);
        result.setRuleCode("EXACT_MATCH");
        result.setExplanation("Repository relationship test");

        result.getBankRows().add(bankRow);
        result.getInternalTransactions().add(internalTransaction);

        ReconciliationResult savedResult =
                reconciliationResultRepository.saveAndFlush(result);

        List<ReconciliationResult> results =
                reconciliationResultRepository
                        .findByRun_IdOrderByIdAsc(savedRun.getId());

        assertEquals(1, results.size());
        assertEquals(
                ReconciliationResultType.EXACT_MATCH,
                results.get(0).getResultType()
        );

        assertEquals(1, results.get(0).getBankRows().size());
        assertEquals(1, results.get(0).getInternalTransactions().size());

        assertEquals(
                1,
                reconciliationResultRepository.countByRun_IdAndResultType(
                        savedRun.getId(),
                        ReconciliationResultType.EXACT_MATCH
                )
        );

        assertNotNull(savedResult.getId());
    }

    private FileImport saveImport(
            FileImportType type,
            String fileName,
            char hashCharacter) {

        FileImport fileImport = new FileImport();
        fileImport.setFileType(type);
        fileImport.setFileName(fileName);
        fileImport.setFileSha256(
                String.valueOf(hashCharacter).repeat(64)
        );
        fileImport.setStatus(FileImportStatus.COMPLETED);
        fileImport.setTotalRows(1);
        fileImport.setImportedRows(1);
        fileImport.setRejectedRows(0);

        return fileImportRepository.saveAndFlush(fileImport);
    }

    private BankStatementRow saveBankRow(
            FileImport fileImport,
            int sourceRowNumber,
            String bankReference) {

        BankStatementRow row = new BankStatementRow();
        row.setFileImport(fileImport);
        row.setSourceRowNumber(sourceRowNumber);
        row.setTransactionDate(LocalDate.of(2026, 10, 1));
        row.setBankReference(bankReference);
        row.setPaymentReference("PAY-100");
        row.setSettlementReference("SET-100");
        row.setDescription("Test bank transaction");
        row.setCreditAmount(new BigDecimal("125.50"));
        row.setDebitAmount(BigDecimal.ZERO);
        row.setCurrency("INR");

        return bankStatementRowRepository.saveAndFlush(row);
    }

    private InternalTransaction saveInternalTransaction(
            FileImport fileImport,
            int sourceRowNumber,
            String transactionId,
            String paymentReference) {

        InternalTransaction transaction = new InternalTransaction();
        transaction.setFileImport(fileImport);
        transaction.setSourceRowNumber(sourceRowNumber);
        transaction.setTransactionId(transactionId);
        transaction.setPaymentReference(paymentReference);
        transaction.setTransactionDate(LocalDate.of(2026, 10, 1));
        transaction.setDescription("Test internal transaction");
        transaction.setAmount(new BigDecimal("125.50"));
        transaction.setCurrency("INR");
        transaction.setPaymentStatus(PaymentStatus.SUCCESS);
        transaction.setSettlementReference("SET-100");

        return internalTransactionRepository.saveAndFlush(transaction);
    }
}