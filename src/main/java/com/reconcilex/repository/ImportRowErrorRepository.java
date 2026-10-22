package com.reconcilex.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.reconcilex.entity.ImportRowError;

@Repository
public interface ImportRowErrorRepository extends JpaRepository<ImportRowError, Long> {

    List<ImportRowError> findByFileImport_IdOrderByRowNumberAsc(Long fileImportId, Pageable pageable);

    void deleteByFileImport_Id(Long fileImportId);

    long countByFileImport_Id(Long fileImportId);
}
