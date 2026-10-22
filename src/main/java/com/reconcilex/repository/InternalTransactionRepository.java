package com.reconcilex.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

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

    @org.springframework.data.jpa.repository.Query("SELECT t.transactionId FROM InternalTransaction t WHERE t.transactionId IN :transactionIds")
    java.util.Set<String> findExistingTransactionIds(@org.springframework.data.repository.query.Param("transactionIds") java.util.Collection<String> transactionIds);
}