package com.reconcilex.rules;

import org.springframework.stereotype.Component;

import com.reconcilex.entity.InternalTransaction;
import com.reconcilex.entity.PaymentStatus;
import com.reconcilex.entity.ReconciliationResultType;

/**
 * Order 910 – any unmatched internal transaction whose status is SUCCESS or
 * PENDING becomes MISSING_IN_BANK.
 *
 * Running at order 910 guarantees all matching rules (including BatchSettlement,
 * Refund, etc.) have already run.  Every SUCCESS/PENDING row that reaches here
 * had no matching bank row.
 */
@Component
public class MissingInBankRule implements MatchingRule {

    public static final String RULE_NAME = "MissingInBankRule";
    public static final String RULE_CODE = "RULE_MISSING_IN_BANK";

    @Override
    public String getName() {
        return RULE_NAME;
    }

    @Override
    public int getOrder() {
        return MatchingRuleOrder.MISSING_IN_BANK;
    }

    @Override
    public void apply(ReconciliationContext context) {
        for (InternalTransaction it : context.getInternalTransactions()) {
            if (context.isInternalMatched(it.getId())) {
                continue;
            }
            if (it.getPaymentStatus() != PaymentStatus.SUCCESS
                    && it.getPaymentStatus() != PaymentStatus.PENDING) {
                continue;
            }
            String explanation = String.format(
                    "Internal transaction %s (%s %s, %s) has no matching bank statement entry.",
                    it.getTransactionId(),
                    it.getAmount().toPlainString(),
                    it.getCurrency(),
                    it.getPaymentStatus()
            );
            context.addMatch(MatchOutcome.internalOnly(
                    ReconciliationResultType.MISSING_IN_BANK,
                    RULE_NAME,
                    RULE_CODE,
                    explanation,
                    it
            ));
        }
    }
}
