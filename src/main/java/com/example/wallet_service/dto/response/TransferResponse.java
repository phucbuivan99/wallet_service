package com.example.wallet_service.dto.response;

import com.example.wallet_service.entity.Transaction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransferResponse {
    private Long transactionId;
    private Long fromAccountId;
    private Long toAccountId;
    private BigDecimal amount;
    private Transaction.TransactionStatus status;
    private String idempotencyKey;
    private String description;
    private LocalDateTime createdAt;
    private BigDecimal fromAccountBalance;
    private BigDecimal toAccountBalance;
}


