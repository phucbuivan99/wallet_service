package com.example.wallet_service.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest{
    @NotBlank(message = "Username required")
    @Size(min = 3, max = 30, message = "Username must be 3-30 characters")
    private String username;

    @NotBlank(message = "Email required")
    @Email(message = "Email invalid")
    private String email;

    @NotBlank(message = "Password required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    private String password;

    @NotBlank(message = "Full name required")
    @Size(max = 50, message = "Full name has limit 50 characters")
    private String fullName;
}
