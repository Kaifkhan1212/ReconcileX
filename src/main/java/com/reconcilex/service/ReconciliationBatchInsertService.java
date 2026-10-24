package com.reconcilex.service;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reconcilex.entity.BankStatementRow;
import com.reconcilex.entity.InternalTransaction;
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

        // Reserve a contiguous block of IDs from the sequence
        List<Long> generatedIds = jdbcTemplate.query(
                "SELECT nextval(pg_get_serial_sequence('reconciliation_results', 'id')) FROM generate_series(1, ?)",
                (rs, rowNum) -> rs.getLong(1),
                count
        );
        if (generatedIds.size() != count) {
            throw new IllegalStateException(
                    "Failed to reserve sequence IDs: expected " + count + ", got " + generatedIds.size());
        }

        Timestamp now = Timestamp.from(Instant.now());

        // --- Insert reconciliation_results ---
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

        // --- Build flat lists of (resultId, bankRowId) and (resultId, internalId) ---
        List<long[]> bankLinks = new ArrayList<>();
        List<long[]> internalLinks = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            long resultId = generatedIds.get(i);
            MatchOutcome outcome = outcomes.get(i);

            for (BankStatementRow b : outcome.getBankRows()) {
                if (b != null && b.getId() != null) {
                    bankLinks.add(new long[]{resultId, b.getId()});
                }
            }
            for (InternalTransaction it : outcome.getInternalTransactions()) {
                if (it != null && it.getId() != null) {
                    internalLinks.add(new long[]{resultId, it.getId()});
                }
            }
        }

        // --- Insert reconciliation_result_bank_rows ---
        if (!bankLinks.isEmpty()) {
            String sqlBankLink = "INSERT INTO reconciliation_result_bank_rows ("
                    + "reconciliation_result_id, bank_statement_row_id"
                    + ") VALUES (?, ?)";

            jdbcTemplate.batchUpdate(sqlBankLink, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    ps.setLong(1, bankLinks.get(i)[0]);
                    ps.setLong(2, bankLinks.get(i)[1]);
                }

                @Override
                public int getBatchSize() {
                    return bankLinks.size();
                }
            });
        }

        // --- Insert reconciliation_result_internal_transactions ---
        if (!internalLinks.isEmpty()) {
            String sqlInternalLink = "INSERT INTO reconciliation_result_internal_transactions ("
                    + "reconciliation_result_id, internal_transaction_id"
                    + ") VALUES (?, ?)";

            jdbcTemplate.batchUpdate(sqlInternalLink, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    ps.setLong(1, internalLinks.get(i)[0]);
                    ps.setLong(2, internalLinks.get(i)[1]);
                }

                @Override
                public int getBatchSize() {
                    return internalLinks.size();
                }
            });
        }

        // --- Insert MATCH_DECISION audit events (one per result, same transaction) ---
        // Captured once outside the BatchPreparedStatementSetter lambda.
        String actor = currentActor();
        String sqlAudit = "INSERT INTO audit_events "
                + "(run_id, event_type, actor, details, occurred_at) "
                + "VALUES (?, 'MATCH_DECISION', ?, ?::jsonb, ?)";

        jdbcTemplate.batchUpdate(sqlAudit, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                MatchOutcome outcome = outcomes.get(i);
                long resultId = generatedIds.get(i);

                ps.setLong(1, runId);
                ps.setString(2, actor);
                ps.setString(3, buildDecisionJson(resultId, outcome));
                ps.setTimestamp(4, now);
            }

            @Override
            public int getBatchSize() {
                return count;
            }
        });
    }

    // ---- helpers ----

    /** Derive the actor from the Spring Security context (or "system" if none). */
    private static String currentActor() {
        org.springframework.security.core.Authentication auth =
                org.springframework.security.core.context.SecurityContextHolder
                        .getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        return "system";
    }

    /**
     * Builds the JSONB details payload for a MATCH_DECISION audit event.
     * All string values are escaped so the result is valid JSON.
     */
    private static String buildDecisionJson(long resultId, MatchOutcome outcome) {
        StringBuilder sb = new StringBuilder("{");

        sb.append("\"resultId\":").append(resultId).append(",");
        sb.append("\"ruleName\":").append(jsonString(outcome.getRuleName())).append(",");
        sb.append("\"resultType\":").append(jsonString(
                outcome.getResultType() != null ? outcome.getResultType().name() : null)).append(",");

        if (outcome.getAmountDifference() != null) {
            sb.append("\"amountDifference\":").append(outcome.getAmountDifference().toPlainString()).append(",");
        } else {
            sb.append("\"amountDifference\":null,");
        }

        // internalTransactionIds array
        sb.append("\"internalTransactionIds\":[");
        List<InternalTransaction> internalTxs = outcome.getInternalTransactions();
        for (int j = 0; j < internalTxs.size(); j++) {
            InternalTransaction it = internalTxs.get(j);
            if (it != null && it.getId() != null) {
                sb.append(it.getId());
            } else {
                sb.append("null");
            }
            if (j < internalTxs.size() - 1) {
                sb.append(",");
            }
        }
        sb.append("],");

        // bankReferences array
        sb.append("\"bankReferences\":[");
        List<BankStatementRow> bankRows = outcome.getBankRows();
        for (int j = 0; j < bankRows.size(); j++) {
            BankStatementRow br = bankRows.get(j);
            if (br != null && br.getBankReference() != null) {
                sb.append(jsonString(br.getBankReference()));
            } else {
                sb.append("null");
            }
            if (j < bankRows.size() - 1) {
                sb.append(",");
            }
        }
        sb.append("],");

        sb.append("\"explanation\":").append(jsonString(outcome.getExplanation()));
        sb.append("}");

        return sb.toString();
    }

    /** Minimal JSON string escaping for our safe, non-binary field values. */
    private static String jsonString(String value) {
        if (value == null) {
            return "null";
        }
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                + "\"";
    }
}
