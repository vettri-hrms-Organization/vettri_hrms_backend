package com.haodaone.auth.controller;

import com.haodaone.auth.dto.ChangePasswordRequest;
import com.haodaone.auth.dto.ForgotPasswordRequest;
import com.haodaone.auth.dto.LoginRequest;
import com.haodaone.auth.dto.LoginResponse;
import com.haodaone.auth.dto.ResetPasswordRequest;
import com.haodaone.auth.dto.RefreshRequest;
import com.haodaone.auth.dto.RegisterRequest;
import com.haodaone.auth.service.AuthService;
import com.haodaone.auth.service.PasswordResetService;
import com.haodaone.auth.service.RegistrationService;
import com.haodaone.auth.service.VerificationService;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.security.CustomUserPrincipal;
import com.haodaone.user.dto.UserDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final RegistrationService registrationService;
    private final VerificationService verificationService;
    private final PasswordResetService passwordResetService;
    private final EmployeeRepository employeeRepository;
    private final com.haodaone.company.repository.CompanyRepository companyRepository;

    /** Optional setter injection keeps the public constructor (used directly by unit tests) unchanged. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.haodaone.auth.service.MfaService mfaService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.haodaone.privacy.service.LegalAcceptanceService legalAcceptanceService;

    public AuthController(AuthService authService, RegistrationService registrationService,
                          VerificationService verificationService, PasswordResetService passwordResetService,
                          EmployeeRepository employeeRepository,
                          com.haodaone.company.repository.CompanyRepository companyRepository) {
        this.authService = authService;
        this.registrationService = registrationService;
        this.verificationService = verificationService;
        this.passwordResetService = passwordResetService;
        this.employeeRepository = employeeRepository;
        this.companyRepository = companyRepository;
    }

    @GetMapping("/verify-email")
    public ResponseEntity<Map<String, String>> verifyEmail(@RequestParam(required = false) String token) {
        return ResponseEntity.ok(Map.of("status", verificationService.verify(token)));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> requestPasswordReset(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestReset(request.identifier());
        return ResponseEntity.accepted().body(Map.of(
                "message", "If an account matches that identifier, a reset link will be sent."));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.token(), request.password());
        return ResponseEntity.ok(Map.of("message", "Password reset successfully."));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.login(request, httpRequest));
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        Object response = registrationService.register(request, httpRequest);
        if (response instanceof LoginResponse loginResponse) {
            return ResponseEntity.status(201).body(loginResponse);
        }
        return ResponseEntity.status(201).body(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.refresh(request.getRefreshToken(), httpRequest));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserDTO> me(@AuthenticationPrincipal CustomUserPrincipal principal) {
        UserDTO dto = UserDTO.from(principal.getUser(), principal.getPermissionGrants());
        employeeRepository.findByUser_UsernameAndDeletedFalse(principal.getUsername())
            .or(() -> employeeRepository.findByEmailIgnoreCaseAndDeletedFalse(principal.getUser().getEmail()))
                .ifPresent(employee -> dto.setEmployeeId(employee.getId()));
        if (dto.getCompanyName() == null && dto.getCompanyId() != null) {
            companyRepository.findById(dto.getCompanyId()).ifPresent(company -> dto.setCompanyName(company.getName()));
        }
        if (mfaService != null) {
            mfaService.applyTo(dto, principal.getId());
        }
        if (legalAcceptanceService != null) {
            legalAcceptanceService.applyTo(dto, principal.getId());
        }
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/change-password")
    public ResponseEntity<Map<String, Object>> changePassword(@AuthenticationPrincipal CustomUserPrincipal principal,
                                                                @Valid @RequestBody ChangePasswordRequest request,
                                                                HttpServletRequest httpRequest) {
        authService.changePassword(principal.getId(), request.getCurrentPassword(), request.getNewPassword());
        // Old sessions are revoked; give this (already two-factor verified) session a fresh token pair.
        LoginResponse session = authService.issueSessionAfterPasswordChange(principal.getId(), httpRequest);
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("message", "Password changed successfully");
        if (session != null) {
            body.put("accessToken", session.getAccessToken());
            body.put("refreshToken", session.getRefreshToken());
            body.put("tokenType", session.getTokenType());
            body.put("user", session.getUser());
        }
        return ResponseEntity.ok(body);
    }
}
