package com.reconcilex.rules;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import org.springframework.stereotype.Component;

import com.reconcilex.entity.BankStatementRow;
import com.reconcilex.entity.InternalTransaction;
import com.reconcilex.entity.PaymentStatus;
import com.reconcilex.entity.ReconciliationResultType;

/**
 * Order 400 – groups unmatched SUCCESS internal rows that share the same
 * non-null settlement_reference (group size &ge; 2), sums their amounts, and
 * matches a single unmatched bank credit that has the same settlement_reference
 * and an exactly equal credit_amount (compareTo == 0).
 *
 * Uses O(N) map lookup via ReconciliationContext settlement-reference index.
 */
@Component
public class BatchSettlementRule implements MatchingRule {

    public static final String RULE_NAME = "BatchSettlementRule";
    public static final String RULE_CODE = "RULE_BATCH_SETTLEMENT";

    @Override
    public String getName() {
        return RULE_NAME;
    }

    @Override
    public int getOrder() {
        return MatchingRuleOrder.BATCH_SETTLEMENT;
    }

    @Override
    public void apply(ReconciliationContext context) {
        // Group unmatched SUCCESS internal rows by settlement_reference
        Map<String, List<InternalTransaction>> groups = new HashMap<>();
        for (InternalTransaction it : context.getInternalTransactions()) {
            if (it.getPaymentStatus() != PaymentStatus.SUCCESS) {
                continue;
            }
            if (context.isInternalMatched(it.getId())) {
                continue;
            }
            String settlRef = it.getSettlementReference();
            if (settlRef == null || settlRef.isBlank()) {
                continue;
            }
            groups.computeIfAbsent(settlRef, k -> new ArrayList<>()).add(it);
        }

        for (Map.Entry<String, List<InternalTransaction>> entry : groups.entrySet()) {
            String settlRef = entry.getKey();
            List<InternalTransaction> group = entry.getValue();
            if (group.size() < 2) {
                continue;
            }

            // Verify none were already matched between grouping and now
            List<InternalTransaction> eligible = new ArrayList<>();
            for (InternalTransaction it : group) {
                if (!context.isInternalMatched(it.getId())) {
                    eligible.add(it);
                }
            }
            if (eligible.size() < 2) {
                continue;
            }

            // Sum amounts
            BigDecimal sum = BigDecimal.ZERO;
            for (InternalTransaction it : eligible) {
                sum = sum.add(it.getAmount());
            }

            // Find unmatched bank credit with same settlement_reference and equal sum
            BankStatementRow matchedBank = null;
            for (BankStatementRow b : context.getCandidateBankRowsBySettlementRef(settlRef)) {
                if (context.isBankRowMatched(b.getId())) {
                    continue;
                }
                if (b.getCreditAmount() == null || b.getCreditAmount().compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                if (b.getCreditAmount().compareTo(sum) == 0) {
                    matchedBank = b;
                    break;
                }
            }

            if (matchedBank == null) {
                // Sum doesn't match – do not match
                continue;
            }

            StringJoiner txnIds = new StringJoiner(", ");
            for (InternalTransaction it : eligible) {
                txnIds.add(it.getTransactionId());
            }
            String explanation = String.format(
                    "Batch settlement: %d internal transactions (%s) with settlement reference %s sum to %s %s, matching bank credit %s.",
                    eligible.size(),
                    txnIds,
                    settlRef,
                    sum.toPlainString(),
                    eligible.get(0).getCurrency(),
                    matchedBank.getBankReference()
            );

            context.addMatch(MatchOutcome.batchMatch(
                    ReconciliationResultType.BATCH_SETTLEMENT,
                    RULE_NAME,
                    RULE_CODE,
                    explanation,
                    eligible,
                    matchedBank
            ));
        }
    }
}
