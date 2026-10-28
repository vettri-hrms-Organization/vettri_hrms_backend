package com.haodaone.sso;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haodaone.sso.service.IdTokenValidator;
import com.haodaone.sso.service.IdTokenValidator.InvalidIdTokenException;
import com.haodaone.sso.service.SsoProperties;
import com.haodaone.sso.service.SsoProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** ID token checks with a locally generated RSA key and JWKS - no network. */
class IdTokenValidatorTest {

    private static final String CLIENT_ID = "client-123.apps.googleusercontent.com";
    private static final String NONCE = "nonce-abc";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Instant NOW = Instant.parse("2026-10-28T06:00:00Z");

    private KeyPair keyPair;
    private KeyPair otherKeyPair;
    private Map<String, Object> jwks;
    private final IdTokenValidator validator = new IdTokenValidator(Clock.fixed(NOW, ZoneOffset.UTC));
    private final IdTokenValidator.IssuerPolicy googleIssuer =
            (iss, claims) -> "https://accounts.google.com".equals(iss) || "accounts.google.com".equals(iss);

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        keyPair = generator.generateKeyPair();
        otherKeyPair = generator.generateKeyPair();
        jwks = jwksFor("k1", (RSAPublicKey) keyPair.getPublic());
    }

    @Test
    void acceptsAValidToken() throws Exception {
        Map<String, Object> claims = validator.validate(token("k1", keyPair.getPrivate(), claims()),
                force -> jwks, googleIssuer, CLIENT_ID, NONCE);
        assertEquals("1234567890", claims.get("sub"));
        assertEquals("asha@acme.in", claims.get("email"));
    }

    @Test
    void rejectsWrongAudienceIssuerNonceAndExpiry() throws Exception {
        Map<String, Object> wrongAud = claims();
        wrongAud.put("aud", "someone-else");
        assertRejected(token("k1", keyPair.getPrivate(), wrongAud), "different application");

        Map<String, Object> wrongIss = claims();
        wrongIss.put("iss", "https://evil.example.com");
        assertRejected(token("k1", keyPair.getPrivate(), wrongIss), "issuer");

        Map<String, Object> wrongNonce = claims();
        wrongNonce.put("nonce", "replayed");
        assertRejected(token("k1", keyPair.getPrivate(), wrongNonce), "nonce");

        Map<String, Object> expired = claims();
        expired.put("exp", NOW.getEpochSecond() - IdTokenValidator.CLOCK_SKEW_SECONDS - 1);
        assertRejected(token("k1", keyPair.getPrivate(), expired), "expired");

        Map<String, Object> future = claims();
        future.put("iat", NOW.getEpochSecond() + 3600);
        assertRejected(token("k1", keyPair.getPrivate(), future), "future");
    }

    @Test
    void acceptsAudienceArrayOnlyWithMatchingAzp() throws Exception {
        Map<String, Object> multi = claims();
        multi.put("aud", List.of(CLIENT_ID, "other"));
        assertRejected(token("k1", keyPair.getPrivate(), multi), "different application");
        multi.put("azp", CLIENT_ID);
        assertEquals("1234567890", validator.validate(token("k1", keyPair.getPrivate(), multi),
                force -> jwks, googleIssuer, CLIENT_ID, NONCE).get("sub"));
    }

    @Test
    void rejectsBadSignaturesTamperingAndOtherAlgorithms() throws Exception {
        assertRejected(token("k1", otherKeyPair.getPrivate(), claims()), "signature");

        String good = token("k1", keyPair.getPrivate(), claims());
        String[] parts = good.split("\\.");
        Map<String, Object> tamperedClaims = claims();
        tamperedClaims.put("email", "ceo@acme.in");
        String tampered = parts[0] + "." + b64(JSON.writeValueAsBytes(tamperedClaims)) + "." + parts[2];
        assertRejected(tampered, "signature");

        String none = b64("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8)) + "." + parts[1] + ".";
        assertRejected(none + "x", "algorithm");
        assertRejected("not-a-jwt", "Malformed");
    }

    @Test
    void refreshesTheKeySetOnceForAnUnknownKid() throws Exception {
        AtomicInteger fetches = new AtomicInteger();
        Map<String, Object> rotated = jwksFor("k2", (RSAPublicKey) otherKeyPair.getPublic());
        IdTokenValidator.JwksSource source = force -> {
            fetches.incrementAndGet();
            return force ? rotated : jwks;
        };
        Map<String, Object> claims = validator.validate(token("k2", otherKeyPair.getPrivate(), claims()),
                source, googleIssuer, CLIENT_ID, NONCE);
        assertEquals("1234567890", claims.get("sub"));
        assertEquals(2, fetches.get());

        assertRejected(token("k9", keyPair.getPrivate(), claims()), "Unknown signing key");
    }

    @Test
    void microsoftIssuerMustMatchTheTokenTenantAndConfiguredTenant() {
        String tenant = "3f1c2b6a-1111-4222-8333-944455556666";
        SsoProperties common = properties("common");
        assertTrue(common.acceptsIssuer(SsoProvider.MICROSOFT, "https://login.microsoftonline.com/" + tenant + "/v2.0", tenant));
        assertTrue(!common.acceptsIssuer(SsoProvider.MICROSOFT, "https://login.microsoftonline.com/" + tenant + "/v2.0",
                "aaaaaaaa-1111-4222-8333-944455556666"));
        SsoProperties single = properties(tenant);
        assertTrue(!single.acceptsIssuer(SsoProvider.MICROSOFT,
                "https://login.microsoftonline.com/aaaaaaaa-1111-4222-8333-944455556666/v2.0",
                "aaaaaaaa-1111-4222-8333-944455556666"));
        SsoProperties orgs = properties("organizations");
        assertTrue(!orgs.acceptsIssuer(SsoProvider.MICROSOFT,
                "https://login.microsoftonline.com/" + SsoProperties.MSA_TENANT_ID + "/v2.0", SsoProperties.MSA_TENANT_ID));
        assertTrue(common.acceptsIssuer(SsoProvider.GOOGLE, "https://accounts.google.com", null));
        assertTrue(!common.acceptsIssuer(SsoProvider.GOOGLE, "https://login.microsoftonline.com/x/v2.0", null));
    }

    // ------------------------------------------------------------------ helpers

    private void assertRejected(String idToken, String messagePart) {
        InvalidIdTokenException ex = assertThrows(InvalidIdTokenException.class,
                () -> validator.validate(idToken, force -> jwks, googleIssuer, CLIENT_ID, NONCE));
        assertTrue(ex.getMessage().contains(messagePart), () -> "Expected '" + messagePart + "' in: " + ex.getMessage());
    }

    private static SsoProperties properties(String tenant) {
        return new SsoProperties("g", "gs", "m", "ms", tenant, "https://api.example.com", "https://app.example.com");
    }

    private static Map<String, Object> claims() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("iss", "https://accounts.google.com");
        claims.put("aud", CLIENT_ID);
        claims.put("sub", "1234567890");
        claims.put("email", "asha@acme.in");
        claims.put("email_verified", true);
        claims.put("nonce", NONCE);
        claims.put("iat", NOW.getEpochSecond() - 10);
        claims.put("exp", NOW.getEpochSecond() + 3600);
        return claims;
    }

    private static String token(String kid, PrivateKey key, Map<String, Object> claims) throws Exception {
        Map<String, Object> header = Map.of("alg", "RS256", "kid", kid, "typ", "JWT");
        String signingInput = b64(JSON.writeValueAsBytes(header)) + "." + b64(JSON.writeValueAsBytes(claims));
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(key);
        signer.update(signingInput.getBytes(StandardCharsets.US_ASCII));
        return signingInput + "." + b64(signer.sign());
    }

    private static Map<String, Object> jwksFor(String kid, RSAPublicKey key) {
        Map<String, Object> jwk = new HashMap<>();
        jwk.put("kty", "RSA");
        jwk.put("use", "sig");
        jwk.put("alg", "RS256");
        jwk.put("kid", kid);
        jwk.put("n", b64(unsigned(key.getModulus().toByteArray())));
        jwk.put("e", b64(unsigned(key.getPublicExponent().toByteArray())));
        return Map.of("keys", List.of(jwk));
    }

    private static byte[] unsigned(byte[] bytes) {
        if (bytes.length > 1 && bytes[0] == 0) {
            byte[] trimmed = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, trimmed, 0, trimmed.length);
            return trimmed;
        }
        return bytes;
    }

    private static String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
