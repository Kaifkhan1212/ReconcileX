package com.reconcilex.service;

import org.springframework.stereotype.Service;

import com.reconcilex.dto.ReconciliationRunSummaryResponse;
import com.reconcilex.entity.ReconciliationRun;

@Service
public class ReconciliationService {

    private final ReconciliationRunLifecycleService lifecycleService;
    private final ReconciliationExecutionService executionService;

    public ReconciliationService(
            ReconciliationRunLifecycleService lifecycleService,
            ReconciliationExecutionService executionService
    ) {
        this.lifecycleService = lifecycleService;
        this.executionService = executionService;
    }

    public ReconciliationRunSummaryResponse runReconciliation(Long bankImportId, Long internalImportId) {
        ReconciliationRun run = lifecycleService.startRun(bankImportId, internalImportId);
        Long runId = run.getId();

        try {
            ReconciliationRunSummaryResponse response =
                    executionService.executeAndSave(runId, bankImportId, internalImportId);
            lifecycleService.completeRun(runId);
            return response;
        } catch (Exception ex) {
            lifecycleService.failRun(runId);
            throw ex;
        }
    }
}
