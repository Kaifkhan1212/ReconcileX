package com.reconcilex.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.reconcilex.entity.BankStatementRow;

public interface BankStatementRowRepository
        extends JpaRepository<BankStatementRow, Long> {

    List<BankStatementRow>
    findByFileImport_IdOrderBySourceRowNumberAsc(Long fileImportId);

    List<BankStatementRow> findByPaymentReference(String paymentReference);

    List<BankStatementRow> findBySettlementReference(String settlementReference);

    boolean existsByBankReference(String bankReference);

    long countByFileImport_Id(Long fileImportId);

    /**
     * Scope dedup check to this import only.
     * After V6 migration, bank_reference uniqueness is per (file_import_id, bank_reference).
     * Rows from different imports sharing the same bank_reference are valid.
     */
    @Query("SELECT b.bankReference FROM BankStatementRow b " +
           "WHERE b.bankReference IN :bankReferences " +
           "AND b.fileImport.id = :fileImportId")
    java.util.Set<String> findExistingBankReferences(
            @Param("bankReferences") java.util.Collection<String> bankReferences,
            @Param("fileImportId") Long fileImportId
    );
}
