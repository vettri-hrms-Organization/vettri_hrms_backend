package com.haodaone.auth.controller;

import com.haodaone.auth.dto.LoginResponse;
import com.haodaone.auth.dto.MfaDTO;
import com.haodaone.auth.service.AuthService;
import com.haodaone.auth.service.MfaService;
import com.haodaone.security.CustomUserPrincipal;
import com.haodaone.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Two-factor authentication.
 *
 * Public (second login step, rate-limited in AbuseProtectionFilter):
 *   POST /api/auth/mfa/verify, POST /api/auth/mfa/email/send
 * Self-service (any signed-in user): status, TOTP enrol/confirm, email OTP,
 *   disable, recovery codes, forget remembered devices.
 * Admin (COMPANY_ADMIN or USER_MANAGE with organisation scope): company policy, reset a user's 2FA.
 */
@RestController
@RequestMapping("/api/auth/mfa")
public class MfaController {

    private static final String ADMIN_RULE =
            "hasRole('COMPANY_ADMIN') or hasRole('SUPER_ADMIN') or @authorizationService.hasOrganizationScope('USER_MANAGE')";

    private final MfaService mfaService;
    private final AuthService authService;

    public MfaController(MfaService mfaService, AuthService authService) {
        this.mfaService = mfaService;
        this.authService = authService;
    }

    // ---- public login step

    @PostMapping("/verify")
    public ResponseEntity<LoginResponse> verify(@Valid @RequestBody MfaDTO.VerifyRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.verifyMfa(request.mfaToken(), request.method(), request.code(),
                request.rememberDevice(), httpRequest));
    }

    @PostMapping("/email/send")
    public ResponseEntity<MfaDTO.EmailSentResponse> sendLoginEmail(@Valid @RequestBody MfaDTO.EmailSendRequest request) {
        return ResponseEntity.ok(mfaService.sendLoginEmailCode(request.mfaToken()));
    }

    // ---- self-service

    @GetMapping("/status")
    public ResponseEntity<MfaDTO.StatusResponse> status(@AuthenticationPrincipal CustomUserPrincipal principal) {
        return ResponseEntity.ok(mfaService.status(principal.getId()));
    }

    @PostMapping("/totp/enroll")
    public ResponseEntity<MfaDTO.TotpEnrollResponse> enrollTotp(@AuthenticationPrincipal CustomUserPrincipal principal) {
        return ResponseEntity.ok(mfaService.enrollTotp(principal.getId()));
    }

    @PostMapping("/totp/confirm")
    public ResponseEntity<MfaDTO.EnableResponse> confirmTotp(@AuthenticationPrincipal CustomUserPrincipal principal,
                                                             @Valid @RequestBody MfaDTO.CodeRequest request) {
        return ResponseEntity.ok(mfaService.confirmTotp(principal.getId(), request.code()));
    }

    @PostMapping("/email/setup/send")
    public ResponseEntity<MfaDTO.EmailSentResponse> sendSetupEmail(@AuthenticationPrincipal CustomUserPrincipal principal) {
        return ResponseEntity.ok(mfaService.sendSetupEmailCode(principal.getId()));
    }

    @PostMapping("/email/enable")
    public ResponseEntity<MfaDTO.EnableResponse> enableEmail(@AuthenticationPrincipal CustomUserPrincipal principal,
                                                             @Valid @RequestBody MfaDTO.CodeRequest request) {
        return ResponseEntity.ok(mfaService.enableEmailOtp(principal.getId(), request.code()));
    }

    @PostMapping("/disable")
    public ResponseEntity<MfaDTO.StatusResponse> disable(@AuthenticationPrincipal CustomUserPrincipal principal,
                                                         @Valid @RequestBody MfaDTO.DisableRequest request) {
        return ResponseEntity.ok(mfaService.disable(principal.getId(), request.currentPassword(), request.method(), request.code()));
    }

    @PostMapping("/recovery-codes/regenerate")
    public ResponseEntity<MfaDTO.RecoveryCodesResponse> regenerate(@AuthenticationPrincipal CustomUserPrincipal principal,
                                                                   @Valid @RequestBody MfaDTO.PasswordRequest request) {
        return ResponseEntity.ok(mfaService.regenerateRecoveryCodes(principal.getId(), request.currentPassword()));
    }

    @DeleteMapping("/trusted-devices")
    public ResponseEntity<MfaDTO.StatusResponse> forgetDevices(@AuthenticationPrincipal CustomUserPrincipal principal) {
        return ResponseEntity.ok(mfaService.revokeTrustedDevices(principal.getId()));
    }

    // ---- company admin

    @GetMapping("/policy")
    @PreAuthorize(ADMIN_RULE)
    public ResponseEntity<MfaDTO.PolicyResponse> policy() {
        return ResponseEntity.ok(new MfaDTO.PolicyResponse(mfaService.policyFor(TenantContext.getCurrentTenant()).name()));
    }

    @PutMapping("/policy")
    @PreAuthorize(ADMIN_RULE)
    public ResponseEntity<MfaDTO.PolicyResponse> updatePolicy(@Valid @RequestBody MfaDTO.PolicyRequest request) {
        return ResponseEntity.ok(mfaService.updatePolicy(TenantContext.getCurrentTenant(), request.policy()));
    }

    @PostMapping("/users/{userId}/reset")
    @PreAuthorize(ADMIN_RULE)
    public ResponseEntity<Void> resetUser(@PathVariable Long userId) {
        mfaService.adminReset(TenantContext.getCurrentTenant(), userId);
        return ResponseEntity.noContent().build();
    }
}
