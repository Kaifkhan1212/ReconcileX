package com.reconcilex.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.reconcilex.dto.ReconciliationRunRequest;
import com.reconcilex.dto.ReconciliationRunSummaryResponse;
import com.reconcilex.service.ReconciliationService;

@RestController
@RequestMapping("/api/reconciliation/runs")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @PostMapping
    public ResponseEntity<ReconciliationRunSummaryResponse> createRun(
            @RequestBody ReconciliationRunRequest request
    ) {
        if (request == null || request.getBankImportId() == null || request.getInternalImportId() == null) {
            throw new IllegalArgumentException("bankImportId and internalImportId are required");
        }
        ReconciliationRunSummaryResponse response = reconciliationService.runReconciliation(
                request.getBankImportId(),
                request.getInternalImportId()
        );
        return ResponseEntity.ok(response);
    }
}
