package com.reconcilex.exception;

import com.reconcilex.dto.ImportSummaryResponse;

public class DuplicateFileImportException extends RuntimeException {

    private final ImportSummaryResponse summary;

    public DuplicateFileImportException(String message, ImportSummaryResponse summary) {
        super(message);
        this.summary = summary;
    }

    public ImportSummaryResponse getSummary() {
        return summary;
    }
}
