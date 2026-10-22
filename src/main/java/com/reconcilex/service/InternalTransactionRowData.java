package com.reconcilex.service;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.reconcilex.entity.PaymentStatus;

public class InternalTransactionRowData {

    private final int sourceRowNumber;
    private final String transactionId;
    private final String paymentReference;
    private final LocalDate transactionDate;
    private final String description;
    private final BigDecimal amount;
    private final String currency;
    private final PaymentStatus paymentStatus;
    private final String settlementReference;

    public InternalTransactionRowData(
            int sourceRowNumber,
            String transactionId,
            String paymentReference,
            LocalDate transactionDate,
            String description,
            BigDecimal amount,
            String currency,
            PaymentStatus paymentStatus,
            String settlementReference) {
        this.sourceRowNumber = sourceRowNumber;
        this.transactionId = transactionId;
        this.paymentReference = paymentReference;
        this.transactionDate = transactionDate;
        this.description = description;
        this.amount = amount;
        this.currency = currency;
        this.paymentStatus = paymentStatus;
        this.settlementReference = settlementReference;
    }

    public int getSourceRowNumber() {
        return sourceRowNumber;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentStatus getPaymentStatus() {
        return paymentStatus;
    }

    public String getSettlementReference() {
        return settlementReference;
    }
}
