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
public class DateToleranceRule implements MatchingRule {

    public static final String RULE_NAME = "DateToleranceRule";
    public static final String RULE_CODE = "RULE_DATE_TOLERANCE";

    @Override
    public String getName() {
        return RULE_NAME;
    }

    @Override
    public int getOrder() {
        return MatchingRuleOrder.DATE_TOLERANCE;
    }

    @Override
    public void apply(ReconciliationContext context) {
        int toleranceDays = context.getDateToleranceDays();

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
                    .filter(b -> {
                        long diffDays = Math.abs(ChronoUnit.DAYS.between(it.getTransactionDate(), b.getTransactionDate()));
                        return diffDays >= 1 && diffDays <= toleranceDays;
                    })
                    .min(tieBreaker);

            if (matchedBankRow.isPresent()) {
                BankStatementRow bankRow = matchedBankRow.get();
                long diffDays = Math.abs(ChronoUnit.DAYS.between(it.getTransactionDate(), bankRow.getTransactionDate()));
                String explanation = String.format(
                        "References and amount (%s %s) match but internal date is %s and bank statement date is %s (%d-day settlement lag).",
                        it.getAmount().toPlainString(),
                        it.getCurrency(),
                        it.getTransactionDate(),
                        bankRow.getTransactionDate(),
                        diffDays
                );
                context.addMatch(new MatchOutcome(
                        ReconciliationResultType.DATE_MISMATCH,
                        RULE_NAME,
                        RULE_CODE,
                        explanation,
                        null,
                        it,
                        bankRow
                ));
                context.registerPrimaryBankCreditMatch(bankRow);
            }
        }
    }
}
