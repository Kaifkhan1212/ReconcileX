package com.reconcilex.rules;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.reconcilex.entity.BankStatementRow;
import com.reconcilex.entity.InternalTransaction;
import com.reconcilex.entity.PaymentStatus;
import com.reconcilex.entity.ReconciliationResultType;

/**
 * Order 500 – two sub-rules in one pass:
 *
 * 1. REFUNDED internal + matching bank DEBIT (debit_amount > 0) with same
 *    payment_reference, currency, amount, and date within tolerance
 *    → REFUND_MATCH.
 *
 * 2. REFUND_INITIATED internal with no matching debit found → REFUND_PENDING_BANK.
 *
 * Uses the payment_reference bank index from ReconciliationContext; no nested loops
 * over all bank rows.
 *
 * Invariant: every REFUNDED and every REFUND_INITIATED row is consumed by this rule.
 */
@Component
public class RefundRule implements MatchingRule {

    public static final String RULE_NAME = "RefundRule";
    public static final String RULE_CODE = "RULE_REFUND";

    @Override
    public String getName() {
        return RULE_NAME;
    }

    @Override
    public int getOrder() {
        return MatchingRuleOrder.REFUND;
    }

    @Override
    public void apply(ReconciliationContext context) {
        int toleranceDays = context.getDateToleranceDays();

        for (InternalTransaction it : context.getInternalTransactions()) {
            if (context.isInternalMatched(it.getId())) {
                continue;
            }

            boolean isRefunded = it.getPaymentStatus() == PaymentStatus.REFUNDED;
            boolean isRefundInitiated = it.getPaymentStatus() == PaymentStatus.REFUND_INITIATED;

            if (!isRefunded && !isRefundInitiated) {
                continue;
            }

            if (isRefunded) {
                // Try to match a bank debit row
                Comparator<BankStatementRow> tieBreaker = Comparator
                        .comparingLong((BankStatementRow b) ->
                                Math.abs(ChronoUnit.DAYS.between(it.getTransactionDate(), b.getTransactionDate())))
                        .thenComparing(BankStatementRow::getBankReference, Comparator.nullsLast(String::compareTo));

                List<BankStatementRow> candidates = context.getCandidateBankRows(it.getPaymentReference());

                Optional<BankStatementRow> matchedDebit = candidates.stream()
                        .filter(b -> !context.isBankRowMatched(b.getId()))
                        .filter(b -> b.getDebitAmount() != null && b.getDebitAmount().compareTo(BigDecimal.ZERO) > 0)
                        .filter(b -> b.getCreditAmount() == null || b.getCreditAmount().compareTo(BigDecimal.ZERO) == 0)
                        .filter(b -> b.getCurrency() != null && b.getCurrency().equalsIgnoreCase(it.getCurrency()))
                        .filter(b -> b.getDebitAmount().compareTo(it.getAmount()) == 0)
                        .filter(b -> Math.abs(ChronoUnit.DAYS.between(
                                it.getTransactionDate(), b.getTransactionDate())) <= toleranceDays)
                        .min(tieBreaker);

                if (matchedDebit.isPresent()) {
                    BankStatementRow debitRow = matchedDebit.get();
                    String explanation = String.format(
                            "Internal refund %s (%s %s, REFUNDED) matches bank debit %s (%s %s) on payment reference %s.",
                            it.getTransactionId(),
                            it.getAmount().toPlainString(),
                            it.getCurrency(),
                            debitRow.getBankReference(),
                            debitRow.getDebitAmount().toPlainString(),
                            debitRow.getCurrency(),
                            it.getPaymentReference()
                    );
                    context.addMatch(new MatchOutcome(
                            ReconciliationResultType.REFUND_MATCH,
                            RULE_NAME,
                            RULE_CODE,
                            explanation,
                            null,
                            it,
                            debitRow
                    ));
                } else {
                    // REFUNDED but no matching debit -> REFUND_PENDING_BANK
                    String explanation = String.format(
                            "Internal refund %s (%s %s, REFUNDED) has no matching bank debit; treated as refund pending bank.",
                            it.getTransactionId(),
                            it.getAmount().toPlainString(),
                            it.getCurrency()
                    );
                    context.addMatch(MatchOutcome.internalOnly(
                            ReconciliationResultType.REFUND_PENDING_BANK,
                            RULE_NAME,
                            RULE_CODE,
                            explanation,
                            it
                    ));
                }
            }

            if (isRefundInitiated && !context.isInternalMatched(it.getId())) {
                // REFUND_INITIATED always → REFUND_PENDING_BANK (bank has not debited yet)
                String explanation = String.format(
                        "Internal refund %s (%s %s, REFUND_INITIATED) has not yet been debited by the bank.",
                        it.getTransactionId(),
                        it.getAmount().toPlainString(),
                        it.getCurrency()
                );
                context.addMatch(MatchOutcome.internalOnly(
                        ReconciliationResultType.REFUND_PENDING_BANK,
                        RULE_NAME,
                        RULE_CODE,
                        explanation,
                        it
                ));
            }
        }
    }
}
