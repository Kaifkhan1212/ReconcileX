package com.reconcilex.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "reconciliation_results")
public class ReconciliationResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private ReconciliationRun run;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_type", nullable = false, length = 40)
    private ReconciliationResultType resultType;

    @Column(name = "rule_code", length = 80)
    private String ruleCode;

    @Column(name = "rule_name", length = 80)
    private String ruleName;

    @Column(name = "amount_difference", precision = 19, scale = 2)
    private BigDecimal amountDifference;

    @Column(name = "explanation", columnDefinition = "text")
    private String explanation;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "reconciliation_result_bank_rows",
            joinColumns = @JoinColumn(name = "reconciliation_result_id"),
            inverseJoinColumns = @JoinColumn(name = "bank_statement_row_id")
    )
    private Set<BankStatementRow> bankRows = new LinkedHashSet<>();

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "reconciliation_result_internal_transactions",
            joinColumns = @JoinColumn(name = "reconciliation_result_id"),
            inverseJoinColumns = @JoinColumn(name = "internal_transaction_id")
    )
    private Set<InternalTransaction> internalTransactions =
            new LinkedHashSet<>();

    public ReconciliationResult() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public ReconciliationRun getRun() {
        return run;
    }

    public void setRun(ReconciliationRun run) {
        this.run = run;
    }

    public ReconciliationResultType getResultType() {
        return resultType;
    }

    public void setResultType(ReconciliationResultType resultType) {
        this.resultType = resultType;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public BigDecimal getAmountDifference() {
        return amountDifference;
    }

    public void setAmountDifference(BigDecimal amountDifference) {
        this.amountDifference = amountDifference;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Set<BankStatementRow> getBankRows() {
        return bankRows;
    }

    public void setBankRows(Set<BankStatementRow> bankRows) {
        this.bankRows = bankRows;
    }

    public Set<InternalTransaction> getInternalTransactions() {
        return internalTransactions;
    }

    public void setInternalTransactions(
            Set<InternalTransaction> internalTransactions) {
        this.internalTransactions = internalTransactions;
    }
}