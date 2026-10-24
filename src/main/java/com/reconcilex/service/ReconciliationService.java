package com.reconcilex.service;

import org.springframework.stereotype.Service;

import com.reconcilex.dto.ReconciliationRunSummaryResponse;
import com.reconcilex.entity.ReconciliationRun;

@Service
public class ReconciliationService {

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

    public ReconciliationRunSummaryResponse runReconciliation(Long bankImportId, Long internalImportId) {
        ReconciliationRun run = lifecycleService.startRun(bankImportId, internalImportId);
        Long runId = run.getId();

        // Audit: run started (committed by startRun's REQUIRES_NEW txn already)
        auditEventService.recordRunStarted(runId);

        try {
            ReconciliationRunSummaryResponse response =
                    executionService.executeAndSave(runId, bankImportId, internalImportId);
            lifecycleService.completeRun(runId);

            // Count total results across all types
            int totalResults = response.getResultCounts() == null ? 0 :
                    response.getResultCounts().values().stream()
                            .mapToInt(Long::intValue).sum();
            auditEventService.recordRunCompleted(runId, totalResults);

            return response;
        } catch (Exception ex) {
            lifecycleService.failRun(runId);
            // REQUIRES_NEW: survives the outer rollback
            auditEventService.recordRunFailed(runId, ex.getMessage());
            throw ex;
        }
    }
}
