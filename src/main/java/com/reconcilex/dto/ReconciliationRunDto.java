package com.reconcilex.dto;

import java.time.Instant;

import com.reconcilex.entity.ReconciliationRunStatus;

/**
 * Summary of one reconciliation run, returned in the paged list endpoint.
 */
public class ReconciliationRunDto {

    private Long id;
    private Long bankImportId;
    private Long internalImportId;
    private ReconciliationRunStatus status;
    private Instant startedAt;
    private Instant completedAt;

    public ReconciliationRunDto() {
    }

    public ReconciliationRunDto(
            Long id,
            Long bankImportId,
            Long internalImportId,
            ReconciliationRunStatus status,
            Instant startedAt,
            Instant completedAt) {
        this.id = id;
        this.bankImportId = bankImportId;
        this.internalImportId = internalImportId;
        this.status = status;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getBankImportId() { return bankImportId; }
    public void setBankImportId(Long bankImportId) { this.bankImportId = bankImportId; }

    public Long getInternalImportId() { return internalImportId; }
    public void setInternalImportId(Long internalImportId) { this.internalImportId = internalImportId; }

    public ReconciliationRunStatus getStatus() { return status; }
    public void setStatus(ReconciliationRunStatus status) { this.status = status; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
