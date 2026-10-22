package com.reconcilex.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.reconcilex.entity.ReconciliationResult;
import com.reconcilex.entity.ReconciliationResultType;

public interface ReconciliationResultRepository
        extends JpaRepository<ReconciliationResult, Long> {

    List<ReconciliationResult> findByRun_IdOrderByIdAsc(Long runId);

    long countByRun_IdAndResultType(
            Long runId,
            ReconciliationResultType resultType
    );
}