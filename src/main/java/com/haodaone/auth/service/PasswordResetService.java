package com.haodaone.auth.service;

import com.haodaone.auth.entity.PasswordResetToken;
import com.haodaone.auth.repository.PasswordResetTokenRepository;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.recruitment.service.EmailService;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;

@Service
public class PasswordResetService {
    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);
    private static final int TOKEN_BYTES = 64;

    private final PasswordResetTokenRepository tokens;
    private final UserRepository users;
    private final EmployeeRepository employees;
    private final AuthService authService;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.password-reset.expiry-minutes:60}")
    private long expiryMinutes;

    public PasswordResetService(PasswordResetTokenRepository tokens, UserRepository users,
                                EmployeeRepository employees, AuthService authService,
                                EmailService emailService) {
        this.tokens = tokens;
        this.users = users;
        this.employees = employees;
        this.authService = authService;
        this.emailService = emailService;
    }

    @Transactional
    public void requestReset(String identifier) {
        if (identifier == null || identifier.isBlank()) return;
        String value = identifier.trim();
        String normalized = value.toLowerCase(Locale.ROOT);
        Optional<User> user = users.findByEmailIgnoreCaseAndDeletedFalse(normalized)
                .or(() -> users.findByUsernameAndDeletedFalse(value))
                .or(() -> employees.findByEmployeeCodeIgnoreCaseAndDeletedFalse(value).map(Employee::getUser));
        if (user.isEmpty() || !user.get().isActive()
                || !"ACTIVE".equalsIgnoreCase(user.get().getAccountStatus())
                || user.get().getEmail() == null || user.get().getEmail().isBlank()) return;

        User account = user.get();
        tokens.invalidateUnusedForUser(account.getId());
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(expiryMinutes);

        PasswordResetToken token = new PasswordResetToken();
        token.setUser(account);
        token.setTokenHash(hash(rawToken));
        token.setExpiresAt(expiresAt);
        tokens.save(token);

        Runnable sendEmail = () -> {
            try {
                emailService.sendPasswordResetEmail(account.getEmail(), account.getFullName(), rawToken, expiryMinutes);
            } catch (RuntimeException exception) {
                log.warn("Could not send password reset email for account id {}", account.getId());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() { sendEmail.run(); }
            });
        } else {
            sendEmail.run();
        }
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank()) throw invalidToken();
        if (!newPassword.matches(".*[A-Z].*") || !newPassword.matches(".*[a-z].*")
                || !newPassword.matches(".*[0-9].*") || !newPassword.matches(".*[^A-Za-z0-9].*")) {
            throw new BadRequestException("Password must include uppercase and lowercase letters, a number, and a special character.");
        }

        PasswordResetToken token = tokens.findByTokenHashForUpdate(hash(rawToken)).orElseThrow(this::invalidToken);
        if (token.getUsedAt() != null || !token.getExpiresAt().isAfter(LocalDateTime.now())) throw invalidToken();

        authService.resetPassword(token.getUser().getId(), newPassword);
        token.setUsedAt(LocalDateTime.now());
        tokens.save(token);
        tokens.invalidateUnusedForUser(token.getUser().getId());
    }

    private BadRequestException invalidToken() {
        return new BadRequestException("This reset link is invalid or expired.");
    }

    private String hash(String value) {
        try {
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 not available", exception);
        }
    }
}