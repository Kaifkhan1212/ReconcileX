package com.reconcilex.service;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.reconcilex.dto.ReconciliationResultDto;
import com.reconcilex.dto.RunSummaryResponse;
import com.reconcilex.entity.ReconciliationResultType;

/**
 * Service for Phase 5 reporting, summaries, filtered result queries, and streaming CSV export.
 *
 * <p>SQL efficiency guarantees:
 * <ul>
 *   <li>Summary queries execute at most 5 SQL statements.</li>
 *   <li>Filtered results execute exactly 2 statements (1 count + 1 paged data query with CTE).</li>
 *   <li>Linked internal IDs and bank references are aggregated in-query via {@code string_agg},
 *       never queried per row (avoiding N+1).</li>
 *   <li>CSV export streams rows using a server-side PostgreSQL cursor (fetchSize = 500)
 *       inside a read-only transaction with autocommit disabled.</li>
 * </ul>
 */
@Service
public class ReconciliationReportService {

    // ---- Paired result types (internal row has a bank counterpart) ----
    private static final Set<ReconciliationResultType> PAIRED_TYPES = EnumSet.of(
            ReconciliationResultType.EXACT_MATCH,
            ReconciliationResultType.AMOUNT_MISMATCH,
            ReconciliationResultType.DATE_MISMATCH,
            ReconciliationResultType.BATCH_SETTLEMENT,
            ReconciliationResultType.REFUND_MATCH
    );

    // ---- Unmatched internal types ----
    private static final Set<ReconciliationResultType> UNMATCHED_INTERNAL_TYPES = EnumSet.of(
            ReconciliationResultType.MISSING_IN_BANK,
            ReconciliationResultType.REFUND_PENDING_BANK,
            ReconciliationResultType.INTERNAL_DUPLICATE_DETECTED
    );

    // ---- Unmatched bank types ----
    private static final Set<ReconciliationResultType> UNMATCHED_BANK_TYPES = EnumSet.of(
            ReconciliationResultType.MISSING_IN_INTERNAL,
            ReconciliationResultType.BANK_DUPLICATE_DETECTED,
            ReconciliationResultType.UNEXPECTED_BANK_DEBIT
    );

    // ---- Explained bank types (bank debit, but ruled-out / expected) ----
    private static final Set<ReconciliationResultType> EXPLAINED_TYPES = EnumSet.of(
            ReconciliationResultType.BANK_SERVICE_CHARGE,
            ReconciliationResultType.IGNORED_FAILED_STATUS
    );

    private static final int CSV_FETCH_SIZE = 500;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate readOnlyTxTemplate;

    public ReconciliationReportService(
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager) {
        this.jdbcTemplate = jdbcTemplate;
        this.readOnlyTxTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyTxTemplate.setReadOnly(true);
        this.readOnlyTxTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // =========================================================================
    // Summary
    // =========================================================================

    /**
     * Computes the full financial and row summary for a run.
     * Uses at most 5 queries:
     * 1. Run status
     * 2. Count per result type
     * 3. Internal value sum per result type
     * 4. Bank credit/debit sums for unmatched and explained types
     * 5. Distinct linked row counts vs imported row counts
     */
    @Transactional(readOnly = true)
    public RunSummaryResponse buildSummary(Long runId, Long bankImportId, Long internalImportId) {
        RunSummaryResponse resp = new RunSummaryResponse();
        resp.setRunId(runId);

        // Q1: Run status and imported counts
        Map<String, Object> runInfo = jdbcTemplate.queryForMap(
                "SELECT r.status, fib.imported_rows AS bank_imported, fii.imported_rows AS internal_imported "
                + "FROM reconciliation_runs r "
                + "JOIN file_imports fib ON fib.id = r.bank_import_id "
                + "JOIN file_imports fii ON fii.id = r.internal_import_id "
                + "WHERE r.id = ?",
                runId);
        resp.setStatus((String) runInfo.get("status"));
        long totalBank = ((Number) runInfo.get("bank_imported")).longValue();
        long totalInternal = ((Number) runInfo.get("internal_imported")).longValue();

        // Q2: Count per result type
        List<Map<String, Object>> countRows = jdbcTemplate.queryForList(
                "SELECT result_type, COUNT(*) AS cnt "
                + "FROM reconciliation_results WHERE run_id = ? "
                + "GROUP BY result_type",
                runId);
        Map<String, Long> countMap = new LinkedHashMap<>();
        for (Map<String, Object> row : countRows) {
            countMap.put((String) row.get("result_type"), ((Number) row.get("cnt")).longValue());
        }
        resp.setCountPerResultType(countMap);

        // Q3: Sum of internal amounts per result type
        List<Map<String, Object>> internalValueRows = jdbcTemplate.queryForList(
                "SELECT r.result_type, COALESCE(SUM(it.amount), 0) AS total_amount "
                + "FROM reconciliation_results r "
                + "LEFT JOIN reconciliation_result_internal_transactions rrit "
                + "    ON rrit.reconciliation_result_id = r.id "
                + "LEFT JOIN internal_transactions it ON it.id = rrit.internal_transaction_id "
                + "WHERE r.run_id = ? "
                + "GROUP BY r.result_type",
                runId);

        Map<String, BigDecimal> valueMap = new LinkedHashMap<>();
        BigDecimal matchedValue = BigDecimal.ZERO;
        BigDecimal unmatchedInternalValue = BigDecimal.ZERO;

        for (Map<String, Object> row : internalValueRows) {
            String type = (String) row.get("result_type");
            BigDecimal total = toBigDecimal(row.get("total_amount"));
            valueMap.put(type, total);

            ReconciliationResultType rt = ReconciliationResultType.valueOf(type);
            if (PAIRED_TYPES.contains(rt)) {
                matchedValue = matchedValue.add(total);
            } else if (UNMATCHED_INTERNAL_TYPES.contains(rt)) {
                unmatchedInternalValue = unmatchedInternalValue.add(total);
            }
        }
        resp.setTotalValuePerResultType(valueMap);
        resp.setMatchedValue(matchedValue);
        resp.setUnmatchedInternalValue(unmatchedInternalValue);

        // Q4: Bank credit/debit sums for unmatched and explained types in single query
        String bankSummarySql = "SELECT "
                + "COALESCE(SUM(CASE WHEN r.result_type IN ('MISSING_IN_INTERNAL', 'BANK_DUPLICATE_DETECTED', 'UNEXPECTED_BANK_DEBIT') THEN b.credit_amount ELSE 0 END), 0) AS unmatched_credit, "
                + "COALESCE(SUM(CASE WHEN r.result_type IN ('MISSING_IN_INTERNAL', 'BANK_DUPLICATE_DETECTED', 'UNEXPECTED_BANK_DEBIT') THEN b.debit_amount ELSE 0 END), 0) AS unmatched_debit, "
                + "COALESCE(SUM(CASE WHEN r.result_type IN ('BANK_SERVICE_CHARGE', 'IGNORED_FAILED_STATUS') THEN b.debit_amount ELSE 0 END), 0) AS explained_debit "
                + "FROM reconciliation_results r "
                + "JOIN reconciliation_result_bank_rows rrbr ON rrbr.reconciliation_result_id = r.id "
                + "JOIN bank_statement_rows b ON b.id = rrbr.bank_statement_row_id "
                + "WHERE r.run_id = ?";

        BigDecimal[] bankValues = jdbcTemplate.queryForObject(
                bankSummarySql,
                (rs, i) -> new BigDecimal[]{
                    toBigDecimal(rs.getObject("unmatched_credit")),
                    toBigDecimal(rs.getObject("unmatched_debit")),
                    toBigDecimal(rs.getObject("explained_debit"))
                },
                runId);

        resp.setUnmatchedBankCreditValue(bankValues != null ? bankValues[0] : BigDecimal.ZERO);
        resp.setUnmatchedBankDebitValue(bankValues != null ? bankValues[1] : BigDecimal.ZERO);
        resp.setExplainedValue(bankValues != null ? bankValues[2] : BigDecimal.ZERO);

        // Q5: Distinct linked counts in single query
        String linkedCountsSql = "SELECT "
                + "(SELECT COUNT(DISTINCT rrit.internal_transaction_id) "
                + " FROM reconciliation_results r "
                + " JOIN reconciliation_result_internal_transactions rrit ON rrit.reconciliation_result_id = r.id "
                + " WHERE r.run_id = ?) AS linked_internal, "
                + "(SELECT COUNT(DISTINCT rrbr.bank_statement_row_id) "
                + " FROM reconciliation_results r "
                + " JOIN reconciliation_result_bank_rows rrbr ON rrbr.reconciliation_result_id = r.id "
                + " WHERE r.run_id = ?) AS linked_bank";

        Long[] linkedCounts = jdbcTemplate.queryForObject(
                linkedCountsSql,
                (rs, i) -> new Long[]{ rs.getLong("linked_internal"), rs.getLong("linked_bank") },
                runId, runId);

        long linkedInternal = (linkedCounts != null && linkedCounts[0] != null) ? linkedCounts[0] : 0L;
        long linkedBank = (linkedCounts != null && linkedCounts[1] != null) ? linkedCounts[1] : 0L;

        resp.setUnclassifiedInternalCount(totalInternal - linkedInternal);
        resp.setUnclassifiedBankCount(totalBank - linkedBank);

        return resp;
    }

    // =========================================================================
    // Paged results with filters
    // =========================================================================

    /**
     * Returns a page of reconciliation results for a run with optional filters.
     *
     * <p>Date filtering applies on the transaction date of the linked rows:
     * {@code COALESCE(min_internal_date, min_bank_date)} must fall within
     * {@code [fromDate, toDate]}.
     *
     * <p>Executes exactly 2 SQL statements per page (count + data) via CTE
     * and fetches aggregated links in the same query.
     */
    @Transactional(readOnly = true)
    public Page<ReconciliationResultDto> findResults(
            Long runId,
            String resultType,
            String ruleName,
            LocalDate fromDate,
            LocalDate toDate,
            Pageable pageable) {

        if (pageable.getPageSize() > 200) {
            throw new IllegalArgumentException("Page size must be 200 or less");
        }
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("fromDate must not be after toDate");
        }

        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> countParams = new ArrayList<>();
        countParams.add(runId);

        if (resultType != null && !resultType.isBlank()) {
            where.append(" AND result_type = ?");
            countParams.add(resultType);
        }
        if (ruleName != null && !ruleName.isBlank()) {
            where.append(" AND rule_name = ?");
            countParams.add(ruleName);
        }
        if (fromDate != null) {
            where.append(" AND COALESCE(min_internal_date, min_bank_date) >= ?");
            countParams.add(Date.valueOf(fromDate));
        }
        if (toDate != null) {
            where.append(" AND COALESCE(min_internal_date, min_bank_date) <= ?");
            countParams.add(Date.valueOf(toDate));
        }

        String cte = buildResultsCte();
        String countSql = "WITH results_cte AS (" + cte + ") SELECT COUNT(*) FROM results_cte" + where;
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, countParams.toArray());
        if (total == null) total = 0L;

        List<Object> dataParams = new ArrayList<>(countParams);
        String dataSql = "WITH results_cte AS (" + cte + ") "
                + "SELECT id, result_type, rule_name, rule_code, amount_difference, explanation, "
                + "       internal_txn_ids, bank_refs, min_internal_date, min_bank_date "
                + "FROM results_cte" + where
                + " ORDER BY id ASC"
                + " LIMIT ? OFFSET ?";
        dataParams.add(pageable.getPageSize());
        dataParams.add(pageable.getOffset());

        List<ReconciliationResultDto> rows = jdbcTemplate.query(
                dataSql,
                (rs, i) -> mapResultRow(rs),
                dataParams.toArray());

        return new PageImpl<>(rows, pageable, total);
    }

    // =========================================================================
    // CSV streaming report
    // =========================================================================

    /**
     * Streams a mismatch CSV report for a run. Only non-EXACT_MATCH results are included.
     *
     * <p>Executes inside a read-only transaction with autocommit disabled and fetchSize = 500,
     * ensuring PostgreSQL streams via a cursor rather than loading the whole result set.
     * SecurityContext is preserved in the streaming thread.
     */
    public StreamingResponseBody streamCsvReport(Long runId) {
        SecurityContext context = SecurityContextHolder.getContext();
        return outputStream -> {
            SecurityContext origContext = SecurityContextHolder.getContext();
            try {
                SecurityContextHolder.setContext(context);
                readOnlyTxTemplate.executeWithoutResult(status -> {
                    streamCsvRows(runId, outputStream);
                });
            } finally {
                SecurityContextHolder.setContext(origContext);
            }
        };
    }

    private void streamCsvRows(Long runId, OutputStream outputStream) {
        String sql = "SELECT r.id, r.result_type, r.rule_name, r.rule_code, "
                + "       r.amount_difference, r.explanation, "
                + "       string_agg(DISTINCT it.transaction_id, ',' ORDER BY it.transaction_id) AS internal_ids, "
                + "       string_agg(DISTINCT b.bank_reference, ',' ORDER BY b.bank_reference) AS bank_refs, "
                + "       MIN(it.transaction_date) AS internal_date, "
                + "       MIN(b.transaction_date) AS bank_date "
                + "FROM reconciliation_results r "
                + "LEFT JOIN reconciliation_result_internal_transactions rrit "
                + "     ON rrit.reconciliation_result_id = r.id "
                + "LEFT JOIN internal_transactions it ON it.id = rrit.internal_transaction_id "
                + "LEFT JOIN reconciliation_result_bank_rows rrbr "
                + "     ON rrbr.reconciliation_result_id = r.id "
                + "LEFT JOIN bank_statement_rows b ON b.id = rrbr.bank_statement_row_id "
                + "WHERE r.run_id = ? AND r.result_type <> 'EXACT_MATCH' "
                + "GROUP BY r.id, r.result_type, r.rule_name, r.rule_code, "
                + "         r.amount_difference, r.explanation "
                + "ORDER BY r.id ASC";

        try {
            Writer writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);

            // CSV Header
            writer.write("result_id,result_type,rule_name,rule_code,"
                    + "amount_difference,explanation,internal_transaction_ids,"
                    + "bank_references,internal_date,bank_date\n");

            RowCallbackHandler handler = rs -> {
                try {
                    StringBuilder line = new StringBuilder();
                    appendCsvField(line, rs.getString("id")); line.append(',');
                    appendCsvField(line, rs.getString("result_type")); line.append(',');
                    appendCsvField(line, rs.getString("rule_name")); line.append(',');
                    appendCsvField(line, rs.getString("rule_code")); line.append(',');
                    appendCsvField(line, rs.getString("amount_difference")); line.append(',');
                    appendCsvField(line, rs.getString("explanation")); line.append(',');
                    appendCsvField(line, rs.getString("internal_ids")); line.append(',');
                    appendCsvField(line, rs.getString("bank_refs")); line.append(',');
                    appendCsvField(line, rs.getString("internal_date")); line.append(',');
                    appendCsvField(line, rs.getString("bank_date")); line.append('\n');
                    writer.write(line.toString());
                } catch (Exception e) {
                    throw new RuntimeException("Error writing CSV row", e);
                }
            };

            jdbcTemplate.query(con -> {
                con.setAutoCommit(false);
                PreparedStatement ps = con.prepareStatement(
                        sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
                ps.setFetchSize(CSV_FETCH_SIZE);
                ps.setLong(1, runId);
                return ps;
            }, handler);

            writer.flush();
        } catch (Exception ex) {
            throw new RuntimeException("CSV report generation failed: " + ex.getMessage(), ex);
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * CTE aggregating linked internal and bank rows for a single run.
     */
    private static String buildResultsCte() {
        return "SELECT r.id, r.run_id, r.result_type, r.rule_name, r.rule_code, "
                + "       r.amount_difference, r.explanation, "
                + "       string_agg(DISTINCT it.transaction_id, ',' ORDER BY it.transaction_id) AS internal_txn_ids, "
                + "       string_agg(DISTINCT b.bank_reference, ',' ORDER BY b.bank_reference) AS bank_refs, "
                + "       MIN(it.transaction_date) AS min_internal_date, "
                + "       MIN(b.transaction_date) AS min_bank_date "
                + "FROM reconciliation_results r "
                + "LEFT JOIN reconciliation_result_internal_transactions rrit "
                + "     ON rrit.reconciliation_result_id = r.id "
                + "LEFT JOIN internal_transactions it ON it.id = rrit.internal_transaction_id "
                + "LEFT JOIN reconciliation_result_bank_rows rrbr "
                + "     ON rrbr.reconciliation_result_id = r.id "
                + "LEFT JOIN bank_statement_rows b ON b.id = rrbr.bank_statement_row_id "
                + "WHERE r.run_id = ? "
                + "GROUP BY r.id, r.run_id, r.result_type, r.rule_name, r.rule_code, "
                + "         r.amount_difference, r.explanation";
    }

    private ReconciliationResultDto mapResultRow(ResultSet rs) throws SQLException {
        ReconciliationResultDto dto = new ReconciliationResultDto();
        dto.setId(rs.getLong("id"));
        dto.setResultType(rs.getString("result_type"));
        dto.setRuleName(rs.getString("rule_name"));
        dto.setRuleCode(rs.getString("rule_code"));
        dto.setAmountDifference(rs.getBigDecimal("amount_difference"));
        dto.setExplanation(rs.getString("explanation"));
        dto.setInternalTransactionIds(rs.getString("internal_txn_ids"));
        dto.setBankReferences(rs.getString("bank_refs"));
        Date internalDate = rs.getDate("min_internal_date");
        if (internalDate != null) dto.setInternalTransactionDate(internalDate.toLocalDate());
        Date bankDate = rs.getDate("min_bank_date");
        if (bankDate != null) dto.setBankTransactionDate(bankDate.toLocalDate());
        return dto;
    }

    /**
     * Appends a CSV field with proper RFC 4180 escaping and formula injection protection:
     * <ul>
     *   <li>Null / empty values write nothing.</li>
     *   <li>Formula injection chars (=, +, -, @, \t, \r) are prefixed with a single quote.</li>
     *   <li>Fields containing comma, double-quote, or newline are quoted.</li>
     *   <li>Embedded double-quotes are doubled ("").</li>
     * </ul>
     */
    static void appendCsvField(StringBuilder sb, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        // Neutralize CSV formula injection
        char first = value.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@'
                || first == '\t' || first == '\r') {
            value = "'" + value;
        }
        boolean needsQuote = value.indexOf(',') >= 0
                || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\r') >= 0;
        if (needsQuote) {
            sb.append('"');
            sb.append(value.replace("\"", "\"\""));
            sb.append('"');
        } else {
            sb.append(value);
        }
    }

    private static String toSqlList(Set<ReconciliationResultType> types) {
        StringBuilder sb = new StringBuilder();
        for (ReconciliationResultType t : types) {
            if (sb.length() > 0) sb.append(',');
            sb.append('\'').append(t.name()).append('\'');
        }
        return sb.toString();
    }

    private static BigDecimal toBigDecimal(Object obj) {
        if (obj == null) return BigDecimal.ZERO;
        if (obj instanceof BigDecimal bd) return bd;
        return new BigDecimal(obj.toString());
    }
}
