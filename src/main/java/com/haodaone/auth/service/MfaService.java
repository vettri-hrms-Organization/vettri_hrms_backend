package com.haodaone.auth.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.auth.dto.MfaDTO;
import com.haodaone.auth.entity.CompanySecuritySetting;
import com.haodaone.auth.entity.MfaChallenge;
import com.haodaone.auth.entity.MfaEmailOtp;
import com.haodaone.auth.entity.MfaMethod;
import com.haodaone.auth.entity.MfaRecoveryCode;
import com.haodaone.auth.entity.MfaTrustedDevice;
import com.haodaone.auth.entity.TwoFactorPolicy;
import com.haodaone.auth.entity.UserMfa;
import com.haodaone.auth.repository.CompanySecuritySettingRepository;
import com.haodaone.auth.repository.MfaChallengeRepository;
import com.haodaone.auth.repository.MfaEmailOtpRepository;
import com.haodaone.auth.repository.MfaRecoveryCodeRepository;
import com.haodaone.auth.repository.MfaTrustedDeviceRepository;
import com.haodaone.auth.repository.UserMfaRepository;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.recruitment.service.EmailService;
import com.haodaone.user.dto.UserDTO;
import com.haodaone.user.entity.User;
import com.haodaone.user.entity.UserPermissionGrant;
import com.haodaone.user.repository.UserPermissionGrantRepository;
import com.haodaone.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Two-factor authentication: TOTP authenticator apps, email OTP, recovery
 * codes, trusted ("remember this device") devices and the company policy.
 *
 * The login-path methods (startLoginChallengeIfRequired, verifyChallenge,
 * recordChallengeFailure, consumeChallenge, issueTrustedDevice) deliberately
 * have no @Transactional of their own and never throw on a wrong code: they
 * run inside AuthService's transaction, which uses noRollbackFor on
 * AuthenticationFailedException so failed-attempt counters persist. A
 * participating @Transactional proxy here would mark that transaction
 * rollback-only on any exception.
 */
@Service
public class MfaService {

    public static final String ISSUER = "Vettri HRMS";
    public static final int CHALLENGE_TTL_SECONDS = 300;
    public static final int CHALLENGE_MAX_FAILURES = 5;
    public static final int EMAIL_OTP_TTL_SECONDS = 300;
    public static final int EMAIL_OTP_MAX_ATTEMPTS = 5;
    public static final int EMAIL_OTP_RESEND_SECONDS = 60;
    public static final int RECOVERY_CODE_COUNT = 10;
    public static final int TRUSTED_DEVICE_DAYS = 30;

    private static final Set<String> ADMIN_ROLE_NAMES = Set.of("SUPER_ADMIN", "COMPANY_ADMIN", "HR_ADMIN");
    private static final char[] RECOVERY_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789".toCharArray();

    /** What AuthService needs to answer a login with {mfaRequired:true,...}. */
    public record LoginChallenge(String mfaToken, List<String> methods, String maskedEmail) { }

    private final UserMfaRepository userMfaRepository;
    private final MfaRecoveryCodeRepository recoveryCodeRepository;
    private final MfaChallengeRepository challengeRepository;
    private final MfaEmailOtpRepository emailOtpRepository;
    private final MfaTrustedDeviceRepository trustedDeviceRepository;
    private final CompanySecuritySettingRepository settingRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final UserPermissionGrantRepository permissionGrantRepository;
    private final PasswordEncoder passwordEncoder;
    private final MfaSecretCipher cipher;
    private final EmailService emailService;
    private final AuditLogService auditLogService;
    private final SecureRandom random = new SecureRandom();
    private Clock clock = Clock.systemDefaultZone();

    public MfaService(UserMfaRepository userMfaRepository,
                      MfaRecoveryCodeRepository recoveryCodeRepository,
                      MfaChallengeRepository challengeRepository,
                      MfaEmailOtpRepository emailOtpRepository,
                      MfaTrustedDeviceRepository trustedDeviceRepository,
                      CompanySecuritySettingRepository settingRepository,
                      CompanyRepository companyRepository,
                      UserRepository userRepository,
                      UserPermissionGrantRepository permissionGrantRepository,
                      PasswordEncoder passwordEncoder,
                      MfaSecretCipher cipher,
                      EmailService emailService,
                      AuditLogService auditLogService) {
        this.userMfaRepository = userMfaRepository;
        this.recoveryCodeRepository = recoveryCodeRepository;
        this.challengeRepository = challengeRepository;
        this.emailOtpRepository = emailOtpRepository;
        this.trustedDeviceRepository = trustedDeviceRepository;
        this.settingRepository = settingRepository;
        this.companyRepository = companyRepository;
        this.userRepository = userRepository;
        this.permissionGrantRepository = permissionGrantRepository;
        this.passwordEncoder = passwordEncoder;
        this.cipher = cipher;
        this.emailService = emailService;
        this.auditLogService = auditLogService;
    }

    /** Tests only (public so it also works through the Spring proxy). */
    public void setClock(Clock clock) {
        this.clock = clock;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    // ------------------------------------------------------------------ policy

    public TwoFactorPolicy policyFor(Long companyId) {
        if (companyId == null) {
            return TwoFactorPolicy.OPTIONAL;
        }
        return settingRepository.findByCompany_IdAndDeletedFalse(companyId)
                .map(CompanySecuritySetting::getTwoFactorPolicy)
                .orElse(TwoFactorPolicy.OPTIONAL);
    }

    private TwoFactorPolicy policyFor(User user) {
        return policyFor(user.getCompany() == null ? null : user.getCompany().getId());
    }

    /** Admin = an admin role, or any role/direct grant carrying a *_MANAGE permission. */
    boolean isAdminUser(User user) {
        boolean fromRoles = user.getRoles().stream()
                .filter(role -> !role.isDeleted())
                .anyMatch(role -> ADMIN_ROLE_NAMES.contains(role.getName())
                        || role.getPermissions().stream()
                        .anyMatch(permission -> !permission.isDeleted() && permission.getCode() != null
                                && permission.getCode().endsWith("_MANAGE")));
        if (fromRoles || user.getCompany() == null) {
            return fromRoles;
        }
        List<UserPermissionGrant> grants = permissionGrantRepository
                .findAllByCompany_IdAndUser_IdAndRevokedAtIsNullAndDeletedFalse(user.getCompany().getId(), user.getId());
        return grants.stream().anyMatch(grant -> grant.isActive() && grant.getPermission() != null
                && grant.getPermission().getCode() != null && grant.getPermission().getCode().endsWith("_MANAGE"));
    }

    boolean isRequiredFor(User user, TwoFactorPolicy policy) {
        return switch (policy) {
            case REQUIRED_ALL -> true;
            case REQUIRED_ADMINS -> isAdminUser(user);
            default -> false;
        };
    }

    /** For /me, outside any transaction: reloads the user so lazy role permissions can be read. */
    @Transactional(readOnly = true)
    public void applyTo(UserDTO dto, Long userId) {
        if (dto == null || userId == null) {
            return;
        }
        userRepository.findById(userId).ifPresent(user -> applyTo(dto, user));
    }

    /** Sets mfaEnabled / mfaEnrollmentRequired on a UserDTO; call inside an open transaction. */
    public void applyTo(UserDTO dto, User user) {
        if (dto == null || user == null || user.getId() == null) {
            return;
        }
        TwoFactorPolicy policy = policyFor(user);
        boolean enabled = policy != TwoFactorPolicy.OFF
                && userMfaRepository.findByUser_IdAndDeletedFalse(user.getId()).map(UserMfa::isEnabled).orElse(false);
        dto.setMfaEnabled(enabled);
        dto.setMfaEnrollmentRequired(!enabled && isRequiredFor(user, policy));
    }

    @Transactional(readOnly = true)
    public MfaDTO.StatusResponse status(Long userId) {
        User user = loadUser(userId);
        return buildStatus(user);
    }

    private MfaDTO.StatusResponse buildStatus(User user) {
        TwoFactorPolicy policy = policyFor(user);
        Optional<UserMfa> mfa = userMfaRepository.findByUser_IdAndDeletedFalse(user.getId());
        boolean totp = mfa.map(UserMfa::isTotpEnabled).orElse(false);
        boolean email = mfa.map(UserMfa::isEmailOtpEnabled).orElse(false);
        boolean required = isRequiredFor(user, policy);
        return new MfaDTO.StatusResponse(totp || email, totp, email,
                recoveryCodeRepository.countByUser_IdAndUsedAtIsNull(user.getId()),
                policy.name(), policy != TwoFactorPolicy.OFF, required, required && !(totp || email),
                trustedDeviceRepository.countByUser_IdAndRevokedAtIsNullAndExpiresAtAfter(user.getId(), now()),
                maskEmail(user.getEmail()));
    }

    // ------------------------------------------------------------------ login path

    /**
     * Called by AuthService after the password checked out. Returns null when
     * tokens can be issued straight away, otherwise a fresh challenge.
     * Users the policy forces into 2FA who have not enrolled yet are challenged
     * by email OTP (their only available factor) and then sent to enrol.
     */
    public LoginChallenge startLoginChallengeIfRequired(User user, String deviceToken, String ipAddress) {
        TwoFactorPolicy policy = policyFor(user);
        if (policy == TwoFactorPolicy.OFF) {
            return null;
        }
        Optional<UserMfa> mfa = userMfaRepository.findByUser_IdAndDeletedFalse(user.getId());
        boolean enabled = mfa.map(UserMfa::isEnabled).orElse(false);
        if (!enabled && !isRequiredFor(user, policy)) {
            return null;
        }
        if (isTrustedDevice(user, deviceToken)) {
            return null;
        }

        List<String> methods = new ArrayList<>();
        if (mfa.map(UserMfa::isTotpEnabled).orElse(false)) {
            methods.add(MfaMethod.TOTP.name());
        }
        if (!enabled || mfa.map(UserMfa::isEmailOtpEnabled).orElse(false)) {
            methods.add(MfaMethod.EMAIL.name());
        }
        if (recoveryCodeRepository.countByUser_IdAndUsedAtIsNull(user.getId()) > 0) {
            methods.add(MfaMethod.RECOVERY.name());
        }

        String rawToken = randomToken(32);
        MfaChallenge challenge = new MfaChallenge();
        challenge.setUser(user);
        challenge.setTokenHash(sha256Hex(rawToken));
        challenge.setMethods(String.join(",", methods));
        challenge.setExpiresAt(now().plusSeconds(CHALLENGE_TTL_SECONDS));
        challenge.setIpAddress(ipAddress);
        challengeRepository.save(challenge);
        return new LoginChallenge(rawToken, methods, maskEmail(user.getEmail()));
    }

    public Optional<MfaChallenge> findActiveChallenge(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        LocalDateTime now = now();
        return challengeRepository.findByTokenHash(sha256Hex(rawToken.trim()))
                .filter(challenge -> challenge.getConsumedAt() == null)
                .filter(challenge -> challenge.getExpiresAt().isAfter(now))
                .filter(challenge -> challenge.getFailedAttempts() < CHALLENGE_MAX_FAILURES);
    }

    public boolean challengeAllows(MfaChallenge challenge, MfaMethod method) {
        return Arrays.asList(challenge.getMethods().split(",")).contains(method.name());
    }

    /** True if the code is right for the method. Never throws for a wrong code. */
    public boolean verifyChallenge(MfaChallenge challenge, MfaMethod method, String code) {
        if (!challengeAllows(challenge, method)) {
            return false;
        }
        User user = challenge.getUser();
        return switch (method) {
            case TOTP -> verifyTotp(user.getId(), code);
            case EMAIL -> emailOtpRepository.findTopByChallenge_IdOrderBySentAtDescIdDesc(challenge.getId())
                    .map(otp -> checkEmailOtp(otp, code))
                    .orElse(false);
            case RECOVERY -> useRecoveryCode(user.getId(), code);
        };
    }

    public void recordChallengeFailure(MfaChallenge challenge) {
        challenge.setFailedAttempts(challenge.getFailedAttempts() + 1);
        challengeRepository.save(challenge);
    }

    public void consumeChallenge(MfaChallenge challenge) {
        challenge.setConsumedAt(now());
        challengeRepository.save(challenge);
    }

    public String issueTrustedDevice(User user, String ipAddress, String userAgent) {
        String rawToken = randomToken(32);
        MfaTrustedDevice device = new MfaTrustedDevice();
        device.setUser(user);
        device.setTokenHash(sha256Hex(rawToken));
        device.setExpiresAt(now().plusDays(TRUSTED_DEVICE_DAYS));
        device.setIpAddress(ipAddress);
        device.setUserAgent(userAgent == null ? null : userAgent.substring(0, Math.min(500, userAgent.length())));
        trustedDeviceRepository.save(device);
        return rawToken;
    }

    private boolean isTrustedDevice(User user, String deviceToken) {
        if (deviceToken == null || deviceToken.isBlank()) {
            return false;
        }
        LocalDateTime now = now();
        return trustedDeviceRepository.findByTokenHash(sha256Hex(deviceToken.trim()))
                .filter(device -> device.getUser().getId().equals(user.getId()))
                .filter(device -> device.getRevokedAt() == null && device.getExpiresAt().isAfter(now))
                .isPresent();
    }

    /** Public, unauthenticated: send (or resend) the email code for a pending login challenge. */
    @Transactional
    public MfaDTO.EmailSentResponse sendLoginEmailCode(String mfaToken) {
        MfaChallenge challenge = findActiveChallenge(mfaToken)
                .orElseThrow(() -> new BadRequestException("Your sign-in session expired. Please sign in again."));
        if (!challengeAllows(challenge, MfaMethod.EMAIL)) {
            throw new BadRequestException("Email codes are not enabled for this account.");
        }
        Optional<MfaEmailOtp> previous = emailOtpRepository.findTopByChallenge_IdOrderBySentAtDescIdDesc(challenge.getId());
        return issueEmailOtp(challenge.getUser(), MfaEmailOtp.PURPOSE_LOGIN, challenge, previous);
    }

    // ------------------------------------------------------------------ self-service

    @Transactional
    public MfaDTO.TotpEnrollResponse enrollTotp(Long userId) {
        User user = loadUser(userId);
        requireAvailable(user);
        UserMfa mfa = getOrCreate(user);
        byte[] secret = Totp.generateSecret();
        mfa.setTotpPendingSecretEnc(cipher.encrypt(secret));
        userMfaRepository.save(mfa);
        String base32 = Totp.base32Encode(secret);
        String account = user.getEmail() != null && !user.getEmail().isBlank() ? user.getEmail() : user.getUsername();
        return new MfaDTO.TotpEnrollResponse(base32, Totp.otpauthUri(ISSUER, account, base32), ISSUER, account);
    }

    @Transactional
    public MfaDTO.EnableResponse confirmTotp(Long userId, String code) {
        User user = loadUser(userId);
        requireAvailable(user);
        UserMfa mfa = userMfaRepository.findByUser_IdAndDeletedFalse(userId)
                .filter(candidate -> candidate.getTotpPendingSecretEnc() != null)
                .orElseThrow(() -> new BadRequestException("Start authenticator setup first."));
        byte[] secret = cipher.decrypt(mfa.getTotpPendingSecretEnc());
        long step = Totp.verify(secret, code, clock.instant().getEpochSecond(), null);
        if (step < 0) {
            throw new BadRequestException("That code didn't match. Check the time on your phone and try the newest code.");
        }
        boolean firstFactor = !mfa.isEnabled();
        mfa.setTotpSecretEnc(mfa.getTotpPendingSecretEnc());
        mfa.setTotpPendingSecretEnc(null);
        mfa.setTotpEnabled(true);
        mfa.setTotpLastUsedStep(step);
        if (firstFactor) {
            mfa.setEnabledAt(now());
        }
        userMfaRepository.save(mfa);
        List<String> codes = recoveryCodeRepository.countByUser_IdAndUsedAtIsNull(userId) == 0
                ? replaceRecoveryCodes(user) : List.of();
        auditLogService.log("User", userId, "MFA_TOTP_ENABLED", "Authenticator app two-factor enabled");
        return new MfaDTO.EnableResponse(buildStatus(user), codes);
    }

    /** Sends a SETUP code to the user's own email (enable email OTP / disable 2FA). */
    @Transactional
    public MfaDTO.EmailSentResponse sendSetupEmailCode(Long userId) {
        User user = loadUser(userId);
        Optional<MfaEmailOtp> previous = emailOtpRepository
                .findTopByUser_IdAndPurposeOrderBySentAtDescIdDesc(userId, MfaEmailOtp.PURPOSE_SETUP);
        return issueEmailOtp(user, MfaEmailOtp.PURPOSE_SETUP, null, previous);
    }

    @Transactional(noRollbackFor = BadRequestException.class)
    public MfaDTO.EnableResponse enableEmailOtp(Long userId, String code) {
        User user = loadUser(userId);
        requireAvailable(user);
        if (!verifySetupEmailCode(userId, code)) {
            throw new BadRequestException("Invalid or expired code.");
        }
        UserMfa mfa = getOrCreate(user);
        boolean firstFactor = !mfa.isEnabled();
        mfa.setEmailOtpEnabled(true);
        if (firstFactor) {
            mfa.setEnabledAt(now());
        }
        userMfaRepository.save(mfa);
        List<String> codes = recoveryCodeRepository.countByUser_IdAndUsedAtIsNull(userId) == 0
                ? replaceRecoveryCodes(user) : List.of();
        auditLogService.log("User", userId, "MFA_EMAIL_ENABLED", "Email code two-factor enabled");
        return new MfaDTO.EnableResponse(buildStatus(user), codes);
    }

    @Transactional(noRollbackFor = BadRequestException.class)
    public MfaDTO.StatusResponse disable(Long userId, String currentPassword, String method, String code) {
        User user = loadUser(userId);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }
        if (isRequiredFor(user, policyFor(user))) {
            throw new BadRequestException("Your organisation requires two-factor authentication, so it can't be turned off.");
        }
        UserMfa mfa = userMfaRepository.findByUser_IdAndDeletedFalse(userId)
                .filter(UserMfa::isEnabled)
                .orElseThrow(() -> new BadRequestException("Two-factor authentication is not enabled."));
        MfaMethod parsed = parseMethod(method);
        boolean ok = switch (parsed) {
            case TOTP -> mfa.isTotpEnabled() && verifyTotp(userId, code);
            // Email ownership alone must not strip an authenticator app: EMAIL only counts if it is an enrolled factor.
            case EMAIL -> mfa.isEmailOtpEnabled() && verifySetupEmailCode(userId, code);
            case RECOVERY -> useRecoveryCode(userId, code);
        };
        if (!ok) {
            throw new BadRequestException("Invalid or expired code.");
        }
        wipe(user);
        auditLogService.log("User", userId, "MFA_DISABLED", "Two-factor authentication disabled by user");
        return buildStatus(user);
    }

    @Transactional
    public MfaDTO.RecoveryCodesResponse regenerateRecoveryCodes(Long userId, String currentPassword) {
        User user = loadUser(userId);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }
        userMfaRepository.findByUser_IdAndDeletedFalse(userId)
                .filter(UserMfa::isEnabled)
                .orElseThrow(() -> new BadRequestException("Turn on two-factor authentication first."));
        List<String> codes = replaceRecoveryCodes(user);
        auditLogService.log("User", userId, "MFA_RECOVERY_REGENERATED", "Recovery codes regenerated");
        return new MfaDTO.RecoveryCodesResponse(codes);
    }

    @Transactional
    public MfaDTO.StatusResponse revokeTrustedDevices(Long userId) {
        User user = loadUser(userId);
        trustedDeviceRepository.revokeAllForUser(userId, now());
        auditLogService.log("User", userId, "MFA_TRUSTED_DEVICES_REVOKED", "Remembered devices signed out of 2FA bypass");
        return buildStatus(user);
    }

    // ------------------------------------------------------------------ admin

    @Transactional
    public MfaDTO.PolicyResponse updatePolicy(Long companyId, TwoFactorPolicy policy) {
        if (companyId == null) {
            throw new BadRequestException("Select a company first.");
        }
        CompanySecuritySetting setting = settingRepository.findByCompany_IdAndDeletedFalse(companyId)
                .orElseGet(() -> {
                    CompanySecuritySetting created = new CompanySecuritySetting();
                    created.setCompany(companyRepository.findById(companyId)
                            .orElseThrow(() -> new ResourceNotFoundException("Company not found")));
                    return created;
                });
        TwoFactorPolicy previous = setting.getTwoFactorPolicy();
        setting.setTwoFactorPolicy(policy);
        settingRepository.save(setting);
        auditLogService.log("Company", companyId, "MFA_POLICY_CHANGED",
                "Two-factor policy changed from " + previous + " to " + policy);
        return new MfaDTO.PolicyResponse(policy.name());
    }

    @Transactional
    public void adminReset(Long companyId, Long targetUserId) {
        if (companyId == null) {
            throw new BadRequestException("Select a company first.");
        }
        User user = userRepository.findByIdAndCompanyIdAndDeletedFalse(targetUserId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        wipe(user);
        auditLogService.log("User", targetUserId, "MFA_RESET_BY_ADMIN",
                "Two-factor authentication reset by an administrator for " + user.getUsername());
    }

    // ------------------------------------------------------------------ internals

    private void wipe(User user) {
        userMfaRepository.findByUser_IdAndDeletedFalse(user.getId()).ifPresent(mfa -> {
            mfa.setTotpEnabled(false);
            mfa.setTotpSecretEnc(null);
            mfa.setTotpPendingSecretEnc(null);
            mfa.setTotpLastUsedStep(null);
            mfa.setEmailOtpEnabled(false);
            mfa.setEnabledAt(null);
            userMfaRepository.save(mfa);
        });
        recoveryCodeRepository.deleteAllForUser(user.getId());
        trustedDeviceRepository.revokeAllForUser(user.getId(), now());
    }

    /** TOTP check with replay protection: the matched step must be newer than the last accepted one. */
    private boolean verifyTotp(Long userId, String code) {
        Optional<UserMfa> found = userMfaRepository.findByUser_IdAndDeletedFalse(userId)
                .filter(UserMfa::isTotpEnabled)
                .filter(candidate -> candidate.getTotpSecretEnc() != null);
        if (found.isEmpty()) {
            return false;
        }
        UserMfa mfa = found.get();
        long step = Totp.verify(cipher.decrypt(mfa.getTotpSecretEnc()), code,
                clock.instant().getEpochSecond(), mfa.getTotpLastUsedStep());
        if (step < 0) {
            return false;
        }
        mfa.setTotpLastUsedStep(step);
        userMfaRepository.save(mfa);
        return true;
    }

    private boolean useRecoveryCode(Long userId, String code) {
        String normalized = normalizeRecoveryCode(code);
        if (normalized.isEmpty()) {
            return false;
        }
        return recoveryCodeRepository.findFirstByUser_IdAndCodeHashAndUsedAtIsNull(userId, sha256Hex(normalized))
                .map(recovery -> {
                    recovery.setUsedAt(now());
                    recoveryCodeRepository.save(recovery);
                    auditLogService.log("User", userId, "MFA_RECOVERY_CODE_USED", "A recovery code was used");
                    return true;
                })
                .orElse(false);
    }

    private boolean verifySetupEmailCode(Long userId, String code) {
        return emailOtpRepository.findTopByUser_IdAndPurposeOrderBySentAtDescIdDesc(userId, MfaEmailOtp.PURPOSE_SETUP)
                .map(otp -> checkEmailOtp(otp, code))
                .orElse(false);
    }

    /** Single use, 5 minute expiry, 5 attempts; every wrong guess is counted. */
    boolean checkEmailOtp(MfaEmailOtp otp, String code) {
        if (otp.getConsumedAt() != null || !otp.getExpiresAt().isAfter(now())
                || otp.getAttempts() >= EMAIL_OTP_MAX_ATTEMPTS) {
            return false;
        }
        String normalized = Totp.normalizeCode(code);
        boolean matches = normalized != null && MessageDigest.isEqual(
                sha256Hex(otp.getSalt() + ":" + normalized).getBytes(StandardCharsets.US_ASCII),
                otp.getCodeHash().getBytes(StandardCharsets.US_ASCII));
        if (matches) {
            otp.setConsumedAt(now());
        } else {
            otp.setAttempts(otp.getAttempts() + 1);
        }
        emailOtpRepository.save(otp);
        return matches;
    }

    private MfaDTO.EmailSentResponse issueEmailOtp(User user, String purpose, MfaChallenge challenge,
                                                   Optional<MfaEmailOtp> previous) {
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new BadRequestException("Your account has no email address for verification codes.");
        }
        LocalDateTime now = now();
        if (previous.isPresent() && previous.get().getSentAt().plusSeconds(EMAIL_OTP_RESEND_SECONDS).isAfter(now)) {
            throw new BadRequestException("Please wait a minute before requesting another code.");
        }
        previous.filter(old -> old.getConsumedAt() == null).ifPresent(old -> {
            old.setConsumedAt(now);
            emailOtpRepository.save(old);
        });
        String code = String.format("%06d", random.nextInt(1_000_000));
        String salt = randomToken(16);
        MfaEmailOtp otp = new MfaEmailOtp();
        otp.setUser(user);
        otp.setPurpose(purpose);
        otp.setChallenge(challenge);
        otp.setSalt(salt);
        otp.setCodeHash(sha256Hex(salt + ":" + code));
        otp.setExpiresAt(now.plusSeconds(EMAIL_OTP_TTL_SECONDS));
        otp.setSentAt(now);
        emailOtpRepository.save(otp);
        boolean sent = emailService.sendMfaCodeEmail(user.getEmail(), user.getFullName(), code, EMAIL_OTP_TTL_SECONDS / 60);
        if (!sent) {
            throw new BadRequestException("We couldn't send the email right now. Use another method or try again later.");
        }
        return new MfaDTO.EmailSentResponse(maskEmail(user.getEmail()), EMAIL_OTP_TTL_SECONDS, EMAIL_OTP_RESEND_SECONDS);
    }

    private List<String> replaceRecoveryCodes(User user) {
        recoveryCodeRepository.deleteAllForUser(user.getId());
        List<String> codes = new ArrayList<>(RECOVERY_CODE_COUNT);
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            StringBuilder raw = new StringBuilder(10);
            for (int c = 0; c < 10; c++) {
                raw.append(RECOVERY_ALPHABET[random.nextInt(RECOVERY_ALPHABET.length)]);
            }
            String display = raw.substring(0, 5) + "-" + raw.substring(5);
            MfaRecoveryCode entity = new MfaRecoveryCode();
            entity.setUser(user);
            entity.setCodeHash(sha256Hex(raw.toString()));
            recoveryCodeRepository.save(entity);
            codes.add(display);
        }
        return codes;
    }

    static String normalizeRecoveryCode(String code) {
        return code == null ? "" : code.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private void requireAvailable(User user) {
        if (policyFor(user) == TwoFactorPolicy.OFF) {
            throw new BadRequestException("Two-factor authentication is turned off for your organisation.");
        }
    }

    private UserMfa getOrCreate(User user) {
        return userMfaRepository.findByUser_IdAndDeletedFalse(user.getId()).orElseGet(() -> {
            UserMfa created = new UserMfa();
            created.setUser(user);
            created.setCompany(user.getCompany());
            return created;
        });
    }

    private User loadUser(Long userId) {
        return userRepository.findById(userId)
                .filter(user -> !user.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    public static MfaMethod parseMethod(String method) {
        try {
            return MfaMethod.valueOf(method == null ? "" : method.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Unknown verification method: " + method);
        }
    }

    static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return null;
        }
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        String visible = local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 2);
        return visible + "***" + email.substring(at);
    }

    private String randomToken(int bytes) {
        byte[] buffer = new byte[bytes];
        random.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }

    static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
