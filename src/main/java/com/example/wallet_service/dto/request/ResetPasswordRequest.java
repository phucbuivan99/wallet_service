package com.example.wallet_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordRequest {
    @NotBlank(message = "Token required")
    private String token;

    @NotBlank(message = "New password required")
    @Size(min = 6, message = "Password must be at least 6 character")
    private String newPassword;
}
