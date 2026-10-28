package com.haodaone.sso.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * SSO configuration (application.properties app.sso.*). Every key defaults to
 * empty, so a provider is only offered once its client id and secret are set.
 */
@Component
public class SsoProperties {

    /** Microsoft personal-account ("consumers") tenant id. */
    public static final String MSA_TENANT_ID = "9188040d-6c67-4c5b-b112-36a304b66dad";
    private static final Pattern GUID = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final String googleClientId;
    private final String googleClientSecret;
    private final String microsoftClientId;
    private final String microsoftClientSecret;
    private final String microsoftTenant;
    private final String redirectBaseUrl;
    private final String frontendUrl;

    public SsoProperties(@Value("${app.sso.google.client-id:}") String googleClientId,
                         @Value("${app.sso.google.client-secret:}") String googleClientSecret,
                         @Value("${app.sso.microsoft.client-id:}") String microsoftClientId,
                         @Value("${app.sso.microsoft.client-secret:}") String microsoftClientSecret,
                         @Value("${app.sso.microsoft.tenant:common}") String microsoftTenant,
                         @Value("${app.sso.redirect-base-url:}") String redirectBaseUrl,
                         @Value("${app.sso.frontend-url:${app.frontend-url:}}") String frontendUrl) {
        this.googleClientId = trim(googleClientId);
        this.googleClientSecret = trim(googleClientSecret);
        this.microsoftClientId = trim(microsoftClientId);
        this.microsoftClientSecret = trim(microsoftClientSecret);
        String tenant = trim(microsoftTenant);
        this.microsoftTenant = tenant.isEmpty() ? "common" : tenant;
        this.redirectBaseUrl = stripSlash(trim(redirectBaseUrl));
        this.frontendUrl = stripSlash(trim(frontendUrl));
    }

    public boolean isConfigured(SsoProvider provider) {
        if (redirectBaseUrl.isEmpty()) return false;
        return switch (provider) {
            case GOOGLE -> !googleClientId.isEmpty() && !googleClientSecret.isEmpty();
            case MICROSOFT -> !microsoftClientId.isEmpty() && !microsoftClientSecret.isEmpty();
        };
    }

    public String clientId(SsoProvider provider) {
        return provider == SsoProvider.GOOGLE ? googleClientId : microsoftClientId;
    }

    public String clientSecret(SsoProvider provider) {
        return provider == SsoProvider.GOOGLE ? googleClientSecret : microsoftClientSecret;
    }

    public String authorizationEndpoint(SsoProvider provider) {
        return provider == SsoProvider.GOOGLE
                ? "https://accounts.google.com/o/oauth2/v2/auth"
                : "https://login.microsoftonline.com/" + microsoftTenant + "/oauth2/v2.0/authorize";
    }

    public String tokenEndpoint(SsoProvider provider) {
        return provider == SsoProvider.GOOGLE
                ? "https://oauth2.googleapis.com/token"
                : "https://login.microsoftonline.com/" + microsoftTenant + "/oauth2/v2.0/token";
    }

    public String jwksUri(SsoProvider provider) {
        return provider == SsoProvider.GOOGLE
                ? "https://www.googleapis.com/oauth2/v3/certs"
                : "https://login.microsoftonline.com/" + microsoftTenant + "/discovery/v2.0/keys";
    }

    public String redirectUri(SsoProvider provider) {
        return redirectBaseUrl + "/api/auth/sso/" + provider.pathName() + "/callback";
    }

    public String frontendLoginUrl() {
        return (frontendUrl.isEmpty() ? "" : frontendUrl) + "/login";
    }

    public boolean secureCookies() {
        return redirectBaseUrl.toLowerCase(Locale.ROOT).startsWith("https://");
    }

    public String microsoftTenant() {
        return microsoftTenant;
    }

    /** Issuer rule for the provider; Microsoft's issuer embeds the token's tenant id. */
    public boolean acceptsIssuer(SsoProvider provider, String issuer, String tenantIdClaim) {
        if (issuer == null) return false;
        if (provider == SsoProvider.GOOGLE) {
            return "https://accounts.google.com".equals(issuer) || "accounts.google.com".equals(issuer);
        }
        if (tenantIdClaim == null || !GUID.matcher(tenantIdClaim).matches()) return false;
        if (!issuer.equals("https://login.microsoftonline.com/" + tenantIdClaim + "/v2.0")) return false;
        String tenant = microsoftTenant.toLowerCase(Locale.ROOT);
        if (GUID.matcher(tenant).matches()) return tenant.equalsIgnoreCase(tenantIdClaim);
        if ("consumers".equals(tenant)) return MSA_TENANT_ID.equalsIgnoreCase(tenantIdClaim);
        if ("organizations".equals(tenant)) return !MSA_TENANT_ID.equalsIgnoreCase(tenantIdClaim);
        return true; // "common" or a verified domain name
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String stripSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
