package com.haodaone.auth.service;

import com.haodaone.audit.entity.LoginHistory;
import com.haodaone.audit.repository.LoginHistoryRepository;
import com.haodaone.audit.service.AuditLogService;
import com.haodaone.auth.dto.LoginRequest;
import com.haodaone.auth.dto.LoginResponse;
import com.haodaone.auth.entity.RefreshToken;
import com.haodaone.auth.repository.RefreshTokenRepository;
import com.haodaone.common.exception.AuthenticationFailedException;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.security.JwtService;
import com.haodaone.user.dto.UserDTO;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_MINUTES = 15;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginHistoryRepository loginHistoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditLogService auditLogService;
    private final EmployeeRepository employeeRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.jwt.refresh-token-expiry-days:7}")
    private long refreshTokenExpiryDays;

    public AuthService(UserRepository userRepository,
                        RefreshTokenRepository refreshTokenRepository,
                        LoginHistoryRepository loginHistoryRepository,
                        PasswordEncoder passwordEncoder,
                        JwtService jwtService,
                        AuditLogService auditLogService,
                        EmployeeRepository employeeRepository) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.loginHistoryRepository = loginHistoryRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditLogService = auditLogService;
        this.employeeRepository = employeeRepository;
    }

    /** Every UserDTO handed back to the frontend needs this resolved the same way - see UserDTO#getEmployeeId. */
    private UserDTO toUserDTO(User user) {
        UserDTO dto = UserDTO.from(user);
        employeeRepository.findByUser_UsernameAndDeletedFalse(user.getUsername())
                .ifPresent(employee -> dto.setEmployeeId(employee.getId()));
        return dto;
    }

    @Transactional
    public LoginResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        String identifier = request.getUsername() == null ? null : request.getUsername().trim();
        String normalizedEmail = identifier == null ? null : identifier.toLowerCase(Locale.ROOT);
        User user = userRepository.findByUsernameAndDeletedFalse(identifier)
            .or(() -> userRepository.findByEmailIgnoreCaseAndDeletedFalse(normalizedEmail))
            .orElseGet(() -> employeeRepository.findByEmployeeCodeIgnoreCaseAndDeletedFalse(identifier)
                .map(com.haodaone.employee.entity.Employee::getUser)
                .orElse(null));

        if (user == null) {
            recordLoginAttempt(request.getUsername(), false, "No such user", httpRequest);
            throw new AuthenticationFailedException("Invalid username or password");
        }

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            recordLoginAttempt(request.getUsername(), false, "Account locked", httpRequest);
            throw new AuthenticationFailedException(
                    "Account locked due to too many failed attempts. Try again after " + user.getLockedUntil());
        }

        if (!user.isActive()) {
            recordLoginAttempt(request.getUsername(), false, "Account inactive", httpRequest);
            throw new AuthenticationFailedException("This account has been deactivated");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            handleFailedAttempt(user);
            recordLoginAttempt(request.getUsername(), false, "Invalid password", httpRequest);
            throw new AuthenticationFailedException("Invalid username or password");
        }

        // Success - reset lockout counters, record login, issue tokens.
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        recordLoginAttempt(request.getUsername(), true, null, httpRequest);
        auditLogService.log("User", user.getId(), "LOGIN", "Successful login");

        List<String> roleNames = user.getRoles().stream().map(r -> r.getName()).toList();
        String accessToken = jwtService.generateAccessToken(user.getUsername(), roleNames);
        String refreshToken = issueRefreshToken(user, httpRequest);

        return new LoginResponse(accessToken, refreshToken, toUserDTO(user));
    }

    @Transactional
    public LoginResponse refresh(String rawRefreshToken, HttpServletRequest httpRequest) {
        String hash = hash(rawRefreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new AuthenticationFailedException("Invalid or expired refresh token"));

        if (!stored.isActive()) {
            throw new AuthenticationFailedException("Invalid or expired refresh token");
        }

        User user = stored.getUser();
        if (!user.isActive()) {
            throw new AuthenticationFailedException("This account has been deactivated");
        }

        // Rotate: revoke the used refresh token and issue a brand new one -
        // limits the blast radius if a refresh token is ever intercepted.
        stored.setRevoked(true);
        stored.setRevokedAt(LocalDateTime.now());
        refreshTokenRepository.save(stored);

        List<String> roleNames = user.getRoles().stream().map(r -> r.getName()).toList();
        String accessToken = jwtService.generateAccessToken(user.getUsername(), roleNames);
        String newRefreshToken = issueRefreshToken(user, httpRequest);

        return new LoginResponse(accessToken, newRefreshToken, toUserDTO(user));
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(hash(rawRefreshToken)).ifPresent(token -> {
            token.setRevoked(true);
            token.setRevokedAt(LocalDateTime.now());
            refreshTokenRepository.save(token);
        });
    }

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationFailedException("User not found"));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        userRepository.save(user);

        // Force re-login everywhere - a changed password should invalidate old sessions.
        refreshTokenRepository.revokeAllForUser(user.getId());
        auditLogService.log("User", user.getId(), "PASSWORD_CHANGE", "Password changed by user");
    }

    private void handleFailedAttempt(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            user.setLockedUntil(LocalDateTime.now().plusMinutes(LOCKOUT_MINUTES));
            log.warn("Account '{}' locked for {} minutes after {} failed login attempts", user.getUsername(), LOCKOUT_MINUTES, attempts);
        }
        userRepository.save(user);
    }

    private void recordLoginAttempt(String username, boolean success, String failureReason, HttpServletRequest request) {
        LoginHistory entry = new LoginHistory();
        entry.setUsername(username);
        entry.setSuccess(success);
        entry.setFailureReason(failureReason);
        entry.setIpAddress(request.getRemoteAddr());
        entry.setUserAgent(request.getHeader("User-Agent"));
        loginHistoryRepository.save(entry);
    }

    private String issueRefreshToken(User user, HttpServletRequest request) {
        String rawToken = generateSecureToken();
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(hash(rawToken));
        token.setExpiresAt(LocalDateTime.now().plusDays(refreshTokenExpiryDays));
        token.setCreatedByIp(request.getRemoteAddr());
        token.setUserAgent(request.getHeader("User-Agent"));
        refreshTokenRepository.save(token);
        return rawToken;
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[64];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Refresh tokens are hashed at rest (SHA-256 is fine here - this isn't a password, it's a high-entropy random token, so no need for slow/salted hashing like bcrypt). */
    private String hash(String value) {
        return sha256(value);
    }

    private String sha256(String value) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
