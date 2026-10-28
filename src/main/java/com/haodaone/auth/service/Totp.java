package com.haodaone.auth.service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Locale;

/**
 * Self-contained TOTP (RFC 6238) on top of HOTP (RFC 4226) with HmacSHA1,
 * 30 second steps and 6 digits - what Google Authenticator, Microsoft
 * Authenticator, Authy, 1Password etc. expect by default - plus the RFC 4648
 * base32 codec used for the shared secret. No external dependency.
 */
public final class Totp {

    public static final int STEP_SECONDS = 30;
    public static final int DIGITS = 6;
    /** Accept the previous, current and next step to tolerate clock drift. */
    public static final int WINDOW = 1;

    private static final char[] BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
    private static final int[] POW10 = {1, 10, 100, 1_000, 10_000, 100_000, 1_000_000, 10_000_000, 100_000_000};
    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {
    }

    /** 160-bit random secret (the RFC 4226 recommended length for SHA-1). */
    public static byte[] generateSecret() {
        byte[] secret = new byte[20];
        RANDOM.nextBytes(secret);
        return secret;
    }

    public static long timeStep(long epochSeconds) {
        return Math.floorDiv(epochSeconds, STEP_SECONDS);
    }

    /** HOTP value for the given counter (time step), zero-padded to {@code digits}. */
    public static String generateCode(byte[] key, long step, int digits) {
        if (digits < 1 || digits > 8) {
            throw new IllegalArgumentException("digits must be 1..8");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24)
                    | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8)
                    | (hash[offset + 3] & 0xff);
            int otp = binary % POW10[digits];
            StringBuilder result = new StringBuilder(Integer.toString(otp));
            while (result.length() < digits) {
                result.insert(0, '0');
            }
            return result.toString();
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA1 not available", ex);
        }
    }

    public static String generateCode(byte[] key, long step) {
        return generateCode(key, step, DIGITS);
    }

    /**
     * Returns the matching time step within +/-WINDOW of {@code epochSeconds},
     * or -1. A step at or before {@code lastUsedStep} never matches, which
     * blocks replaying a code (or an older one) that was already accepted.
     */
    public static long verify(byte[] key, String code, long epochSeconds, Long lastUsedStep) {
        String normalized = normalizeCode(code);
        if (normalized == null || normalized.length() != DIGITS) {
            return -1;
        }
        long current = timeStep(epochSeconds);
        for (long step = current - WINDOW; step <= current + WINDOW; step++) {
            if (lastUsedStep != null && step <= lastUsedStep) {
                continue;
            }
            if (constantTimeEquals(generateCode(key, step), normalized)) {
                return step;
            }
        }
        return -1;
    }

    /** Strips spaces/dashes; returns null if anything other than digits remains. */
    static String normalizeCode(String code) {
        if (code == null) {
            return null;
        }
        String stripped = code.replace(" ", "").replace("-", "").trim();
        return stripped.matches("\\d+") ? stripped : null;
    }

    public static String otpauthUri(String issuer, String account, String base32Secret) {
        String label = urlEncode(issuer) + ":" + urlEncode(account);
        return "otpauth://totp/" + label
                + "?secret=" + base32Secret
                + "&issuer=" + urlEncode(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + STEP_SECONDS;
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    public static String base32Encode(byte[] data) {
        StringBuilder out = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                out.append(BASE32_ALPHABET[(buffer >> (bitsLeft - 5)) & 0x1f]);
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            out.append(BASE32_ALPHABET[(buffer << (5 - bitsLeft)) & 0x1f]);
        }
        return out.toString();
    }

    /** Case-insensitive; ignores spaces, dashes and '=' padding. */
    public static byte[] base32Decode(String encoded) {
        String clean = encoded.replace(" ", "").replace("-", "").replace("=", "").toUpperCase(Locale.ROOT);
        ByteArrayOutputStream out = new ByteArrayOutputStream(clean.length() * 5 / 8);
        int buffer = 0;
        int bitsLeft = 0;
        for (char c : clean.toCharArray()) {
            int value;
            if (c >= 'A' && c <= 'Z') {
                value = c - 'A';
            } else if (c >= '2' && c <= '7') {
                value = 26 + (c - '2');
            } else {
                throw new IllegalArgumentException("Invalid base32 character: " + c);
            }
            buffer = (buffer << 5) | value;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out.write((buffer >> (bitsLeft - 8)) & 0xff);
                bitsLeft -= 8;
            }
        }
        return out.toByteArray();
    }

    private static boolean constantTimeEquals(String a, String b) {
        return java.security.MessageDigest.isEqual(a.getBytes(StandardCharsets.US_ASCII), b.getBytes(StandardCharsets.US_ASCII));
    }
}
