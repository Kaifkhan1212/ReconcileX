package com.reconcilex.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.reconcilex.entity.BankStatementRow;

public interface BankStatementRowRepository
        extends JpaRepository<BankStatementRow, Long> {

    List<BankStatementRow>
    findByFileImport_IdOrderBySourceRowNumberAsc(Long fileImportId);

    List<BankStatementRow> findByPaymentReference(String paymentReference);

    List<BankStatementRow> findBySettlementReference(String settlementReference);

    boolean existsByBankReference(String bankReference);

    long countByFileImport_Id(Long fileImportId);

    @org.springframework.data.jpa.repository.Query("SELECT b.bankReference FROM BankStatementRow b WHERE b.bankReference IN :bankReferences")
    java.util.Set<String> findExistingBankReferences(@org.springframework.data.repository.query.Param("bankReferences") java.util.Collection<String> bankReferences);
}