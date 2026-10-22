package com.reconcilex.dto;

public class ImportRowErrorDto {

    private int rowNumber;
    private String field;
    private String reason;
    private String rawValue;

    public ImportRowErrorDto() {
    }

    public ImportRowErrorDto(int rowNumber, String field, String reason, String rawValue) {
        this.rowNumber = rowNumber;
        this.field = field;
        this.reason = reason;
        this.rawValue = rawValue;
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public void setRowNumber(int rowNumber) {
        this.rowNumber = rowNumber;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getRawValue() {
        return rawValue;
    }

    public void setRawValue(String rawValue) {
        this.rawValue = rawValue;
    }
}
