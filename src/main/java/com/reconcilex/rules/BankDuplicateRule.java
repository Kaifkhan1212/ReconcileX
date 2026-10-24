package com.reconcilex.rules;

import org.springframework.stereotype.Component;

import com.reconcilex.entity.BankStatementRow;
import com.reconcilex.entity.ReconciliationResultType;

/**
 * Order 350 – after the primary 1-to-1 matching rules (100, 200, 300), find
 * unmatched bank CREDITS whose (payment_reference, credit_amount, currency)
 * key is already recorded as a primary match.  Those are duplicate bank
 * postings for a single settlement.
 *
 * SCN-41: BNK2036 was matched by ExactMatchRule; BNK2037 has the same key
 * and is flagged as BANK_DUPLICATE_DETECTED here.
 *
 * Only compares against primary 1-to-1 matches (orders 100-300), NOT against
 * batch-settlement or refund results.
 */
@Component
public class BankDuplicateRule implements MatchingRule {

    public static final String RULE_NAME = "BankDuplicateRule";
    public static final String RULE_CODE = "RULE_BANK_DUPLICATE";

    @Override
    public String getName() {
        return RULE_NAME;
    }

    @Override
    public int getOrder() {
        return MatchingRuleOrder.BANK_DUPLICATE;
    }

    @Override
    public void apply(ReconciliationContext context) {
        for (BankStatementRow b : context.getBankRows()) {
            if (context.isBankRowMatched(b.getId())) {
                continue;
            }
            // Only credit rows
            if (b.getCreditAmount() == null || b.getCreditAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
                continue;
            }
            if (!context.isPrimaryBankCreditMatched(b)) {
                continue;
            }
            String explanation = String.format(
                    "Bank credit %s (%.2f %s) has the same payment reference and amount as a previously matched bank credit; flagged as bank duplicate posting.",
                    b.getBankReference(),
                    b.getCreditAmount(),
                    b.getCurrency()
            );
            context.addMatch(MatchOutcome.bankOnly(
                    ReconciliationResultType.BANK_DUPLICATE_DETECTED,
                    RULE_NAME,
                    RULE_CODE,
                    explanation,
                    b
            ));
        }
    }
}
