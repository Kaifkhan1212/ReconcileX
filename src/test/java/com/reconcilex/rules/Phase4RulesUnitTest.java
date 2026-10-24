package com.reconcilex.rules;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.reconcilex.config.MatchingProperties;
import com.reconcilex.entity.BankStatementRow;
import com.reconcilex.entity.InternalTransaction;
import com.reconcilex.entity.PaymentStatus;
import com.reconcilex.entity.ReconciliationResultType;

/**
 * Unit tests for Phase 4 rules.  No Spring context, no database.
 */
class Phase4RulesUnitTest {

    // ────────────────────────────────────────────────────────────── helpers ──

    private static long idSeq = 1;

    private InternalTransaction intern(
            String txnId, String payRef, BigDecimal amount, String currency,
            LocalDate date, PaymentStatus status, String settlRef) {
        InternalTransaction it = new InternalTransaction();
        it.setId(idSeq++);
        it.setTransactionId(txnId);
        it.setPaymentReference(payRef);
        it.setAmount(amount);
        it.setCurrency(currency);
        it.setTransactionDate(date);
        it.setPaymentStatus(status);
        it.setSettlementReference(settlRef);
        return it;
    }

    private BankStatementRow bank(
            String bankRef, String payRef, BigDecimal credit, BigDecimal debit,
            String currency, LocalDate date, String settlRef, String description) {
        BankStatementRow b = new BankStatementRow();
        b.setId(idSeq++);
        b.setBankReference(bankRef);
        b.setPaymentReference(payRef);
        b.setCreditAmount(credit);
        b.setDebitAmount(debit);
        b.setCurrency(currency);
        b.setTransactionDate(date);
        b.setSettlementReference(settlRef);
        b.setDescription(description);
        return b;
    }

    private MatchingProperties defaultProps() {
        return new MatchingProperties();
    }

    private static final LocalDate D1 = LocalDate.of(2026, 10, 1);
    private static final LocalDate D2 = LocalDate.of(2026, 10, 2);

    // ─────────────────────────────────────────── FailedStatusRule ────────────

    @Test
    void failedStatusRule_consumesFailedRows() {
        InternalTransaction failed = intern("TXN-F", "PAY-F", new BigDecimal("100"), "INR", D1, PaymentStatus.FAILED, null);
        InternalTransaction ok = intern("TXN-OK", "PAY-OK", new BigDecimal("100"), "INR", D1, PaymentStatus.SUCCESS, null);
        ReconciliationContext ctx = new ReconciliationContext(List.of(failed, ok), List.of(), 3);
        new FailedStatusRule().apply(ctx);
        assertEquals(1, ctx.getOutcomes().size());
        assertEquals(ReconciliationResultType.IGNORED_FAILED_STATUS, ctx.getOutcomes().get(0).getResultType());
        assertTrue(ctx.isInternalMatched(failed.getId()));
    }

    // ─────────────────────────────────────────── InternalDuplicateRule ───────

    @Test
    void internalDuplicateRule_flagsSecondRow_primaryStaysUnmatched() {
        InternalTransaction p = intern("TXN-1036", "PAY-DUP", new BigDecimal("2100"), "INR", D1, PaymentStatus.SUCCESS, null);
        InternalTransaction d = intern("TXN-1037", "PAY-DUP", new BigDecimal("2100"), "INR", D1, PaymentStatus.SUCCESS, null);
        ReconciliationContext ctx = new ReconciliationContext(List.of(p, d), List.of(), 3);
        new InternalDuplicateRule().apply(ctx);
        assertEquals(1, ctx.getOutcomes().size());
        assertEquals(ReconciliationResultType.INTERNAL_DUPLICATE_DETECTED, ctx.getOutcomes().get(0).getResultType());
        // Primary TXN-1036 is NOT consumed
        assertTrue(ctx.isInternalMatched(d.getId()));
    }

    @Test
    void internalDuplicateRule_onlyOnePairPerGroup() {
        // Three rows with same key → 2 duplicates flagged, 1 primary
        InternalTransaction p = intern("TXN-A", "PAY-X", new BigDecimal("500"), "INR", D1, PaymentStatus.SUCCESS, null);
        InternalTransaction d1 = intern("TXN-B", "PAY-X", new BigDecimal("500"), "INR", D1, PaymentStatus.SUCCESS, null);
        InternalTransaction d2 = intern("TXN-C", "PAY-X", new BigDecimal("500"), "INR", D1, PaymentStatus.SUCCESS, null);
        ReconciliationContext ctx = new ReconciliationContext(List.of(p, d1, d2), List.of(), 3);
        new InternalDuplicateRule().apply(ctx);
        assertEquals(2, ctx.getOutcomes().size());
    }

    // ─────────────────────────────────────────── BankDuplicateRule ───────────

    @Test
    void bankDuplicateRule_detectsSecondPosting() {
        InternalTransaction it = intern("TXN-1038", "PAY-DUP-02", new BigDecimal("3500"), "INR", D1, PaymentStatus.SUCCESS, null);
        BankStatementRow b1 = bank("BNK2036", "PAY-DUP-02", new BigDecimal("3500"), BigDecimal.ZERO, "INR", D1, null, "Razorpay payout");
        BankStatementRow b2 = bank("BNK2037", "PAY-DUP-02", new BigDecimal("3500"), BigDecimal.ZERO, "INR", D2, null, "Razorpay duplicate posting");

        ReconciliationContext ctx = new ReconciliationContext(List.of(it), List.of(b1, b2), 3);

        // ExactMatchRule matches it + b1 and registers the primary key
        new ExactMatchRule().apply(ctx);
        assertEquals(1, ctx.getOutcomes().size());
        assertTrue(ctx.isBankRowMatched(b1.getId()));

        // BankDuplicateRule should detect b2
        new BankDuplicateRule().apply(ctx);
        assertEquals(2, ctx.getOutcomes().size());
        assertEquals(ReconciliationResultType.BANK_DUPLICATE_DETECTED, ctx.getOutcomes().get(1).getResultType());
        assertTrue(ctx.isBankRowMatched(b2.getId()));
    }

    @Test
    void bankDuplicateRule_doesNotMatchDebits() {
        // A debit row with same payment ref should not be flagged as bank duplicate
        InternalTransaction it = intern("TXN-X", "PAY-X", new BigDecimal("1000"), "INR", D1, PaymentStatus.SUCCESS, null);
        BankStatementRow credit = bank("BNK-C", "PAY-X", new BigDecimal("1000"), BigDecimal.ZERO, "INR", D1, null, "Credit");
        BankStatementRow debit = bank("BNK-D", "PAY-X", BigDecimal.ZERO, new BigDecimal("1000"), "INR", D1, null, "Debit");

        ReconciliationContext ctx = new ReconciliationContext(List.of(it), List.of(credit, debit), 3);
        new ExactMatchRule().apply(ctx);
        new BankDuplicateRule().apply(ctx);

        // Only 1 outcome – the debit is NOT flagged as bank duplicate
        assertEquals(1, ctx.getOutcomes().size());
    }

    // ─────────────────────────────────────────── BatchSettlementRule ─────────

    @Test
    void batchSettlementRule_matchesCorrectSum() {
        InternalTransaction t1 = intern("TXN1", "PAY1", new BigDecimal("1250"), "INR", D1, PaymentStatus.SUCCESS, "SETL-BATCH-1");
        InternalTransaction t2 = intern("TXN2", "PAY2", new BigDecimal("2750"), "INR", D1, PaymentStatus.SUCCESS, "SETL-BATCH-1");
        InternalTransaction t3 = intern("TXN3", "PAY3", new BigDecimal("1000"), "INR", D1, PaymentStatus.SUCCESS, "SETL-BATCH-1");
        BankStatementRow b = bank("BNK-BATCH", "BATCH-01", new BigDecimal("5000"), BigDecimal.ZERO, "INR", D1, "SETL-BATCH-1", "Batch");

        ReconciliationContext ctx = new ReconciliationContext(List.of(t1, t2, t3), List.of(b), 3);
        new BatchSettlementRule().apply(ctx);

        assertEquals(1, ctx.getOutcomes().size());
        MatchOutcome o = ctx.getOutcomes().get(0);
        assertEquals(ReconciliationResultType.BATCH_SETTLEMENT, o.getResultType());
        assertEquals(3, o.getInternalTransactions().size());
        assertEquals(1, o.getBankRows().size());
    }

    @Test
    void batchSettlementRule_wrongSumNotMatched() {
        InternalTransaction t1 = intern("TXN1", "PAY1", new BigDecimal("1000"), "INR", D1, PaymentStatus.SUCCESS, "SETL-BATCH-2");
        InternalTransaction t2 = intern("TXN2", "PAY2", new BigDecimal("2000"), "INR", D1, PaymentStatus.SUCCESS, "SETL-BATCH-2");
        // Bank credit is 4000, but sum is 3000 → no match
        BankStatementRow b = bank("BNK-BATCH2", "BATCH-02", new BigDecimal("4000"), BigDecimal.ZERO, "INR", D1, "SETL-BATCH-2", "Batch");

        ReconciliationContext ctx = new ReconciliationContext(List.of(t1, t2), List.of(b), 3);
        new BatchSettlementRule().apply(ctx);

        assertTrue(ctx.getOutcomes().isEmpty(), "Wrong sum must not produce a match");
    }

    // ─────────────────────────────────────────── RefundRule ──────────────────

    @Test
    void refundRule_refundedMatchesBankDebit() {
        InternalTransaction refunded = intern("TXN-R", "PAY-REF-1", new BigDecimal("1500"), "INR", D1, PaymentStatus.REFUNDED, null);
        BankStatementRow debit = bank("BNK-D", "PAY-REF-1", BigDecimal.ZERO, new BigDecimal("1500"), "INR", D1, null, "Refund debit");

        ReconciliationContext ctx = new ReconciliationContext(List.of(refunded), List.of(debit), 3);
        new RefundRule().apply(ctx);

        assertEquals(1, ctx.getOutcomes().size());
        assertEquals(ReconciliationResultType.REFUND_MATCH, ctx.getOutcomes().get(0).getResultType());
    }

    @Test
    void refundRule_refundedWithNoMatchingDebitBecomesRefundPendingBank() {
        InternalTransaction refunded = intern("TXN-R2", "PAY-REF-2", new BigDecimal("800"), "INR", D1, PaymentStatus.REFUNDED, null);

        ReconciliationContext ctx = new ReconciliationContext(List.of(refunded), List.of(), 3);
        new RefundRule().apply(ctx);

        assertEquals(1, ctx.getOutcomes().size());
        assertEquals(ReconciliationResultType.REFUND_PENDING_BANK, ctx.getOutcomes().get(0).getResultType());
    }

    @Test
    void refundRule_refundInitiatedAlwaysBecomesRefundPendingBank() {
        InternalTransaction initiated = intern("TXN-RI", "PAY-REF-3", new BigDecimal("800"), "INR", D1, PaymentStatus.REFUND_INITIATED, null);
        // Even with a debit available (different payment ref), REFUND_INITIATED → REFUND_PENDING_BANK
        ReconciliationContext ctx = new ReconciliationContext(List.of(initiated), List.of(), 3);
        new RefundRule().apply(ctx);

        assertEquals(1, ctx.getOutcomes().size());
        assertEquals(ReconciliationResultType.REFUND_PENDING_BANK, ctx.getOutcomes().get(0).getResultType());
    }

    @Test
    void refundRule_refundedWithDifferentAmountNotMatched() {
        InternalTransaction refunded = intern("TXN-R3", "PAY-REF-4", new BigDecimal("1500"), "INR", D1, PaymentStatus.REFUNDED, null);
        BankStatementRow debit = bank("BNK-D2", "PAY-REF-4", BigDecimal.ZERO, new BigDecimal("999"), "INR", D1, null, "Refund debit");

        ReconciliationContext ctx = new ReconciliationContext(List.of(refunded), List.of(debit), 3);
        new RefundRule().apply(ctx);

        // Amount mismatch → REFUND_PENDING_BANK, not REFUND_MATCH
        assertEquals(1, ctx.getOutcomes().size());
        assertEquals(ReconciliationResultType.REFUND_PENDING_BANK, ctx.getOutcomes().get(0).getResultType());
    }

    // ─────────────────────────────────────────── ServiceChargeRule ───────────

    @Test
    void serviceChargeRule_matchesKeywordWholeWord() {
        MatchingProperties props = new MatchingProperties();
        BankStatementRow debit = bank("BNK-SC", null, BigDecimal.ZERO, new BigDecimal("590"), "INR", D1, null,
                "Monthly account maintenance charges + GST");

        ReconciliationContext ctx = new ReconciliationContext(List.of(), List.of(debit), 3);
        new ServiceChargeRule(props).apply(ctx);

        assertEquals(1, ctx.getOutcomes().size());
        assertEquals(ReconciliationResultType.BANK_SERVICE_CHARGE, ctx.getOutcomes().get(0).getResultType());
    }

    @Test
    void serviceChargeRule_doesNotMatchCredits() {
        MatchingProperties props = new MatchingProperties();
        BankStatementRow credit = bank("BNK-C", null, new BigDecimal("590"), BigDecimal.ZERO, "INR", D1, null,
                "Monthly account maintenance charges + GST");

        ReconciliationContext ctx = new ReconciliationContext(List.of(), List.of(credit), 3);
        new ServiceChargeRule(props).apply(ctx);

        assertTrue(ctx.getOutcomes().isEmpty(), "ServiceChargeRule must not match credit rows");
    }

    /**
     * Verifies that BNK2034 ("Chargeback debit for disputed card txn") is NOT
     * matched by the service-charge keyword "charge" (whole-word only).
     */
    @Test
    void serviceChargeRule_chargebackNotMatchedByChargeKeyword() {
        MatchingProperties props = new MatchingProperties();
        BankStatementRow chargeback = bank("BNK2034", "CHG-BCK-771", BigDecimal.ZERO, new BigDecimal("950"),
                "INR", D1, null, "Chargeback debit for disputed card txn");

        ReconciliationContext ctx = new ReconciliationContext(List.of(), List.of(chargeback), 3);
        new ServiceChargeRule(props).apply(ctx);

        assertTrue(ctx.getOutcomes().isEmpty(),
                "'Chargeback' must not be matched by whole-word keyword 'charge'");
    }

    // ─────────────────────────────────────────── UnexpectedDebitRule ─────────

    @Test
    void unexpectedDebitRule_matchesChargebackDescription() {
        MatchingProperties props = new MatchingProperties();
        BankStatementRow chargeback = bank("BNK2034", "CHG-BCK-771", BigDecimal.ZERO, new BigDecimal("950"),
                "INR", D1, null, "Chargeback debit for disputed card txn");

        ReconciliationContext ctx = new ReconciliationContext(List.of(), List.of(chargeback), 3);
        new UnexpectedDebitRule(props).apply(ctx);

        assertEquals(1, ctx.getOutcomes().size());
        assertEquals(ReconciliationResultType.UNEXPECTED_BANK_DEBIT, ctx.getOutcomes().get(0).getResultType());
    }

    @Test
    void unexpectedDebitRule_doesNotMatchCredits() {
        MatchingProperties props = new MatchingProperties();
        BankStatementRow credit = bank("BNK-C", null, new BigDecimal("950"), BigDecimal.ZERO, "INR", D1, null,
                "Chargeback refund credit");

        ReconciliationContext ctx = new ReconciliationContext(List.of(), List.of(credit), 3);
        new UnexpectedDebitRule(props).apply(ctx);

        assertTrue(ctx.getOutcomes().isEmpty(), "UnexpectedDebitRule must not match credit rows");
    }

    // ─────────────────────────────────────────── MissingInBankRule ───────────

    @Test
    void missingInBankRule_classifiesSuccessAndPending() {
        InternalTransaction success = intern("TXN-S", "PAY-S", new BigDecimal("1000"), "INR", D1, PaymentStatus.SUCCESS, null);
        InternalTransaction pending = intern("TXN-P", "PAY-P", new BigDecimal("2000"), "INR", D1, PaymentStatus.PENDING, null);
        ReconciliationContext ctx = new ReconciliationContext(List.of(success, pending), List.of(), 3);
        new MissingInBankRule().apply(ctx);
        assertEquals(2, ctx.getOutcomes().size());
        assertTrue(ctx.getOutcomes().stream()
                .allMatch(o -> o.getResultType() == ReconciliationResultType.MISSING_IN_BANK));
    }

    // ─────────────────────────────────────────── MissingInInternalRule ───────

    @Test
    void missingInInternalRule_classifiesAllRemainingBankRows() {
        BankStatementRow credit = bank("BNK-C", null, new BigDecimal("15000"), BigDecimal.ZERO, "INR", D1, null, "NEFT credit");
        BankStatementRow debit = bank("BNK-D", null, BigDecimal.ZERO, new BigDecimal("12000"), "INR", D1, null, "Loan EMI auto-debit");
        ReconciliationContext ctx = new ReconciliationContext(List.of(), List.of(credit, debit), 3);
        new MissingInInternalRule().apply(ctx);
        assertEquals(2, ctx.getOutcomes().size());
        assertTrue(ctx.getOutcomes().stream()
                .allMatch(o -> o.getResultType() == ReconciliationResultType.MISSING_IN_INTERNAL));
    }

    // ─────────────────────────── Full-engine invariant (property-style) ──────

    // ─────────────────────────── BNK2034, BNK2044, BNK2045 end-to-end ───────

    /**
     * Requirement 5:
     * BNK2034 ("Chargeback debit for disputed card txn") is NOT matched by
     * the service-charge keyword "charge" (whole word only) and ends up as
     * UNEXPECTED_BANK_DEBIT.
     * BNK2044 ("Commercial business vehicle loan auto-debit") and BNK2045
     * ("Commercial electricity auto-debit BESCOM") end up as MISSING_IN_INTERNAL.
     */
    @Test
    void testBnk2034NotMatchedByServiceChargeAndEndsUpUnexpectedDebit_andBnk2044Bnk2045MissingInInternal() {
        MatchingProperties props = defaultProps();

        BankStatementRow bnk2034 = bank("BNK2034", "CHG-BCK-771", BigDecimal.ZERO, new BigDecimal("950.00"),
                "INR", LocalDate.of(2026, 10, 9), null, "Chargeback debit for disputed card txn");
        BankStatementRow bnk2044 = bank("BNK2044", "EMI-DEB-551", BigDecimal.ZERO, new BigDecimal("12000.00"),
                "INR", LocalDate.of(2026, 10, 10), null, "Commercial business vehicle loan auto-debit");
        BankStatementRow bnk2045 = bank("BNK2045", "UTIL-EB-902", BigDecimal.ZERO, new BigDecimal("2500.00"),
                "INR", LocalDate.of(2026, 10, 11), null, "Commercial electricity auto-debit BESCOM");

        List<BankStatementRow> bankRows = List.of(bnk2034, bnk2044, bnk2045);
        ReconciliationContext ctx = new ReconciliationContext(List.of(), bankRows, 3);

        ReconciliationRuleEngine engine = new ReconciliationRuleEngine(List.of(
                new FailedStatusRule(),
                new InternalDuplicateRule(),
                new ExactMatchRule(),
                new DateToleranceRule(),
                new AmountMismatchRule(),
                new BankDuplicateRule(),
                new BatchSettlementRule(),
                new RefundRule(),
                new ServiceChargeRule(props),
                new UnexpectedDebitRule(props),
                new MissingInBankRule(),
                new MissingInInternalRule()
        ));

        engine.execute(ctx);

        assertEquals(3, ctx.getOutcomes().size());

        MatchOutcome outcome2034 = ctx.getOutcomes().stream()
                .filter(o -> o.getBankRows().stream().anyMatch(b -> "BNK2034".equals(b.getBankReference())))
                .findFirst()
                .orElseThrow();
        assertEquals(ReconciliationResultType.UNEXPECTED_BANK_DEBIT, outcome2034.getResultType(),
                "BNK2034 must be UNEXPECTED_BANK_DEBIT");
        assertEquals("UnexpectedDebitRule", outcome2034.getRuleName());

        MatchOutcome outcome2044 = ctx.getOutcomes().stream()
                .filter(o -> o.getBankRows().stream().anyMatch(b -> "BNK2044".equals(b.getBankReference())))
                .findFirst()
                .orElseThrow();
        assertEquals(ReconciliationResultType.MISSING_IN_INTERNAL, outcome2044.getResultType(),
                "BNK2044 must be MISSING_IN_INTERNAL");
        assertEquals("MissingInInternalRule", outcome2044.getRuleName());

        MatchOutcome outcome2045 = ctx.getOutcomes().stream()
                .filter(o -> o.getBankRows().stream().anyMatch(b -> "BNK2045".equals(b.getBankReference())))
                .findFirst()
                .orElseThrow();
        assertEquals(ReconciliationResultType.MISSING_IN_INTERNAL, outcome2045.getResultType(),
                "BNK2045 must be MISSING_IN_INTERNAL");
        assertEquals("MissingInInternalRule", outcome2045.getRuleName());
    }

    // ─────────────────────────── Full-engine invariant (property-style) ──────

    /**
     * Requirement 3:
     * Property-style test that for several small generated datasets every row
     * is classified exactly once.
     */
    @Test
    void propertyTest_severalSmallGeneratedDatasets_everyRowClassifiedExactlyOnce() {
        MatchingProperties props = defaultProps();
        ReconciliationRuleEngine engine = new ReconciliationRuleEngine(List.of(
                new FailedStatusRule(),
                new InternalDuplicateRule(),
                new ExactMatchRule(),
                new DateToleranceRule(),
                new AmountMismatchRule(),
                new BankDuplicateRule(),
                new BatchSettlementRule(),
                new RefundRule(),
                new ServiceChargeRule(props),
                new UnexpectedDebitRule(props),
                new MissingInBankRule(),
                new MissingInInternalRule()
        ));

        // Generate and verify 5 distinct datasets with various scenarios and sizes
        for (int seed = 1; seed <= 5; seed++) {
            List<InternalTransaction> internals = new ArrayList<>();
            List<BankStatementRow> banks = new ArrayList<>();

            // 1. Exact matches (varying count)
            for (int i = 1; i <= seed; i++) {
                internals.add(intern("TXN-E-" + seed + "-" + i, "PAY-E-" + seed + "-" + i,
                        new BigDecimal(100 * i), "INR", D1, PaymentStatus.SUCCESS, "SETL-E-" + seed + "-" + i));
                banks.add(bank("BNK-E-" + seed + "-" + i, "PAY-E-" + seed + "-" + i,
                        new BigDecimal(100 * i), BigDecimal.ZERO, "INR", D1, "SETL-E-" + seed + "-" + i, "Payout"));
            }

            // 2. Date tolerance matches
            internals.add(intern("TXN-D-" + seed, "PAY-D-" + seed, new BigDecimal("250"), "INR", D1, PaymentStatus.SUCCESS, null));
            banks.add(bank("BNK-D-" + seed, "PAY-D-" + seed, new BigDecimal("250"), BigDecimal.ZERO, "INR", D1.plusDays(2), null, "Delayed"));

            // 3. Amount mismatch
            internals.add(intern("TXN-A-" + seed, "PAY-A-" + seed, new BigDecimal("300"), "INR", D1, PaymentStatus.SUCCESS, null));
            banks.add(bank("BNK-A-" + seed, "PAY-A-" + seed, new BigDecimal("290"), BigDecimal.ZERO, "INR", D1, null, "Mismatch"));

            // 4. Failed transactions (ignored)
            internals.add(intern("TXN-F-" + seed, "PAY-F-" + seed, new BigDecimal("50"), "INR", D1, PaymentStatus.FAILED, null));

            // 5. Leftover REFUNDED with no bank debit -> REFUND_PENDING_BANK
            internals.add(intern("TXN-R-NODEBIT-" + seed, "PAY-R-NOD-" + seed, new BigDecimal("85"), "INR", D1, PaymentStatus.REFUNDED, null));

            // 6. REFUND_INITIATED internal -> REFUND_PENDING_BANK
            internals.add(intern("TXN-RI-" + seed, "PAY-RI-" + seed, new BigDecimal("95"), "INR", D1, PaymentStatus.REFUND_INITIATED, null));

            // 7. REFUNDED with matching bank debit -> REFUND_MATCH
            internals.add(intern("TXN-R-MATCH-" + seed, "PAY-R-M-" + seed, new BigDecimal("120"), "INR", D1, PaymentStatus.REFUNDED, null));
            banks.add(bank("BNK-R-DEB-" + seed, "PAY-R-M-" + seed, BigDecimal.ZERO, new BigDecimal("120"), "INR", D1, null, "Refund debit"));

            // 8. Leftover SUCCESS and PENDING internals -> MISSING_IN_BANK
            internals.add(intern("TXN-SUCC-MISSING-" + seed, "PAY-S-MISS-" + seed, new BigDecimal("500"), "INR", D1, PaymentStatus.SUCCESS, null));
            internals.add(intern("TXN-PEND-MISSING-" + seed, "PAY-P-MISS-" + seed, new BigDecimal("600"), "INR", D1, PaymentStatus.PENDING, null));

            // 9. Bank service charges
            banks.add(bank("BNK-SC-" + seed, null, BigDecimal.ZERO, new BigDecimal("118"), "INR", D1, null, "Monthly maintenance fee"));

            // 10. Unexpected debit (chargeback)
            banks.add(bank("BNK-CB-" + seed, "CHG-BCK-" + seed, BigDecimal.ZERO, new BigDecimal("950"), "INR", D1, null, "Chargeback debit"));

            // 11. Leftover bank credits and debits -> MISSING_IN_INTERNAL
            banks.add(bank("BNK-ORPHAN-C-" + seed, "ORPH-C-" + seed, new BigDecimal("10000"), BigDecimal.ZERO, "INR", D1, null, "Direct NEFT deposit"));
            banks.add(bank("BNK-ORPHAN-D-" + seed, "ORPH-D-" + seed, BigDecimal.ZERO, new BigDecimal("2500"), "INR", D1, null, "Commercial electricity BESCOM"));

            ReconciliationContext ctx = new ReconciliationContext(internals, banks, 3);
            engine.execute(ctx);

            // Assert every internal transaction is classified exactly once
            for (InternalTransaction it : internals) {
                assertTrue(ctx.isInternalMatched(it.getId()),
                        "Dataset " + seed + ": internal " + it.getTransactionId() + " was not classified");
            }

            // Assert every bank row is classified exactly once
            for (BankStatementRow b : banks) {
                assertTrue(ctx.isBankRowMatched(b.getId()),
                        "Dataset " + seed + ": bank row " + b.getBankReference() + " was not classified");
            }

            // Invariant: no leftover counts
            assertEquals(0, ctx.getUnmatchedSuccessInternalCount(),
                    "Dataset " + seed + ": unmatched success internal count must be 0");
            assertEquals(0, ctx.getUnmatchedBankCount(),
                    "Dataset " + seed + ": unmatched bank count must be 0");
            assertEquals(0, ctx.getUnprocessedBankDebitsCount(),
                    "Dataset " + seed + ": unprocessed bank debits count must be 0");
        }
    }
}
