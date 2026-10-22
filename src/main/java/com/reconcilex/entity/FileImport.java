package com.reconcilex.entity;

import java.time.Instant;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "file_imports",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_file_import_type_hash",
                        columnNames = {"file_type", "file_sha256"}
                )
        }
)
public class FileImport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false, length = 30)
    private FileImportType fileType;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_sha256", nullable = false, length = 64)
    private String fileSha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private FileImportStatus status = FileImportStatus.PENDING;

    @Column(name = "total_rows", nullable = false)
    private int totalRows = 0;

    @Column(name = "imported_rows", nullable = false)
    private int importedRows = 0;

    @Column(name = "rejected_rows", nullable = false)
    private int rejectedRows = 0;

    @Column(name = "skipped_rows", nullable = false)
    private int skippedRows = 0;

    @CreationTimestamp
    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public FileImport() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public FileImportType getFileType() {
        return fileType;
    }

    public void setFileType(FileImportType fileType) {
        this.fileType = fileType;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileSha256() {
        return fileSha256;
    }

    public void setFileSha256(String fileSha256) {
        this.fileSha256 = fileSha256;
    }

    public FileImportStatus getStatus() {
        return status;
    }

    public void setStatus(FileImportStatus status) {
        this.status = status;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(int totalRows) {
        this.totalRows = totalRows;
    }

    public int getImportedRows() {
        return importedRows;
    }

    public void setImportedRows(int importedRows) {
        this.importedRows = importedRows;
    }

    public int getRejectedRows() {
        return rejectedRows;
    }

    public void setRejectedRows(int rejectedRows) {
        this.rejectedRows = rejectedRows;
    }

    public int getSkippedRows() {
        return skippedRows;
    }

    public void setSkippedRows(int skippedRows) {
        this.skippedRows = skippedRows;
    }

    public int getInsertedRows() {
        return importedRows;
    }

    public void setInsertedRows(int insertedRows) {
        this.importedRows = insertedRows;
    }

    public int getInvalidRows() {
        return rejectedRows;
    }

    public void setInvalidRows(int invalidRows) {
        this.rejectedRows = invalidRows;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }
}