package com.haodaone.auth.controller;

import com.haodaone.auth.dto.ChangePasswordRequest;
import com.haodaone.auth.dto.LoginRequest;
import com.haodaone.auth.dto.LoginResponse;
import com.haodaone.auth.dto.RefreshRequest;
import com.haodaone.auth.service.AuthService;
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
    private final EmployeeRepository employeeRepository;

    public AuthController(AuthService authService, EmployeeRepository employeeRepository) {
        this.authService = authService;
        this.employeeRepository = employeeRepository;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.login(request, httpRequest));
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
        UserDTO dto = UserDTO.from(principal.getUser());
        employeeRepository.findByUser_UsernameAndDeletedFalse(principal.getUsername())
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
