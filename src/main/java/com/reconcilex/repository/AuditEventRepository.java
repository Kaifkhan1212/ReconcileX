package com.reconcilex.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.reconcilex.entity.AuditEvent;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    /** All events for a run, newest first. */
    Page<AuditEvent> findByRunIdOrderByOccurredAtDesc(Long runId, Pageable pageable);

    /** All events of a given type, newest first. */
    Page<AuditEvent> findByEventTypeOrderByOccurredAtDesc(String eventType, Pageable pageable);

    /** All events for a run AND a given event type. */
    Page<AuditEvent> findByRunIdAndEventTypeOrderByOccurredAtDesc(
            Long runId, String eventType, Pageable pageable);

    /** All events, newest first. */
    Page<AuditEvent> findAllByOrderByOccurredAtDesc(Pageable pageable);

    /** Used in tests to verify audit survival. */
    List<AuditEvent> findByRunIdOrderByOccurredAtAsc(Long runId);

    List<AuditEvent> findByEventType(String eventType);

    /** Count how many events of a given type exist for a run — used in MATCH_DECISION assertions. */
    long countByRunIdAndEventType(Long runId, String eventType);

    /** All events of a given type for a run — used in per-result MATCH_DECISION assertions. */
    List<AuditEvent> findByRunIdAndEventType(Long runId, String eventType);
}
