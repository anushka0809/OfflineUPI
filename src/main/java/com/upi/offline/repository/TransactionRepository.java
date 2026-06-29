package com.upi.offline.repository;

import com.upi.offline.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {

    List<Transaction> findByStatus(String status);

    long countByStatus(String status);

    boolean existsByTransactionId(String transactionId);

    Optional<Transaction> findByTransactionId(String transactionId);

    @Query("SELECT SUM(t.amount) FROM Transaction t")
    Double sumAllAmount();

    @Query("SELECT SUM(t.amount) FROM Transaction t WHERE t.status = :status")
    Double sumAmountByStatus(@Param("status") String status);
}