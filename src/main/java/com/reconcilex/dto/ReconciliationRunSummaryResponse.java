package com.reconcilex.dto;

import java.util.Map;

import com.reconcilex.entity.ReconciliationResultType;
import com.reconcilex.entity.ReconciliationRunStatus;

public class ReconciliationRunSummaryResponse {

    private Long runId;
    private ReconciliationRunStatus status;
    private Map<ReconciliationResultType, Long> resultCounts;
    private long unmatchedInternalCount;
    private long unmatchedBankCount;
    private long unprocessedBankDebits;

    public ReconciliationRunSummaryResponse() {
    }

    public ReconciliationRunSummaryResponse(
            Long runId,
            ReconciliationRunStatus status,
            Map<ReconciliationResultType, Long> resultCounts,
            long unmatchedInternalCount,
            long unmatchedBankCount,
            long unprocessedBankDebits
    ) {
        this.runId = runId;
        this.status = status;
        this.resultCounts = resultCounts;
        this.unmatchedInternalCount = unmatchedInternalCount;
        this.unmatchedBankCount = unmatchedBankCount;
        this.unprocessedBankDebits = unprocessedBankDebits;
    }

    public Long getRunId() {
        return runId;
    }

    public void setRunId(Long runId) {
        this.runId = runId;
    }

    public ReconciliationRunStatus getStatus() {
        return status;
    }

    public void setStatus(ReconciliationRunStatus status) {
        this.status = status;
    }

    public Map<ReconciliationResultType, Long> getResultCounts() {
        return resultCounts;
    }

    public void setResultCounts(Map<ReconciliationResultType, Long> resultCounts) {
        this.resultCounts = resultCounts;
    }

    public long getUnmatchedInternalCount() {
        return unmatchedInternalCount;
    }

    public void setUnmatchedInternalCount(long unmatchedInternalCount) {
        this.unmatchedInternalCount = unmatchedInternalCount;
    }

    public long getUnmatchedBankCount() {
        return unmatchedBankCount;
    }

    public void setUnmatchedBankCount(long unmatchedBankCount) {
        this.unmatchedBankCount = unmatchedBankCount;
    }

    public long getUnprocessedBankDebits() {
        return unprocessedBankDebits;
    }

    public void setUnprocessedBankDebits(long unprocessedBankDebits) {
        this.unprocessedBankDebits = unprocessedBankDebits;
    }
}
