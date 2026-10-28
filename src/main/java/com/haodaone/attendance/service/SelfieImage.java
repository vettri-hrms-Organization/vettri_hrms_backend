package com.haodaone.attendance.service;

import com.haodaone.common.exception.BadRequestException;

import java.util.Base64;

/**
 * A decoded, validated check-in selfie. Accepts raw base64 or a data URL
 * ("data:image/jpeg;base64,...") and checks the real file signature, not just
 * the declared type: only JPEG and PNG up to {@link #MAX_BYTES} are accepted.
 */
public record SelfieImage(byte[] bytes, String contentType, String extension) {

    public static final int MAX_BYTES = 1024 * 1024;
    /** Base64 inflates by 4/3; reject oversized payloads before decoding them. */
    private static final int MAX_ENCODED_CHARS = (MAX_BYTES / 3 + 1) * 4 + 128;

    public static SelfieImage decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            throw new BadRequestException("SELFIE_REQUIRED");
        }
        String payload = encoded.trim();
        String declaredType = null;
        if (payload.startsWith("data:")) {
            int comma = payload.indexOf(',');
            if (comma < 0 || !payload.substring(0, comma).toLowerCase().endsWith(";base64")) {
                throw new BadRequestException("SELFIE_INVALID_TYPE: the selfie must be a base64 JPEG or PNG image.");
            }
            declaredType = payload.substring(5, payload.indexOf(';')).toLowerCase();
            payload = payload.substring(comma + 1);
        }
        if (payload.length() > MAX_ENCODED_CHARS) {
            throw new BadRequestException("SELFIE_TOO_LARGE: the selfie must be 1 MB or smaller.");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(payload.replaceAll("\\s", ""));
        } catch (IllegalArgumentException invalid) {
            throw new BadRequestException("SELFIE_INVALID_TYPE: the selfie is not valid base64 image data.");
        }
        if (bytes.length == 0) {
            throw new BadRequestException("SELFIE_REQUIRED");
        }
        if (bytes.length > MAX_BYTES) {
            throw new BadRequestException("SELFIE_TOO_LARGE: the selfie must be 1 MB or smaller.");
        }
        String detected = isJpeg(bytes) ? "image/jpeg" : isPng(bytes) ? "image/png" : null;
        if (detected == null || (declaredType != null && !declaredType.equals(detected)
                && !("image/jpg".equals(declaredType) && "image/jpeg".equals(detected)))) {
            throw new BadRequestException("SELFIE_INVALID_TYPE: only JPEG or PNG selfies are accepted.");
        }
        return new SelfieImage(bytes, detected, "image/png".equals(detected) ? "png" : "jpg");
    }

    private static boolean isJpeg(byte[] b) {
        return b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
    }

    private static boolean isPng(byte[] b) {
        return b.length > 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A;
    }
}
