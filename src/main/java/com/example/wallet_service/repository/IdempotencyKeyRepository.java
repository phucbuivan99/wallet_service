package com.example.wallet_service.repository;

import com.example.wallet_service.entity.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {
    // Use pessimistic lock to prevent concurrent processing of same idempotency key
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT ik FROM IdempotencyKey ik WHERE ik.keyValue = :keyValue")
    Optional<IdempotencyKey> findByKeyValueWithLock(@Param("keyValue") String keyValue);

    Optional<IdempotencyKey> findByKeyValue(String keyValue);

    // Clean up expired keys
    @Query("DELETE FROM IdempotencyKey ik WHERE ik.expiresAt < :now")
    void deleteExpiredKeys(@Param("now") LocalDateTime now);
}


