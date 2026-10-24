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
@WithMockUser(username = "admin", roles = {"ADMIN"})
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
        jdbcTemplate.execute("TRUNCATE TABLE audit_events CASCADE");
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
                .andExpect(jsonPath("$.resultCounts.DATE_MISMATCH").value(3))
                .andExpect(jsonPath("$.resultCounts.BATCH_SETTLEMENT").value(2))
                .andExpect(jsonPath("$.resultCounts.MISSING_IN_BANK").value(3))
                .andExpect(jsonPath("$.resultCounts.MISSING_IN_INTERNAL").value(11))
                .andExpect(jsonPath("$.resultCounts.BANK_SERVICE_CHARGE").value(8))
                .andExpect(jsonPath("$.resultCounts.REFUND_MATCH").value(2))
                .andExpect(jsonPath("$.resultCounts.UNEXPECTED_BANK_DEBIT").value(1))
                .andExpect(jsonPath("$.resultCounts.REFUND_PENDING_BANK").value(1))
                .andExpect(jsonPath("$.resultCounts.INTERNAL_DUPLICATE_DETECTED").value(1))
                .andExpect(jsonPath("$.resultCounts.BANK_DUPLICATE_DETECTED").value(1))
                .andExpect(jsonPath("$.resultCounts.IGNORED_FAILED_STATUS").value(1))
                .andExpect(jsonPath("$.unmatchedInternalCount").value(0))
                .andExpect(jsonPath("$.unmatchedBankCount").value(0))
                .andExpect(jsonPath("$.unprocessedBankDebits").value(0));

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

        // 4. Verify all 50 bank rows are classified exactly once in Phase 4 (was 25 in Phase 3)
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
        assertEquals(50, totalBankRowsLinksCount, "Phase 4 classifies all 50 bank rows");
        assertEquals(totalBankRowsLinksCount, distinctBankRowsCount, "Bank rows must never be matched twice");

        // 5. Verify all 40 internal transactions are classified exactly once
        Integer distinctInternalRowsCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT rit.internal_transaction_id) "
                        + "FROM reconciliation_results r "
                        + "JOIN reconciliation_result_internal_transactions rit ON r.id = rit.reconciliation_result_id "
                        + "WHERE r.run_id = ?",
                Integer.class,
                runId
        );
        Integer totalInternalRowsLinksCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(rit.internal_transaction_id) "
                        + "FROM reconciliation_results r "
                        + "JOIN reconciliation_result_internal_transactions rit ON r.id = rit.reconciliation_result_id "
                        + "WHERE r.run_id = ?",
                Integer.class,
                runId
        );
        assertEquals(40, totalInternalRowsLinksCount, "Phase 4 classifies all 40 internal transactions");
        assertEquals(totalInternalRowsLinksCount, distinctInternalRowsCount, "Internal rows must never be matched twice");
    }

    /**
     * Requirement 1:
     * Explicit scenario test covering all 54 scenarios (SCN-01 to SCN-54).
     * Maps each scenario explicitly to its expected result types.
     * SCN-40 and SCN-41 each produce TWO results:
     *   - SCN-40: INTERNAL_DUPLICATE_DETECTED (TXN1037) + EXACT_MATCH (TXN1036 & BNK2035)
     *   - SCN-41: EXACT_MATCH (TXN1038 & BNK2036) + BANK_DUPLICATE_DETECTED (BNK2037)
     * Asserts:
     *   - 56 total reconciliation results
     *   - 40 internal rows classified once
     *   - 50 bank rows classified once
     */
    @Test
    void testScenarioReconciliation_All54Scenarios_56Results() throws Exception {
        ImportSummaryResponse bankImport = importBankCsv();
        ImportSummaryResponse internalImport = importInternalCsv();

        var summary = reconciliationService.runReconciliation(bankImport.getImportId(), internalImport.getImportId());
        Long runId = summary.getRunId();

        // --- Explicit mapping: Scenario ID -> List of expected result types ---
        Map<String, List<ReconciliationResultType>> expectedScenarioResultTypes = new java.util.LinkedHashMap<>();

        // SCN-01 to SCN-15: 1:1 EXACT_MATCH
        for (int i = 1; i <= 15; i++) {
            expectedScenarioResultTypes.put(String.format("SCN-%02d", i), List.of(ReconciliationResultType.EXACT_MATCH));
        }
        // SCN-16 to SCN-18: AMOUNT_MISMATCH
        expectedScenarioResultTypes.put("SCN-16", List.of(ReconciliationResultType.AMOUNT_MISMATCH));
        expectedScenarioResultTypes.put("SCN-17", List.of(ReconciliationResultType.AMOUNT_MISMATCH));
        expectedScenarioResultTypes.put("SCN-18", List.of(ReconciliationResultType.AMOUNT_MISMATCH));
        // SCN-19 to SCN-21: DATE_MISMATCH
        expectedScenarioResultTypes.put("SCN-19", List.of(ReconciliationResultType.DATE_MISMATCH));
        expectedScenarioResultTypes.put("SCN-20", List.of(ReconciliationResultType.DATE_MISMATCH));
        expectedScenarioResultTypes.put("SCN-21", List.of(ReconciliationResultType.DATE_MISMATCH));
        // SCN-22 to SCN-23: BATCH_SETTLEMENT
        expectedScenarioResultTypes.put("SCN-22", List.of(ReconciliationResultType.BATCH_SETTLEMENT));
        expectedScenarioResultTypes.put("SCN-23", List.of(ReconciliationResultType.BATCH_SETTLEMENT));
        // SCN-24 to SCN-26: MISSING_IN_BANK
        expectedScenarioResultTypes.put("SCN-24", List.of(ReconciliationResultType.MISSING_IN_BANK));
        expectedScenarioResultTypes.put("SCN-25", List.of(ReconciliationResultType.MISSING_IN_BANK));
        expectedScenarioResultTypes.put("SCN-26", List.of(ReconciliationResultType.MISSING_IN_BANK));
        // SCN-27: IGNORED_FAILED_STATUS
        expectedScenarioResultTypes.put("SCN-27", List.of(ReconciliationResultType.IGNORED_FAILED_STATUS));
        // SCN-28 to SCN-31: MISSING_IN_INTERNAL
        expectedScenarioResultTypes.put("SCN-28", List.of(ReconciliationResultType.MISSING_IN_INTERNAL));
        expectedScenarioResultTypes.put("SCN-29", List.of(ReconciliationResultType.MISSING_IN_INTERNAL));
        expectedScenarioResultTypes.put("SCN-30", List.of(ReconciliationResultType.MISSING_IN_INTERNAL));
        expectedScenarioResultTypes.put("SCN-31", List.of(ReconciliationResultType.MISSING_IN_INTERNAL));
        // SCN-32 to SCN-35: BANK_SERVICE_CHARGE
        expectedScenarioResultTypes.put("SCN-32", List.of(ReconciliationResultType.BANK_SERVICE_CHARGE));
        expectedScenarioResultTypes.put("SCN-33", List.of(ReconciliationResultType.BANK_SERVICE_CHARGE));
        expectedScenarioResultTypes.put("SCN-34", List.of(ReconciliationResultType.BANK_SERVICE_CHARGE));
        expectedScenarioResultTypes.put("SCN-35", List.of(ReconciliationResultType.BANK_SERVICE_CHARGE));
        // SCN-36 to SCN-37: REFUND_MATCH
        expectedScenarioResultTypes.put("SCN-36", List.of(ReconciliationResultType.REFUND_MATCH));
        expectedScenarioResultTypes.put("SCN-37", List.of(ReconciliationResultType.REFUND_MATCH));
        // SCN-38: UNEXPECTED_BANK_DEBIT
        expectedScenarioResultTypes.put("SCN-38", List.of(ReconciliationResultType.UNEXPECTED_BANK_DEBIT));
        // SCN-39: REFUND_PENDING_BANK
        expectedScenarioResultTypes.put("SCN-39", List.of(ReconciliationResultType.REFUND_PENDING_BANK));

        // SCN-40: PRODUCES TWO RESULTS:
        // 1) INTERNAL_DUPLICATE_DETECTED for duplicate TXN1037
        // 2) EXACT_MATCH for primary TXN1036 matching bank row BNK2035
        expectedScenarioResultTypes.put("SCN-40", List.of(
                ReconciliationResultType.INTERNAL_DUPLICATE_DETECTED,
                ReconciliationResultType.EXACT_MATCH
        ));

        // SCN-41: PRODUCES TWO RESULTS:
        // 1) EXACT_MATCH for primary TXN1038 matching bank row BNK2036
        // 2) BANK_DUPLICATE_DETECTED for duplicate bank posting BNK2037
        expectedScenarioResultTypes.put("SCN-41", List.of(
                ReconciliationResultType.EXACT_MATCH,
                ReconciliationResultType.BANK_DUPLICATE_DETECTED
        ));

        // SCN-42 to SCN-43: EXACT_MATCH
        expectedScenarioResultTypes.put("SCN-42", List.of(ReconciliationResultType.EXACT_MATCH));
        expectedScenarioResultTypes.put("SCN-43", List.of(ReconciliationResultType.EXACT_MATCH));
        // SCN-44: BANK_SERVICE_CHARGE
        expectedScenarioResultTypes.put("SCN-44", List.of(ReconciliationResultType.BANK_SERVICE_CHARGE));
        // SCN-45 to SCN-46: MISSING_IN_INTERNAL
        expectedScenarioResultTypes.put("SCN-45", List.of(ReconciliationResultType.MISSING_IN_INTERNAL));
        expectedScenarioResultTypes.put("SCN-46", List.of(ReconciliationResultType.MISSING_IN_INTERNAL));
        // SCN-47: BANK_SERVICE_CHARGE
        expectedScenarioResultTypes.put("SCN-47", List.of(ReconciliationResultType.BANK_SERVICE_CHARGE));
        // SCN-48 to SCN-50: MISSING_IN_INTERNAL
        expectedScenarioResultTypes.put("SCN-48", List.of(ReconciliationResultType.MISSING_IN_INTERNAL));
        expectedScenarioResultTypes.put("SCN-49", List.of(ReconciliationResultType.MISSING_IN_INTERNAL));
        expectedScenarioResultTypes.put("SCN-50", List.of(ReconciliationResultType.MISSING_IN_INTERNAL));
        // SCN-51: BANK_SERVICE_CHARGE
        expectedScenarioResultTypes.put("SCN-51", List.of(ReconciliationResultType.BANK_SERVICE_CHARGE));
        // SCN-52: MISSING_IN_INTERNAL
        expectedScenarioResultTypes.put("SCN-52", List.of(ReconciliationResultType.MISSING_IN_INTERNAL));
        // SCN-53: BANK_SERVICE_CHARGE
        expectedScenarioResultTypes.put("SCN-53", List.of(ReconciliationResultType.BANK_SERVICE_CHARGE));
        // SCN-54: MISSING_IN_INTERNAL
        expectedScenarioResultTypes.put("SCN-54", List.of(ReconciliationResultType.MISSING_IN_INTERNAL));

        // Verify total mapped results count = 56
        int totalExpectedResults = expectedScenarioResultTypes.values().stream().mapToInt(List::size).sum();
        assertEquals(56, totalExpectedResults, "54 scenarios map to exactly 56 total results");

        // 1. Assert exactly 56 total results persisted in database
        Integer totalDbResults = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reconciliation_results WHERE run_id = ?",
                Integer.class,
                runId
        );
        assertEquals(56, totalDbResults, "Database must contain exactly 56 reconciliation results");

        // 2. Assert exactly 40 internal rows classified once
        Integer totalInternalLinks = jdbcTemplate.queryForObject(
                "SELECT COUNT(rit.internal_transaction_id) FROM reconciliation_result_internal_transactions rit "
                        + "JOIN reconciliation_results r ON r.id = rit.reconciliation_result_id WHERE r.run_id = ?",
                Integer.class,
                runId
        );
        Integer distinctInternalLinks = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT rit.internal_transaction_id) FROM reconciliation_result_internal_transactions rit "
                        + "JOIN reconciliation_results r ON r.id = rit.reconciliation_result_id WHERE r.run_id = ?",
                Integer.class,
                runId
        );
        assertEquals(40, totalInternalLinks, "Exactly 40 internal rows linked");
        assertEquals(40, distinctInternalLinks, "Every internal row classified once");

        // 3. Assert exactly 50 bank rows classified once
        Integer totalBankLinks = jdbcTemplate.queryForObject(
                "SELECT COUNT(rbr.bank_statement_row_id) FROM reconciliation_result_bank_rows rbr "
                        + "JOIN reconciliation_results r ON r.id = rbr.reconciliation_result_id WHERE r.run_id = ?",
                Integer.class,
                runId
        );
        Integer distinctBankLinks = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT rbr.bank_statement_row_id) FROM reconciliation_result_bank_rows rbr "
                        + "JOIN reconciliation_results r ON r.id = rbr.reconciliation_result_id WHERE r.run_id = ?",
                Integer.class,
                runId
        );
        assertEquals(50, totalBankLinks, "Exactly 50 bank rows linked");
        assertEquals(50, distinctBankLinks, "Every bank row classified once");

        // 4. Verify specific handling of SCN-40:
        //    TXN1037 flagged as INTERNAL_DUPLICATE_DETECTED (no bank row linked)
        //    TXN1036 matched with BNK2035 as EXACT_MATCH
        List<Map<String, Object>> scn40Duplicate = jdbcTemplate.queryForList(
                "SELECT r.result_type, t.transaction_id "
                        + "FROM reconciliation_results r "
                        + "JOIN reconciliation_result_internal_transactions rit ON r.id = rit.reconciliation_result_id "
                        + "JOIN internal_transactions t ON t.id = rit.internal_transaction_id "
                        + "WHERE r.run_id = ? AND t.transaction_id = 'TXN1037'",
                runId
        );
        assertEquals(1, scn40Duplicate.size());
        assertEquals("INTERNAL_DUPLICATE_DETECTED", scn40Duplicate.get(0).get("result_type"));

        List<Map<String, Object>> scn40Primary = jdbcTemplate.queryForList(
                "SELECT r.result_type, t.transaction_id, b.bank_reference "
                        + "FROM reconciliation_results r "
                        + "JOIN reconciliation_result_internal_transactions rit ON r.id = rit.reconciliation_result_id "
                        + "JOIN internal_transactions t ON t.id = rit.internal_transaction_id "
                        + "JOIN reconciliation_result_bank_rows rbr ON r.id = rbr.reconciliation_result_id "
                        + "JOIN bank_statement_rows b ON b.id = rbr.bank_statement_row_id "
                        + "WHERE r.run_id = ? AND t.transaction_id = 'TXN1036'",
                runId
        );
        assertEquals(1, scn40Primary.size());
        assertEquals("EXACT_MATCH", scn40Primary.get(0).get("result_type"));
        assertEquals("BNK2035", scn40Primary.get(0).get("bank_reference"));

        // 5. Verify specific handling of SCN-41:
        //    TXN1038 matched with BNK2036 as EXACT_MATCH
        //    BNK2037 flagged as BANK_DUPLICATE_DETECTED (no internal row linked)
        List<Map<String, Object>> scn41Primary = jdbcTemplate.queryForList(
                "SELECT r.result_type, t.transaction_id, b.bank_reference "
                        + "FROM reconciliation_results r "
                        + "JOIN reconciliation_result_internal_transactions rit ON r.id = rit.reconciliation_result_id "
                        + "JOIN internal_transactions t ON t.id = rit.internal_transaction_id "
                        + "JOIN reconciliation_result_bank_rows rbr ON r.id = rbr.reconciliation_result_id "
                        + "JOIN bank_statement_rows b ON b.id = rbr.bank_statement_row_id "
                        + "WHERE r.run_id = ? AND t.transaction_id = 'TXN1038'",
                runId
        );
        assertEquals(1, scn41Primary.size());
        assertEquals("EXACT_MATCH", scn41Primary.get(0).get("result_type"));
        assertEquals("BNK2036", scn41Primary.get(0).get("bank_reference"));

        List<Map<String, Object>> scn41Duplicate = jdbcTemplate.queryForList(
                "SELECT r.result_type, b.bank_reference "
                        + "FROM reconciliation_results r "
                        + "JOIN reconciliation_result_bank_rows rbr ON r.id = rbr.reconciliation_result_id "
                        + "JOIN bank_statement_rows b ON b.id = rbr.bank_statement_row_id "
                        + "WHERE r.run_id = ? AND b.bank_reference = 'BNK2037'",
                runId
        );
        assertEquals(1, scn41Duplicate.size());
        assertEquals("BANK_DUPLICATE_DETECTED", scn41Duplicate.get(0).get("result_type"));

        // 6. Verify result type totals match exactly
        Map<ReconciliationResultType, Long> counts = summary.getResultCounts();
        assertEquals(19L, counts.get(ReconciliationResultType.EXACT_MATCH));
        assertEquals(3L, counts.get(ReconciliationResultType.AMOUNT_MISMATCH));
        assertEquals(3L, counts.get(ReconciliationResultType.DATE_MISMATCH));
        assertEquals(2L, counts.get(ReconciliationResultType.BATCH_SETTLEMENT));
        assertEquals(3L, counts.get(ReconciliationResultType.MISSING_IN_BANK));
        assertEquals(11L, counts.get(ReconciliationResultType.MISSING_IN_INTERNAL));
        assertEquals(8L, counts.get(ReconciliationResultType.BANK_SERVICE_CHARGE));
        assertEquals(2L, counts.get(ReconciliationResultType.REFUND_MATCH));
        assertEquals(1L, counts.get(ReconciliationResultType.UNEXPECTED_BANK_DEBIT));
        assertEquals(1L, counts.get(ReconciliationResultType.REFUND_PENDING_BANK));
        assertEquals(1L, counts.get(ReconciliationResultType.INTERNAL_DUPLICATE_DETECTED));
        assertEquals(1L, counts.get(ReconciliationResultType.BANK_DUPLICATE_DETECTED));
        assertEquals(1L, counts.get(ReconciliationResultType.IGNORED_FAILED_STATUS));
        assertEquals(0L, summary.getUnmatchedInternalCount());
        assertEquals(0L, summary.getUnmatchedBankCount());
        assertEquals(0L, summary.getUnprocessedBankDebits());
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

        // No-op audit service stub for this test (we only care about FAILED status)
        com.reconcilex.service.AuditEventService noopAudit =
                new com.reconcilex.service.AuditEventService(jdbcTemplate) {
                    @Override public void recordRunStarted(Long runId) {}
                    @Override public void recordRunCompleted(Long runId, int c) {}
                    @Override public void recordRunFailed(Long runId, String msg) {}
                };
        ReconciliationService customReconciliationService = new ReconciliationService(
                lifecycleService,
                failingExecutionService,
                noopAudit
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
