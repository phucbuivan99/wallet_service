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
public class TransactionHistoryResponse {
    private Long transactionId;
    private Long fromAccountId;
    private String fromUsername;
    private Long toAccountId;
    private String toUsername;
    private BigDecimal amount;
    private Transaction.TransactionStatus status;
    private String description;
    private LocalDateTime createdAt;
    private String transactionType; // "SENT" or "RECEIVED"
}


