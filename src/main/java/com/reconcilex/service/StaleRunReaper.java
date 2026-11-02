package com.reconcilex.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.reconcilex.entity.ReconciliationRun;
import com.reconcilex.entity.ReconciliationRunStatus;
import com.reconcilex.repository.ReconciliationRunRepository;

/**
 * Periodic task that detects stuck reconciliation runs and marks them FAILED.
 *
 * <p>A run is considered stuck if it has been in RUNNING status for more than
 * {@value #STALE_THRESHOLD_MINUTES} minutes. This covers:
 * <ul>
 *   <li>Runs that timed out on Render's free-tier HTTP gateway before async was introduced.</li>
 *   <li>Runs whose async thread was killed by a JVM restart or OOM.</li>
 * </ul>
 *
 * <p>Fires every 2 minutes. At most a run can remain stuck for ~2 minutes after
 * the threshold before being detected.
 */
@Service
public class StaleRunReaper {

    private static final Logger log = LoggerFactory.getLogger(StaleRunReaper.class);

    /** Runs stuck longer than this are considered failed. */
    private static final long STALE_THRESHOLD_MINUTES = 10;

    private final ReconciliationRunRepository runRepository;

    public StaleRunReaper(ReconciliationRunRepository runRepository) {
        this.runRepository = runRepository;
    }

    @Scheduled(fixedDelay = 120_000) // every 2 minutes
    @Transactional
    public void reapStaleRuns() {
        Instant staleThreshold = Instant.now().minus(STALE_THRESHOLD_MINUTES, ChronoUnit.MINUTES);

        List<ReconciliationRun> staleRuns = runRepository.findStaleRunningRuns(
                ReconciliationRunStatus.RUNNING, staleThreshold);

        if (staleRuns.isEmpty()) {
            return;
        }

        log.warn("StaleRunReaper: found {} stuck run(s) older than {} minutes — marking FAILED",
                staleRuns.size(), STALE_THRESHOLD_MINUTES);

        for (ReconciliationRun run : staleRuns) {
            log.warn("  → Marking run #{} FAILED (started at {})", run.getId(), run.getStartedAt());
            run.setStatus(ReconciliationRunStatus.FAILED);
            run.setCompletedAt(Instant.now());
        }

        runRepository.saveAll(staleRuns);
    }
}

