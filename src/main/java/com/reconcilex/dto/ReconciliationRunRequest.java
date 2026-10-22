package com.reconcilex.dto;

public class ReconciliationRunRequest {

    private Long bankImportId;
    private Long internalImportId;

    public ReconciliationRunRequest() {
    }

    public ReconciliationRunRequest(Long bankImportId, Long internalImportId) {
        this.bankImportId = bankImportId;
        this.internalImportId = internalImportId;
    }

    public Long getBankImportId() {
        return bankImportId;
    }

    public void setBankImportId(Long bankImportId) {
        this.bankImportId = bankImportId;
    }

    public Long getInternalImportId() {
        return internalImportId;
    }

    public void setInternalImportId(Long internalImportId) {
        this.internalImportId = internalImportId;
    }
}
