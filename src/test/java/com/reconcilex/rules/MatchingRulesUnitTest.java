package com.reconcilex.rules;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.reconcilex.entity.BankStatementRow;
import com.reconcilex.entity.InternalTransaction;
import com.reconcilex.entity.PaymentStatus;
import com.reconcilex.entity.ReconciliationResultType;

class MatchingRulesUnitTest {

    private InternalTransaction createInternal(
            Long id,
            String txnId,
            String paymentRef,
            BigDecimal amount,
            String currency,
            LocalDate date,
            PaymentStatus status
    ) {
        InternalTransaction it = new InternalTransaction();
        it.setId(id);
        it.setTransactionId(txnId);
        it.setPaymentReference(paymentRef);
        it.setAmount(amount);
        it.setCurrency(currency);
        it.setTransactionDate(date);
        it.setPaymentStatus(status);
        return it;
    }

    private BankStatementRow createBank(
            Long id,
            String bankRef,
            String paymentRef,
            BigDecimal creditAmount,
            BigDecimal debitAmount,
            String currency,
            LocalDate date
    ) {
        BankStatementRow b = new BankStatementRow();
        b.setId(id);
        b.setBankReference(bankRef);
        b.setPaymentReference(paymentRef);
        b.setCreditAmount(creditAmount);
        b.setDebitAmount(debitAmount);
        b.setCurrency(currency);
        b.setTransactionDate(date);
        return b;
    }

    @Test
    void testExactMatchRule_Success() {
        InternalTransaction it = createInternal(1L, "TXN1", "PAY1", new BigDecimal("100.00"), "INR", LocalDate.of(2026, 10, 1), PaymentStatus.SUCCESS);
        BankStatementRow b = createBank(10L, "BNK1", "PAY1", new BigDecimal("100.00"), BigDecimal.ZERO, "INR", LocalDate.of(2026, 10, 1));

        ReconciliationContext context = new ReconciliationContext(List.of(it), List.of(b), 3);
        ExactMatchRule rule = new ExactMatchRule();
        rule.apply(context);

        assertEquals(1, context.getOutcomes().size());
        MatchOutcome outcome = context.getOutcomes().get(0);
        assertEquals(ReconciliationResultType.EXACT_MATCH, outcome.getResultType());
        assertTrue(context.isInternalMatched(1L));
        assertTrue(context.isBankRowMatched(10L));
    }

    @Test
    void testExactMatchRule_NonSuccessInternalStatusIgnored() {
        InternalTransaction it = createInternal(1L, "TXN1", "PAY1", new BigDecimal("100.00"), "INR", LocalDate.of(2026, 10, 1), PaymentStatus.FAILED);
        BankStatementRow b = createBank(10L, "BNK1", "PAY1", new BigDecimal("100.00"), BigDecimal.ZERO, "INR", LocalDate.of(2026, 10, 1));

        ReconciliationContext context = new ReconciliationContext(List.of(it), List.of(b), 3);
        ExactMatchRule rule = new ExactMatchRule();
        rule.apply(context);

        assertTrue(context.getOutcomes().isEmpty());
        assertFalse(context.isInternalMatched(1L));
        assertFalse(context.isBankRowMatched(10L));
    }

    @Test
    void testExactMatchRule_DebitRowIgnored() {
        InternalTransaction it = createInternal(1L, "TXN1", "PAY1", new BigDecimal("100.00"), "INR", LocalDate.of(2026, 10, 1), PaymentStatus.SUCCESS);
        BankStatementRow b = createBank(10L, "BNK1", "PAY1", BigDecimal.ZERO, new BigDecimal("100.00"), "INR", LocalDate.of(2026, 10, 1));

        ReconciliationContext context = new ReconciliationContext(List.of(it), List.of(b), 3);
        ExactMatchRule rule = new ExactMatchRule();
        rule.apply(context);

        assertTrue(context.getOutcomes().isEmpty());
    }

    @Test
    void testDateToleranceBoundary_3DaysVs4Days() {
        InternalTransaction it3 = createInternal(1L, "TXN1", "PAY1", new BigDecimal("500.00"), "INR", LocalDate.of(2026, 10, 1), PaymentStatus.SUCCESS);
        BankStatementRow b3 = createBank(10L, "BNK1", "PAY1", new BigDecimal("500.00"), BigDecimal.ZERO, "INR", LocalDate.of(2026, 10, 4)); // 3 days difference

        ReconciliationContext context3 = new ReconciliationContext(List.of(it3), List.of(b3), 3);
        DateToleranceRule rule = new DateToleranceRule();
        rule.apply(context3);

        assertEquals(1, context3.getOutcomes().size());
        assertEquals(ReconciliationResultType.DATE_MISMATCH, context3.getOutcomes().get(0).getResultType());

        // 4 days difference (outside 3-day tolerance)
        InternalTransaction it4 = createInternal(2L, "TXN2", "PAY2", new BigDecimal("500.00"), "INR", LocalDate.of(2026, 10, 1), PaymentStatus.SUCCESS);
        BankStatementRow b4 = createBank(20L, "BNK2", "PAY2", new BigDecimal("500.00"), BigDecimal.ZERO, "INR", LocalDate.of(2026, 10, 5)); // 4 days difference

        ReconciliationContext context4 = new ReconciliationContext(List.of(it4), List.of(b4), 3);
        rule.apply(context4);

        assertTrue(context4.getOutcomes().isEmpty(), "Bank row 4 days away should not match when tolerance is 3 days");
    }

    @Test
    void testAmountMismatchRule_DifferenceCalculationAndPrecision() {
        // 1850.50 vs 1850.00 -> difference must be bank minus internal = -0.50
        InternalTransaction it = createInternal(1L, "TXN1", "PAY1", new BigDecimal("1850.50"), "INR", LocalDate.of(2026, 10, 1), PaymentStatus.SUCCESS);
        BankStatementRow b = createBank(10L, "BNK1", "PAY1", new BigDecimal("1850.00"), BigDecimal.ZERO, "INR", LocalDate.of(2026, 10, 1));

        ReconciliationContext context = new ReconciliationContext(List.of(it), List.of(b), 3);
        AmountMismatchRule rule = new AmountMismatchRule();
        rule.apply(context);

        assertEquals(1, context.getOutcomes().size());
        MatchOutcome outcome = context.getOutcomes().get(0);
        assertEquals(ReconciliationResultType.AMOUNT_MISMATCH, outcome.getResultType());
        assertEquals(0, new BigDecimal("-0.50").compareTo(outcome.getAmountDifference()));
    }

    @Test
    void testAmountMismatchRule_DateOutsideToleranceLeavesUnclassified() {
        InternalTransaction it = createInternal(1L, "TXN1", "PAY1", new BigDecimal("2000.00"), "INR", LocalDate.of(2026, 10, 1), PaymentStatus.SUCCESS);
        BankStatementRow b = createBank(10L, "BNK1", "PAY1", new BigDecimal("1900.00"), BigDecimal.ZERO, "INR", LocalDate.of(2026, 10, 6)); // 5 days diff

        ReconciliationContext context = new ReconciliationContext(List.of(it), List.of(b), 3);
        AmountMismatchRule rule = new AmountMismatchRule();
        rule.apply(context);

        assertTrue(context.getOutcomes().isEmpty());
    }

    @Test
    void testTieBreaking_SmallestDateDifferenceFirst() {
        InternalTransaction it = createInternal(1L, "TXN1", "PAY1", new BigDecimal("100.00"), "INR", LocalDate.of(2026, 10, 5), PaymentStatus.SUCCESS);
        BankStatementRow b1 = createBank(10L, "BNK_FAR", "PAY1", new BigDecimal("100.00"), BigDecimal.ZERO, "INR", LocalDate.of(2026, 10, 7)); // 2 days diff
        BankStatementRow b2 = createBank(11L, "BNK_NEAR", "PAY1", new BigDecimal("100.00"), BigDecimal.ZERO, "INR", LocalDate.of(2026, 10, 6)); // 1 day diff

        ReconciliationContext context = new ReconciliationContext(List.of(it), List.of(b1, b2), 3);
        DateToleranceRule rule = new DateToleranceRule();
        rule.apply(context);

        assertEquals(1, context.getOutcomes().size());
        MatchOutcome outcome = context.getOutcomes().get(0);
        assertEquals(11L, outcome.getBankRow().getId(), "Should choose bank row with smaller date difference");
    }

    @Test
    void testTieBreaking_LowestBankReferenceOnEqualDateDifference() {
        InternalTransaction it = createInternal(1L, "TXN1", "PAY1", new BigDecimal("100.00"), "INR", LocalDate.of(2026, 10, 5), PaymentStatus.SUCCESS);
        BankStatementRow bZ = createBank(10L, "BNK_Z", "PAY1", new BigDecimal("100.00"), BigDecimal.ZERO, "INR", LocalDate.of(2026, 10, 6)); // 1 day diff
        BankStatementRow bA = createBank(11L, "BNK_A", "PAY1", new BigDecimal("100.00"), BigDecimal.ZERO, "INR", LocalDate.of(2026, 10, 6)); // 1 day diff

        ReconciliationContext context = new ReconciliationContext(List.of(it), List.of(bZ, bA), 3);
        DateToleranceRule rule = new DateToleranceRule();
        rule.apply(context);

        assertEquals(1, context.getOutcomes().size());
        MatchOutcome outcome = context.getOutcomes().get(0);
        assertEquals("BNK_A", outcome.getBankRow().getBankReference(), "Should choose lowest bank reference lexicographically");
    }

    @Test
    void testRuleMajorProcessing_FuzzyRuleMustNotStealBankRowNeededByExactMatch() {
        // Transaction 1 has 1-day date difference with BNK1
        InternalTransaction txn1 = createInternal(1L, "TXN01", "PAY1", new BigDecimal("100.00"), "INR", LocalDate.of(2026, 10, 1), PaymentStatus.SUCCESS);
        // Transaction 2 has EXACT date match with BNK1
        InternalTransaction txn2 = createInternal(2L, "TXN02", "PAY1", new BigDecimal("100.00"), "INR", LocalDate.of(2026, 10, 2), PaymentStatus.SUCCESS);

        BankStatementRow bnk1 = createBank(10L, "BNK1", "PAY1", new BigDecimal("100.00"), BigDecimal.ZERO, "INR", LocalDate.of(2026, 10, 2));

        ReconciliationContext context = new ReconciliationContext(List.of(txn1, txn2), List.of(bnk1), 3);

        ReconciliationRuleEngine engine = new ReconciliationRuleEngine(List.of(
                new DateToleranceRule(),
                new ExactMatchRule(),
                new AmountMismatchRule()
        ));
        engine.execute(context);

        assertEquals(1, context.getOutcomes().size());
        MatchOutcome outcome = context.getOutcomes().get(0);
        assertEquals(ReconciliationResultType.EXACT_MATCH, outcome.getResultType());
        assertEquals("TXN02", outcome.getInternalTransaction().getTransactionId(), "Exact match should have consumed BNK1; DateTolerance must not steal it");
    }
}
