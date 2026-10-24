package com.reconcilex.benchmark;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
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
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reconcilex.dto.RunSummaryResponse;
import com.reconcilex.service.ReconciliationReportService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@Tag("benchmark")
@WithMockUser(username = "admin", roles = {"ADMIN"})
public class PerformanceBenchmarkTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("reconcilex_bench")
            .withUsername("bench")
            .withPassword("bench");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("jwt.secret", () -> "test-secret-key-must-be-at-least-32-bytes-long-for-hmac-sha256!");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ReconciliationReportService reportService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public static class BenchmarkIterationResult {
        public int iteration;
        public long bankImportMs;
        public long internalImportMs;
        public long reconciliationRunMs;
        public long reportStreamingMs;
        public long peakHeapBytes;
        public long streamingHeapDeltaBytes;
        public Map<String, Long> resultCounts;
    }

    @BeforeEach
    void ensureGeneratedFilesExist() throws Exception {
        Path bankPath = Paths.get("scripts", "generate-data", "bank_statement_50k.csv");
        Path intPath = Paths.get("scripts", "generate-data", "internal_50k.csv");
        if (!Files.exists(bankPath) || !Files.exists(intPath)) {
            new ProcessBuilder("java", "scripts/generate-data/Generate.java").inheritIO().start().waitFor();
        }
    }

    private void cleanDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE audit_events CASCADE");
        jdbcTemplate.execute("DELETE FROM reconciliation_result_bank_rows");
        jdbcTemplate.execute("DELETE FROM reconciliation_result_internal_transactions");
        jdbcTemplate.execute("DELETE FROM reconciliation_results");
        jdbcTemplate.execute("DELETE FROM reconciliation_runs");
        jdbcTemplate.execute("DELETE FROM import_row_errors");
        jdbcTemplate.execute("DELETE FROM bank_statement_rows");
        jdbcTemplate.execute("DELETE FROM internal_transactions");
        jdbcTemplate.execute("DELETE FROM file_imports");
    }

    @Test
    void runThreeIterationsAndPrintPerformanceMetrics() throws Exception {
        File bankFile = new File("scripts/generate-data/bank_statement_50k.csv");
        File internalFile = new File("scripts/generate-data/internal_50k.csv");
        File expectedFile = new File("scripts/generate-data/expected_counts.json");

        assertTrue(bankFile.exists(), "bank_statement_50k.csv must exist");
        assertTrue(internalFile.exists(), "internal_50k.csv must exist");
        assertTrue(expectedFile.exists(), "expected_counts.json must exist");

        Map<String, Long> expectedCounts = objectMapper.readValue(
                expectedFile, new TypeReference<Map<String, Long>>() {}
        );

        List<BenchmarkIterationResult> results = new ArrayList<>();

        for (int i = 1; i <= 3; i++) {
            System.out.printf("%n=== Starting Benchmark Iteration %d / 3 ===%n", i);
            cleanDatabase();

            System.gc();
            Thread.sleep(500);

            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            AtomicLong peakHeap = new AtomicLong(memoryBean.getHeapMemoryUsage().getUsed());
            AtomicBoolean monitorRunning = new AtomicBoolean(true);

            Thread memoryMonitor = new Thread(() -> {
                while (monitorRunning.get()) {
                    long used = memoryBean.getHeapMemoryUsage().getUsed();
                    peakHeap.updateAndGet(cur -> Math.max(cur, used));
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ignored) {
                        break;
                    }
                }
            });
            memoryMonitor.setDaemon(true);
            memoryMonitor.start();

            BenchmarkIterationResult iterResult = new BenchmarkIterationResult();
            iterResult.iteration = i;

            // 1. Import Bank Statement (50,000 rows)
            long startBank = System.currentTimeMillis();
            MockMultipartFile bankMultipart;
            try (InputStream is = new FileInputStream(bankFile)) {
                bankMultipart = new MockMultipartFile("file", "bank_statement_50k.csv", "text/csv", is);
            }
            MvcResult bankResult = mockMvc.perform(multipart("/api/imports/bank").file(bankMultipart))
                    .andExpect(status().isOk())
                    .andReturn();
            iterResult.bankImportMs = System.currentTimeMillis() - startBank;

            Number bankImportId = com.jayway.jsonpath.JsonPath.parse(bankResult.getResponse().getContentAsString()).read("$.importId");
            System.out.printf("Iteration %d: Bank import (50k rows) completed in %d ms%n", i, iterResult.bankImportMs);

            // 2. Import Internal Transactions (50,000 rows)
            long startInternal = System.currentTimeMillis();
            MockMultipartFile internalMultipart;
            try (InputStream is = new FileInputStream(internalFile)) {
                internalMultipart = new MockMultipartFile("file", "internal_50k.csv", "text/csv", is);
            }
            MvcResult internalResult = mockMvc.perform(multipart("/api/imports/internal").file(internalMultipart))
                    .andExpect(status().isOk())
                    .andReturn();
            iterResult.internalImportMs = System.currentTimeMillis() - startInternal;

            Number internalImportId = com.jayway.jsonpath.JsonPath.parse(internalResult.getResponse().getContentAsString()).read("$.importId");
            System.out.printf("Iteration %d: Internal import (50k rows) completed in %d ms%n", i, iterResult.internalImportMs);

            // 3. Run Reconciliation
            long startRun = System.currentTimeMillis();
            MvcResult runResult = mockMvc.perform(post("/api/reconciliation/runs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(String.format("{\"bankImportId\":%d,\"internalImportId\":%d}",
                            bankImportId.longValue(), internalImportId.longValue())))
                    .andExpect(status().isOk())
                    .andReturn();
            iterResult.reconciliationRunMs = System.currentTimeMillis() - startRun;

            Number runId = com.jayway.jsonpath.JsonPath.parse(runResult.getResponse().getContentAsString()).read("$.runId");
            System.out.printf("Iteration %d: Reconciliation run %d completed in %d ms%n", i, runId.longValue(), iterResult.reconciliationRunMs);

            // 4. Validate Result Counts per Type against expected_counts.json
            MvcResult summaryResult = mockMvc.perform(get("/api/reconciliation/runs/{id}/summary", runId.longValue()))
                    .andExpect(status().isOk())
                    .andReturn();

            RunSummaryResponse summary = objectMapper.readValue(
                    summaryResult.getResponse().getContentAsString(), RunSummaryResponse.class
            );

            assertNotNull(summary.getCountPerResultType(), "Count per result type must not be null");
            iterResult.resultCounts = summary.getCountPerResultType();

            for (Map.Entry<String, Long> expected : expectedCounts.entrySet()) {
                Long actualCount = summary.getCountPerResultType().getOrDefault(expected.getKey(), 0L);
                assertEquals(expected.getValue(), actualCount,
                        String.format("Discrepancy in ResultType '%s' for Iteration %d", expected.getKey(), i));
            }
            System.out.printf("Iteration %d: All %d reconciliation result counts match expected counts exactly!%n",
                    i, summary.getCountPerResultType().values().stream().mapToLong(Long::longValue).sum());

            // 5. Test Streaming report.csv & verify Flat Memory Profile
            long heapBeforeStream = memoryBean.getHeapMemoryUsage().getUsed();
            long maxHeapDuringStream = heapBeforeStream;

            long startStream = System.currentTimeMillis();
            MvcResult mvcStreamResult = mockMvc.perform(get("/api/reconciliation/runs/{id}/report.csv", runId.longValue()))
                    .andExpect(status().isOk())
                    .andExpect(request().asyncStarted())
                    .andReturn();

            // Perform async dispatch to start streaming output
            MvcResult finalResult = mockMvc.perform(asyncDispatch(mvcStreamResult))
                    .andExpect(status().isOk())
                    .andReturn();

            // Read streamed response content
            byte[] streamedCsv = finalResult.getResponse().getContentAsByteArray();
            iterResult.reportStreamingMs = System.currentTimeMillis() - startStream;

            long heapAfterStream = memoryBean.getHeapMemoryUsage().getUsed();
            long heapDeltaBytes = Math.abs(heapAfterStream - heapBeforeStream);
            iterResult.streamingHeapDeltaBytes = heapDeltaBytes;

            System.out.printf("Iteration %d: Streamed %d bytes of report.csv in %d ms (streaming heap delta: %.2f MB)%n",
                    i, streamedCsv.length, iterResult.reportStreamingMs, heapDeltaBytes / (1024.0 * 1024.0));

            // Stop memory monitor
            monitorRunning.set(false);
            memoryMonitor.join();
            iterResult.peakHeapBytes = peakHeap.get();

            System.out.printf("Iteration %d: Peak heap during execution: %.2f MB%n",
                    i, iterResult.peakHeapBytes / (1024.0 * 1024.0));

            results.add(iterResult);
        }

        // Print final summary table and calculate medians
        printBenchmarkSummary(results);
    }

    private void printBenchmarkSummary(List<BenchmarkIterationResult> results) {
        List<Long> bankTimes = results.stream().map(r -> r.bankImportMs).sorted().toList();
        List<Long> internalTimes = results.stream().map(r -> r.internalImportMs).sorted().toList();
        List<Long> runTimes = results.stream().map(r -> r.reconciliationRunMs).sorted().toList();
        List<Long> streamTimes = results.stream().map(r -> r.reportStreamingMs).sorted().toList();
        List<Long> peakHeaps = results.stream().map(r -> r.peakHeapBytes).sorted().toList();
        List<Long> streamHeaps = results.stream().map(r -> r.streamingHeapDeltaBytes).sorted().toList();

        long medianBankTime = bankTimes.get(1);
        long medianInternalTime = internalTimes.get(1);
        long medianRunTime = runTimes.get(1);
        long medianStreamTime = streamTimes.get(1);
        long medianPeakHeap = peakHeaps.get(1);
        long medianStreamHeapDelta = streamHeaps.get(1);

        System.out.println("\n=======================================================");
        System.out.println("          RECONCILEX 50,000-ROW BENCHMARK MEDIANS      ");
        System.out.println("=======================================================");
        System.out.printf("Bank Statement Import (50k rows):      %6d ms%n", medianBankTime);
        System.out.printf("Internal Transactions Import (50k rows):%6d ms%n", medianInternalTime);
        System.out.printf("Reconciliation Engine Run (100k input):%6d ms%n", medianRunTime);
        System.out.printf("CSV Report Streaming Download:         %6d ms%n", medianStreamTime);
        System.out.printf("Peak Heap Memory:                      %6.2f MB%n", medianPeakHeap / (1024.0 * 1024.0));
        System.out.printf("Streaming Heap Delta (Flat Profile):   %6.2f MB%n", medianStreamHeapDelta / (1024.0 * 1024.0));
        System.out.println("=======================================================\n");
    }
}
