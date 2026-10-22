package com.reconcilex.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.reconcilex.entity.FileImport;
import com.reconcilex.entity.FileImportType;

public interface FileImportRepository
        extends JpaRepository<FileImport, Long> {

    Optional<FileImport> findByFileTypeAndFileSha256(
            FileImportType fileType,
            String fileSha256
    );

    boolean existsByFileTypeAndFileSha256(
            FileImportType fileType,
            String fileSha256
    );
}