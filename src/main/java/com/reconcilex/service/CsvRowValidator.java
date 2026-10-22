package com.reconcilex.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.reconcilex.entity.PaymentStatus;

@Component
public class CsvRowValidator {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public ValidationResult<BankStatementRowData> validateBankStatementRow(
            int rowNumber,
            Map<String, String> row) {

        List<RowValidationError> errors = new ArrayList<>();

        // 1. Transaction Date
        String rawDate = row.get("transaction_date");
        LocalDate transactionDate = parseDate(rowNumber, "transaction_date", rawDate, errors);

        // 2. Bank Reference (required business key)
        String rawBankRef = row.get("bank_reference");
        String bankReference = parseRequiredString(rowNumber, "bank_reference", rawBankRef, errors);

        // 3. Payment Reference (optional)
        String paymentReference = parseOptionalString(row.get("payment_reference"));

        // 4. Settlement Reference (optional, sample row BNK2034 has none)
        String settlementReference = parseOptionalString(row.get("settlement_reference"));

        // 5. Description (optional)
        String description = parseOptionalString(row.get("description"));

        // 6. Credit Amount (required)
        String rawCredit = row.get("credit_amount");
        BigDecimal creditAmount = parseAmount(rowNumber, "credit_amount", rawCredit, errors);

        // 7. Debit Amount (required)
        String rawDebit = row.get("debit_amount");
        BigDecimal debitAmount = parseAmount(rowNumber, "debit_amount", rawDebit, errors);

        // 8. Currency (required, 3-letter code)
        String rawCurrency = row.get("currency");
        String currency = parseCurrency(rowNumber, "currency", rawCurrency, errors);

        if (!errors.isEmpty()) {
            return ValidationResult.invalid(errors);
        }

        return ValidationResult.valid(new BankStatementRowData(
                rowNumber,
                transactionDate,
                bankReference,
                paymentReference,
                settlementReference,
                description,
                creditAmount,
                debitAmount,
                currency
        ));
    }

    public ValidationResult<InternalTransactionRowData> validateInternalTransactionRow(
            int rowNumber,
            Map<String, String> row) {

        List<RowValidationError> errors = new ArrayList<>();

        // 1. Transaction ID (required business key)
        String rawTxnId = row.get("transaction_id");
        String transactionId = parseRequiredString(rowNumber, "transaction_id", rawTxnId, errors);

        // 2. Payment Reference (optional)
        String paymentReference = parseOptionalString(row.get("payment_reference"));

        // 3. Transaction Date (required)
        String rawDate = row.get("transaction_date");
        LocalDate transactionDate = parseDate(rowNumber, "transaction_date", rawDate, errors);

        // 4. Description (optional)
        String description = parseOptionalString(row.get("description"));

        // 5. Amount (required)
        String rawAmount = row.get("amount");
        BigDecimal amount = parseAmount(rowNumber, "amount", rawAmount, errors);

        // 6. Currency (required)
        String rawCurrency = row.get("currency");
        String currency = parseCurrency(rowNumber, "currency", rawCurrency, errors);

        // 7. Payment Status (required)
        String rawStatus = row.get("payment_status");
        PaymentStatus paymentStatus = parsePaymentStatus(rowNumber, "payment_status", rawStatus, errors);

        // 8. Settlement Reference (optional)
        String settlementReference = parseOptionalString(row.get("settlement_reference"));

        if (!errors.isEmpty()) {
            return ValidationResult.invalid(errors);
        }

        return ValidationResult.valid(new InternalTransactionRowData(
                rowNumber,
                transactionId,
                paymentReference,
                transactionDate,
                description,
                amount,
                currency,
                paymentStatus,
                settlementReference
        ));
    }

    private String parseRequiredString(
            int rowNumber,
            String fieldName,
            String rawValue,
            List<RowValidationError> errors) {

        if (rawValue == null || rawValue.trim().isEmpty()) {
            errors.add(new RowValidationError(
                    rowNumber,
                    fieldName,
                    "Field " + fieldName + " is required",
                    rawValue
            ));
            return null;
        }
        return rawValue.trim();
    }

    private String parseOptionalString(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String trimmed = rawValue.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private LocalDate parseDate(
            int rowNumber,
            String fieldName,
            String rawValue,
            List<RowValidationError> errors) {

        if (rawValue == null || rawValue.trim().isEmpty()) {
            errors.add(new RowValidationError(
                    rowNumber,
                    fieldName,
                    "Field " + fieldName + " is required",
                    rawValue
            ));
            return null;
        }

        String trimmed = rawValue.trim();
        try {
            return LocalDate.parse(trimmed, DATE_FORMATTER);
        } catch (DateTimeParseException ex) {
            errors.add(new RowValidationError(
                    rowNumber,
                    fieldName,
                    "Invalid date format, expected yyyy-MM-dd",
                    trimmed
            ));
            return null;
        }
    }

    private BigDecimal parseAmount(
            int rowNumber,
            String fieldName,
            String rawValue,
            List<RowValidationError> errors) {

        if (rawValue == null || rawValue.trim().isEmpty()) {
            errors.add(new RowValidationError(
                    rowNumber,
                    fieldName,
                    "Field " + fieldName + " is required",
                    rawValue
            ));
            return null;
        }

        String trimmed = rawValue.trim();
        try {
            BigDecimal bd = new BigDecimal(trimmed);
            if (bd.compareTo(BigDecimal.ZERO) < 0) {
                errors.add(new RowValidationError(
                        rowNumber,
                        fieldName,
                        "Amount must be greater than or equal to 0",
                        trimmed
                ));
                return null;
            }
            if (bd.scale() > 2) {
                errors.add(new RowValidationError(
                        rowNumber,
                        fieldName,
                        "Amount scale must be at most 2",
                        trimmed
                ));
                return null;
            }
            return bd.setScale(2, RoundingMode.UNNECESSARY);
        } catch (NumberFormatException ex) {
            errors.add(new RowValidationError(
                    rowNumber,
                    fieldName,
                    "Invalid amount format",
                    trimmed
            ));
            return null;
        }
    }

    private String parseCurrency(
            int rowNumber,
            String fieldName,
            String rawValue,
            List<RowValidationError> errors) {

        if (rawValue == null || rawValue.trim().isEmpty()) {
            errors.add(new RowValidationError(
                    rowNumber,
                    fieldName,
                    "Field " + fieldName + " is required",
                    rawValue
            ));
            return null;
        }

        String trimmed = rawValue.trim();
        if (!trimmed.matches("^[A-Za-z]{3}$")) {
            errors.add(new RowValidationError(
                    rowNumber,
                    fieldName,
                    "Currency must be a 3-letter alphabetic code",
                    trimmed
            ));
            return null;
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    private PaymentStatus parsePaymentStatus(
            int rowNumber,
            String fieldName,
            String rawValue,
            List<RowValidationError> errors) {

        if (rawValue == null || rawValue.trim().isEmpty()) {
            errors.add(new RowValidationError(
                    rowNumber,
                    fieldName,
                    "Field " + fieldName + " is required",
                    rawValue
            ));
            return null;
        }

        String trimmed = rawValue.trim().toUpperCase(Locale.ROOT);
        try {
            return PaymentStatus.valueOf(trimmed);
        } catch (IllegalArgumentException ex) {
            errors.add(new RowValidationError(
                    rowNumber,
                    fieldName,
                    "Invalid payment_status value",
                    rawValue.trim()
            ));
            return null;
        }
    }
}
