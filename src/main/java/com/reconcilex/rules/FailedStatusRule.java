package com.reconcilex.rules;

import org.springframework.stereotype.Component;

import com.reconcilex.entity.InternalTransaction;
import com.reconcilex.entity.PaymentStatus;
import com.reconcilex.entity.ReconciliationResultType;

/**
 * Order 10 – internal transactions with status FAILED are consumed first and
 * classified as IGNORED_FAILED_STATUS.  They must never be matched against
 * bank rows or inspected by later rules.
 */
@Component
public class FailedStatusRule implements MatchingRule {

    public static final String RULE_NAME = "FailedStatusRule";
    public static final String RULE_CODE = "RULE_FAILED_STATUS";

    @Override
    public String getName() {
        return RULE_NAME;
    }

    @Override
    public int getOrder() {
        return MatchingRuleOrder.FAILED_STATUS;
    }

    @Override
    public void apply(ReconciliationContext context) {
        for (InternalTransaction it : context.getInternalTransactions()) {
            if (it.getPaymentStatus() != PaymentStatus.FAILED) {
                continue;
            }
            if (context.isInternalMatched(it.getId())) {
                continue;
            }
            String explanation = String.format(
                    "Internal transaction %s has status FAILED and should not appear in the bank statement.",
                    it.getTransactionId()
            );
            context.addMatch(MatchOutcome.internalOnly(
                    ReconciliationResultType.IGNORED_FAILED_STATUS,
                    RULE_NAME,
                    RULE_CODE,
                    explanation,
                    it
            ));
        }
    }
}
