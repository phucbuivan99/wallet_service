package com.example.wallet_service.service;

import com.example.wallet_service.dto.request.TransferRequest;
import com.example.wallet_service.dto.response.BalanceResponse;
import com.example.wallet_service.dto.response.TransferResponse;
import com.example.wallet_service.dto.response.TransactionHistoryResponse;
import com.example.wallet_service.entity.Account;
import com.example.wallet_service.entity.IdempotencyKey;
import com.example.wallet_service.entity.Transaction;
import com.example.wallet_service.entity.User;
import com.example.wallet_service.exception.BadRequestException;
import com.example.wallet_service.exception.ResourceNotFoundException;
import com.example.wallet_service.repository.AccountRepository;
import com.example.wallet_service.repository.IdempotencyKeyRepository;
import com.example.wallet_service.repository.TransactionRepository;
import com.example.wallet_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final UserRepository userRepository;

    /**
     * Get account balance
     * Using READ_COMMITTED isolation level to read committed data
     */
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public BalanceResponse getBalance(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        Account account = accountRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found for user: " + userId));

        return BalanceResponse.builder()
                .accountId(account.getId())
                .userId(user.getId())
                .username(user.getUsername())
                .balance(account.getBalance())
                .lastUpdated(account.getUpdatedAt())
                .build();
    }

    /**
     * Transfer money between accounts
     * 
     * Key features:
     * 1. SERIALIZABLE isolation level - highest isolation, prevents phantom reads
     * 2. Pessimistic locking on accounts - prevents concurrent modifications
     * 3. Idempotency check - prevents double transfer
     * 4. Balance validation - ensures no negative balance
     * 5. Atomic transaction - all or nothing
     */
    @Transactional(
            isolation = Isolation.SERIALIZABLE,
            rollbackFor = Exception.class
    )
    public TransferResponse transfer(Long fromUserId, TransferRequest request) {
        log.info("Processing transfer request from user {} to account {} with amount {}", 
                fromUserId, request.getToAccountId(), request.getAmount());

        // Step 1: Check idempotency key (with pessimistic lock)
        IdempotencyKey idempotencyKey = checkAndCreateIdempotencyKey(
                fromUserId, 
                request.getIdempotencyKey()
        );

        // If key was already used, return the existing transaction
        if (idempotencyKey.getIsUsed() && idempotencyKey.getTransactionId() != null) {
            Transaction existingTransaction = transactionRepository
                    .findById(idempotencyKey.getTransactionId())
                    .orElseThrow(() -> new BadRequestException("Transaction not found for idempotency key"));

            Account fromAccount = accountRepository.findById(existingTransaction.getFromAccount().getId())
                    .orElseThrow();
            Account toAccount = accountRepository.findById(existingTransaction.getToAccount().getId())
                    .orElseThrow();

            return buildTransferResponse(existingTransaction, fromAccount, toAccount);
        }

        // Step 2: Get accounts with pessimistic lock (prevents concurrent modifications)
        Account fromAccount = accountRepository.findByIdWithLock(
                accountRepository.findByUserId(fromUserId)
                        .orElseThrow(() -> new ResourceNotFoundException("Account not found for user: " + fromUserId))
                        .getId()
        ).orElseThrow(() -> new ResourceNotFoundException("From account not found"));

        Account toAccount = accountRepository.findByIdWithLock(request.getToAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("To account not found"));

        // Step 3: Validate transfer
        validateTransfer(fromAccount, toAccount, request.getAmount());

        // Step 4: Create transaction record (PENDING status)
        Transaction transaction = Transaction.builder()
                .fromAccount(fromAccount)
                .toAccount(toAccount)
                .amount(request.getAmount())
                .status(Transaction.TransactionStatus.PENDING)
                .idempotencyKey(request.getIdempotencyKey())
                .description(request.getDescription())
                .build();

        transaction = transactionRepository.save(transaction);

        try {
            // Step 5: Perform transfer (atomic operation)
            fromAccount.setBalance(fromAccount.getBalance().subtract(request.getAmount()));
            toAccount.setBalance(toAccount.getBalance().add(request.getAmount()));

            accountRepository.save(fromAccount);
            accountRepository.save(toAccount);

            // Step 6: Update transaction status to COMPLETED
            transaction.setStatus(Transaction.TransactionStatus.COMPLETED);
            transaction = transactionRepository.save(transaction);

            // Step 7: Mark idempotency key as used
            idempotencyKey.setIsUsed(true);
            idempotencyKey.setTransactionId(transaction.getId());
            idempotencyKeyRepository.save(idempotencyKey);

            log.info("Transfer completed successfully. Transaction ID: {}", transaction.getId());

            return buildTransferResponse(transaction, fromAccount, toAccount);

        } catch (Exception e) {
            log.error("Error during transfer, rolling back transaction", e);
            transaction.setStatus(Transaction.TransactionStatus.FAILED);
            transactionRepository.save(transaction);
            throw new BadRequestException("Transfer failed: " + e.getMessage());
        }
    }

    /**
     * Check and create idempotency key with pessimistic lock
     */
    private IdempotencyKey checkAndCreateIdempotencyKey(Long userId, String keyValue) {
        // Try to get existing key with lock
        IdempotencyKey existingKey = idempotencyKeyRepository.findByKeyValueWithLock(keyValue)
                .orElse(null);

        if (existingKey != null) {
            // Check if expired
            if (existingKey.getExpiresAt().isBefore(LocalDateTime.now())) {
                log.warn("Idempotency key expired: {}", keyValue);
                throw new BadRequestException("Idempotency key has expired");
            }
            return existingKey;
        }

        // Create new idempotency key
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        IdempotencyKey newKey = IdempotencyKey.builder()
                .keyValue(keyValue)
                .user(user)
                .isUsed(false)
                .expiresAt(LocalDateTime.now().plusHours(24)) // Expires after 24 hours
                .build();

        return idempotencyKeyRepository.save(newKey);
    }

    /**
     * Validate transfer request
     */
    private void validateTransfer(Account fromAccount, Account toAccount, BigDecimal amount) {
        // Check if transferring to same account
        if (fromAccount.getId().equals(toAccount.getId())) {
            throw new BadRequestException("Cannot transfer to the same account");
        }

        // Check if amount is positive
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Transfer amount must be greater than zero");
        }

        // Check if balance is sufficient (no negative balance)
        if (fromAccount.getBalance().compareTo(amount) < 0) {
            throw new BadRequestException(
                    String.format("Insufficient balance. Current balance: %s, Required: %s",
                            fromAccount.getBalance(), amount)
            );
        }
    }

    /**
     * Build transfer response
     */
    private TransferResponse buildTransferResponse(Transaction transaction, Account fromAccount, Account toAccount) {
        return TransferResponse.builder()
                .transactionId(transaction.getId())
                .fromAccountId(fromAccount.getId())
                .toAccountId(toAccount.getId())
                .amount(transaction.getAmount())
                .status(transaction.getStatus())
                .idempotencyKey(transaction.getIdempotencyKey())
                .description(transaction.getDescription())
                .createdAt(transaction.getCreatedAt())
                .fromAccountBalance(fromAccount.getBalance())
                .toAccountBalance(toAccount.getBalance())
                .build();
    }

    /**
     * Get transaction history for an account
     */
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public Page<TransactionHistoryResponse> getTransactionHistory(Long userId, Pageable pageable) {
        Account account = accountRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found for user: " + userId));

        Page<Transaction> transactions = transactionRepository.findByAccountId(account.getId(), pageable);

        return transactions.map(t -> {
            String transactionType = t.getFromAccount().getId().equals(account.getId()) 
                    ? "SENT" 
                    : "RECEIVED";

            return TransactionHistoryResponse.builder()
                    .transactionId(t.getId())
                    .fromAccountId(t.getFromAccount().getId())
                    .fromUsername(t.getFromAccount().getUser().getUsername())
                    .toAccountId(t.getToAccount().getId())
                    .toUsername(t.getToAccount().getUser().getUsername())
                    .amount(t.getAmount())
                    .status(t.getStatus())
                    .description(t.getDescription())
                    .createdAt(t.getCreatedAt())
                    .transactionType(transactionType)
                    .build();
        });
    }

    /**
     * Create account for a user (if not exists)
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Account createAccountForUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        return accountRepository.findByUser(user)
                .orElseGet(() -> {
                    Account newAccount = Account.builder()
                            .user(user)
                            .balance(BigDecimal.ZERO)
                            .build();
                    return accountRepository.save(newAccount);
                });
    }

    /**
     * Get user ID by username
     */
    @Transactional(readOnly = true)
    public Long getUserIdByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + username));
        return user.getId();
    }
}

