package com.reconcilex.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A single reconciliation result row returned by the filtered results endpoint.
 * Linked internal transaction IDs and bank references are fetched in the same
 * SQL query using {@code string_agg} to avoid N+1 queries.
 */
public class ReconciliationResultDto {

    private Long id;
    private String resultType;
    private String ruleName;
    private String ruleCode;
    private BigDecimal amountDifference;
    private String explanation;

    /** Comma-separated list of linked internal transaction IDs (from string_agg). */
    private String internalTransactionIds;

    /** Comma-separated list of linked bank references (from string_agg). */
    private String bankReferences;

    /**
     * Earliest transaction date among all linked internal transactions.
     * Used as the representative date for date filtering.
     */
    private LocalDate internalTransactionDate;

    /**
     * Earliest transaction date among all linked bank rows.
     */
    private LocalDate bankTransactionDate;

    public ReconciliationResultDto() {
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getResultType() { return resultType; }
    public void setResultType(String resultType) { this.resultType = resultType; }

    public String getRuleName() { return ruleName; }
    public void setRuleName(String ruleName) { this.ruleName = ruleName; }

    public String getRuleCode() { return ruleCode; }
    public void setRuleCode(String ruleCode) { this.ruleCode = ruleCode; }

    public BigDecimal getAmountDifference() { return amountDifference; }
    public void setAmountDifference(BigDecimal amountDifference) {
        this.amountDifference = amountDifference;
    }

    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }

    public String getInternalTransactionIds() { return internalTransactionIds; }
    public void setInternalTransactionIds(String internalTransactionIds) {
        this.internalTransactionIds = internalTransactionIds;
    }

    public String getBankReferences() { return bankReferences; }
    public void setBankReferences(String bankReferences) { this.bankReferences = bankReferences; }

    public LocalDate getInternalTransactionDate() { return internalTransactionDate; }
    public void setInternalTransactionDate(LocalDate internalTransactionDate) {
        this.internalTransactionDate = internalTransactionDate;
    }

    public LocalDate getBankTransactionDate() { return bankTransactionDate; }
    public void setBankTransactionDate(LocalDate bankTransactionDate) {
        this.bankTransactionDate = bankTransactionDate;
    }
}
