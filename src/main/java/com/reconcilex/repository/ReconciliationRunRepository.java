package com.reconcilex.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.reconcilex.entity.ReconciliationRun;
import com.reconcilex.entity.ReconciliationRunStatus;

public interface ReconciliationRunRepository
        extends JpaRepository<ReconciliationRun, Long> {

    List<ReconciliationRun> findByStatusOrderByStartedAtDesc(ReconciliationRunStatus status);

    /**
     * Paged list of all runs, ordered newest-first (by startedAt DESC, then id DESC).
     * Uses JOIN FETCH to load the associated FileImport in the same query.
     */
    @Query("SELECT r FROM ReconciliationRun r "
           + "ORDER BY r.startedAt DESC, r.id DESC")
    Page<ReconciliationRun> findAllOrderByStartedAtDesc(Pageable pageable);

    /**
     * Returns a run with its associated FileImports eagerly loaded.
     */
    @Query("SELECT r FROM ReconciliationRun r "
           + "JOIN FETCH r.bankImport "
           + "JOIN FETCH r.internalImport "
           + "WHERE r.id = :id")
    java.util.Optional<ReconciliationRun> findByIdWithImports(@Param("id") Long id);

    /**
     * Finds all runs with the given status whose startedAt is before the given threshold.
     * Used by {@link com.reconcilex.service.StaleRunReaper} to detect stuck runs.
     */
    @Query("SELECT r FROM ReconciliationRun r "
           + "WHERE r.status = :status AND r.startedAt < :threshold")
    List<ReconciliationRun> findStaleRunningRuns(
            @Param("status") ReconciliationRunStatus status,
            @Param("threshold") Instant threshold);
}
