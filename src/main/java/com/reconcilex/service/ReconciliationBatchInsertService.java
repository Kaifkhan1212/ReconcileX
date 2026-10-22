package com.reconcilex.service;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reconcilex.rules.MatchOutcome;

@Service
public class ReconciliationBatchInsertService {

    private final JdbcTemplate jdbcTemplate;

    public ReconciliationBatchInsertService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void insertReconciliationResults(Long runId, List<MatchOutcome> outcomes) {
        if (outcomes == null || outcomes.isEmpty()) {
            return;
        }

        int count = outcomes.size();
        List<Long> generatedIds = jdbcTemplate.query(
                "SELECT nextval(pg_get_serial_sequence('reconciliation_results', 'id')) FROM generate_series(1, ?)",
                (rs, rowNum) -> rs.getLong(1),
                count
        );

        if (generatedIds.size() != count) {
            throw new IllegalStateException("Failed to reserve sequence IDs: expected " + count + ", got " + generatedIds.size());
        }

        Timestamp now = Timestamp.from(Instant.now());

        String sqlResult = "INSERT INTO reconciliation_results ("
                + "id, run_id, result_type, rule_name, rule_code, explanation, amount_difference, created_at"
                + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        jdbcTemplate.batchUpdate(sqlResult, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                MatchOutcome outcome = outcomes.get(i);
                Long id = generatedIds.get(i);

                ps.setLong(1, id);
                ps.setLong(2, runId);
                ps.setString(3, outcome.getResultType().name());

                if (outcome.getRuleName() != null) {
                    ps.setString(4, outcome.getRuleName());
                } else {
                    ps.setNull(4, Types.VARCHAR);
                }

                if (outcome.getRuleCode() != null) {
                    ps.setString(5, outcome.getRuleCode());
                } else {
                    ps.setNull(5, Types.VARCHAR);
                }

                if (outcome.getExplanation() != null) {
                    ps.setString(6, outcome.getExplanation());
                } else {
                    ps.setNull(6, Types.VARCHAR);
                }

                if (outcome.getAmountDifference() != null) {
                    ps.setBigDecimal(7, outcome.getAmountDifference());
                } else {
                    ps.setNull(7, Types.NUMERIC);
                }

                ps.setTimestamp(8, now);
            }

            @Override
            public int getBatchSize() {
                return count;
            }
        });

        String sqlBankLink = "INSERT INTO reconciliation_result_bank_rows ("
                + "reconciliation_result_id, bank_statement_row_id"
                + ") VALUES (?, ?)";

        jdbcTemplate.batchUpdate(sqlBankLink, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                Long resultId = generatedIds.get(i);
                Long bankRowId = outcomes.get(i).getBankRow().getId();
                ps.setLong(1, resultId);
                ps.setLong(2, bankRowId);
            }

            @Override
            public int getBatchSize() {
                return count;
            }
        });

        String sqlInternalLink = "INSERT INTO reconciliation_result_internal_transactions ("
                + "reconciliation_result_id, internal_transaction_id"
                + ") VALUES (?, ?)";

        jdbcTemplate.batchUpdate(sqlInternalLink, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                Long resultId = generatedIds.get(i);
                Long internalId = outcomes.get(i).getInternalTransaction().getId();
                ps.setLong(1, resultId);
                ps.setLong(2, internalId);
            }

            @Override
            public int getBatchSize() {
                return count;
            }
        });
    }
}
