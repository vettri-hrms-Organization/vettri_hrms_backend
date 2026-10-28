package com.haodaone.sso.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Verifies an OpenID Connect ID token without extra libraries: RS256 signature
 * against the provider's JWKS (java.security), then iss / aud / azp / exp / iat
 * / nbf / nonce / sub. Any failure throws InvalidIdTokenException.
 */
public class IdTokenValidator {

    public static final long CLOCK_SKEW_SECONDS = 60;
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Source of the provider's JSON Web Key Set ({"keys":[...]}); forceRefresh after an unknown kid. */
    public interface JwksSource {
        Map<String, Object> jwks(boolean forceRefresh);
    }

    /** Provider-specific issuer rule: (iss, full claims) -> accepted. */
    public interface IssuerPolicy {
        boolean accepts(String issuer, Map<String, Object> claims);
    }

    public static class InvalidIdTokenException extends RuntimeException {
        public InvalidIdTokenException(String message) {
            super(message);
        }
    }

    private final Clock clock;

    public IdTokenValidator(Clock clock) {
        this.clock = clock;
    }

    public Map<String, Object> validate(String idToken, JwksSource jwks, IssuerPolicy issuerPolicy,
                                        String clientId, String expectedNonce) {
        if (idToken == null || idToken.isBlank()) throw new InvalidIdTokenException("Missing ID token");
        if (clientId == null || clientId.isBlank()) throw new InvalidIdTokenException("SSO client is not configured");
        String[] parts = idToken.split("\\.");
        if (parts.length != 3) throw new InvalidIdTokenException("Malformed ID token");
        Map<String, Object> header = parseJson(parts[0]);
        if (!"RS256".equals(header.get("alg"))) throw new InvalidIdTokenException("Unsupported ID token algorithm");
        String kid = header.get("kid") instanceof String k ? k : null;

        PublicKey key = findKey(jwks.jwks(false), kid);
        if (key == null) key = findKey(jwks.jwks(true), kid);
        if (key == null) throw new InvalidIdTokenException("Unknown signing key");
        if (!verifySignature(key, parts[0] + "." + parts[1], decode(parts[2]))) {
            throw new InvalidIdTokenException("Invalid ID token signature");
        }

        Map<String, Object> claims = parseJson(parts[1]);
        String issuer = claims.get("iss") instanceof String s ? s : null;
        if (!issuerPolicy.accepts(issuer, claims)) throw new InvalidIdTokenException("Unexpected token issuer");

        Object aud = claims.get("aud");
        boolean audienceOk;
        if (aud instanceof String single) {
            audienceOk = single.equals(clientId);
        } else if (aud instanceof Collection<?> many) {
            audienceOk = many.contains(clientId) && (many.size() == 1 || clientId.equals(claims.get("azp")));
        } else {
            audienceOk = false;
        }
        if (!audienceOk) throw new InvalidIdTokenException("Token was issued for a different application");
        if (claims.get("azp") instanceof String azp && !azp.equals(clientId)) {
            throw new InvalidIdTokenException("Token was issued for a different application");
        }

        long now = clock.instant().getEpochSecond();
        Long exp = number(claims.get("exp"));
        if (exp == null || now > exp + CLOCK_SKEW_SECONDS) throw new InvalidIdTokenException("ID token expired");
        Long iat = number(claims.get("iat"));
        if (iat != null && iat > now + CLOCK_SKEW_SECONDS) throw new InvalidIdTokenException("ID token issued in the future");
        Long nbf = number(claims.get("nbf"));
        if (nbf != null && nbf > now + CLOCK_SKEW_SECONDS) throw new InvalidIdTokenException("ID token not yet valid");

        Object nonce = claims.get("nonce");
        if (expectedNonce == null || !(nonce instanceof String n)
                || !MessageDigest.isEqual(n.getBytes(StandardCharsets.UTF_8), expectedNonce.getBytes(StandardCharsets.UTF_8))) {
            throw new InvalidIdTokenException("ID token nonce mismatch");
        }
        if (!(claims.get("sub") instanceof String sub) || sub.isBlank()) {
            throw new InvalidIdTokenException("ID token has no subject");
        }
        return claims;
    }

    @SuppressWarnings("unchecked")
    private static PublicKey findKey(Map<String, Object> jwks, String kid) {
        if (jwks == null || !(jwks.get("keys") instanceof List<?> keys)) return null;
        for (Object item : keys) {
            if (!(item instanceof Map<?, ?> raw)) continue;
            Map<String, Object> jwk = (Map<String, Object>) raw;
            if (!"RSA".equals(jwk.get("kty"))) continue;
            if (jwk.get("use") instanceof String use && !"sig".equals(use)) continue;
            if (kid != null && !kid.equals(jwk.get("kid"))) continue;
            if (!(jwk.get("n") instanceof String n) || !(jwk.get("e") instanceof String e)) continue;
            try {
                RSAPublicKeySpec spec = new RSAPublicKeySpec(new BigInteger(1, decode(n)), new BigInteger(1, decode(e)));
                return KeyFactory.getInstance("RSA").generatePublic(spec);
            } catch (Exception ex) {
                // skip malformed keys
            }
        }
        return null;
    }

    private static boolean verifySignature(PublicKey key, String signedPart, byte[] signature) {
        try {
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(key);
            verifier.update(signedPart.getBytes(StandardCharsets.US_ASCII));
            return verifier.verify(signature);
        } catch (Exception ex) {
            return false;
        }
    }

    private static Map<String, Object> parseJson(String base64Url) {
        try {
            return JSON.readValue(decode(base64Url), new TypeReference<Map<String, Object>>() { });
        } catch (InvalidIdTokenException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new InvalidIdTokenException("Malformed ID token");
        }
    }

    private static byte[] decode(String base64Url) {
        try {
            return Base64.getUrlDecoder().decode(base64Url);
        } catch (IllegalArgumentException ex) {
            throw new InvalidIdTokenException("Malformed ID token");
        }
    }

    private static Long number(Object value) {
        return value instanceof Number n ? n.longValue() : null;
    }
}
