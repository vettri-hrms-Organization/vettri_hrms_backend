package com.haodaone.auth.dto;

import com.haodaone.auth.entity.TwoFactorPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public final class MfaDTO {
    private MfaDTO() { }

    /**
     * available - false when the company policy is OFF (enrolment hidden).
     * required - the company policy requires this user to use 2FA.
     * enrollmentRequired - required but not yet enrolled (frontend forces the security page).
     */
    public record StatusResponse(boolean enabled, boolean totpEnabled, boolean emailOtpEnabled,
                                 long recoveryCodesRemaining, String policy, boolean available,
                                 boolean required, boolean enrollmentRequired, long trustedDevices,
                                 String email) { }

    public record TotpEnrollResponse(String secret, String otpauthUri, String issuer, String account) { }

    public record CodeRequest(@NotBlank(message = "Code is required") String code) { }

    /** recoveryCodes is non-empty only when a fresh set was generated (shown once). */
    public record EnableResponse(StatusResponse status, List<String> recoveryCodes) { }

    public record DisableRequest(@NotBlank(message = "Current password is required") String currentPassword,
                                 @NotBlank(message = "Method is required") String method,
                                 @NotBlank(message = "Code is required") String code) { }

    public record PasswordRequest(@NotBlank(message = "Current password is required") String currentPassword) { }

    public record RecoveryCodesResponse(List<String> recoveryCodes) { }

    public record VerifyRequest(@NotBlank(message = "mfaToken is required") String mfaToken,
                                @NotBlank(message = "Method is required") String method,
                                @NotBlank(message = "Code is required") String code,
                                boolean rememberDevice) { }

    public record EmailSendRequest(@NotBlank(message = "mfaToken is required") String mfaToken) { }

    public record EmailSentResponse(String sentTo, int expiresInSeconds, int resendAfterSeconds) { }

    public record PolicyRequest(@NotNull(message = "Policy is required") TwoFactorPolicy policy) { }

    public record PolicyResponse(String policy) { }
}
