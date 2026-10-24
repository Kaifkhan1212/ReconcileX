package com.reconcilex.service;

import java.sql.Timestamp;
import java.time.Instant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes append-only audit events.
 *
 * <p>Survival methods (failRun, failImport, rejectDuplicate) use
 * {@link Propagation#REQUIRES_NEW} so the audit write commits in its own
 * transaction and survives any outer rollback.
 *
 * <p>Normal methods (startRun, completeRun, etc.) participate in the
 * existing transaction because they must be rolled back together with their
 * parent if the outer transaction fails.
 *
 * <p>Rules:
 * <ul>
 *   <li>Never stores passwords or file contents in details.</li>
 *   <li>Actor is the authenticated username or "system".</li>
 *   <li>Details column is cast to JSONB via ?::jsonb at insert time.</li>
 * </ul>
 */
@Service
public class AuditEventService {

    // ---- Event type constants ----
    public static final String RUN_STARTED   = "RUN_STARTED";
    public static final String RUN_COMPLETED = "RUN_COMPLETED";
    public static final String RUN_FAILED    = "RUN_FAILED";

    public static final String IMPORT_STARTED   = "IMPORT_STARTED";
    public static final String IMPORT_COMPLETED = "IMPORT_COMPLETED";
    public static final String IMPORT_FAILED    = "IMPORT_FAILED";
    public static final String IMPORT_DUPLICATE_REJECTED = "IMPORT_DUPLICATE_REJECTED";

    public static final String MATCH_DECISION = "MATCH_DECISION";

    private final JdbcTemplate jdbcTemplate;

    public AuditEventService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // ---- Ordinary (non-REQUIRES_NEW) audit writes ----

    /** Participates in the outer transaction (roll back together). */
    @Transactional
    public void recordRunStarted(Long runId) {
        save(runId, RUN_STARTED,
             "{\"message\":\"Reconciliation run started\"}");
    }

    /** Participates in the outer transaction. */
    @Transactional
    public void recordRunCompleted(Long runId, int resultCount) {
        save(runId, RUN_COMPLETED,
             "{\"resultCount\":" + resultCount + "}");
    }

    /** Participates in the outer transaction. */
    @Transactional
    public void recordImportStarted(Long importId, String fileName) {
        String details = "{\"importId\":" + importId
                + ",\"fileName\":" + jsonString(fileName) + "}";
        save(null, IMPORT_STARTED, details);
    }

    /** Participates in the outer transaction. */
    @Transactional
    public void recordImportCompleted(Long importId, int importedRows, int rejectedRows) {
        String details = "{\"importId\":" + importId
                + ",\"importedRows\":" + importedRows
                + ",\"rejectedRows\":" + rejectedRows + "}";
        save(null, IMPORT_COMPLETED, details);
    }

    // ---- REQUIRES_NEW: must survive outer rollback ----

    /**
     * Written in its own transaction so it persists even when the outer
     * reconciliation transaction is rolled back after a run failure.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRunFailed(Long runId, String errorMessage) {
        String details = "{\"message\":" + jsonString(errorMessage) + "}";
        save(runId, RUN_FAILED, details);
    }

    /**
     * Written in its own transaction so it persists even when the import
     * transaction itself is rolled back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordImportFailed(Long importId, String errorMessage) {
        String details = "{\"importId\":" + importId
                + ",\"message\":" + jsonString(errorMessage) + "}";
        save(null, IMPORT_FAILED, details);
    }

    /**
     * Written in its own transaction. Called when a duplicate file upload
     * is rejected; the outer request never started a DB transaction that
     * could be rolled back, but using REQUIRES_NEW is safe and consistent.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDuplicateRejected(Long existingImportId, String fileName) {
        String details = "{\"existingImportId\":" + existingImportId
                + ",\"fileName\":" + jsonString(fileName) + "}";
        save(null, IMPORT_DUPLICATE_REJECTED, details);
    }

    // ---- Internal helper ----

    private void save(Long runId, String eventType, String details) {
        jdbcTemplate.update(
                "INSERT INTO audit_events (run_id, event_type, actor, details, occurred_at) "
                + "VALUES (?, ?, ?, ?::jsonb, ?)",
                runId,
                eventType,
                currentActor(),
                details,
                Timestamp.from(Instant.now())
        );
    }

    private String currentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        return "system";
    }

    /** Minimal JSON string escaping (no passwords or binary; safe for our use). */
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
