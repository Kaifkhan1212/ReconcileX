package com.reconcilex.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.reconcilex.dto.ReconciliationRunSummaryResponse;
import com.reconcilex.entity.ReconciliationRun;

/**
 * Orchestrates reconciliation run lifecycle.
 *
 * <p>The HTTP controller calls {@link #startRunAndExecuteAsync} which:
 * <ol>
 *   <li>Creates the run record synchronously (REQUIRES_NEW txn) so the run ID is
 *       available immediately for the 202 response.</li>
 *   <li>Dispatches {@link #executeAsync} on the {@code reconciliationExecutor} thread
 *       pool — the HTTP thread returns 202 while matching proceeds in the background.</li>
 * </ol>
 * The frontend polls GET /api/reconciliation/runs/{id} until status changes from
 * RUNNING to COMPLETED or FAILED.
 *
 * <p>{@link #runReconciliation} is a synchronous variant kept for integration tests.
 */
@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private final ReconciliationRunLifecycleService lifecycleService;
    private final ReconciliationExecutionService executionService;
    private final AuditEventService auditEventService;

    public ReconciliationService(
            ReconciliationRunLifecycleService lifecycleService,
            ReconciliationExecutionService executionService,
            AuditEventService auditEventService
    ) {
        this.lifecycleService = lifecycleService;
        this.executionService = executionService;
        this.auditEventService = auditEventService;
    }

    /**
     * Starts a reconciliation run synchronously (so the caller gets back a run ID),
     * then fires the heavy matching work asynchronously on the Spring task executor.
     *
     * @return the newly-created {@link ReconciliationRun} with status RUNNING
     */
    public ReconciliationRun startRunAndExecuteAsync(Long bankImportId, Long internalImportId) {
        // 1. Create run record — REQUIRES_NEW txn commits immediately, run ID is stable
        ReconciliationRun run = lifecycleService.startRun(bankImportId, internalImportId);
        Long runId = run.getId();

        auditEventService.recordRunStarted(runId);

        // 2. Dispatch matching to the async thread pool; HTTP thread is free to return 202
        executeAsync(runId, bankImportId, internalImportId);

        return run;
    }

    /**
     * Executes the matching engine on the Spring {@code reconciliationExecutor} thread pool.
     * Updates run status to COMPLETED or FAILED when done.
     */
    @Async("reconciliationExecutor")
    public void executeAsync(Long runId, Long bankImportId, Long internalImportId) {
        log.info("Async reconciliation starting: runId={} bankImport={} internalImport={}",
                runId, bankImportId, internalImportId);
        try {
            ReconciliationRunSummaryResponse response =
                    executionService.executeAndSave(runId, bankImportId, internalImportId);
            lifecycleService.completeRun(runId);

            int totalResults = response.getResultCounts() == null ? 0 :
                    response.getResultCounts().values().stream()
                            .mapToInt(Long::intValue).sum();
            auditEventService.recordRunCompleted(runId, totalResults);

            log.info("Async reconciliation completed: runId={} totalResults={}", runId, totalResults);
        } catch (Exception ex) {
            log.error("Async reconciliation failed: runId={} error={}", runId, ex.getMessage(), ex);
            lifecycleService.failRun(runId);
            auditEventService.recordRunFailed(runId, ex.getMessage());
        }
    }

    /**
     * Synchronous reconciliation — used by integration tests and the failure-simulation test.
     * Runs the full lifecycle (start → execute → complete/fail) on the caller's thread.
     *
     * @return {@link ReconciliationRunSummaryResponse} with final counts
     * @throws RuntimeException if the execution service throws (run will be FAILED)
     */
    public ReconciliationRunSummaryResponse runReconciliation(Long bankImportId, Long internalImportId) {
        ReconciliationRun run = lifecycleService.startRun(bankImportId, internalImportId);
        Long runId = run.getId();

        auditEventService.recordRunStarted(runId);

        try {
            ReconciliationRunSummaryResponse response =
                    executionService.executeAndSave(runId, bankImportId, internalImportId);
            lifecycleService.completeRun(runId);

            int totalResults = response.getResultCounts() == null ? 0 :
                    response.getResultCounts().values().stream()
                            .mapToInt(Long::intValue).sum();
            auditEventService.recordRunCompleted(runId, totalResults);

            return response;
        } catch (Exception ex) {
            lifecycleService.failRun(runId);
            auditEventService.recordRunFailed(runId, ex.getMessage());
            throw ex;
        }
    }

}
