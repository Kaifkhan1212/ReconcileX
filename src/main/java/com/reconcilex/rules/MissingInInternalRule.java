package com.reconcilex.rules;

import org.springframework.stereotype.Component;

import com.reconcilex.entity.BankStatementRow;
import com.reconcilex.entity.ReconciliationResultType;

/**
 * Order 920 – every remaining unmatched bank row (credit or debit) that was not
 * classified by any earlier rule becomes MISSING_IN_INTERNAL.
 *
 * This is the final catch-all for bank rows and guarantees the invariant that
 * all 50 bank rows are classified exactly once after a full run.
 */
@Component
public class MissingInInternalRule implements MatchingRule {

    public static final String RULE_NAME = "MissingInInternalRule";
    public static final String RULE_CODE = "RULE_MISSING_IN_INTERNAL";

    @Override
    public String getName() {
        return RULE_NAME;
    }

    @Override
    public int getOrder() {
        return MatchingRuleOrder.MISSING_IN_INTERNAL;
    }

    @Override
    public void apply(ReconciliationContext context) {
        for (BankStatementRow b : context.getBankRows()) {
            if (context.isBankRowMatched(b.getId())) {
                continue;
            }
            String explanation = String.format(
                    "Bank row %s (%s %s credit, %s %s debit) has no matching internal transaction record.",
                    b.getBankReference(),
                    b.getCreditAmount() != null ? b.getCreditAmount().toPlainString() : "0",
                    b.getCurrency(),
                    b.getDebitAmount() != null ? b.getDebitAmount().toPlainString() : "0",
                    b.getCurrency()
            );
            context.addMatch(MatchOutcome.bankOnly(
                    ReconciliationResultType.MISSING_IN_INTERNAL,
                    RULE_NAME,
                    RULE_CODE,
                    explanation,
                    b
            ));
        }
    }
}
