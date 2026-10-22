package com.reconcilex.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(
        name = "import_row_errors",
        indexes = {
                @Index(name = "idx_import_row_errors_file_import_id", columnList = "file_import_id")
        }
)
public class ImportRowError {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "file_import_id", nullable = false)
    private FileImport fileImport;

    @Column(name = "row_number", nullable = false)
    private int rowNumber;

    @Column(name = "field_name", nullable = false, length = 100)
    private String fieldName;

    @Column(name = "error_message", nullable = false, columnDefinition = "text")
    private String errorMessage;

    @Column(name = "raw_value", columnDefinition = "text")
    private String rawValue;

    public ImportRowError() {
    }

    public ImportRowError(FileImport fileImport, int rowNumber, String fieldName, String errorMessage, String rawValue) {
        this.fileImport = fileImport;
        this.rowNumber = rowNumber;
        this.fieldName = fieldName;
        this.errorMessage = errorMessage;
        this.rawValue = rawValue;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public FileImport getFileImport() {
        return fileImport;
    }

    public void setFileImport(FileImport fileImport) {
        this.fileImport = fileImport;
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public void setRowNumber(int rowNumber) {
        this.rowNumber = rowNumber;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getRawValue() {
        return rawValue;
    }

    public void setRawValue(String rawValue) {
        this.rawValue = rawValue;
    }
}
