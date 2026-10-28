package com.haodaone.sso.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.auth.dto.LoginResponse;
import com.haodaone.auth.service.AuthService;
import com.haodaone.common.exception.AuthenticationFailedException;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.sso.dto.SsoDTO;
import com.haodaone.sso.entity.CompanySsoSetting;
import com.haodaone.sso.entity.SsoLoginAttempt;
import com.haodaone.sso.entity.UserSsoIdentity;
import com.haodaone.sso.repository.SsoLoginAttemptRepository;
import com.haodaone.sso.repository.UserSsoIdentityRepository;
import com.haodaone.user.entity.Role;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.RoleRepository;
import com.haodaone.user.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Google / Microsoft (Entra ID) sign-in using the OAuth2 authorization-code
 * flow with PKCE, handled entirely by the backend:
 * <ol>
 *   <li>GET /api/auth/sso/{provider}/start - stores hashed state + nonce + PKCE
 *       verifier, sets an HttpOnly browser-binding cookie, redirects to the IdP.</li>
 *   <li>GET /api/auth/sso/{provider}/callback - checks state + cookie, exchanges
 *       the code server-side, validates the ID token (signature via JWKS, iss,
 *       aud, exp, nonce), resolves the user and redirects to the SPA with a
 *       one-time code (never tokens) in the URL.</li>
 *   <li>POST /api/auth/sso/exchange {code} - single use, 2 minutes; returns the
 *       normal login response (or the Vettri MFA challenge).</li>
 * </ol>
 * Users are matched by linked identity, else by verified email to an existing
 * active user; auto-provisioning only when a company enabled it for the domain.
 */
@Service
public class SsoService {

    private static final Logger log = LoggerFactory.getLogger(SsoService.class);
    public static final String BINDING_COOKIE = "vettri_sso_binding";
    static final int ATTEMPT_TTL_SECONDS = 600;
    static final int EXCHANGE_TTL_SECONDS = 120;
    private static final long JWKS_TTL_SECONDS = 3600;
    private static final long JWKS_MIN_REFRESH_SECONDS = 60;

    private final SsoProperties properties;
    private final SsoPolicyService policyService;
    private final SsoLoginAttemptRepository attempts;
    private final UserSsoIdentityRepository identities;
    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final AuditLogService auditLogService;
    private final OidcHttpClient httpClient;
    private final SecureRandom random = new SecureRandom();
    private final Map<SsoProvider, CachedJwks> jwksCache = new EnumMap<>(SsoProvider.class);
    private Clock clock = Clock.systemUTC();

    private record CachedJwks(Map<String, Object> jwks, Instant fetchedAt) {
    }

    /** Result of the callback: where the browser goes next. */
    public record Redirect(String url) {
    }

    public SsoService(SsoProperties properties, SsoPolicyService policyService, SsoLoginAttemptRepository attempts,
                      UserSsoIdentityRepository identities, UserRepository users, RoleRepository roles,
                      PasswordEncoder passwordEncoder, AuthService authService, AuditLogService auditLogService,
                      OidcHttpClient httpClient) {
        this.properties = properties;
        this.policyService = policyService;
        this.attempts = attempts;
        this.identities = identities;
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.authService = authService;
        this.auditLogService = auditLogService;
        this.httpClient = httpClient;
    }

    /** Tests only. */
    public void setClock(Clock clock) {
        this.clock = clock;
    }

    public SsoDTO.ProvidersResponse providers() {
        return new SsoDTO.ProvidersResponse(policyService.configuredProviders());
    }

    // ------------------------------------------------------------------ 1. start

    @Transactional
    public String start(String providerPath, HttpServletRequest request, HttpServletResponse response) {
        SsoProvider provider = SsoProvider.fromPath(providerPath)
                .filter(properties::isConfigured)
                .orElseThrow(() -> new BadRequestException("This sign-in provider is not available"));
        String state = randomToken(32);
        String nonce = randomToken(24);
        String verifier = randomToken(48);
        String binding = randomToken(32);

        SsoLoginAttempt attempt = new SsoLoginAttempt();
        attempt.setProvider(provider.name());
        attempt.setStateHash(sha256Hex(state));
        attempt.setBrowserBindingHash(sha256Hex(binding));
        attempt.setNonce(nonce);
        attempt.setCodeVerifier(verifier);
        attempt.setExpiresAt(now().plusSeconds(ATTEMPT_TTL_SECONDS));
        attempt.setIpAddress(request.getRemoteAddr());
        attempts.save(attempt);

        ResponseCookie cookie = ResponseCookie.from(BINDING_COOKIE, binding)
                .httpOnly(true)
                .secure(properties.secureCookies())
                .sameSite("Lax")
                .path("/api/auth/sso")
                .maxAge(ATTEMPT_TTL_SECONDS)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        Map<String, String> params = new LinkedHashMap<>();
        params.put("client_id", properties.clientId(provider));
        params.put("response_type", "code");
        params.put("redirect_uri", properties.redirectUri(provider));
        params.put("scope", "openid email profile");
        params.put("state", state);
        params.put("nonce", nonce);
        params.put("code_challenge", base64Url(sha256(verifier)));
        params.put("code_challenge_method", "S256");
        params.put("prompt", "select_account");
        if (provider == SsoProvider.MICROSOFT) params.put("response_mode", "query");
        return properties.authorizationEndpoint(provider) + "?" + query(params);
    }

    // ------------------------------------------------------------------ 2. callback

    @Transactional
    public Redirect callback(String providerPath, String code, String state, String error,
                             HttpServletRequest request, HttpServletResponse response) {
        clearBindingCookie(response);
        Optional<SsoProvider> providerOpt = SsoProvider.fromPath(providerPath).filter(properties::isConfigured);
        if (providerOpt.isEmpty()) return failure("unavailable");
        SsoProvider provider = providerOpt.get();
        if (state == null || state.isBlank()) return failure("expired");

        SsoLoginAttempt attempt = attempts.findByStateHash(sha256Hex(state)).orElse(null);
        if (attempt == null || attempt.getCallbackAt() != null || !provider.name().equals(attempt.getProvider())
                || attempt.getExpiresAt().isBefore(now())) {
            return failure("expired");
        }
        attempt.setCallbackAt(now());
        attempts.save(attempt);

        String binding = cookieValue(request, BINDING_COOKIE);
        if (binding == null || !MessageDigest.isEqual(sha256Hex(binding).getBytes(StandardCharsets.US_ASCII),
                attempt.getBrowserBindingHash().getBytes(StandardCharsets.US_ASCII))) {
            log.warn("SSO callback without matching browser binding for provider {}", provider);
            return failure("expired");
        }
        if (error != null && !error.isBlank()) {
            return failure("access_denied".equals(error) ? "cancelled" : "failed");
        }
        if (code == null || code.isBlank()) return failure("failed");

        Map<String, Object> claims;
        try {
            claims = validateIdToken(provider, exchangeCode(provider, code, attempt.getCodeVerifier()), attempt.getNonce());
        } catch (IdTokenValidator.InvalidIdTokenException ex) {
            log.warn("Rejected {} ID token: {}", provider, ex.getMessage());
            authService.recordSsoFailure(null, "SSO " + provider + ": " + ex.getMessage(), request);
            return failure("failed");
        } catch (RuntimeException ex) {
            log.warn("SSO token exchange with {} failed: {}", provider, ex.getClass().getSimpleName());
            return failure("failed");
        }

        Resolution resolution = resolveUser(provider, claims);
        if (resolution.error() != null) {
            authService.recordSsoFailure(resolution.email(), "SSO " + provider + ": " + resolution.error(), request);
            return failure(resolution.error());
        }
        User user = resolution.user();
        String exchangeCode = randomToken(32);
        attempt.setUser(user);
        attempt.setExchangeCodeHash(sha256Hex(exchangeCode));
        attempt.setExchangeExpiresAt(now().plusSeconds(EXCHANGE_TTL_SECONDS));
        attempts.save(attempt);
        return new Redirect(properties.frontendLoginUrl() + "?sso_code=" + encode(exchangeCode));
    }

    // ------------------------------------------------------------------ 3. exchange

    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    public LoginResponse exchange(String code, HttpServletRequest request) {
        if (code == null || code.isBlank()) throw new AuthenticationFailedException("Your sign-in link expired. Please try again.");
        SsoLoginAttempt attempt = attempts.findByExchangeCodeHash(sha256Hex(code.trim())).orElse(null);
        if (attempt == null || attempt.getUser() == null || attempt.getExchangedAt() != null
                || attempt.getExchangeExpiresAt() == null || attempt.getExchangeExpiresAt().isBefore(now())) {
            throw new AuthenticationFailedException("Your sign-in link expired. Please try again.");
        }
        attempt.setExchangedAt(now());
        attempts.save(attempt);
        User user = attempt.getUser();
        SsoProvider provider = SsoProvider.valueOf(attempt.getProvider());
        return authService.completeSsoLogin(user, provider.displayName(), policyService.trustsSsoMfa(user), request);
    }

    // ------------------------------------------------------------------ helpers

    record Resolution(User user, String email, String error) {
    }

    /** Linked identity first; else verified email -> existing user (linked now); else optional auto-provisioning. */
    Resolution resolveUser(SsoProvider provider, Map<String, Object> claims) {
        String subject = (String) claims.get("sub");
        String issuer = (String) claims.get("iss");
        String email = verifiedEmail(provider, claims);

        Optional<UserSsoIdentity> linked = identities.findByProviderAndSubjectAndDeletedFalse(provider.name(), subject);
        if (linked.isPresent()) {
            UserSsoIdentity identity = linked.get();
            User user = identity.getUser();
            if (user == null || user.isDeleted()) return new Resolution(null, email, "account_not_found");
            identity.setLastLoginAt(now());
            identities.save(identity);
            return new Resolution(user, email, null);
        }
        if (email == null) return new Resolution(null, null, "email_not_verified");

        User user = users.findByEmailIgnoreCaseAndDeletedFalse(email).orElse(null);
        if (user == null) {
            Optional<CompanySsoSetting> provisioning = policyService.autoProvisionCompanyFor(email);
            if (provisioning.isEmpty()) return new Resolution(null, email, "account_not_found");
            user = provision(provisioning.get(), email, claims);
            if (user == null) return new Resolution(null, email, "account_not_found");
        } else if (!identities.findAllByUser_IdAndProviderAndDeletedFalse(user.getId(), provider.name()).isEmpty()) {
            // This Vettri account is already linked to a different account at the same provider.
            return new Resolution(null, email, "identity_conflict");
        }

        UserSsoIdentity identity = new UserSsoIdentity();
        identity.setUser(user);
        identity.setProvider(provider.name());
        identity.setIssuer(issuer);
        identity.setSubject(subject);
        identity.setEmail(email);
        identity.setLastLoginAt(now());
        identities.save(identity);
        auditLogService.log("User", user.getId(), "SSO_LINK", "Linked " + provider.displayName() + " sign-in");
        return new Resolution(user, email, null);
    }

    /**
     * Google: email + email_verified=true. Microsoft: a single-tenant app trusts
     * its tenant's email/UPN; multi-tenant needs the xms_edov (domain owner
     * verified) optional claim, except for personal Microsoft accounts.
     */
    String verifiedEmail(SsoProvider provider, Map<String, Object> claims) {
        if (provider == SsoProvider.GOOGLE) {
            Object verified = claims.get("email_verified");
            boolean ok = Boolean.TRUE.equals(verified) || "true".equals(verified);
            return ok ? normalizeEmail(claims.get("email")) : null;
        }
        String email = normalizeEmail(claims.get("email"));
        if (email == null) email = normalizeEmail(claims.get("preferred_username"));
        if (email == null) return null;
        String tid = claims.get("tid") instanceof String t ? t : "";
        Object edov = claims.get("xms_edov");
        boolean domainVerified = Boolean.TRUE.equals(edov) || "true".equals(edov) || "1".equals(String.valueOf(edov));
        boolean singleTenant = properties.microsoftTenant().matches("^[0-9a-fA-F-]{36}$")
                && properties.microsoftTenant().equalsIgnoreCase(tid);
        boolean personal = SsoProperties.MSA_TENANT_ID.equalsIgnoreCase(tid);
        return singleTenant || personal || domainVerified ? email : null;
    }

    private User provision(CompanySsoSetting setting, String email, Map<String, Object> claims) {
        Role employee = roles.findByName("EMPLOYEE").orElse(null);
        if (employee == null) return null;
        String name = claims.get("name") instanceof String n && !n.isBlank() ? n.trim() : email.substring(0, email.indexOf('@'));
        User user = new User();
        user.setUsername(email);
        user.setEmail(email);
        user.setFullName(name.length() > 150 ? name.substring(0, 150) : name);
        user.setPasswordHash(passwordEncoder.encode(randomToken(32)));
        user.setActive(true);
        user.setAccountStatus("ACTIVE");
        user.setEmailVerified(true);
        user.setEmailVerifiedAt(LocalDateTime.now());
        user.setMustChangePassword(false);
        user.setCompany(setting.getCompany());
        user.setRoles(new HashSet<>(List.of(employee)));
        User saved = users.save(user);
        auditLogService.log("User", saved.getId(), "SSO_AUTO_PROVISION", "Created from verified SSO email domain");
        return saved;
    }

    private String exchangeCode(SsoProvider provider, String code, String verifier) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "authorization_code");
        form.put("code", code);
        form.put("redirect_uri", properties.redirectUri(provider));
        form.put("client_id", properties.clientId(provider));
        form.put("client_secret", properties.clientSecret(provider));
        form.put("code_verifier", verifier);
        Map<String, Object> body = httpClient.exchangeCode(properties.tokenEndpoint(provider), form);
        Object idToken = body == null ? null : body.get("id_token");
        if (!(idToken instanceof String token) || token.isBlank()) {
            throw new IdTokenValidator.InvalidIdTokenException("Token response has no ID token");
        }
        return token;
    }

    Map<String, Object> validateIdToken(SsoProvider provider, String idToken, String nonce) {
        IdTokenValidator validator = new IdTokenValidator(clock);
        return validator.validate(idToken, force -> jwks(provider, force),
                (issuer, claims) -> properties.acceptsIssuer(provider, issuer,
                        claims.get("tid") instanceof String tid ? tid : null),
                properties.clientId(provider), nonce);
    }

    private synchronized Map<String, Object> jwks(SsoProvider provider, boolean forceRefresh) {
        CachedJwks cached = jwksCache.get(provider);
        Instant now = clock.instant();
        boolean stale = cached == null || cached.fetchedAt().plusSeconds(JWKS_TTL_SECONDS).isBefore(now);
        boolean mayRefresh = cached == null || cached.fetchedAt().plusSeconds(JWKS_MIN_REFRESH_SECONDS).isBefore(now);
        if (stale || (forceRefresh && mayRefresh)) {
            Map<String, Object> fresh = httpClient.fetchJwks(properties.jwksUri(provider));
            if (fresh != null) {
                cached = new CachedJwks(fresh, now);
                jwksCache.put(provider, cached);
            }
        }
        return cached == null ? Map.of() : cached.jwks();
    }

    /** Daily housekeeping: SSO attempts are only useful for minutes. */
    @Scheduled(cron = "0 40 3 * * *", zone = "Asia/Kolkata")
    @Transactional
    public void purgeOldAttempts() {
        int removed = attempts.deleteCreatedBefore(LocalDateTime.now().minusDays(1));
        if (removed > 0) log.info("Purged {} old SSO login attempts", removed);
    }

    private Redirect failure(String reason) {
        return new Redirect(properties.frontendLoginUrl() + "?sso_error=" + encode(reason));
    }

    private void clearBindingCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(BINDING_COOKIE, "")
                .httpOnly(true).secure(properties.secureCookies()).sameSite("Lax")
                .path("/api/auth/sso").maxAge(0).build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private static String cookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }

    private static String normalizeEmail(Object value) {
        if (!(value instanceof String s) || !s.contains("@")) return null;
        String email = s.trim().toLowerCase(Locale.ROOT);
        return email.length() > 150 ? null : email;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), java.time.ZoneId.systemDefault());
    }

    private String randomToken(int bytes) {
        byte[] buffer = new byte[bytes];
        random.nextBytes(buffer);
        return base64Url(buffer);
    }

    private static String query(Map<String, String> params) {
        StringBuilder builder = new StringBuilder();
        params.forEach((key, value) -> {
            if (builder.length() > 0) builder.append('&');
            builder.append(encode(key)).append('=').append(encode(value));
        });
        return builder.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    static String sha256Hex(String value) {
        return HexFormat.of().formatHex(sha256(value));
    }
}
