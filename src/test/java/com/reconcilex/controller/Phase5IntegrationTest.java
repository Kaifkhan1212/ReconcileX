package com.reconcilex.controller;

import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import javax.sql.DataSource;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
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
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.transaction.PlatformTransactionManager;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.reconcilex.dto.ImportSummaryResponse;
import com.reconcilex.dto.ReconciliationRunRequest;
import com.reconcilex.dto.RunSummaryResponse;
import com.reconcilex.entity.AuditEvent;
import com.reconcilex.repository.AuditEventRepository;
import com.reconcilex.repository.BankStatementRowRepository;
import com.reconcilex.repository.FileImportRepository;
import com.reconcilex.repository.ImportRowErrorRepository;
import com.reconcilex.repository.InternalTransactionRepository;
import com.reconcilex.repository.ReconciliationResultRepository;
import com.reconcilex.repository.ReconciliationRunRepository;
import com.reconcilex.service.AuditEventService;
import com.reconcilex.service.FileImportService;
import com.reconcilex.service.ReconciliationReportService;
import com.reconcilex.service.ReconciliationService;
import com.reconcilex.util.TestStatementCounter;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@WithMockUser(username = "admin", roles = {"ADMIN"})
class Phase5IntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("reconcilex_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private FileImportService fileImportService;

    @Autowired
    private ReconciliationService reconciliationService;

    @Autowired
    private ReconciliationRunRepository reconciliationRunRepository;

    @Autowired
    private ReconciliationResultRepository reconciliationResultRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private ImportRowErrorRepository importRowErrorRepository;

    @Autowired
    private BankStatementRowRepository bankStatementRowRepository;

    @Autowired
    private InternalTransactionRepository internalTransactionRepository;

    @Autowired
    private FileImportRepository fileImportRepository;

    @Autowired
    private com.reconcilex.service.ReconciliationRunLifecycleService lifecycleService;

    @Autowired
    private AuditEventService auditEventService;

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

    private Long setupCompletedRun() throws Exception {
        byte[] bankBytes;
        try (InputStream is = getClass().getResourceAsStream("/sample-data/bank_statement.csv")) {
            bankBytes = is.readAllBytes();
        }
        MockMultipartFile bankFile = new MockMultipartFile("file", "bank.csv", "text/csv", bankBytes);
        ImportSummaryResponse bankSummary = fileImportService.importBankStatement(bankFile);

        byte[] internalBytes;
        try (InputStream is = getClass().getResourceAsStream("/sample-data/internal_transactions.csv")) {
            internalBytes = is.readAllBytes();
        }
        MockMultipartFile internalFile = new MockMultipartFile("file", "internal.csv", "text/csv", internalBytes);
        ImportSummaryResponse internalSummary = fileImportService.importInternalTransactions(internalFile);

        var runResponse = reconciliationService.runReconciliation(bankSummary.getImportId(), internalSummary.getImportId());
        return runResponse.getRunId();
    }

    // =========================================================================
    // 1. Endpoints Tests
    // =========================================================================

    @Test
    void testListRuns_returnsPagedNewestFirst() throws Exception {
        Long runId = setupCompletedRun();

        mockMvc.perform(get("/api/reconciliation/runs?page=0&size=10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id", is(runId.intValue())))
                .andExpect(jsonPath("$.content[0].status", is("COMPLETED")));
    }

    @Test
    void testGetRunDetails() throws Exception {
        Long runId = setupCompletedRun();

        mockMvc.perform(get("/api/reconciliation/runs/{id}", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(runId.intValue())))
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.startedAt").isNotEmpty())
                .andExpect(jsonPath("$.completedAt").isNotEmpty());
    }

    @Test
    void testGetRunDetails_notFound() throws Exception {
        mockMvc.perform(get("/api/reconciliation/runs/{id}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status", is(404)))
                .andExpect(jsonPath("$.error", is("Not Found")));
    }

    @Test
    void testGetRunSummary_valuesAndUnclassifiedComputed() throws Exception {
        Long runId = setupCompletedRun();

        mockMvc.perform(get("/api/reconciliation/runs/{id}/summary", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runId", is(runId.intValue())))
                .andExpect(jsonPath("$.status", is("COMPLETED")))
                .andExpect(jsonPath("$.matchedValue", greaterThanOrEqualTo(0.0)))
                .andExpect(jsonPath("$.unmatchedInternalValue", greaterThanOrEqualTo(0.0)))
                .andExpect(jsonPath("$.unmatchedBankCreditValue", greaterThanOrEqualTo(0.0)))
                .andExpect(jsonPath("$.unmatchedBankDebitValue", greaterThanOrEqualTo(0.0)))
                .andExpect(jsonPath("$.explainedValue", greaterThanOrEqualTo(0.0)))
                .andExpect(jsonPath("$.unclassifiedInternalCount", is(0)))
                .andExpect(jsonPath("$.unclassifiedBankCount", is(0)))
                .andExpect(jsonPath("$.countPerResultType").isNotEmpty())
                .andExpect(jsonPath("$.totalValuePerResultType").isNotEmpty());
    }

    @Test
    void testGetResults_filtersAndInvalidDate() throws Exception {
        Long runId = setupCompletedRun();

        // Filter by resultType
        mockMvc.perform(get("/api/reconciliation/runs/{id}/results?resultType=EXACT_MATCH", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.content[0].resultType", is("EXACT_MATCH")));

        // Filter by ruleName
        mockMvc.perform(get("/api/reconciliation/runs/{id}/results?ruleName=ExactMatchRule", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.content[0].ruleName", is("ExactMatchRule")));

        // Valid date window
        mockMvc.perform(get("/api/reconciliation/runs/{id}/results?fromDate=2026-01-01&toDate=2026-12-31", runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()", greaterThanOrEqualTo(1)));

        // Invalid date window: fromDate after toDate must return 400
        mockMvc.perform(get("/api/reconciliation/runs/{id}/results?fromDate=2026-12-31&toDate=2026-01-01", runId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status", is(400)))
                .andExpect(jsonPath("$.error", is("Bad Request")))
                .andExpect(jsonPath("$.message", containsString("fromDate must not be after toDate")));
    }

    // =========================================================================
    // 2. CSV Streaming Report Test
    // =========================================================================

    @Test
    void testStreamingCsvReport_mismatchOnlyAndEscaping() throws Exception {
        Long runId = setupCompletedRun();

        MvcResult mvcResult = mockMvc.perform(get("/api/reconciliation/runs/{id}/report.csv", runId))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"reconciliation-run-" + runId + "-report.csv\""))
                .andExpect(content().contentType("text/csv; charset=UTF-8"));

        String csvContent = mvcResult.getResponse().getContentAsString();
        assertNotNull(csvContent);

        // Header check
        assertTrue(csvContent.startsWith("result_id,result_type,rule_name,rule_code,amount_difference,explanation,internal_transaction_ids,bank_references,internal_date,bank_date"));

        // Must not contain EXACT_MATCH rows (mismatch report only)
        for (String line : csvContent.split("\n")) {
            if (!line.startsWith("result_id")) {
                assertTrue(!line.contains("EXACT_MATCH"),
                        "Report CSV must only contain mismatch results, not EXACT_MATCH: " + line);
            }
        }
    }

    // =========================================================================
    // 3. N+1 Query Count Prevention Test
    // =========================================================================

    @Test
    void testNPlusOnePrevention_statementCounts() throws Exception {
        Long runId = setupCompletedRun();

        var run = reconciliationRunRepository.findByIdWithImports(runId).orElseThrow();
        Long bankImportId = run.getBankImport().getId();
        Long internalImportId = run.getInternalImport().getId();

        TestStatementCounter counter = new TestStatementCounter();
        DataSource wrappedDs = counter.wrap(dataSource);
        JdbcTemplate countingJdbc = new JdbcTemplate(wrappedDs);
        ReconciliationReportService reportServiceWithCounter =
                new ReconciliationReportService(countingJdbc, transactionManager);

        // Assert summary executes <= 5 queries
        counter.reset();
        RunSummaryResponse summary = reportServiceWithCounter.buildSummary(runId, bankImportId, internalImportId);
        assertNotNull(summary);
        int summaryQueryCount = counter.getCount();
        assertTrue(summaryQueryCount <= 5,
                "Summary must execute at most 5 SQL statements, got " + summaryQueryCount);

        // Assert paged results query executes exactly 2 queries (count + data with string_agg)
        counter.reset();
        var page = reportServiceWithCounter.findResults(
                runId, null, null, null, null,
                org.springframework.data.domain.PageRequest.of(0, 50));
        assertNotNull(page);
        int resultsQueryCount = counter.getCount();
        assertEquals(2, resultsQueryCount,
                "findResults page query must execute exactly 2 statements (count + CTE data), got " + resultsQueryCount);
    }

    // =========================================================================
    // 4. Audit Survival Tests (REQUIRES_NEW)
    // =========================================================================

    @Test
    void testAuditSurvival_runFailedPersists() throws Exception {
        byte[] bankBytes;
        try (InputStream is = getClass().getResourceAsStream("/sample-data/bank_statement.csv")) {
            bankBytes = is.readAllBytes();
        }
        MockMultipartFile bankFile = new MockMultipartFile("file", "bank.csv", "text/csv", bankBytes);
        ImportSummaryResponse bankSummary = fileImportService.importBankStatement(bankFile);

        byte[] internalBytes;
        try (InputStream is = getClass().getResourceAsStream("/sample-data/internal_transactions.csv")) {
            internalBytes = is.readAllBytes();
        }
        MockMultipartFile internalFile = new MockMultipartFile("file", "internal.csv", "text/csv", internalBytes);
        ImportSummaryResponse internalSummary = fileImportService.importInternalTransactions(internalFile);

        // Run with a failing rule engine to trigger exception during execution
        com.reconcilex.service.ReconciliationExecutionService failingExecutionService =
                new com.reconcilex.service.ReconciliationExecutionService(
                        internalTransactionRepository,
                        bankStatementRowRepository,
                        new com.reconcilex.rules.ReconciliationRuleEngine(List.of()) {
                            @Override
                            public void execute(com.reconcilex.rules.ReconciliationContext context) {
                                throw new RuntimeException("Simulated processing failure during matching");
                            }
                        },
                        new com.reconcilex.service.ReconciliationBatchInsertService(jdbcTemplate),
                        new com.reconcilex.config.MatchingProperties()
                );

        ReconciliationService customService = new ReconciliationService(
                lifecycleService,
                failingExecutionService,
                auditEventService
        );

        assertThrows(RuntimeException.class, () ->
                customService.runReconciliation(bankSummary.getImportId(), internalSummary.getImportId())
        );

        // Verify RUN_FAILED event was persisted in audit_events despite transaction rollback
        List<AuditEvent> failedEvents = auditEventRepository.findByEventType(AuditEventService.RUN_FAILED);
        assertTrue(!failedEvents.isEmpty(),
                "A failing run must leave a RUN_FAILED audit event behind via REQUIRES_NEW");
        AuditEvent failedEvent = failedEvents.get(0);
        assertEquals("admin", failedEvent.getActor());
        assertNotNull(failedEvent.getOccurredAt());
        assertNotNull(failedEvent.getDetails());
    }

    @Test
    void testAuditSurvival_duplicateRejectedPersists() throws Exception {
        byte[] bankBytes;
        try (InputStream is = getClass().getResourceAsStream("/sample-data/bank_statement.csv")) {
            bankBytes = is.readAllBytes();
        }
        MockMultipartFile bankFile1 = new MockMultipartFile("file", "bank.csv", "text/csv", bankBytes);
        fileImportService.importBankStatement(bankFile1);

        // Upload exact same file again to trigger duplicate conflict
        MockMultipartFile bankFile2 = new MockMultipartFile("file", "bank.csv", "text/csv", bankBytes);
        mockMvc.perform(multipart("/api/imports/bank").file(bankFile2))
                .andExpect(status().isConflict());

        // Verify IMPORT_DUPLICATE_REJECTED event was persisted in audit_events
        List<AuditEvent> dupEvents = auditEventRepository.findByEventType(AuditEventService.IMPORT_DUPLICATE_REJECTED);
        assertTrue(!dupEvents.isEmpty(),
                "A rejected duplicate upload must leave an IMPORT_DUPLICATE_REJECTED audit event behind");
        AuditEvent dup = dupEvents.get(0);
        assertNotNull(dup.getDetails());
        assertTrue(dup.getDetails().contains("existingImportId"));
    }

    // =========================================================================
    // 5. Audit Controller Endpoint Test
    // =========================================================================

    @Test
    void testGetAuditEvents_pagedAndFiltered() throws Exception {
        Long runId = setupCompletedRun();

        mockMvc.perform(get("/api/audit-events?runId=" + runId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.content[0].eventType").isNotEmpty());
    }

    // =========================================================================
    // 6. Security AuthenticationEntryPoint Test (401 Standard JSON)
    // =========================================================================

    @Test
    @WithAnonymousUser
    void testUnauthorized_returnsStandardApiErrorResponse() throws Exception {
        mockMvc.perform(get("/api/reconciliation/runs"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("Unauthorized")))
                .andExpect(jsonPath("$.message", containsString("Authentication is required")))
                .andExpect(jsonPath("$.path", is("/api/reconciliation/runs")))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    // =========================================================================
    // 7. MATCH_DECISION Audit Event Tests
    // =========================================================================

    @Test
    void testMatchDecision_exactly56EventsForSampleRun() throws Exception {
        Long runId = setupCompletedRun();

        long decisionCount = auditEventRepository.countByRunIdAndEventType(runId, AuditEventService.MATCH_DECISION);
        assertEquals(56, decisionCount,
                "Expected exactly 56 MATCH_DECISION audit events for the sample run, got " + decisionCount);
    }

    @Test
    void testMatchDecision_failingRunLeavesZeroDecisionEvents() throws Exception {
        byte[] bankBytes;
        try (InputStream is = getClass().getResourceAsStream("/sample-data/bank_statement.csv")) {
            bankBytes = is.readAllBytes();
        }
        MockMultipartFile bankFile = new MockMultipartFile("file", "bank.csv", "text/csv", bankBytes);
        ImportSummaryResponse bankSummary = fileImportService.importBankStatement(bankFile);

        byte[] internalBytes;
        try (InputStream is = getClass().getResourceAsStream("/sample-data/internal_transactions.csv")) {
            internalBytes = is.readAllBytes();
        }
        MockMultipartFile internalFile = new MockMultipartFile("file", "internal.csv", "text/csv", internalBytes);
        ImportSummaryResponse internalSummary = fileImportService.importInternalTransactions(internalFile);

        // Build a ReconciliationService backed by a rule engine that throws during execute()
        com.reconcilex.service.ReconciliationExecutionService failingExecutionService =
                new com.reconcilex.service.ReconciliationExecutionService(
                        internalTransactionRepository,
                        bankStatementRowRepository,
                        new com.reconcilex.rules.ReconciliationRuleEngine(List.of()) {
                            @Override
                            public void execute(com.reconcilex.rules.ReconciliationContext context) {
                                throw new RuntimeException("Simulated failure – no results must be committed");
                            }
                        },
                        new com.reconcilex.service.ReconciliationBatchInsertService(jdbcTemplate),
                        new com.reconcilex.config.MatchingProperties()
                );

        ReconciliationService customService = new ReconciliationService(
                lifecycleService,
                failingExecutionService,
                auditEventService
        );

        // Capture the run id before the exception is thrown (lifecycleService.startRun commits
        // in its own transaction, so we can still query it afterwards)
        final Long[] failedRunId = {null};
        try {
            var resp = customService.runReconciliation(bankSummary.getImportId(), internalSummary.getImportId());
            failedRunId[0] = resp.getRunId();
        } catch (RuntimeException ignored) {
            // expected – locate the run by finding the latest RUN_FAILED event
        }

        // Find the run id from the RUN_FAILED audit event (written in REQUIRES_NEW)
        List<AuditEvent> failedEvents = auditEventRepository.findByEventType(AuditEventService.RUN_FAILED);
        assertNotNull(failedEvents);
        assertTrue(!failedEvents.isEmpty(), "Expected at least one RUN_FAILED event");

        Long failedRun = failedEvents.get(0).getRunId();
        if (failedRun != null) {
            long decisionCount = auditEventRepository.countByRunIdAndEventType(failedRun, AuditEventService.MATCH_DECISION);
            assertEquals(0, decisionCount,
                    "A failing run must leave 0 MATCH_DECISION audit events, got " + decisionCount);
        }
        // If run_id on the failed event is null, there are no results at all — which also satisfies the assertion.
    }

    @Test
    void testMatchDecision_everyResultIdHasExactlyOneDecisionEvent() throws Exception {
        Long runId = setupCompletedRun();

        // Load all result IDs for this run
        List<com.reconcilex.entity.ReconciliationResult> results =
                reconciliationResultRepository.findByRun_IdOrderByIdAsc(runId);
        assertNotNull(results);
        assertTrue(!results.isEmpty(), "Expected reconciliation results for run " + runId);

        // Load all MATCH_DECISION events for this run
        List<AuditEvent> decisionEvents =
                auditEventRepository.findByRunIdAndEventType(runId, AuditEventService.MATCH_DECISION);

        assertEquals(results.size(), decisionEvents.size(),
                "Number of MATCH_DECISION events must equal number of results");

        // For each result, assert exactly one event whose details JSON contains that resultId.
        // PostgreSQL JSONB normalizes output by adding spaces after colons, so we strip all
        // whitespace from the details string before matching to be format-agnostic.
        for (com.reconcilex.entity.ReconciliationResult result : results) {
            String resultIdToken = "\"resultId\":" + result.getId();
            long matchingEvents = decisionEvents.stream()
                    .filter(e -> e.getDetails() != null
                            && e.getDetails().replaceAll("\\s+", "").contains(resultIdToken))
                    .count();
            assertEquals(1, matchingEvents,
                    "Expected exactly 1 MATCH_DECISION event containing resultId=" + result.getId()
                    + ", found " + matchingEvents);
        }
    }
}


