package com.reconcilex.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.reconcilex.dto.AuditEventDto;
import com.reconcilex.entity.AuditEvent;
import com.reconcilex.repository.AuditEventRepository;

/**
 * REST controller for audit events.
 * GET /api/audit-events?runId=&eventType=&page=&size=
 */
@RestController
@RequestMapping("/api/audit-events")
public class AuditController {

    private final AuditEventRepository auditEventRepository;

    public AuditController(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    /**
     * Lists audit events with optional filters.
     * Fixed sort: occurredAt DESC. Max page size 200.
     *
     * @param runId     optional run ID filter
     * @param eventType optional event type filter
     * @param page      0-based page number (default 0)
     * @param size      page size, max 200 (default 50)
     */
    @GetMapping
    public ResponseEntity<Page<AuditEventDto>> listAuditEvents(
            @RequestParam(required = false) Long runId,
            @RequestParam(required = false) String eventType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        if (size > 200) {
            throw new IllegalArgumentException("Page size must be 200 or less");
        }
        Pageable pageable = PageRequest.of(page, size);

        Page<AuditEvent> events;
        if (runId != null && eventType != null && !eventType.isBlank()) {
            events = auditEventRepository
                    .findByRunIdAndEventTypeOrderByOccurredAtDesc(runId, eventType, pageable);
        } else if (runId != null) {
            events = auditEventRepository
                    .findByRunIdOrderByOccurredAtDesc(runId, pageable);
        } else if (eventType != null && !eventType.isBlank()) {
            events = auditEventRepository
                    .findByEventTypeOrderByOccurredAtDesc(eventType, pageable);
        } else {
            events = auditEventRepository
                    .findAllByOrderByOccurredAtDesc(pageable);
        }

        Page<AuditEventDto> dtoPage = events.map(this::toDto);
        return ResponseEntity.ok(dtoPage);
    }

    private AuditEventDto toDto(AuditEvent e) {
        return new AuditEventDto(
                e.getId(), e.getRunId(), e.getEventType(),
                e.getActor(), e.getDetails(), e.getOccurredAt()
        );
    }
}
