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
public class AmountMismatchRule implements MatchingRule {

    public static final String RULE_NAME = "AmountMismatchRule";
    public static final String RULE_CODE = "RULE_AMOUNT_MISMATCH";

    @Override
    public String getName() {
        return RULE_NAME;
    }

    @Override
    public int getOrder() {
        return MatchingRuleOrder.AMOUNT_MISMATCH;
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
                    .filter(b -> b.getCreditAmount().compareTo(it.getAmount()) != 0)
                    .filter(b -> Math.abs(ChronoUnit.DAYS.between(it.getTransactionDate(), b.getTransactionDate())) <= toleranceDays)
                    .min(tieBreaker);

            if (matchedBankRow.isPresent()) {
                BankStatementRow bankRow = matchedBankRow.get();
                BigDecimal amountDifference = bankRow.getCreditAmount().subtract(it.getAmount());
                String explanation = String.format(
                        "Payment reference %s matches but internal amount is %s %s while bank credit is %s %s (discrepancy of %s %s).",
                        it.getPaymentReference(),
                        it.getAmount().toPlainString(),
                        it.getCurrency(),
                        bankRow.getCreditAmount().toPlainString(),
                        bankRow.getCurrency(),
                        amountDifference.abs().toPlainString(),
                        it.getCurrency()
                );
                context.addMatch(new MatchOutcome(
                        ReconciliationResultType.AMOUNT_MISMATCH,
                        RULE_NAME,
                        RULE_CODE,
                        explanation,
                        amountDifference,
                        it,
                        bankRow
                ));
                context.registerPrimaryBankCreditMatch(bankRow);
            }
        }
    }
}
