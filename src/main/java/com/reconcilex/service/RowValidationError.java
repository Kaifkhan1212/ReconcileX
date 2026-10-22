package com.reconcilex.service;

public class RowValidationError {

    private final int rowNumber;
    private final String fieldName;
    private final String errorMessage;
    private final String rawValue;

    public RowValidationError(int rowNumber, String fieldName, String errorMessage, String rawValue) {
        this.rowNumber = rowNumber;
        this.fieldName = fieldName;
        this.errorMessage = errorMessage;
        this.rawValue = rawValue;
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public String getFieldName() {
        return fieldName;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getRawValue() {
        return rawValue;
    }
}
