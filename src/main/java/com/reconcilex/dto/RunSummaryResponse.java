package com.reconcilex.dto;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Financial and count summary for a reconciliation run.
 *
 * <h3>Value definitions</h3>
 * <dl>
 *   <dt>totalValuePerResultType</dt>
 *   <dd>For each result type, the sum of the internal transaction amounts
 *       linked to results of that type. Results with no internal links
 *       contribute zero to this map.</dd>
 *
 *   <dt>matchedValue</dt>
 *   <dd>Sum of internal transaction amounts for results whose type is
 *       EXACT_MATCH, AMOUNT_MISMATCH, DATE_MISMATCH, BATCH_SETTLEMENT,
 *       or REFUND_MATCH – i.e. results where the internal row has a
 *       corresponding bank row (paired results).</dd>
 *
 *   <dt>unmatchedInternalValue</dt>
 *   <dd>Sum of internal transaction amounts for results whose type is
 *       MISSING_IN_BANK, REFUND_PENDING_BANK, or INTERNAL_DUPLICATE_DETECTED
 *       – internal rows that could not be paired with any bank row.</dd>
 *
 *   <dt>unmatchedBankCreditValue</dt>
 *   <dd>Sum of bank credit_amount for results whose type is
 *       MISSING_IN_INTERNAL or BANK_DUPLICATE_DETECTED.</dd>
 *
 *   <dt>unmatchedBankDebitValue</dt>
 *   <dd>Sum of bank debit_amount for results whose type is
 *       MISSING_IN_INTERNAL or BANK_DUPLICATE_DETECTED.
 *       Credits and debits are reported separately; they are never summed
 *       together because they have opposite economic direction.</dd>
 *
 *   <dt>explainedValue</dt>
 *   <dd>Sum of bank debit_amount for results whose type is
 *       BANK_SERVICE_CHARGE or IGNORED_FAILED_STATUS – bank debits that
 *       have been explained by a rule and are not considered unreconciled.</dd>
 *
 *   <dt>unclassifiedInternalCount</dt>
 *   <dd>Number of internal transaction rows in the import that are NOT
 *       referenced by any reconciliation result for this run
 *       (importedRows - distinct linked rows).</dd>
 *
 *   <dt>unclassifiedBankCount</dt>
 *   <dd>Number of bank statement rows in the import that are NOT
 *       referenced by any reconciliation result for this run.</dd>
 * </dl>
 */
public class RunSummaryResponse {

    private Long runId;
    private String status;

    /** Per-result-type row counts. */
    private Map<String, Long> countPerResultType;

    /** Per-result-type sum of linked internal amounts (see class Javadoc). */
    private Map<String, BigDecimal> totalValuePerResultType;

    /** Sum of internal amounts for paired results (see class Javadoc). */
    private BigDecimal matchedValue;

    /** Sum of internal amounts for unmatched internal rows (see class Javadoc). */
    private BigDecimal unmatchedInternalValue;

    /** Sum of bank credit_amount for unmatched bank rows (see class Javadoc). */
    private BigDecimal unmatchedBankCreditValue;

    /** Sum of bank debit_amount for unmatched bank rows (see class Javadoc). */
    private BigDecimal unmatchedBankDebitValue;

    /** Sum of bank debit_amount explained by service-charge / failed-status rules (see class Javadoc). */
    private BigDecimal explainedValue;

    /** Internal rows in the import not linked to any result (see class Javadoc). */
    private long unclassifiedInternalCount;

    /** Bank rows in the import not linked to any result (see class Javadoc). */
    private long unclassifiedBankCount;

    public RunSummaryResponse() {
    }

    public Long getRunId() { return runId; }
    public void setRunId(Long runId) { this.runId = runId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Map<String, Long> getCountPerResultType() { return countPerResultType; }
    public void setCountPerResultType(Map<String, Long> countPerResultType) {
        this.countPerResultType = countPerResultType;
    }

    public Map<String, BigDecimal> getTotalValuePerResultType() { return totalValuePerResultType; }
    public void setTotalValuePerResultType(Map<String, BigDecimal> totalValuePerResultType) {
        this.totalValuePerResultType = totalValuePerResultType;
    }

    public BigDecimal getMatchedValue() { return matchedValue; }
    public void setMatchedValue(BigDecimal matchedValue) { this.matchedValue = matchedValue; }

    public BigDecimal getUnmatchedInternalValue() { return unmatchedInternalValue; }
    public void setUnmatchedInternalValue(BigDecimal unmatchedInternalValue) {
        this.unmatchedInternalValue = unmatchedInternalValue;
    }

    public BigDecimal getUnmatchedBankCreditValue() { return unmatchedBankCreditValue; }
    public void setUnmatchedBankCreditValue(BigDecimal unmatchedBankCreditValue) {
        this.unmatchedBankCreditValue = unmatchedBankCreditValue;
    }

    public BigDecimal getUnmatchedBankDebitValue() { return unmatchedBankDebitValue; }
    public void setUnmatchedBankDebitValue(BigDecimal unmatchedBankDebitValue) {
        this.unmatchedBankDebitValue = unmatchedBankDebitValue;
    }

    public BigDecimal getExplainedValue() { return explainedValue; }
    public void setExplainedValue(BigDecimal explainedValue) { this.explainedValue = explainedValue; }

    public long getUnclassifiedInternalCount() { return unclassifiedInternalCount; }
    public void setUnclassifiedInternalCount(long unclassifiedInternalCount) {
        this.unclassifiedInternalCount = unclassifiedInternalCount;
    }

    public long getUnclassifiedBankCount() { return unclassifiedBankCount; }
    public void setUnclassifiedBankCount(long unclassifiedBankCount) {
        this.unclassifiedBankCount = unclassifiedBankCount;
    }
}
