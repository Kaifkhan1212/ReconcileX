package com.reconcilex.rules;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.reconcilex.entity.BankStatementRow;
import com.reconcilex.entity.InternalTransaction;
import com.reconcilex.entity.PaymentStatus;

public class ReconciliationContext {

    private final List<InternalTransaction> internalTransactions;
    private final List<BankStatementRow> bankRows;

    // Index: payment_reference -> bank rows (ordered by bank_reference for determinism)
    private final Map<String, List<BankStatementRow>> bankRowsByPaymentRef;

    // Index: settlement_reference -> bank rows (for batch settlement lookup)
    private final Map<String, List<BankStatementRow>> bankRowsBySettlementRef;

    // Index: payment_reference -> internal transactions (for refund debit lookup)
    private final Map<String, List<InternalTransaction>> internalByPaymentRef;

    private final Set<Long> matchedInternalIds;
    private final Set<Long> matchedBankRowIds;
    private final List<MatchOutcome> outcomes;
    private final int dateToleranceDays;

    /**
     * Tracks the composite key "(paymentRef|amount|currency)" of bank credits that were
     * matched by orders 100-300 (EXACT, DATE_MISMATCH, AMOUNT_MISMATCH).
     * Used by BankDuplicateRule (order 350) to detect second postings.
     */
    private final Set<String> primaryMatchedBankCreditKeys;

    public ReconciliationContext(
            List<InternalTransaction> internalTransactions,
            List<BankStatementRow> bankRows,
            int dateToleranceDays
    ) {
        this.dateToleranceDays = dateToleranceDays;
        this.matchedInternalIds = new HashSet<>();
        this.matchedBankRowIds = new HashSet<>();
        this.outcomes = new ArrayList<>();
        this.primaryMatchedBankCreditKeys = new HashSet<>();

        // --- Sort internal transactions by transaction_id for determinism ---
        List<InternalTransaction> sortedInternal = new ArrayList<>(internalTransactions);
        sortedInternal.sort(Comparator.comparing(
                InternalTransaction::getTransactionId,
                Comparator.nullsLast(String::compareTo)
        ));
        this.internalTransactions = Collections.unmodifiableList(sortedInternal);

        // --- Sort bank rows by bank_reference for determinism ---
        List<BankStatementRow> allBank = new ArrayList<>(bankRows);
        allBank.sort(Comparator.comparing(
                BankStatementRow::getBankReference,
                Comparator.nullsLast(String::compareTo)
        ));
        this.bankRows = Collections.unmodifiableList(allBank);

        // --- Build payment_reference index for bank rows ---
        Map<String, List<BankStatementRow>> payRefIndex = new HashMap<>();
        for (BankStatementRow row : this.bankRows) {
            if (row.getPaymentReference() != null && !row.getPaymentReference().isBlank()) {
                payRefIndex.computeIfAbsent(row.getPaymentReference(), k -> new ArrayList<>()).add(row);
            }
        }
        this.bankRowsByPaymentRef = Collections.unmodifiableMap(payRefIndex);

        // --- Build settlement_reference index for bank rows ---
        Map<String, List<BankStatementRow>> settlRefIndex = new HashMap<>();
        for (BankStatementRow row : this.bankRows) {
            if (row.getSettlementReference() != null && !row.getSettlementReference().isBlank()) {
                settlRefIndex.computeIfAbsent(row.getSettlementReference(), k -> new ArrayList<>()).add(row);
            }
        }
        this.bankRowsBySettlementRef = Collections.unmodifiableMap(settlRefIndex);

        // --- Build payment_reference index for internal transactions ---
        Map<String, List<InternalTransaction>> intPayRefIndex = new HashMap<>();
        for (InternalTransaction it : this.internalTransactions) {
            if (it.getPaymentReference() != null && !it.getPaymentReference().isBlank()) {
                intPayRefIndex.computeIfAbsent(it.getPaymentReference(), k -> new ArrayList<>()).add(it);
            }
        }
        this.internalByPaymentRef = Collections.unmodifiableMap(intPayRefIndex);
    }

    // --- Accessors ---

    public List<InternalTransaction> getInternalTransactions() {
        return internalTransactions;
    }

    public List<BankStatementRow> getBankRows() {
        return bankRows;
    }

    public int getDateToleranceDays() {
        return dateToleranceDays;
    }

    public boolean isInternalMatched(Long internalId) {
        return matchedInternalIds.contains(internalId);
    }

    public boolean isBankRowMatched(Long bankRowId) {
        return matchedBankRowIds.contains(bankRowId);
    }

    public List<BankStatementRow> getCandidateBankRows(String paymentReference) {
        if (paymentReference == null || paymentReference.isBlank()) {
            return Collections.emptyList();
        }
        return bankRowsByPaymentRef.getOrDefault(paymentReference, Collections.emptyList());
    }

    public List<BankStatementRow> getCandidateBankRowsBySettlementRef(String settlementReference) {
        if (settlementReference == null || settlementReference.isBlank()) {
            return Collections.emptyList();
        }
        return bankRowsBySettlementRef.getOrDefault(settlementReference, Collections.emptyList());
    }

    public List<InternalTransaction> getCandidateInternalByPaymentRef(String paymentReference) {
        if (paymentReference == null || paymentReference.isBlank()) {
            return Collections.emptyList();
        }
        return internalByPaymentRef.getOrDefault(paymentReference, Collections.emptyList());
    }

    /**
     * Register a bank credit key (payRef|amount|currency) as primarily matched (orders 100-300).
     * Called only by ExactMatchRule, DateToleranceRule, AmountMismatchRule outcomes.
     */
    public void registerPrimaryBankCreditMatch(BankStatementRow bankRow) {
        if (bankRow.getCreditAmount() != null
                && bankRow.getCreditAmount().compareTo(BigDecimal.ZERO) > 0
                && bankRow.getPaymentReference() != null) {
            String key = bankRow.getPaymentReference()
                    + "|" + bankRow.getCreditAmount().toPlainString()
                    + "|" + bankRow.getCurrency();
            primaryMatchedBankCreditKeys.add(key);
        }
    }

    /**
     * Check if a bank credit key was matched by one of the primary rules (100-300).
     */
    public boolean isPrimaryBankCreditMatched(BankStatementRow bankRow) {
        if (bankRow.getPaymentReference() == null
                || bankRow.getCreditAmount() == null
                || bankRow.getCurrency() == null) {
            return false;
        }
        String key = bankRow.getPaymentReference()
                + "|" + bankRow.getCreditAmount().toPlainString()
                + "|" + bankRow.getCurrency();
        return primaryMatchedBankCreditKeys.contains(key);
    }

    // --- Match recording ---

    public void addMatch(MatchOutcome outcome) {
        for (InternalTransaction it : outcome.getInternalTransactions()) {
            if (it != null && it.getId() != null) {
                matchedInternalIds.add(it.getId());
            }
        }
        for (BankStatementRow b : outcome.getBankRows()) {
            if (b != null && b.getId() != null) {
                matchedBankRowIds.add(b.getId());
            }
        }
        outcomes.add(outcome);
    }

    public List<MatchOutcome> getOutcomes() {
        return Collections.unmodifiableList(outcomes);
    }

    // --- Unmatched counts (used for invariant check; after all rules run, both should be 0) ---

    public long getUnmatchedSuccessInternalCount() {
        long count = 0;
        for (InternalTransaction it : internalTransactions) {
            if (it.getPaymentStatus() == PaymentStatus.SUCCESS && !isInternalMatched(it.getId())) {
                count++;
            }
        }
        return count;
    }

    public long getUnmatchedBankCount() {
        long count = 0;
        for (BankStatementRow row : bankRows) {
            if (row.getCreditAmount() != null
                    && row.getCreditAmount().compareTo(BigDecimal.ZERO) > 0
                    && !isBankRowMatched(row.getId())) {
                count++;
            }
        }
        return count;
    }

    public long getUnprocessedBankDebitsCount() {
        long count = 0;
        for (BankStatementRow row : bankRows) {
            if (row.getDebitAmount() != null
                    && row.getDebitAmount().compareTo(BigDecimal.ZERO) > 0
                    && !isBankRowMatched(row.getId())) {
                count++;
            }
        }
        return count;
    }
}
