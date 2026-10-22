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
    private final Map<String, List<BankStatementRow>> bankRowsByPaymentRef;
    private final Set<Long> matchedInternalIds;
    private final Set<Long> matchedBankRowIds;
    private final List<MatchOutcome> outcomes;
    private final int dateToleranceDays;

    public ReconciliationContext(
            List<InternalTransaction> internalTransactions,
            List<BankStatementRow> bankRows,
            int dateToleranceDays
    ) {
        this.dateToleranceDays = dateToleranceDays;
        this.matchedInternalIds = new HashSet<>();
        this.matchedBankRowIds = new HashSet<>();
        this.outcomes = new ArrayList<>();

        // Deterministic sorting of internal transactions by transaction_id
        List<InternalTransaction> sortedInternal = new ArrayList<>(internalTransactions);
        sortedInternal.sort(Comparator.comparing(
                InternalTransaction::getTransactionId,
                Comparator.nullsLast(String::compareTo)
        ));
        this.internalTransactions = Collections.unmodifiableList(sortedInternal);

        // Deterministic indexing of bank rows by payment_reference, candidates sorted by bank_reference
        List<BankStatementRow> allBank = new ArrayList<>(bankRows);
        allBank.sort(Comparator.comparing(
                BankStatementRow::getBankReference,
                Comparator.nullsLast(String::compareTo)
        ));
        this.bankRows = Collections.unmodifiableList(allBank);

        Map<String, List<BankStatementRow>> index = new HashMap<>();
        for (BankStatementRow row : this.bankRows) {
            if (row.getPaymentReference() != null && !row.getPaymentReference().isBlank()) {
                index.computeIfAbsent(row.getPaymentReference(), k -> new ArrayList<>()).add(row);
            }
        }
        this.bankRowsByPaymentRef = Collections.unmodifiableMap(index);
    }

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

    public void addMatch(MatchOutcome outcome) {
        if (outcome.getInternalTransaction() != null && outcome.getInternalTransaction().getId() != null) {
            matchedInternalIds.add(outcome.getInternalTransaction().getId());
        }
        if (outcome.getBankRow() != null && outcome.getBankRow().getId() != null) {
            matchedBankRowIds.add(outcome.getBankRow().getId());
        }
        outcomes.add(outcome);
    }

    public List<MatchOutcome> getOutcomes() {
        return Collections.unmodifiableList(outcomes);
    }

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
            if (row.getCreditAmount() != null
                    && row.getCreditAmount().compareTo(BigDecimal.ZERO) == 0) {
                count++;
            }
        }
        return count;
    }
}
