package com.example.wallet_service.controller;

import com.example.wallet_service.dto.request.TransferRequest;
import com.example.wallet_service.dto.response.BalanceResponse;
import com.example.wallet_service.dto.response.TransferResponse;
import com.example.wallet_service.dto.response.TransactionHistoryResponse;
import com.example.wallet_service.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Payment", description = "Account & Payment APIs")
@SecurityRequirement(name = "bearerAuth")
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/balance")
    @Operation(summary = "Get account balance", description = "Get the current balance for the authenticated user")
    public ResponseEntity<BalanceResponse> getBalance(Authentication authentication) {
        Long userId = getUserIdFromAuthentication(authentication);
        BalanceResponse response = paymentService.getBalance(userId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/transfer")
    @Operation(
            summary = "Transfer money", 
            description = "Transfer money from authenticated user's account to another account. " +
                         "Requires idempotency key to prevent double transfers."
    )
    public ResponseEntity<TransferResponse> transfer(
            @Valid @RequestBody TransferRequest request,
            Authentication authentication
    ) {
        Long userId = getUserIdFromAuthentication(authentication);
        TransferResponse response = paymentService.transfer(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/transactions")
    @Operation(
            summary = "Get transaction history", 
            description = "Get transaction history for the authenticated user with pagination"
    )
    public ResponseEntity<Page<TransactionHistoryResponse>> getTransactionHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir,
            Authentication authentication
    ) {
        Long userId = getUserIdFromAuthentication(authentication);
        
        Sort sort = sortDir.equalsIgnoreCase("ASC") 
                ? Sort.by(sortBy).ascending() 
                : Sort.by(sortBy).descending();
        
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<TransactionHistoryResponse> response = paymentService.getTransactionHistory(userId, pageable);
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/account/create")
    @Operation(
            summary = "Create account", 
            description = "Create an account for the authenticated user if it doesn't exist"
    )
    public ResponseEntity<BalanceResponse> createAccount(Authentication authentication) {
        Long userId = getUserIdFromAuthentication(authentication);
        paymentService.createAccountForUser(userId);
        BalanceResponse response = paymentService.getBalance(userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Extract user ID from authentication object
     */
    private Long getUserIdFromAuthentication(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new RuntimeException("User not authenticated");
        }

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String username = userDetails.getUsername();
        
        return paymentService.getUserIdByUsername(username);
    }
}

