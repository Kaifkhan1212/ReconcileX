package com.reconcilex.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.reconcilex.entity.PaymentStatus;

class CsvRowValidatorTest {

    private CsvRowValidator validator;

    @BeforeEach
    void setUp() {
        validator = new CsvRowValidator();
    }

    @Test
    void validBankStatementRowPasses() {
        Map<String, String> row = new HashMap<>();
        row.put("transaction_date", "2026-10-01");
        row.put("bank_reference", "BNK2001");
        row.put("payment_reference", "PAY-1001");
        row.put("settlement_reference", "SETL-20261001-01");
        row.put("description", "Razorpay payout order 1001");
        row.put("credit_amount", "1500.00");
        row.put("debit_amount", "0.00");
        row.put("currency", "INR");

        ValidationResult<BankStatementRowData> result = validator.validateBankStatementRow(2, row);

        assertTrue(result.isValid());
        assertNotNull(result.getData());
        assertEquals("BNK2001", result.getData().getBankReference());
        assertEquals(LocalDate.of(2026, 10, 1), result.getData().getTransactionDate());
        assertEquals(new BigDecimal("1500.00"), result.getData().getCreditAmount());
        assertEquals(new BigDecimal("0.00"), result.getData().getDebitAmount());
        assertEquals("INR", result.getData().getCurrency());
    }

    @Test
    void bankStatementRowWithBlankSettlementReferencePassesAndStoresNull() {
        Map<String, String> row = new HashMap<>();
        row.put("transaction_date", "2026-10-08");
        row.put("bank_reference", "BNK2034");
        row.put("payment_reference", "PAY-1033");
        row.put("settlement_reference", "   ");
        row.put("description", "Sample transaction without settlement");
        row.put("credit_amount", "500.00");
        row.put("debit_amount", "0.00");
        row.put("currency", "inr");

        ValidationResult<BankStatementRowData> result = validator.validateBankStatementRow(34, row);

        assertTrue(result.isValid());
        assertNull(result.getData().getSettlementReference());
        assertEquals("INR", result.getData().getCurrency());
    }

    @Test
    void bankStatementRowFailsOnBadDateAndNegativeAmount() {
        Map<String, String> row = new HashMap<>();
        row.put("transaction_date", "01-10-2026");
        row.put("bank_reference", "BNK9999");
        row.put("payment_reference", "PAY-9999");
        row.put("settlement_reference", "");
        row.put("description", "Invalid row");
        row.put("credit_amount", "-100.00");
        row.put("debit_amount", "0.00");
        row.put("currency", "INVALID_CURRENCY");

        ValidationResult<BankStatementRowData> result = validator.validateBankStatementRow(5, row);

        assertFalse(result.isValid());
        assertEquals(3, result.getErrors().size());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.getFieldName().equals("transaction_date")));
        assertTrue(result.getErrors().stream().anyMatch(e -> e.getFieldName().equals("credit_amount")));
        assertTrue(result.getErrors().stream().anyMatch(e -> e.getFieldName().equals("currency")));
    }

    @Test
    void amountWithScaleGreaterThanTwoFails() {
        Map<String, String> row = new HashMap<>();
        row.put("transaction_date", "2026-10-01");
        row.put("bank_reference", "BNK2001");
        row.put("payment_reference", "PAY-1001");
        row.put("settlement_reference", "");
        row.put("description", "Desc");
        row.put("credit_amount", "1500.555");
        row.put("debit_amount", "0.00");
        row.put("currency", "INR");

        ValidationResult<BankStatementRowData> result = validator.validateBankStatementRow(2, row);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.getFieldName().equals("credit_amount")));
    }

    @Test
    void validInternalTransactionPasses() {
        Map<String, String> row = new HashMap<>();
        row.put("transaction_id", "TXN1001");
        row.put("payment_reference", "PAY-1001");
        row.put("transaction_date", "2026-10-01");
        row.put("description", "Checkout Order #1001");
        row.put("amount", "1500.00");
        row.put("currency", "INR");
        row.put("payment_status", "SUCCESS");
        row.put("settlement_reference", "SETL-20261001-01");

        ValidationResult<InternalTransactionRowData> result = validator.validateInternalTransactionRow(2, row);

        assertTrue(result.isValid());
        assertNotNull(result.getData());
        assertEquals("TXN1001", result.getData().getTransactionId());
        assertEquals(PaymentStatus.SUCCESS, result.getData().getPaymentStatus());
        assertEquals(new BigDecimal("1500.00"), result.getData().getAmount());
    }

    @Test
    void internalTransactionWithBadPaymentStatusFails() {
        Map<String, String> row = new HashMap<>();
        row.put("transaction_id", "TXN1001");
        row.put("payment_reference", "PAY-1001");
        row.put("transaction_date", "2026-10-01");
        row.put("description", "Checkout Order #1001");
        row.put("amount", "1500.00");
        row.put("currency", "INR");
        row.put("payment_status", "NOT_A_STATUS");
        row.put("settlement_reference", "");

        ValidationResult<InternalTransactionRowData> result = validator.validateInternalTransactionRow(2, row);

        assertFalse(result.isValid());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.getFieldName().equals("payment_status")));
    }
}
