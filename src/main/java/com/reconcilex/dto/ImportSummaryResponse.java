package com.reconcilex.dto;

import java.util.ArrayList;
import java.util.List;

import com.reconcilex.entity.FileImportType;

public class ImportSummaryResponse {

    private Long importId;
    private FileImportType fileType;
    private int totalRows;
    private int insertedRows;
    private int skippedRows;
    private int invalidRows;
    private List<ImportRowErrorDto> errors = new ArrayList<>();

    public ImportSummaryResponse() {
    }

    public ImportSummaryResponse(
            Long importId,
            FileImportType fileType,
            int totalRows,
            int insertedRows,
            int skippedRows,
            int invalidRows,
            List<ImportRowErrorDto> errors) {
        this.importId = importId;
        this.fileType = fileType;
        this.totalRows = totalRows;
        this.insertedRows = insertedRows;
        this.skippedRows = skippedRows;
        this.invalidRows = invalidRows;
        this.errors = errors != null ? errors : new ArrayList<>();
    }

    public Long getImportId() {
        return importId;
    }

    public void setImportId(Long importId) {
        this.importId = importId;
    }

    public FileImportType getFileType() {
        return fileType;
    }

    public void setFileType(FileImportType fileType) {
        this.fileType = fileType;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public void setTotalRows(int totalRows) {
        this.totalRows = totalRows;
    }

    public int getInsertedRows() {
        return insertedRows;
    }

    public void setInsertedRows(int insertedRows) {
        this.insertedRows = insertedRows;
    }

    public int getSkippedRows() {
        return skippedRows;
    }

    public void setSkippedRows(int skippedRows) {
        this.skippedRows = skippedRows;
    }

    public int getInvalidRows() {
        return invalidRows;
    }

    public void setInvalidRows(int invalidRows) {
        this.invalidRows = invalidRows;
    }

    public List<ImportRowErrorDto> getErrors() {
        return errors;
    }

    public void setErrors(List<ImportRowErrorDto> errors) {
        this.errors = errors;
    }
}
