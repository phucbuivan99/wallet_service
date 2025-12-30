package com.example.wallet_service.repository;

import com.example.wallet_service.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    // Get transactions for an account (both sent and received)
    @Query("SELECT t FROM Transaction t WHERE " +
           "(t.fromAccount.id = :accountId OR t.toAccount.id = :accountId) " +
           "ORDER BY t.createdAt DESC")
    Page<Transaction> findByAccountId(@Param("accountId") Long accountId, Pageable pageable);

    // Get sent transactions
    @Query("SELECT t FROM Transaction t WHERE t.fromAccount.id = :accountId ORDER BY t.createdAt DESC")
    Page<Transaction> findSentTransactionsByAccountId(@Param("accountId") Long accountId, Pageable pageable);

    // Get received transactions
    @Query("SELECT t FROM Transaction t WHERE t.toAccount.id = :accountId ORDER BY t.createdAt DESC")
    Page<Transaction> findReceivedTransactionsByAccountId(@Param("accountId") Long accountId, Pageable pageable);
}

