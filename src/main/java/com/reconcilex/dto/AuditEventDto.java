package com.reconcilex.dto;

import java.time.Instant;

/**
 * A single audit event returned by {@code GET /api/audit-events}.
 */
public class AuditEventDto {

    private Long id;
    private Long runId;
    private String eventType;
    private String actor;
    private String details;
    private Instant occurredAt;

    public AuditEventDto() {
    }

    public AuditEventDto(Long id, Long runId, String eventType,
                         String actor, String details, Instant occurredAt) {
        this.id = id;
        this.runId = runId;
        this.eventType = eventType;
        this.actor = actor;
        this.details = details;
        this.occurredAt = occurredAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getRunId() { return runId; }
    public void setRunId(Long runId) { this.runId = runId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public Instant getOccurredAt() { return occurredAt; }
    public void setOccurredAt(Instant occurredAt) { this.occurredAt = occurredAt; }
}
