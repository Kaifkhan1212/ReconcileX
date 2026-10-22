package com.reconcilex.service;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BatchInsertService {

    private final JdbcTemplate jdbcTemplate;

    public BatchInsertService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insertBankStatementRows(Long fileImportId, List<BankStatementRowData> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }

        String sql = "INSERT INTO bank_statement_rows ("
                + "file_import_id, source_row_number, transaction_date, bank_reference, "
                + "payment_reference, settlement_reference, description, credit_amount, "
                + "debit_amount, currency"
                + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                BankStatementRowData row = rows.get(i);
                ps.setLong(1, fileImportId);
                ps.setInt(2, row.getSourceRowNumber());
                ps.setDate(3, Date.valueOf(row.getTransactionDate()));
                ps.setString(4, row.getBankReference());

                if (row.getPaymentReference() != null) {
                    ps.setString(5, row.getPaymentReference());
                } else {
                    ps.setNull(5, Types.VARCHAR);
                }

                if (row.getSettlementReference() != null) {
                    ps.setString(6, row.getSettlementReference());
                } else {
                    ps.setNull(6, Types.VARCHAR);
                }

                if (row.getDescription() != null) {
                    ps.setString(7, row.getDescription());
                } else {
                    ps.setNull(7, Types.VARCHAR);
                }

                ps.setBigDecimal(8, row.getCreditAmount());
                ps.setBigDecimal(9, row.getDebitAmount());
                ps.setString(10, row.getCurrency());
            }

            @Override
            public int getBatchSize() {
                return rows.size();
            }
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insertInternalTransactions(Long fileImportId, List<InternalTransactionRowData> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }

        String sql = "INSERT INTO internal_transactions ("
                + "file_import_id, source_row_number, transaction_id, payment_reference, "
                + "transaction_date, description, amount, currency, payment_status, "
                + "settlement_reference"
                + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                InternalTransactionRowData row = rows.get(i);
                ps.setLong(1, fileImportId);
                ps.setInt(2, row.getSourceRowNumber());
                ps.setString(3, row.getTransactionId());

                if (row.getPaymentReference() != null) {
                    ps.setString(4, row.getPaymentReference());
                } else {
                    ps.setNull(4, Types.VARCHAR);
                }

                ps.setDate(5, Date.valueOf(row.getTransactionDate()));

                if (row.getDescription() != null) {
                    ps.setString(6, row.getDescription());
                } else {
                    ps.setNull(6, Types.VARCHAR);
                }

                ps.setBigDecimal(7, row.getAmount());
                ps.setString(8, row.getCurrency());
                ps.setString(9, row.getPaymentStatus().name());

                if (row.getSettlementReference() != null) {
                    ps.setString(10, row.getSettlementReference());
                } else {
                    ps.setNull(10, Types.VARCHAR);
                }
            }

            @Override
            public int getBatchSize() {
                return rows.size();
            }
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void insertRowErrors(Long fileImportId, List<RowValidationError> errors) {
        if (errors == null || errors.isEmpty()) {
            return;
        }

        String sql = "INSERT INTO import_row_errors ("
                + "file_import_id, row_number, field_name, error_message, raw_value"
                + ") VALUES (?, ?, ?, ?, ?)";

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                RowValidationError err = errors.get(i);
                ps.setLong(1, fileImportId);
                ps.setInt(2, err.getRowNumber());
                ps.setString(3, err.getFieldName());
                ps.setString(4, err.getErrorMessage());

                if (err.getRawValue() != null) {
                    ps.setString(5, err.getRawValue());
                } else {
                    ps.setNull(5, Types.VARCHAR);
                }
            }

            @Override
            public int getBatchSize() {
                return errors.size();
            }
        });
    }
}
