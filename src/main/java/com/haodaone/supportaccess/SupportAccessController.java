package com.haodaone.supportaccess;

import com.haodaone.security.CustomUserPrincipal;
import com.haodaone.tenant.TenantContext;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Company side: everyone sees whether Vettri support currently has access; admins grant and revoke it. */
@RestController
@Validated
public class SupportAccessController {

    private final SupportAccessService service;
    private final PlatformOverviewService overview;

    public SupportAccessController(SupportAccessService service, PlatformOverviewService overview) {
        this.service = service;
        this.overview = overview;
    }

    /** Platform dashboard: plans, payments, validity and usage counts per company - no employee data. */
    @GetMapping("/api/admin/platform-overview")
    @PreAuthorize("@companySecurity.isSuperAdmin()")
    public PlatformOverviewService.Overview platformOverview() {
        return overview.overview();
    }

    public record GrantRequest(@Min(1) @Max(SupportAccessService.MAX_HOURS) int hours, String accessLevel, @Size(max = 500) String reason) { }

    /** Banner data for every signed-in user of the company. */
    @GetMapping("/api/support-access/status")
    @PreAuthorize("isAuthenticated()")
    public SupportAccessService.Status status() {
        return service.status(TenantContext.getCurrentTenant());
    }

    @PostMapping("/api/support-access")
    @PreAuthorize("hasRole('COMPANY_ADMIN') or @authorizationService.hasOrganizationScope('PRIVACY_MANAGE')")
    public SupportAccessService.Status grant(@RequestBody @Validated GrantRequest request, @AuthenticationPrincipal CustomUserPrincipal principal) {
        requireCompanyUser(principal);
        return service.grant(TenantContext.getCurrentTenant(), principal.getId(), request.hours(), request.accessLevel(), request.reason());
    }

    @DeleteMapping("/api/support-access")
    @PreAuthorize("hasRole('COMPANY_ADMIN') or @authorizationService.hasOrganizationScope('PRIVACY_MANAGE')")
    public SupportAccessService.Status revoke(@AuthenticationPrincipal CustomUserPrincipal principal) {
        requireCompanyUser(principal);
        return service.revoke(TenantContext.getCurrentTenant(), principal.getId());
    }

    /** Platform staff working inside a company must never grant or extend their own access. */
    private static void requireCompanyUser(CustomUserPrincipal principal) {
        if (principal == null || principal.getRoleNames().contains("SUPER_ADMIN")) {
            throw new org.springframework.security.access.AccessDeniedException("Only the company's own admins can change Vettri support access.");
        }
    }

    /** Platform side: the companies a SUPER_ADMIN may open right now (active grants + demo workspaces). */
    @GetMapping("/api/admin/support-access/companies")
    @PreAuthorize("@companySecurity.isSuperAdmin()")
    public List<SupportAccessService.AccessibleCompany> accessibleCompanies() {
        return service.accessibleCompanies();
    }

    /** Called when a SUPER_ADMIN switches into a company: checks the grant and writes the company's audit log. */
    @PostMapping("/api/admin/support-access/{companyId}/open")
    @PreAuthorize("@companySecurity.isSuperAdmin()")
    public SupportAccessService.AccessibleCompany open(@PathVariable Long companyId, @AuthenticationPrincipal CustomUserPrincipal principal) {
        return service.openSession(companyId, principal.getId());
    }
}
