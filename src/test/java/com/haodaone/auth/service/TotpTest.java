package com.haodaone.auth.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TotpTest {

    private static final byte[] RFC_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @Test
    void matchesRfc6238Sha1TestVectors() {
        long[][] vectors = {
                {59L, 94287082L},
                {1111111109L, 7081804L},
                {1111111111L, 14050471L},
                {1234567890L, 89005924L},
                {2000000000L, 69279037L},
                {20000000000L, 65353130L},
        };
        for (long[] vector : vectors) {
            String expected = String.format("%08d", vector[1]);
            assertEquals(expected, Totp.generateCode(RFC_SECRET, Totp.timeStep(vector[0]), 8), "T=" + vector[0]);
        }
    }

    @Test
    void sixDigitCodeIsTheLowSixDigitsOfTheRfcValue() {
        assertEquals("287082", Totp.generateCode(RFC_SECRET, Totp.timeStep(59)));
        assertEquals("081804", Totp.generateCode(RFC_SECRET, Totp.timeStep(1111111109)));
    }

    @Test
    void base32RoundTripsAndMatchesRfc4648() {
        assertEquals("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", Totp.base32Encode(RFC_SECRET));
        assertArrayEquals(RFC_SECRET, Totp.base32Decode("gezd gnbv gy3t qojq gezd gnbv gy3t qojq"));
        assertEquals("MZXW6YQ", Totp.base32Encode("foob".getBytes(StandardCharsets.US_ASCII)));
        assertArrayEquals("foob".getBytes(StandardCharsets.US_ASCII), Totp.base32Decode("MZXW6YQ="));
        byte[] random = Totp.generateSecret();
        assertArrayEquals(random, Totp.base32Decode(Totp.base32Encode(random)));
        assertThrows(IllegalArgumentException.class, () -> Totp.base32Decode("ABC1"));
    }

    @Test
    void acceptsOneStepOfDriftEitherWayButNotTwo() {
        long now = 1_700_000_000L;
        long step = Totp.timeStep(now);
        assertEquals(step - 1, Totp.verify(RFC_SECRET, Totp.generateCode(RFC_SECRET, step - 1), now, null));
        assertEquals(step + 1, Totp.verify(RFC_SECRET, Totp.generateCode(RFC_SECRET, step + 1), now, null));
        assertEquals(-1, Totp.verify(RFC_SECRET, Totp.generateCode(RFC_SECRET, step - 2), now, null));
        assertEquals(-1, Totp.verify(RFC_SECRET, Totp.generateCode(RFC_SECRET, step + 2), now, null));
        assertEquals(-1, Totp.verify(RFC_SECRET, "abcdef", now, null));
        assertEquals(-1, Totp.verify(RFC_SECRET, null, now, null));
    }

    @Test
    void rejectsReplayOfTheSameOrAnOlderStep() {
        long now = 1_700_000_000L;
        long step = Totp.timeStep(now);
        String code = Totp.generateCode(RFC_SECRET, step);
        long accepted = Totp.verify(RFC_SECRET, code, now, null);
        assertEquals(step, accepted);
        assertEquals(-1, Totp.verify(RFC_SECRET, code, now, accepted));
        assertEquals(-1, Totp.verify(RFC_SECRET, Totp.generateCode(RFC_SECRET, step - 1), now, accepted));
        assertEquals(step + 1, Totp.verify(RFC_SECRET, Totp.generateCode(RFC_SECRET, step + 1), now + 30, accepted));
    }

    @Test
    void otpauthUriCarriesIssuerAccountAndSecret() {
        String uri = Totp.otpauthUri("Vettri HRMS", "asha@example.com", "JBSWY3DPEHPK3PXP");
        assertTrue(uri.startsWith("otpauth://totp/Vettri%20HRMS:asha%40example.com?secret=JBSWY3DPEHPK3PXP"));
        assertTrue(uri.contains("&issuer=Vettri%20HRMS"));
        assertTrue(uri.contains("&digits=6&period=30"));
    }

    @Test
    void cipherRoundTripsWithRandomIvAndRejectsAnotherKey() {
        MfaSecretCipher cipher = new MfaSecretCipher("", "jwt-secret-for-tests-0123456789abcdef");
        byte[] secret = Totp.generateSecret();
        String first = cipher.encrypt(secret);
        String second = cipher.encrypt(secret);
        assertNotEquals(first, second);
        assertTrue(first.startsWith("v1:"));
        assertArrayEquals(secret, cipher.decrypt(first));

        MfaSecretCipher other = new MfaSecretCipher("a-dedicated-totp-key", "jwt-secret-for-tests-0123456789abcdef");
        assertThrows(IllegalStateException.class, () -> other.decrypt(first));
    }

    @Test
    void hkdfMatchesRfc5869TestCase1() {
        byte[] ikm = new byte[22];
        java.util.Arrays.fill(ikm, (byte) 0x0b);
        byte[] salt = java.util.HexFormat.of().parseHex("000102030405060708090a0b0c");
        byte[] info = java.util.HexFormat.of().parseHex("f0f1f2f3f4f5f6f7f8f9");
        byte[] okm = MfaSecretCipher.hkdfSha256(ikm, salt, info, 42);
        assertEquals("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865",
                java.util.HexFormat.of().formatHex(okm));
    }
}
