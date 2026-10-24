package com.reconcilex.rules;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import com.reconcilex.entity.BankStatementRow;
import com.reconcilex.entity.InternalTransaction;
import com.reconcilex.entity.ReconciliationResultType;

public class MatchOutcome {

    private final ReconciliationResultType resultType;
    private final String ruleName;
    private final String ruleCode;
    private final String explanation;
    private final BigDecimal amountDifference;
    private final List<InternalTransaction> internalTransactions;
    private final List<BankStatementRow> bankRows;

    // --- Full constructor (N internal, M bank) ---
    public MatchOutcome(
            ReconciliationResultType resultType,
            String ruleName,
            String ruleCode,
            String explanation,
            BigDecimal amountDifference,
            List<InternalTransaction> internalTransactions,
            List<BankStatementRow> bankRows
    ) {
        this.resultType = resultType;
        this.ruleName = ruleName;
        this.ruleCode = ruleCode;
        this.explanation = explanation;
        this.amountDifference = amountDifference;
        this.internalTransactions = internalTransactions != null
                ? Collections.unmodifiableList(internalTransactions)
                : Collections.emptyList();
        this.bankRows = bankRows != null
                ? Collections.unmodifiableList(bankRows)
                : Collections.emptyList();
    }

    // --- Convenience: 1 internal + 1 bank (the most common case) ---
    public MatchOutcome(
            ReconciliationResultType resultType,
            String ruleName,
            String ruleCode,
            String explanation,
            BigDecimal amountDifference,
            InternalTransaction internalTransaction,
            BankStatementRow bankRow
    ) {
        this(
                resultType,
                ruleName,
                ruleCode,
                explanation,
                amountDifference,
                internalTransaction != null ? List.of(internalTransaction) : Collections.emptyList(),
                bankRow != null ? List.of(bankRow) : Collections.emptyList()
        );
    }

    // --- Convenience: 1 internal + 0 bank ---
    public static MatchOutcome internalOnly(
            ReconciliationResultType resultType,
            String ruleName,
            String ruleCode,
            String explanation,
            InternalTransaction internalTransaction
    ) {
        return new MatchOutcome(resultType, ruleName, ruleCode, explanation, null,
                List.of(internalTransaction), Collections.emptyList());
    }

    // --- Convenience: 0 internal + 1 bank ---
    public static MatchOutcome bankOnly(
            ReconciliationResultType resultType,
            String ruleName,
            String ruleCode,
            String explanation,
            BankStatementRow bankRow
    ) {
        return new MatchOutcome(resultType, ruleName, ruleCode, explanation, null,
                Collections.emptyList(), List.of(bankRow));
    }

    // --- Convenience: N internal + 1 bank (BATCH_SETTLEMENT) ---
    public static MatchOutcome batchMatch(
            ReconciliationResultType resultType,
            String ruleName,
            String ruleCode,
            String explanation,
            List<InternalTransaction> internalTransactions,
            BankStatementRow bankRow
    ) {
        return new MatchOutcome(resultType, ruleName, ruleCode, explanation, null,
                internalTransactions, List.of(bankRow));
    }

    // --- Accessors ---

    public ReconciliationResultType getResultType() {
        return resultType;
    }

    public String getRuleName() {
        return ruleName;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public String getExplanation() {
        return explanation;
    }

    public BigDecimal getAmountDifference() {
        return amountDifference;
    }

    public List<InternalTransaction> getInternalTransactions() {
        return internalTransactions;
    }

    public List<BankStatementRow> getBankRows() {
        return bankRows;
    }

    /**
     * Backward-compatible single-internal accessor.
     * Returns the first internal transaction, or null if none.
     */
    public InternalTransaction getInternalTransaction() {
        return internalTransactions.isEmpty() ? null : internalTransactions.get(0);
    }

    /**
     * Backward-compatible single-bank accessor.
     * Returns the first bank row, or null if none.
     */
    public BankStatementRow getBankRow() {
        return bankRows.isEmpty() ? null : bankRows.get(0);
    }
}
