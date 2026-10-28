package com.haodaone.privacy.controller;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.privacy.dto.PrivacyDTO;
import com.haodaone.privacy.service.CompanyDataExportService;
import com.haodaone.privacy.service.CompanyDeletionService;
import com.haodaone.privacy.service.DataPrincipalRequestService;
import com.haodaone.privacy.service.LegalAcceptanceService;
import com.haodaone.privacy.service.MonitoringConsentService;
import com.haodaone.privacy.service.PrivacySettingsService;
import com.haodaone.security.CustomUserPrincipal;
import com.haodaone.tenant.TenantContext;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * HR / admin privacy tools. PRIVACY_MANAGE (HR_ADMIN, COMPANY_ADMIN):
 * settings, request queue, erasure, consent and acceptance registers.
 * COMPANY_ADMIN only: full company export and account deletion.
 * SUPER_ADMIN: cross-company deletion overview.
 */
@RestController
@RequestMapping("/api/privacy/admin")
public class PrivacyAdminController {

    private final PrivacySettingsService settingsService;
    private final DataPrincipalRequestService requestService;
    private final MonitoringConsentService consentService;
    private final LegalAcceptanceService acceptanceService;
    private final CompanyDataExportService companyExportService;
    private final CompanyDeletionService deletionService;
    private final AuditLogService auditLogService;

    public PrivacyAdminController(PrivacySettingsService settingsService, DataPrincipalRequestService requestService,
                                  MonitoringConsentService consentService, LegalAcceptanceService acceptanceService,
                                  CompanyDataExportService companyExportService, CompanyDeletionService deletionService,
                                  AuditLogService auditLogService) {
        this.settingsService = settingsService;
        this.requestService = requestService;
        this.consentService = consentService;
        this.acceptanceService = acceptanceService;
        this.companyExportService = companyExportService;
        this.deletionService = deletionService;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/settings")
    @PreAuthorize("hasAuthority('PRIVACY_MANAGE')")
    public PrivacyDTO.PrivacySettingsView settings() {
        return settingsService.get();
    }

    @PutMapping("/settings")
    @PreAuthorize("hasAuthority('PRIVACY_MANAGE')")
    public PrivacyDTO.PrivacySettingsView updateSettings(@Valid @RequestBody PrivacyDTO.PrivacySettingsRequest request) {
        return settingsService.update(request);
    }

    @GetMapping("/requests")
    @PreAuthorize("hasAuthority('PRIVACY_MANAGE')")
    public List<PrivacyDTO.DataRequestView> requests() {
        return requestService.companyQueue();
    }

    @PatchMapping("/requests/{id}")
    @PreAuthorize("hasAuthority('PRIVACY_MANAGE')")
    public PrivacyDTO.DataRequestView updateRequest(@AuthenticationPrincipal CustomUserPrincipal principal,
                                                    @PathVariable Long id,
                                                    @Valid @RequestBody PrivacyDTO.DataRequestUpdate body) {
        return requestService.update(id, principal.getId(), body);
    }

    @GetMapping("/requests/{id}/erasure-eligibility")
    @PreAuthorize("hasAuthority('PRIVACY_MANAGE')")
    public PrivacyDTO.ErasureEligibility erasureEligibility(@PathVariable Long id) {
        return requestService.erasureEligibility(id);
    }

    @PostMapping("/requests/{id}/erase")
    @PreAuthorize("hasAuthority('PRIVACY_MANAGE')")
    public PrivacyDTO.ErasureResult erase(@AuthenticationPrincipal CustomUserPrincipal principal, @PathVariable Long id) {
        return requestService.erase(id, principal.getId());
    }

    @GetMapping("/consents")
    @PreAuthorize("hasAuthority('PRIVACY_MANAGE')")
    public List<PrivacyDTO.ConsentView> consents() {
        return consentService.companyConsents();
    }

    @GetMapping("/acceptances")
    @PreAuthorize("hasAuthority('PRIVACY_MANAGE')")
    public List<PrivacyDTO.AcceptanceView> acceptances() {
        return acceptanceService.companyAcceptances(TenantContext.getCurrentTenant());
    }

    /** ZIP of CSVs (one per table) for the whole company. Not transactional on purpose (see CompanyDataExportService). */
    @GetMapping("/company-export")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public ResponseEntity<byte[]> companyExport() {
        Long companyId = TenantContext.getCurrentTenant();
        if (companyId == null) {
            throw new com.haodaone.common.exception.BadRequestException("Company context is required");
        }
        byte[] zip = companyExportService.buildZip(companyId);
        auditLogService.log("Company", companyId, "DATA_EXPORT", "Full company data export downloaded");
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"vettri-company-export-" + LocalDate.now() + ".zip\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(zip);
    }

    @GetMapping("/account-deletion")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public ResponseEntity<PrivacyDTO.DeletionRequestView> deletionStatus() {
        PrivacyDTO.DeletionRequestView current = deletionService.current();
        return current == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(current);
    }

    @PostMapping("/account-deletion")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public PrivacyDTO.DeletionRequestView requestDeletion(@AuthenticationPrincipal CustomUserPrincipal principal,
                                                          @Valid @RequestBody PrivacyDTO.DeletionRequestCreate body) {
        return deletionService.request(principal.getId(), body);
    }

    @DeleteMapping("/account-deletion")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public PrivacyDTO.DeletionRequestView cancelDeletion(@AuthenticationPrincipal CustomUserPrincipal principal) {
        return deletionService.cancel(principal.getId());
    }

    @GetMapping("/account-deletions/all")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public List<PrivacyDTO.DeletionRequestView> allDeletions() {
        return deletionService.all();
    }
}
