package com.reconcilex.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.reconcilex.entity.FileImport;
import com.reconcilex.entity.FileImportStatus;
import com.reconcilex.entity.ReconciliationRun;
import com.reconcilex.entity.ReconciliationRunStatus;
import com.reconcilex.repository.FileImportRepository;
import com.reconcilex.repository.ReconciliationRunRepository;

@Service
public class ReconciliationRunLifecycleService {

    private final ReconciliationRunRepository reconciliationRunRepository;
    private final FileImportRepository fileImportRepository;

    public ReconciliationRunLifecycleService(
            ReconciliationRunRepository reconciliationRunRepository,
            FileImportRepository fileImportRepository
    ) {
        this.reconciliationRunRepository = reconciliationRunRepository;
        this.fileImportRepository = fileImportRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ReconciliationRun startRun(Long bankImportId, Long internalImportId) {
        if (bankImportId == null || internalImportId == null) {
            throw new IllegalArgumentException("bankImportId and internalImportId must not be null");
        }

        FileImport bankImport = fileImportRepository.findById(bankImportId)
                .orElseThrow(() -> new IllegalArgumentException("Bank import not found: " + bankImportId));
        FileImport internalImport = fileImportRepository.findById(internalImportId)
                .orElseThrow(() -> new IllegalArgumentException("Internal import not found: " + internalImportId));

        if (bankImport.getStatus() != FileImportStatus.COMPLETED) {
            throw new IllegalArgumentException("Bank import is not COMPLETED. Current status: " + bankImport.getStatus());
        }
        if (internalImport.getStatus() != FileImportStatus.COMPLETED) {
            throw new IllegalArgumentException("Internal import is not COMPLETED. Current status: " + internalImport.getStatus());
        }

        ReconciliationRun run = new ReconciliationRun();
        run.setBankImport(bankImport);
        run.setInternalImport(internalImport);
        run.setStatus(ReconciliationRunStatus.RUNNING);
        run.setStartedAt(Instant.now());

        return reconciliationRunRepository.saveAndFlush(run);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeRun(Long runId) {
        reconciliationRunRepository.findById(runId).ifPresent(run -> {
            run.setStatus(ReconciliationRunStatus.COMPLETED);
            run.setCompletedAt(Instant.now());
            reconciliationRunRepository.saveAndFlush(run);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failRun(Long runId) {
        reconciliationRunRepository.findById(runId).ifPresent(run -> {
            run.setStatus(ReconciliationRunStatus.FAILED);
            run.setCompletedAt(Instant.now());
            reconciliationRunRepository.saveAndFlush(run);
        });
    }
}
