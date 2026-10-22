package com.reconcilex.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "bank_statement_rows",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_bank_statement_import_row",
                        columnNames = {"file_import_id", "source_row_number"}
                ),
                @UniqueConstraint(
                        name = "uq_bank_statement_bank_ref",
                        columnNames = {"bank_reference"}
                )
        }
)
public class BankStatementRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "file_import_id", nullable = false)
    private FileImport fileImport;

    @Column(name = "source_row_number", nullable = false)
    private int sourceRowNumber;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    @Column(name = "bank_reference", unique = true, length = 120)
    private String bankReference;

    @Column(name = "payment_reference", length = 120)
    private String paymentReference;

    @Column(name = "settlement_reference", length = 120)
    private String settlementReference;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(
            name = "credit_amount",
            nullable = false,
            precision = 19,
            scale = 2
    )
    private BigDecimal creditAmount = BigDecimal.ZERO;

    @Column(
            name = "debit_amount",
            nullable = false,
            precision = 19,
            scale = 2
    )
    private BigDecimal debitAmount = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    public BankStatementRow() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public FileImport getFileImport() {
        return fileImport;
    }

    public void setFileImport(FileImport fileImport) {
        this.fileImport = fileImport;
    }

    public int getSourceRowNumber() {
        return sourceRowNumber;
    }

    public void setSourceRowNumber(int sourceRowNumber) {
        this.sourceRowNumber = sourceRowNumber;
    }

    public LocalDate getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(LocalDate transactionDate) {
        this.transactionDate = transactionDate;
    }

    public String getBankReference() {
        return bankReference;
    }

    public void setBankReference(String bankReference) {
        this.bankReference = bankReference;
    }

    public String getPaymentReference() {
        return paymentReference;
    }

    public void setPaymentReference(String paymentReference) {
        this.paymentReference = paymentReference;
    }

    public String getSettlementReference() {
        return settlementReference;
    }

    public void setSettlementReference(String settlementReference) {
        this.settlementReference = settlementReference;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getCreditAmount() {
        return creditAmount;
    }

    public void setCreditAmount(BigDecimal creditAmount) {
        this.creditAmount = creditAmount;
    }

    public BigDecimal getDebitAmount() {
        return debitAmount;
    }

    public void setDebitAmount(BigDecimal debitAmount) {
        this.debitAmount = debitAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}