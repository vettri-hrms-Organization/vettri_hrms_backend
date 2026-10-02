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

    public AuthController(AuthService authService, RegistrationService registrationService,
                          VerificationService verificationService, PasswordResetService passwordResetService,
                          EmployeeRepository employeeRepository) {
        this.authService = authService;
        this.registrationService = registrationService;
        this.verificationService = verificationService;
        this.passwordResetService = passwordResetService;
        this.employeeRepository = employeeRepository;
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
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(@AuthenticationPrincipal CustomUserPrincipal principal,
                                                                @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(principal.getId(), request.getCurrentPassword(), request.getNewPassword());
        return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
    }
}
