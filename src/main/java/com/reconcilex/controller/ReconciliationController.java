package com.reconcilex.controller;

import java.time.LocalDate;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.reconcilex.dto.ReconciliationResultDto;
import com.reconcilex.dto.ReconciliationRunDto;
import com.reconcilex.dto.ReconciliationRunRequest;
import com.reconcilex.dto.ReconciliationRunSummaryResponse;
import com.reconcilex.dto.RunSummaryResponse;
import com.reconcilex.entity.ReconciliationRun;
import com.reconcilex.repository.ReconciliationRunRepository;
import com.reconcilex.service.ReconciliationReportService;
import com.reconcilex.service.ReconciliationService;

/**
 * REST controller for reconciliation runs and reports.
 *
 * <p>Date filter note: {@code fromDate} and {@code toDate} filter on the
 * transaction date of the linked rows (MIN of linked internal dates and MIN
 * of linked bank dates). fromDate after toDate returns 400.
 *
 * <p>Sorting is fixed server-side (id ASC for results, startedAt DESC for
 * runs). No sort parameter is accepted.
 */
@RestController
@RequestMapping("/api/reconciliation/runs")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;
    private final ReconciliationRunRepository runRepository;
    private final ReconciliationReportService reportService;

    public ReconciliationController(
            ReconciliationService reconciliationService,
            ReconciliationRunRepository runRepository,
            ReconciliationReportService reportService) {
        this.reconciliationService = reconciliationService;
        this.runRepository = runRepository;
        this.reportService = reportService;
    }

    // ---- POST /api/reconciliation/runs ----

    @PostMapping
    public ResponseEntity<ReconciliationRunSummaryResponse> createRun(
            @RequestBody ReconciliationRunRequest request
    ) {
        if (request == null || request.getBankImportId() == null
                || request.getInternalImportId() == null) {
            throw new IllegalArgumentException("bankImportId and internalImportId are required");
        }
        ReconciliationRunSummaryResponse response = reconciliationService.runReconciliation(
                request.getBankImportId(),
                request.getInternalImportId()
        );
        return ResponseEntity.ok(response);
    }

    // ---- GET /api/reconciliation/runs ----

    /**
     * Paged list of all runs, newest first.
     * Query parameters: page (0-based), size (max 200).
     */
    @GetMapping
    public ResponseEntity<Page<ReconciliationRunDto>> listRuns(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        if (size > 200) {
            throw new IllegalArgumentException("Page size must be 200 or less");
        }
        Pageable pageable = PageRequest.of(page, size);
        Page<ReconciliationRun> runPage = runRepository.findAllOrderByStartedAtDesc(pageable);
        Page<ReconciliationRunDto> dtoPage = runPage.map(this::toDto);
        return ResponseEntity.ok(dtoPage);
    }

    // ---- GET /api/reconciliation/runs/{id} ----

    @GetMapping("/{id}")
    public ResponseEntity<ReconciliationRunDto> getRun(@PathVariable Long id) {
        ReconciliationRun run = runRepository.findByIdWithImports(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Run not found: " + id));
        return ResponseEntity.ok(toDto(run));
    }

    // ---- GET /api/reconciliation/runs/{id}/summary ----

    @GetMapping("/{id}/summary")
    public ResponseEntity<RunSummaryResponse> getSummary(@PathVariable Long id) {
        ReconciliationRun run = runRepository.findByIdWithImports(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Run not found: " + id));
        RunSummaryResponse summary = reportService.buildSummary(
                id,
                run.getBankImport().getId(),
                run.getInternalImport().getId()
        );
        return ResponseEntity.ok(summary);
    }

    // ---- GET /api/reconciliation/runs/{id}/results ----

    /**
     * Filtered paged results. Accepted filters: resultType, ruleName,
     * fromDate (yyyy-MM-dd), toDate (yyyy-MM-dd). Fixed sort: id ASC.
     * Max page size 200.
     */
    @GetMapping("/{id}/results")
    public ResponseEntity<Page<ReconciliationResultDto>> getResults(
            @PathVariable Long id,
            @RequestParam(required = false) String resultType,
            @RequestParam(required = false) String ruleName,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        if (!runRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Run not found: " + id);
        }
        if (size > 200) {
            throw new IllegalArgumentException("Page size must be 200 or less");
        }
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("fromDate must not be after toDate");
        }
        Pageable pageable = PageRequest.of(page, size);
        Page<ReconciliationResultDto> results =
                reportService.findResults(id, resultType, ruleName, fromDate, toDate, pageable);
        return ResponseEntity.ok(results);
    }

    // ---- GET /api/reconciliation/runs/{id}/report.csv ----

    /**
     * Streams a mismatch CSV report (all non-EXACT_MATCH results) for the
     * given run. The query runs inside a read-only transaction with
     * PostgreSQL cursor streaming (fetchSize = 500).
     */
    @GetMapping("/{id}/report.csv")
    public ResponseEntity<StreamingResponseBody> getCsvReport(@PathVariable Long id) {
        if (!runRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Run not found: " + id);
        }
        StreamingResponseBody body = reportService.streamCsvReport(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"reconciliation-run-" + id + "-report.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(body);
    }

    // ---- Mapping helper ----

    private ReconciliationRunDto toDto(ReconciliationRun run) {
        return new ReconciliationRunDto(
                run.getId(),
                run.getBankImport() != null ? run.getBankImport().getId() : null,
                run.getInternalImport() != null ? run.getInternalImport().getId() : null,
                run.getStatus(),
                run.getStartedAt(),
                run.getCompletedAt()
        );
    }
}
