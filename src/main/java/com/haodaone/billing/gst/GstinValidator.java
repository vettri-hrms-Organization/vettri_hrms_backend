package com.haodaone.billing.gst;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * GSTIN format + check-digit validation.
 *
 * Format (15 chars): 2-digit state code, 10-char PAN (5 letters, 4 digits,
 * 1 letter), entity number (1-9 / A-Z), 'Z' (default), check character.
 * The check character is the GSTN mod-36 checksum: for each of the first 14
 * characters (base-36 value), multiply by 1 / 2 alternately, add the
 * quotient and remainder of the product divided by 36; the check character
 * is (36 - sum mod 36) mod 36.
 */
public final class GstinValidator {

    private static final String CHARSET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final Pattern FORMAT = Pattern.compile("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z][1-9A-Z]Z[0-9A-Z]$");

    private GstinValidator() {
    }

    public static String normalize(String gstin) {
        return gstin == null ? null : gstin.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    public static boolean isValid(String gstin) {
        String value = normalize(gstin);
        if (value == null || !FORMAT.matcher(value).matches()) {
            return false;
        }
        int stateCode = Integer.parseInt(value.substring(0, 2));
        if (stateCode < 1 || stateCode > 99) {
            return false;
        }
        return value.charAt(14) == checkCharacter(value.substring(0, 14));
    }

    public static char checkCharacter(String first14) {
        int sum = 0;
        for (int i = 0; i < 14; i++) {
            int value = CHARSET.indexOf(first14.charAt(i));
            if (value < 0) {
                throw new IllegalArgumentException("Invalid GSTIN character");
            }
            int product = value * (i % 2 == 0 ? 1 : 2);
            sum += product / 36 + product % 36;
        }
        return CHARSET.charAt((36 - sum % 36) % 36);
    }

    /** Two-digit state code embedded in a GSTIN, or null when the GSTIN is blank. */
    public static String stateCodeOf(String gstin) {
        String value = normalize(gstin);
        return value == null || value.length() < 2 ? null : value.substring(0, 2);
    }
}
