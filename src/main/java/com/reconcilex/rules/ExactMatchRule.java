package com.reconcilex.rules;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.reconcilex.entity.BankStatementRow;
import com.reconcilex.entity.InternalTransaction;
import com.reconcilex.entity.PaymentStatus;
import com.reconcilex.entity.ReconciliationResultType;

@Component
public class ExactMatchRule implements MatchingRule {

    public static final String RULE_NAME = "ExactMatchRule";
    public static final String RULE_CODE = "RULE_EXACT_MATCH";

    @Override
    public String getName() {
        return RULE_NAME;
    }

    @Override
    public int getOrder() {
        return MatchingRuleOrder.EXACT_MATCH;
    }

    @Override
    public void apply(ReconciliationContext context) {
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

            Comparator<BankStatementRow> tieBreaker = Comparator
                    .comparingLong((BankStatementRow b) -> Math.abs(ChronoUnit.DAYS.between(it.getTransactionDate(), b.getTransactionDate())))
                    .thenComparing(BankStatementRow::getBankReference, Comparator.nullsLast(String::compareTo));

            Optional<BankStatementRow> matchedBankRow = context.getCandidateBankRows(it.getPaymentReference()).stream()
                    .filter(b -> !context.isBankRowMatched(b.getId()))
                    .filter(b -> b.getCreditAmount() != null && b.getCreditAmount().compareTo(BigDecimal.ZERO) > 0)
                    .filter(b -> b.getCurrency() != null && b.getCurrency().equalsIgnoreCase(it.getCurrency()))
                    .filter(b -> b.getCreditAmount().compareTo(it.getAmount()) == 0)
                    .filter(b -> Math.abs(ChronoUnit.DAYS.between(it.getTransactionDate(), b.getTransactionDate())) == 0)
                    .min(tieBreaker);

            if (matchedBankRow.isPresent()) {
                BankStatementRow bankRow = matchedBankRow.get();
                String explanation = String.format(
                        "Transaction amount (%s %s) and payment reference (%s) match exactly on same date.",
                        it.getAmount().toPlainString(),
                        it.getCurrency(),
                        it.getPaymentReference()
                );
                context.addMatch(new MatchOutcome(
                        ReconciliationResultType.EXACT_MATCH,
                        RULE_NAME,
                        RULE_CODE,
                        explanation,
                        null,
                        it,
                        bankRow
                ));
            }
        }
    }
}
