package com.example.wallet_service.service;

import com.example.wallet_service.dto.request.ForgotPasswordRequest;
import com.example.wallet_service.dto.request.LoginRequest;
import com.example.wallet_service.dto.request.RegisterRequest;
import com.example.wallet_service.dto.request.ResetPasswordRequest;
import com.example.wallet_service.dto.response.AuthResponse;
import com.example.wallet_service.entity.Role;
import com.example.wallet_service.entity.User;
import com.example.wallet_service.exception.BadRequestException;
import com.example.wallet_service.exception.ResourceNotFoundException;
import com.example.wallet_service.repository.RoleRepository;
import com.example.wallet_service.repository.UserRepository;
import com.example.wallet_service.util.JwtTokenUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenUtil jwtTokenUtil;
    private final EmailService emailService;

    @Transactional
    public AuthResponse register(RegisterRequest request) throws BadRequestException {
        // check user exist
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BadRequestException("User existed");
        }

        // check email existed
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email existed");
        }

        // create new user
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .enabled(true)
                .accountNonLocked(true)
                .build();

        // assign role, default ROLE_USER
        Role userRole = roleRepository.findByName(Role.RoleName.ROLE_USER)
                .orElseThrow(() -> new RuntimeException("Role ROLE_USER not existed"));
        user.getRoles().add(userRole);

        user = userRepository.save(user);

        // create JWT token
        String token = jwtTokenUtil.generateToken(user.getUsername(), getAuthorities(user));

        return AuthResponse.builder()
                .token(token)
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .roles(getAuthorities(user))
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        // Authenticate user
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);

        // get user from db
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new ResourceNotFoundException("User not existed"));

        // create JWT token
        String token = jwtTokenUtil.generateToken(user.getUsername(), getAuthorities(user));

        return AuthResponse.builder()
                .token(token)
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .roles(getAuthorities(user))
                .build();
    }

    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Email not existed"));

        // create reset token
        String resetToken = UUID.randomUUID().toString();
        user.setResetPasswordToken(resetToken);
        user.setResetPasswordTokenExpiry(LocalDateTime.now().plusHours(1));

        userRepository.save(user);

        // send email reset password
        emailService.sendPasswordResetEmail(user.getEmail(), resetToken);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) throws BadRequestException {
        User user = userRepository.findByResetPasswordToken(request.getToken())
                .orElseThrow(() -> new BadRequestException("Token invalid"));

        // check token expired
        if (user.getResetPasswordTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Token expired");
        }

        // update password
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setResetPasswordToken(null);
        user.setResetPasswordTokenExpiry(null);

        userRepository.save(user);
    }

    private Set<String> getAuthorities(User user) {
        return user.getRoles().stream()
                .map(role -> role.getName().name())
                .collect(Collectors.toSet());
    }
}