package com.reconcilex.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.reconcilex.entity.ReconciliationRun;
import com.reconcilex.entity.ReconciliationRunStatus;

public interface ReconciliationRunRepository
        extends JpaRepository<ReconciliationRun, Long> {

    List<ReconciliationRun>
    findByStatusOrderByStartedAtDesc(ReconciliationRunStatus status);
}