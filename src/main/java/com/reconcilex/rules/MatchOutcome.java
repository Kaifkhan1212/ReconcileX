package com.reconcilex.rules;

import java.math.BigDecimal;

import com.reconcilex.entity.BankStatementRow;
import com.reconcilex.entity.InternalTransaction;
import com.reconcilex.entity.ReconciliationResultType;

public class MatchOutcome {

    private final ReconciliationResultType resultType;
    private final String ruleName;
    private final String ruleCode;
    private final String explanation;
    private final BigDecimal amountDifference;
    private final InternalTransaction internalTransaction;
    private final BankStatementRow bankRow;

    public MatchOutcome(
            ReconciliationResultType resultType,
            String ruleName,
            String ruleCode,
            String explanation,
            BigDecimal amountDifference,
            InternalTransaction internalTransaction,
            BankStatementRow bankRow
    ) {
        this.resultType = resultType;
        this.ruleName = ruleName;
        this.ruleCode = ruleCode;
        this.explanation = explanation;
        this.amountDifference = amountDifference;
        this.internalTransaction = internalTransaction;
        this.bankRow = bankRow;
    }

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

    public InternalTransaction getInternalTransaction() {
        return internalTransaction;
    }

    public BankStatementRow getBankRow() {
        return bankRow;
    }
}
