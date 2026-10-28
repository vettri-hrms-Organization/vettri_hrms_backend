package com.haodaone.notifications.whatsapp;

import com.haodaone.common.exception.BadRequestException;
import com.haodaone.security.CustomUserPrincipal;
import com.haodaone.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Self-service: GET/PUT /api/notifications/whatsapp/opt-in (any signed-in user, own record).
 * Admin (COMPANY_ADMIN / SUPER_ADMIN / USER_MANAGE organisation scope): settings + delivery log.
 */
@RestController
@RequestMapping("/api/notifications/whatsapp")
public class WhatsAppController {

    private static final String ADMIN_RULE =
            "hasRole('COMPANY_ADMIN') or hasRole('SUPER_ADMIN') or @authorizationService.hasOrganizationScope('USER_MANAGE')";

    private final WhatsAppService service;

    public WhatsAppController(WhatsAppService service) {
        this.service = service;
    }

    @GetMapping("/opt-in")
    @PreAuthorize("isAuthenticated()")
    public WhatsAppService.OptInView optIn(@AuthenticationPrincipal CustomUserPrincipal principal) {
        return service.optIn(principal.getId(), ownCompany(principal));
    }

    @PutMapping("/opt-in")
    @PreAuthorize("isAuthenticated()")
    public WhatsAppService.OptInView updateOptIn(@AuthenticationPrincipal CustomUserPrincipal principal,
                                                 @RequestBody WhatsAppService.OptInRequest request,
                                                 HttpServletRequest httpRequest) {
        return service.updateOptIn(principal.getId(), ownCompany(principal), request, httpRequest.getRemoteAddr());
    }

    @GetMapping("/settings")
    @PreAuthorize(ADMIN_RULE)
    public WhatsAppService.SettingsView settings() {
        return service.settings(tenant());
    }

    @PutMapping("/settings")
    @PreAuthorize(ADMIN_RULE)
    public WhatsAppService.SettingsView updateSettings(@RequestBody WhatsAppService.SettingsRequest request) {
        return service.updateSettings(tenant(), request);
    }

    @GetMapping("/deliveries")
    @PreAuthorize(ADMIN_RULE)
    public List<WhatsAppService.DeliveryView> deliveries() {
        return service.deliveries(tenant());
    }

    private static Long ownCompany(CustomUserPrincipal principal) {
        if (principal == null || principal.getCompanyId() == null) throw new BadRequestException("A company account is required");
        return principal.getCompanyId();
    }

    private static Long tenant() {
        Long companyId = TenantContext.getCurrentTenant();
        if (companyId == null) throw new BadRequestException("Select a company first");
        return companyId;
    }
}
