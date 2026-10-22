package com.reconcilex.service;

import java.math.BigDecimal;
import java.time.LocalDate;

public class BankStatementRowData {

    private final int sourceRowNumber;
    private final LocalDate transactionDate;
    private final String bankReference;
    private final String paymentReference;
    private final String settlementReference;
    private final String description;
    private final BigDecimal creditAmount;
    private final BigDecimal debitAmount;
    private final String currency;

    public BankStatementRowData(
            int sourceRowNumber,
            LocalDate transactionDate,
            String bankReference,
            String paymentReference,
            String settlementReference,
            String description,
            BigDecimal creditAmount,
            BigDecimal debitAmount,
            String currency) {
        this.sourceRowNumber = sourceRowNumber;
        this.transactionDate = transactionDate;
        this.bankReference = bankReference;
        this.paymentReference = paymentReference;
        this.settlementReference = settlementReference;
        this.description = description;
        this.creditAmount = creditAmount;
        this.debitAmount = debitAmount;
        this.currency = currency;
    }

    public int getSourceRowNumber() {
        return sourceRowNumber;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public String getBankReference() {
        return bankReference;
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public String getSettlementReference() {
        return settlementReference;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getCreditAmount() {
        return creditAmount;
    }

    public BigDecimal getDebitAmount() {
        return debitAmount;
    }

    public String getCurrency() {
        return currency;
    }
}
