package com.reconcilex.controller;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.reconcilex.dto.ImportSummaryResponse;
import com.reconcilex.dto.ReconciliationRunRequest;
import com.reconcilex.entity.BankStatementRow;
import com.reconcilex.entity.FileImport;
import com.reconcilex.entity.FileImportStatus;
import com.reconcilex.entity.FileImportType;
import com.reconcilex.entity.InternalTransaction;
import com.reconcilex.entity.ReconciliationResult;
import com.reconcilex.entity.ReconciliationResultType;
import com.reconcilex.entity.ReconciliationRun;
import com.reconcilex.entity.ReconciliationRunStatus;
import com.reconcilex.repository.BankStatementRowRepository;
import com.reconcilex.repository.FileImportRepository;
import com.reconcilex.repository.ImportRowErrorRepository;
import com.reconcilex.repository.InternalTransactionRepository;
import com.reconcilex.repository.ReconciliationResultRepository;
import com.reconcilex.repository.ReconciliationRunRepository;
import com.reconcilex.service.FileImportService;
import com.reconcilex.service.ReconciliationExecutionService;
import com.reconcilex.service.ReconciliationRunLifecycleService;
import com.reconcilex.service.ReconciliationService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@WithMockUser(username = "admin", roles = {"USER"})
class ReconciliationIntegrationTest {

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
    private FileImportService fileImportService;

    @Autowired
    private ReconciliationService reconciliationService;

    @Autowired
    private ReconciliationRunLifecycleService lifecycleService;

    @Autowired
    private FileImportRepository fileImportRepository;

    @Autowired
    private BankStatementRowRepository bankStatementRowRepository;

    @Autowired
    private InternalTransactionRepository internalTransactionRepository;

    @Autowired
    private ImportRowErrorRepository importRowErrorRepository;

    @Autowired
    private ReconciliationRunRepository reconciliationRunRepository;

    @Autowired
    private ReconciliationResultRepository reconciliationResultRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.execute("DELETE FROM reconciliation_result_bank_rows");
        jdbcTemplate.execute("DELETE FROM reconciliation_result_internal_transactions");
        jdbcTemplate.execute("DELETE FROM reconciliation_results");
        jdbcTemplate.execute("DELETE FROM reconciliation_runs");
        importRowErrorRepository.deleteAllInBatch();
        bankStatementRowRepository.deleteAllInBatch();
        internalTransactionRepository.deleteAllInBatch();
        fileImportRepository.deleteAllInBatch();
    }

    private ImportSummaryResponse importBankCsv() throws Exception {
        byte[] fileBytes;
        try (InputStream is = getClass().getResourceAsStream("/sample-data/bank_statement.csv")) {
            fileBytes = is.readAllBytes();
        }
        MockMultipartFile file = new MockMultipartFile(
                "file", "bank_statement.csv", "text/csv", fileBytes
        );
        return fileImportService.importBankStatement(file);
    }

    private ImportSummaryResponse importInternalCsv() throws Exception {
        byte[] fileBytes;
        try (InputStream is = getClass().getResourceAsStream("/sample-data/internal_transactions.csv")) {
            fileBytes = is.readAllBytes();
        }
        MockMultipartFile file = new MockMultipartFile(
                "file", "internal_transactions.csv", "text/csv", fileBytes
        );
        return fileImportService.importInternalTransactions(file);
    }

    @Test
    void testReconciliationRun_SampleDataMatchingRules() throws Exception {
        ImportSummaryResponse bankImport = importBankCsv();
        ImportSummaryResponse internalImport = importInternalCsv();

        ReconciliationRunRequest request = new ReconciliationRunRequest(
                bankImport.getImportId(),
                internalImport.getImportId()
        );

        String jsonPayload = String.format("{\"bankImportId\":%d,\"internalImportId\":%d}",
                bankImport.getImportId(), internalImport.getImportId());

        mockMvc.perform(post("/api/reconciliation/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.resultCounts.EXACT_MATCH").value(19))
                .andExpect(jsonPath("$.resultCounts.AMOUNT_MISMATCH").value(3))
                .andExpect(jsonPath("$.resultCounts.DATE_MISMATCH").value(3));

        List<ReconciliationRun> runs = reconciliationRunRepository.findAll();
        assertEquals(1, runs.size());
        Long runId = runs.get(0).getId();

        // 1. Verify exact matches: TXN1001-1015, TXN1039, TXN1040 with BNK2001-2015, BNK2038, BNK2039
        List<Map<String, Object>> exactMatches = jdbcTemplate.queryForList(
                "SELECT t.transaction_id, b.bank_reference "
                        + "FROM reconciliation_results r "
                        + "JOIN reconciliation_result_internal_transactions rit ON r.id = rit.reconciliation_result_id "
                        + "JOIN internal_transactions t ON t.id = rit.internal_transaction_id "
                        + "JOIN reconciliation_result_bank_rows rbr ON r.id = rbr.reconciliation_result_id "
                        + "JOIN bank_statement_rows b ON b.id = rbr.bank_statement_row_id "
                        + "WHERE r.run_id = ? AND r.result_type = 'EXACT_MATCH'",
                runId
        );
        assertEquals(19, exactMatches.size());

        Map<String, String> exactMap = exactMatches.stream()
                .collect(Collectors.toMap(m -> (String) m.get("transaction_id"), m -> (String) m.get("bank_reference")));

        for (int i = 1; i <= 15; i++) {
            String txn = String.format("TXN%04d", 1000 + i);
            String bnk = String.format("BNK%04d", 2000 + i);
            assertEquals(bnk, exactMap.get(txn), "Expected " + txn + " to match " + bnk);
        }
        assertEquals("BNK2038", exactMap.get("TXN1039"));
        assertEquals("BNK2039", exactMap.get("TXN1040"));

        // 2. Verify AMOUNT_MISMATCH: TXN1016, TXN1017, TXN1018 with differences -50.00, -100.00, -0.50
        List<Map<String, Object>> amountMismatches = jdbcTemplate.queryForList(
                "SELECT t.transaction_id, b.bank_reference, r.amount_difference "
                        + "FROM reconciliation_results r "
                        + "JOIN reconciliation_result_internal_transactions rit ON r.id = rit.reconciliation_result_id "
                        + "JOIN internal_transactions t ON t.id = rit.internal_transaction_id "
                        + "JOIN reconciliation_result_bank_rows rbr ON r.id = rbr.reconciliation_result_id "
                        + "JOIN bank_statement_rows b ON b.id = rbr.bank_statement_row_id "
                        + "WHERE r.run_id = ? AND r.result_type = 'AMOUNT_MISMATCH'",
                runId
        );
        assertEquals(3, amountMismatches.size());

        Map<String, BigDecimal> amountDiffMap = amountMismatches.stream()
                .collect(Collectors.toMap(m -> (String) m.get("transaction_id"), m -> (BigDecimal) m.get("amount_difference")));

        assertEquals(0, new BigDecimal("-50.00").compareTo(amountDiffMap.get("TXN1016")));
        assertEquals(0, new BigDecimal("-100.00").compareTo(amountDiffMap.get("TXN1017")));
        assertEquals(0, new BigDecimal("-0.50").compareTo(amountDiffMap.get("TXN1018")));

        // 3. Verify DATE_MISMATCH: TXN1019, TXN1020, TXN1021
        List<Map<String, Object>> dateMismatches = jdbcTemplate.queryForList(
                "SELECT t.transaction_id, b.bank_reference "
                        + "FROM reconciliation_results r "
                        + "JOIN reconciliation_result_internal_transactions rit ON r.id = rit.reconciliation_result_id "
                        + "JOIN internal_transactions t ON t.id = rit.internal_transaction_id "
                        + "JOIN reconciliation_result_bank_rows rbr ON r.id = rbr.reconciliation_result_id "
                        + "JOIN bank_statement_rows b ON b.id = rbr.bank_statement_row_id "
                        + "WHERE r.run_id = ? AND r.result_type = 'DATE_MISMATCH'",
                runId
        );
        assertEquals(3, dateMismatches.size());
        Set<String> dateTxnIds = dateMismatches.stream()
                .map(m -> (String) m.get("transaction_id"))
                .collect(Collectors.toSet());
        assertTrue(dateTxnIds.contains("TXN1019"));
        assertTrue(dateTxnIds.contains("TXN1020"));
        assertTrue(dateTxnIds.contains("TXN1021"));

        // 4. Verify a bank row is never matched twice in a run
        Integer distinctBankRowsCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT rbr.bank_statement_row_id) "
                        + "FROM reconciliation_results r "
                        + "JOIN reconciliation_result_bank_rows rbr ON r.id = rbr.reconciliation_result_id "
                        + "WHERE r.run_id = ?",
                Integer.class,
                runId
        );
        Integer totalBankRowsLinksCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(rbr.bank_statement_row_id) "
                        + "FROM reconciliation_results r "
                        + "JOIN reconciliation_result_bank_rows rbr ON r.id = rbr.reconciliation_result_id "
                        + "WHERE r.run_id = ?",
                Integer.class,
                runId
        );
        assertEquals(25, totalBankRowsLinksCount);
        assertEquals(totalBankRowsLinksCount, distinctBankRowsCount, "Bank rows must never be matched twice");
    }

    @Test
    void testReconciliationRun_Idempotency() throws Exception {
        ImportSummaryResponse bankImport = importBankCsv();
        ImportSummaryResponse internalImport = importInternalCsv();

        ReconciliationRunRequest request = new ReconciliationRunRequest(
                bankImport.getImportId(),
                internalImport.getImportId()
        );

        // Run 1
        var summary1 = reconciliationService.runReconciliation(bankImport.getImportId(), internalImport.getImportId());
        // Run 2
        var summary2 = reconciliationService.runReconciliation(bankImport.getImportId(), internalImport.getImportId());

        assertEquals(summary1.getResultCounts(), summary2.getResultCounts());
        assertEquals(summary1.getUnmatchedInternalCount(), summary2.getUnmatchedInternalCount());
        assertEquals(summary1.getUnmatchedBankCount(), summary2.getUnmatchedBankCount());
        assertEquals(summary1.getUnprocessedBankDebits(), summary2.getUnprocessedBankDebits());
    }

    @Test
    void testReconciliationRun_NonExistingOrNonCompletedImportReturns400() throws Exception {
        // Missing import ID
        String missingPayload = "{\"bankImportId\":999999,\"internalImportId\":888888}";
        mockMvc.perform(post("/api/reconciliation/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(missingPayload))
                .andExpect(status().isBadRequest());

        // Non-completed import
        FileImport pendingImport = new FileImport();
        pendingImport.setFileName("pending.csv");
        pendingImport.setFileType(FileImportType.BANK_STATEMENT);
        pendingImport.setFileSha256("0000000000000000000000000000000000000000000000000000000000000001");
        pendingImport.setStatus(FileImportStatus.PENDING);
        fileImportRepository.saveAndFlush(pendingImport);

        ImportSummaryResponse internalImport = importInternalCsv();

        String pendingPayload = String.format("{\"bankImportId\":%d,\"internalImportId\":%d}",
                pendingImport.getId(), internalImport.getImportId());
        mockMvc.perform(post("/api/reconciliation/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(pendingPayload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testRunLifecycle_FailureMarksFailedAndSavesNoPartialResults() throws Exception {
        ImportSummaryResponse bankImport = importBankCsv();
        ImportSummaryResponse internalImport = importInternalCsv();

        ReconciliationExecutionService failingExecutionService = new ReconciliationExecutionService(
                internalTransactionRepository,
                bankStatementRowRepository,
                new com.reconcilex.rules.ReconciliationRuleEngine(List.of()) {
                    @Override
                    public void execute(com.reconcilex.rules.ReconciliationContext context) {
                        throw new RuntimeException("Simulated processing failure");
                    }
                },
                new com.reconcilex.service.ReconciliationBatchInsertService(jdbcTemplate),
                new com.reconcilex.config.MatchingProperties()
        );

        ReconciliationService customReconciliationService = new ReconciliationService(
                lifecycleService,
                failingExecutionService
        );

        assertThrows(RuntimeException.class, () ->
                customReconciliationService.runReconciliation(bankImport.getImportId(), internalImport.getImportId())
        );

        List<ReconciliationRun> runs = reconciliationRunRepository.findAll();
        assertEquals(1, runs.size());
        ReconciliationRun run = runs.get(0);
        assertEquals(ReconciliationRunStatus.FAILED, run.getStatus(), "Run must end up FAILED, not stuck on RUNNING");
        assertNotNull(run.getCompletedAt(), "Completed timestamp must be set on failure");

        // Verify no partial results exist for this run
        assertEquals(0, reconciliationResultRepository.findByRun_IdOrderByIdAsc(run.getId()).size(),
                "No partial reconciliation results should be persisted on failure");
    }
}
