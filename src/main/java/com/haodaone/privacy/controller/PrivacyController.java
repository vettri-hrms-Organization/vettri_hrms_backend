package com.haodaone.privacy.controller;

import com.haodaone.privacy.dto.PrivacyDTO;
import com.haodaone.privacy.service.DataPrincipalRequestService;
import com.haodaone.privacy.service.MonitoringConsentService;
import com.haodaone.privacy.service.PrivacySettingsService;
import com.haodaone.security.CustomUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Employee self-service privacy (any signed-in user; data is always the
 * caller's own): grievance contact, monitoring consent, rights requests.
 */
@RestController
@RequestMapping("/api/privacy")
@PreAuthorize("isAuthenticated()")
public class PrivacyController {

    private final PrivacySettingsService settingsService;
    private final MonitoringConsentService consentService;
    private final DataPrincipalRequestService requestService;

    public PrivacyController(PrivacySettingsService settingsService, MonitoringConsentService consentService,
                             DataPrincipalRequestService requestService) {
        this.settingsService = settingsService;
        this.consentService = consentService;
        this.requestService = requestService;
    }

    @GetMapping("/contact")
    public PrivacyDTO.PrivacyContact contact() {
        return settingsService.contact();
    }

    @GetMapping("/monitoring-notice")
    public PrivacyDTO.MonitoringNotice monitoringNotice() {
        return consentService.notice();
    }

    @GetMapping("/monitoring-consent")
    public PrivacyDTO.MonitoringConsentStatus consentStatus(@AuthenticationPrincipal CustomUserPrincipal principal) {
        return consentService.status(principal.getId());
    }

    @PostMapping("/monitoring-consent")
    public PrivacyDTO.MonitoringConsentStatus grant(@AuthenticationPrincipal CustomUserPrincipal principal,
                                                    HttpServletRequest httpRequest) {
        return consentService.grant(principal.getId(), httpRequest);
    }

    @PostMapping("/monitoring-consent/withdraw")
    public PrivacyDTO.MonitoringConsentStatus withdraw(@AuthenticationPrincipal CustomUserPrincipal principal,
                                                       @Valid @RequestBody(required = false) PrivacyDTO.WithdrawRequest body) {
        return consentService.withdraw(principal.getId(), body == null ? null : body.reason());
    }

    @GetMapping("/requests")
    public List<PrivacyDTO.DataRequestView> myRequests(@AuthenticationPrincipal CustomUserPrincipal principal) {
        return requestService.myRequests(principal.getId());
    }

    @PostMapping("/requests")
    public PrivacyDTO.DataRequestView create(@AuthenticationPrincipal CustomUserPrincipal principal,
                                             @Valid @RequestBody PrivacyDTO.DataRequestCreate body) {
        return requestService.create(principal.getId(), body);
    }

    @GetMapping("/requests/erasure-eligibility")
    public PrivacyDTO.ErasureEligibility erasureEligibility(@AuthenticationPrincipal CustomUserPrincipal principal) {
        return requestService.myErasureEligibility(principal.getId());
    }

    @GetMapping("/requests/{id}/download")
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal CustomUserPrincipal principal, @PathVariable Long id) {
        byte[] zip = requestService.download(principal.getId(), id);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"my-personal-data-" + id + ".zip\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(zip);
    }
}
