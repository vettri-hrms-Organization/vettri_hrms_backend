package com.haodaone.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts TOTP seeds at rest with AES-256-GCM.
 *
 * Key material: {@code app.security.totp-encryption-key} (any string; set a
 * long random value, e.g. {@code openssl rand -base64 48}). If unset, falls
 * back to {@code app.jwt.secret}. Either way the 256-bit AES key is derived
 * with HKDF-SHA256 (RFC 5869, fixed salt + info "vettri-totp-secret-v1"), so
 * the raw JWT signing secret is never used directly as an encryption key.
 * Rotating the source value makes existing TOTP enrolments undecryptable
 * (users would need an admin 2FA reset), so set the dedicated property once
 * and keep it stable.
 *
 * Stored format: "v1:" + base64(12-byte IV || ciphertext+tag).
 */
@Component
public class MfaSecretCipher {

    private static final String PREFIX = "v1:";
    private static final byte[] HKDF_SALT = "vettri-hrms-mfa".getBytes(StandardCharsets.UTF_8);
    private static final byte[] HKDF_INFO = "vettri-totp-secret-v1".getBytes(StandardCharsets.UTF_8);

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public MfaSecretCipher(@Value("${app.security.totp-encryption-key:}") String totpKey,
                           @Value("${app.jwt.secret}") String jwtSecret) {
        String source = totpKey != null && !totpKey.isBlank() ? totpKey : jwtSecret;
        if (source == null || source.isBlank()) {
            throw new IllegalStateException("app.security.totp-encryption-key or app.jwt.secret must be set");
        }
        this.key = new SecretKeySpec(hkdfSha256(source.getBytes(StandardCharsets.UTF_8), HKDF_SALT, HKDF_INFO, 32), "AES");
    }

    public String encrypt(byte[] plaintext) {
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plaintext);
            byte[] combined = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Could not encrypt the authenticator secret", ex);
        }
    }

    public byte[] decrypt(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            throw new IllegalStateException("Unsupported authenticator secret format");
        }
        try {
            byte[] combined = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, combined, 0, 12));
            return cipher.doFinal(combined, 12, combined.length - 12);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("Could not decrypt the authenticator secret (was the encryption key changed?)", ex);
        }
    }

    static byte[] hkdfSha256(byte[] inputKeyMaterial, byte[] salt, byte[] info, int length) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(salt, "HmacSHA256"));
            byte[] prk = mac.doFinal(inputKeyMaterial);

            mac.init(new SecretKeySpec(prk, "HmacSHA256"));
            byte[] result = new byte[length];
            byte[] previous = new byte[0];
            int written = 0;
            for (int counter = 1; written < length; counter++) {
                mac.update(previous);
                mac.update(info);
                mac.update((byte) counter);
                previous = mac.doFinal();
                int chunk = Math.min(previous.length, length - written);
                System.arraycopy(previous, 0, result, written, chunk);
                written += chunk;
            }
            return result;
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HmacSHA256 not available", ex);
        }
    }
}
