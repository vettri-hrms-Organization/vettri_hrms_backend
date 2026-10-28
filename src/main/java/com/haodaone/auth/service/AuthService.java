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
import com.haodaone.user.repository.UserPermissionGrantRepository;
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
    private final UserPermissionGrantRepository permissionGrantRepository;
    private final SecureRandom secureRandom = new SecureRandom();
    private final MfaService mfaService;

    @Value("${app.jwt.refresh-token-expiry-days:7}")
    private long refreshTokenExpiryDays;

    public AuthService(UserRepository userRepository,
                        RefreshTokenRepository refreshTokenRepository,
                        LoginHistoryRepository loginHistoryRepository,
                        PasswordEncoder passwordEncoder,
                        JwtService jwtService,
                        AuditLogService auditLogService,
                        EmployeeRepository employeeRepository,
                        UserPermissionGrantRepository permissionGrantRepository,
                        MfaService mfaService) {
        this.mfaService = mfaService;
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.loginHistoryRepository = loginHistoryRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditLogService = auditLogService;
        this.employeeRepository = employeeRepository;
        this.permissionGrantRepository = permissionGrantRepository;
    }

    /** Every UserDTO handed back to the frontend needs this resolved the same way - see UserDTO#getEmployeeId. */
    private UserDTO toUserDTO(User user) {
        UserDTO dto = UserDTO.from(user, user.getCompany() == null ? List.of()
                : permissionGrantRepository.findAllByCompany_IdAndUser_IdAndRevokedAtIsNullAndDeletedFalse(
                        user.getCompany().getId(), user.getId()));
        employeeRepository.findByUser_UsernameAndDeletedFalse(user.getUsername())
                .ifPresent(employee -> dto.setEmployeeId(employee.getId()));
        if (user.getCompany() != null) {
            dto.setCompanyName(user.getCompany().getName());
        }
        mfaService.applyTo(dto, user);
        if (legalAcceptanceService != null) {
            legalAcceptanceService.applyTo(dto, user);
        }
        return dto;
    }

    /** Optional field injection keeps the constructor (used directly by unit tests) unchanged. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.haodaone.privacy.service.LegalAcceptanceService legalAcceptanceService;

    @Transactional(noRollbackFor = AuthenticationFailedException.class)
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

        if (!isAuthenticationEnabled(user)) {
            recordLoginAttempt(request.getUsername(), false, "Account inactive", httpRequest);
            throw new AuthenticationFailedException("This account is not active. Complete account setup or contact your administrator.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            handleFailedAttempt(user);
            recordLoginAttempt(request.getUsername(), false, "Invalid password", httpRequest);
            throw new AuthenticationFailedException("Invalid username or password");
        }

        // Company enforces SSO-only sign-in (break-glass admin excepted). Checked after the
        // password so the message never reveals the policy to someone without the password.
        if (ssoPolicyService != null && ssoPolicyService.isPasswordLoginBlocked(user)) {
            recordLoginAttempt(request.getUsername(), false, "Password login disabled (SSO only)", httpRequest);
            throw new com.haodaone.common.exception.SsoRequiredException(
                    "Your organisation requires signing in with Google or Microsoft. Use the single sign-on buttons.");
        }

        // Password is right. If a second factor is due, hand back a short-lived
        // challenge instead of tokens (counters reset only once MFA succeeds).
        MfaService.LoginChallenge challenge = mfaService.startLoginChallengeIfRequired(
                user, request.getDeviceToken(), httpRequest == null ? null : httpRequest.getRemoteAddr());
        if (challenge != null) {
            return LoginResponse.mfaChallenge(challenge.mfaToken(), challenge.methods(),
                    MfaService.CHALLENGE_TTL_SECONDS, challenge.maskedEmail());
        }

        return completeLogin(user, request.getUsername(), "Successful login", httpRequest);
    }

    /** Optional setter injection keeps the constructor (used directly by unit tests) unchanged. */
    private com.haodaone.sso.service.SsoPolicyService ssoPolicyService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setSsoPolicyService(com.haodaone.sso.service.SsoPolicyService ssoPolicyService) {
        this.ssoPolicyService = ssoPolicyService;
    }

    /**
     * Finishes an SSO sign-in for a user whose identity provider ID token was
     * already validated (see sso.service.SsoService). The same lockout/active
     * checks as password login apply; Vettri two-factor still applies unless
     * skipVettriMfa (company setting "trust SSO MFA").
     */
    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    public LoginResponse completeSsoLogin(User user, String providerName, boolean skipVettriMfa, HttpServletRequest httpRequest) {
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            recordLoginAttempt(user.getUsername(), false, "Account locked (SSO " + providerName + ")", httpRequest);
            throw new AuthenticationFailedException(
                    "Account locked due to too many failed attempts. Try again after " + user.getLockedUntil());
        }
        if (!isAuthenticationEnabled(user)) {
            recordLoginAttempt(user.getUsername(), false, "Account inactive (SSO " + providerName + ")", httpRequest);
            throw new AuthenticationFailedException("This account is not active. Complete account setup or contact your administrator.");
        }
        if (!skipVettriMfa) {
            MfaService.LoginChallenge challenge = mfaService.startLoginChallengeIfRequired(
                    user, null, httpRequest == null ? null : httpRequest.getRemoteAddr());
            if (challenge != null) {
                return LoginResponse.mfaChallenge(challenge.mfaToken(), challenge.methods(),
                        MfaService.CHALLENGE_TTL_SECONDS, challenge.maskedEmail());
            }
        }
        return completeLogin(user, user.getUsername(), "Successful login (SSO: " + providerName + ")", httpRequest);
    }

    /** login_history entry for an SSO attempt that failed before a user could be resolved. */
    @Transactional
    public void recordSsoFailure(String identifier, String reason, HttpServletRequest httpRequest) {
        recordLoginAttempt(identifier == null ? "(sso)" : identifier, false, reason, httpRequest);
    }

    /** Success - reset lockout counters, record login, issue tokens. Shared by password-only and MFA logins. */
    private LoginResponse completeLogin(User user, String loginIdentifier, String auditDetail, HttpServletRequest httpRequest) {
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        recordLoginAttempt(loginIdentifier, true, null, httpRequest);
        auditLogService.log("User", user.getId(), "LOGIN", auditDetail);

        List<String> roleNames = user.getRoles().stream().map(r -> r.getName()).toList();
        String accessToken = jwtService.generateAccessToken(user.getUsername(), roleNames);
        String refreshToken = issueRefreshToken(user, httpRequest);

        return new LoginResponse(accessToken, refreshToken, toUserDTO(user));
    }

    /**
     * Second step of an MFA login. Wrong codes count toward the same lockout as
     * wrong passwords and are recorded in login_history; the challenge itself
     * dies after MfaService.CHALLENGE_MAX_FAILURES wrong codes or 5 minutes.
     */
    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    public LoginResponse verifyMfa(String mfaToken, String method, String code, boolean rememberDevice,
                                   HttpServletRequest httpRequest) {
        com.haodaone.auth.entity.MfaMethod mfaMethod = MfaService.parseMethod(method);
        com.haodaone.auth.entity.MfaChallenge challenge = mfaService.findActiveChallenge(mfaToken)
                .orElseThrow(() -> new AuthenticationFailedException("Your sign-in session expired. Please sign in again."));
        User user = challenge.getUser();

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            recordLoginAttempt(user.getUsername(), false, "Account locked", httpRequest);
            throw new AuthenticationFailedException(
                    "Account locked due to too many failed attempts. Try again after " + user.getLockedUntil());
        }
        if (!isAuthenticationEnabled(user)) {
            recordLoginAttempt(user.getUsername(), false, "Account inactive", httpRequest);
            throw new AuthenticationFailedException("This account is not active. Complete account setup or contact your administrator.");
        }

        if (!mfaService.verifyChallenge(challenge, mfaMethod, code)) {
            mfaService.recordChallengeFailure(challenge);
            handleFailedAttempt(user);
            recordLoginAttempt(user.getUsername(), false, "Invalid " + mfaMethod.name() + " code", httpRequest);
            throw new AuthenticationFailedException("Invalid or expired verification code");
        }

        mfaService.consumeChallenge(challenge);
        LoginResponse response = completeLogin(user, user.getUsername(),
                "Successful login (two-factor: " + mfaMethod.name() + ")", httpRequest);
        if (rememberDevice) {
            response.setDeviceToken(mfaService.issueTrustedDevice(user,
                    httpRequest == null ? null : httpRequest.getRemoteAddr(),
                    httpRequest == null ? null : httpRequest.getHeader("User-Agent")));
        }
        return response;
    }

    /**
     * After a successful password change every refresh token is revoked; the
     * caller already proved both factors for the current session, so hand it a
     * fresh token pair instead of forcing a full (two-factor) sign-in again.
     */
    @Transactional
    public LoginResponse issueSessionAfterPasswordChange(Long userId, HttpServletRequest httpRequest) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthenticationFailedException("User not found"));
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
        if (!isAuthenticationEnabled(user)) {
            throw new AuthenticationFailedException("This account is not active. Complete account setup or contact your administrator.");
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

    @Transactional
    public void resetPassword(Long userId, String newPassword) {
        User user = userRepository.findById(userId)
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> new AuthenticationFailedException("User not found"));
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);
        refreshTokenRepository.revokeAllForUser(user.getId());
        auditLogService.log("User", user.getId(), "PASSWORD_RESET", "Password reset using recovery link");
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

    /** Keep token issuance aligned with CustomUserPrincipal.isEnabled(). */
    private boolean isAuthenticationEnabled(User user) {
        return user.isActive()
                && "ACTIVE".equalsIgnoreCase(user.getAccountStatus())
                && (user.getLockedUntil() == null || user.getLockedUntil().isBefore(LocalDateTime.now()));
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
