package com.example.wallet_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.reset-password.frontend-url:http://localhost:5173/reset-password}")
    private String resetPasswordFrontendUrl;

    public void sendPasswordResetEmail(String email, String token) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(email);
            message.setSubject("Reset Password - Wallet Service");
            message.setText("To reset your password, please click on the following link:\n" +
                    resetPasswordFrontendUrl + "?token=" + token + "\n\n" +
                    "This link will expire in 1 hour.");

            mailSender.send(message);
            log.info("Password reset email sent to: {}", email);
        } catch (Exception e) {
            log.error("Error sending email to {}: {}", email, e.getMessage());
            // In production, you can throw exception or queue for retry
        }
    }
}