package com.reconcilex.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.reconcilex.entity.InternalTransaction;

public interface InternalTransactionRepository
        extends JpaRepository<InternalTransaction, Long> {

    List<InternalTransaction>
    findByFileImport_IdOrderBySourceRowNumberAsc(Long fileImportId);

    List<InternalTransaction>
    findByPaymentReference(String paymentReference);

    List<InternalTransaction>
    findBySettlementReference(String settlementReference);

    boolean existsByTransactionId(String transactionId);

    long countByFileImport_Id(Long fileImportId);

    /**
     * Scope dedup check to this import only.
     * After V6 migration, transaction_id uniqueness is per (file_import_id, transaction_id).
     * Rows from different imports sharing the same transaction_id are valid.
     */
    @Query("SELECT t.transactionId FROM InternalTransaction t " +
           "WHERE t.transactionId IN :transactionIds " +
           "AND t.fileImport.id = :fileImportId")
    java.util.Set<String> findExistingTransactionIds(
            @Param("transactionIds") java.util.Collection<String> transactionIds,
            @Param("fileImportId") Long fileImportId
    );
}
