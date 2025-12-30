package com.example.wallet_service.dto.response;

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
public class BalanceResponse {
    private Long accountId;
    private Long userId;
    private String username;
    private BigDecimal balance;
    private LocalDateTime lastUpdated;
}


