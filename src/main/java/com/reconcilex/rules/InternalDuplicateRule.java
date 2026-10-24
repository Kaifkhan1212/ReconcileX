package com.reconcilex.rules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.reconcilex.entity.InternalTransaction;
import com.reconcilex.entity.PaymentStatus;
import com.reconcilex.entity.ReconciliationResultType;

/**
 * Order 50 – among unmatched SUCCESS internal rows sharing the same
 * (payment_reference, amount, currency), keep the one with the lexicographically
 * lowest transaction_id as the primary (it stays in the pool for exact-matching).
 * Every other row in the group is immediately consumed as INTERNAL_DUPLICATE_DETECTED.
 *
 * SCN-40: TXN1036 < TXN1037 lexicographically, so TXN1036 is primary and later
 * matches BNK2035 via ExactMatchRule; TXN1037 is flagged here.
 */
@Component
public class InternalDuplicateRule implements MatchingRule {

    public static final String RULE_NAME = "InternalDuplicateRule";
    public static final String RULE_CODE = "RULE_INTERNAL_DUPLICATE";

    @Override
    public String getName() {
        return RULE_NAME;
    }

    @Override
    public int getOrder() {
        return MatchingRuleOrder.INTERNAL_DUPLICATE;
    }

    @Override
    public void apply(ReconciliationContext context) {
        // Group unmatched SUCCESS rows by composite key
        Map<String, List<InternalTransaction>> groups = new HashMap<>();
        for (InternalTransaction it : context.getInternalTransactions()) {
            if (it.getPaymentStatus() != PaymentStatus.SUCCESS) {
                continue;
            }
            if (context.isInternalMatched(it.getId())) {
                continue;
            }
            if (it.getPaymentReference() == null || it.getPaymentReference().isBlank()) {
                continue;
            }
            String key = it.getPaymentReference()
                    + "|" + it.getAmount().toPlainString()
                    + "|" + it.getCurrency();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(it);
        }

        for (Map.Entry<String, List<InternalTransaction>> entry : groups.entrySet()) {
            List<InternalTransaction> group = entry.getValue();
            if (group.size() <= 1) {
                continue;
            }

            // Primary = lowest transaction_id (list is already sorted by transaction_id in context)
            // group is populated from context.getInternalTransactions() which is sorted, so first = primary
            InternalTransaction primary = group.get(0);
            for (int i = 1; i < group.size(); i++) {
                InternalTransaction duplicate = group.get(i);
                if (context.isInternalMatched(duplicate.getId())) {
                    continue;
                }
                String explanation = String.format(
                        "Internal transaction %s shares payment reference %s and amount %s %s with primary %s; flagged as internal duplicate.",
                        duplicate.getTransactionId(),
                        duplicate.getPaymentReference(),
                        duplicate.getAmount().toPlainString(),
                        duplicate.getCurrency(),
                        primary.getTransactionId()
                );
                context.addMatch(MatchOutcome.internalOnly(
                        ReconciliationResultType.INTERNAL_DUPLICATE_DETECTED,
                        RULE_NAME,
                        RULE_CODE,
                        explanation,
                        duplicate
                ));
            }
        }
    }
}
