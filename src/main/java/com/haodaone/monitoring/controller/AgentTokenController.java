package com.haodaone.monitoring.controller;

import com.haodaone.monitoring.dto.AgentTokenOtpDTO;
import com.haodaone.monitoring.service.OtpService;
import com.haodaone.security.CustomUserPrincipal;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/monitoring/devices/{deviceId}/token")
public class AgentTokenController {
    private final OtpService otpService;
    public AgentTokenController(OtpService otpService) { this.otpService = otpService; }

    @PostMapping("/request-otp")
    @PreAuthorize("hasAuthority('MONITORING_MANAGE') and (@companySecurity.canManageDevice(#deviceId) or @companySecurity.isSuperAdmin())")
    public AgentTokenOtpDTO.RequestResponse requestOtp(@PathVariable Long deviceId, @AuthenticationPrincipal CustomUserPrincipal principal) {
        return otpService.requestOtp(deviceId, principal.getUser());
    }

    @PostMapping("/confirm")
    @PreAuthorize("hasAuthority('MONITORING_MANAGE') and (@companySecurity.canManageDevice(#deviceId) or @companySecurity.isSuperAdmin())")
    public AgentTokenOtpDTO.ConfirmResponse confirm(@PathVariable Long deviceId, @Valid @RequestBody AgentTokenOtpDTO.ConfirmRequest request,
                                                     @AuthenticationPrincipal CustomUserPrincipal principal, HttpServletRequest httpRequest) {
        return otpService.confirm(deviceId, request, principal.getUser(), httpRequest.getRemoteAddr());
    }

    @GetMapping("/history")
    @PreAuthorize("hasAuthority('MONITORING_MANAGE') and (@companySecurity.canManageDevice(#deviceId) or @companySecurity.isSuperAdmin())")
    public List<AgentTokenOtpDTO.RotationHistoryResponse> history(@PathVariable Long deviceId, @AuthenticationPrincipal CustomUserPrincipal principal) {
        return otpService.history(deviceId, principal.getUser());
    }
}