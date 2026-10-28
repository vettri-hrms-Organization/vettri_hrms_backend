package com.haodaone.billing.controller;

import com.haodaone.billing.dto.BillingDtos.*;
import com.haodaone.billing.service.BillingAccountService;
import com.haodaone.billing.service.BillingLifecycleService;
import com.haodaone.billing.service.RenewalPaymentService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.security.CustomUserPrincipal;
import com.haodaone.tenant.TenantContext;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Company-side subscription billing. Status is readable by every signed-in
 * user (global banner); details by COMPANY_ADMIN / HR_ADMIN / SUPER_ADMIN;
 * anything that pays or changes the subscription is COMPANY_ADMIN only.
 * All of /api/billing/** stays writable while the company is read-only.
 */
@RestController
@RequestMapping("/api/billing")
public class SubscriptionBillingController {

    private final BillingAccountService accounts;
    private final BillingLifecycleService lifecycle;
    private final RenewalPaymentService renewals;

    public SubscriptionBillingController(BillingAccountService accounts, BillingLifecycleService lifecycle, RenewalPaymentService renewals) {
        this.accounts = accounts;
        this.lifecycle = lifecycle;
        this.renewals = renewals;
    }

    @GetMapping("/status")
    public BillingStatusResponse status(@AuthenticationPrincipal CustomUserPrincipal principal) {
        Long tenant = TenantContext.getCurrentTenant();
        return accounts.status(tenant != null ? tenant : principal.getCompanyId(), principal.getRoleNames());
    }

    @GetMapping("/subscription")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_ADMIN', 'SUPER_ADMIN')")
    public SubscriptionOverview overview(@AuthenticationPrincipal CustomUserPrincipal principal) {
        return accounts.overview(companyId(principal));
    }

    @PostMapping("/subscription/pay-now")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public Map<String, Object> payNow(@AuthenticationPrincipal CustomUserPrincipal principal) {
        return renewals.createRenewalOrder(ownCompanyId(principal), principal.getId());
    }

    @PostMapping("/subscription/verify")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public Map<String, Object> verify(@AuthenticationPrincipal CustomUserPrincipal principal,
                                      @Valid @RequestBody RenewalVerifyRequest request) {
        return renewals.verifyRenewalPayment(ownCompanyId(principal), principal.getId(),
                request.razorpayOrderId(), request.razorpayPaymentId(), request.razorpaySignature());
    }

    @PostMapping("/subscription/change-plan")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public Map<String, String> changePlan(@AuthenticationPrincipal CustomUserPrincipal principal,
                                          @Valid @RequestBody ChangePlanRequest request) {
        var sub = accounts.subscription(ownCompanyId(principal));
        return Map.of("message", lifecycle.changePlan(sub, request.plan(), request.billingCycle(), BillingLifecycleService.today()));
    }

    @PostMapping("/subscription/cancel")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public Map<String, String> cancel(@AuthenticationPrincipal CustomUserPrincipal principal) {
        var sub = accounts.subscription(ownCompanyId(principal));
        lifecycle.setCancelAtPeriodEnd(sub, true);
        return Map.of("message", "Your subscription will end on " + sub.getRenewalDate() + ".");
    }

    @PostMapping("/subscription/resume")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public Map<String, String> resume(@AuthenticationPrincipal CustomUserPrincipal principal) {
        lifecycle.setCancelAtPeriodEnd(accounts.subscription(ownCompanyId(principal)), false);
        return Map.of("message", "Your subscription will renew as usual.");
    }

    @PostMapping("/subscription/auto-debit")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public Map<String, Object> startAutoDebit(@AuthenticationPrincipal CustomUserPrincipal principal) {
        return renewals.startAutoDebit(ownCompanyId(principal));
    }

    @DeleteMapping("/subscription/auto-debit")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public Map<String, String> cancelAutoDebit(@AuthenticationPrincipal CustomUserPrincipal principal) {
        renewals.cancelAutoDebit(ownCompanyId(principal));
        return Map.of("message", "Auto-debit cancelled. Renewals can be paid with Pay now.");
    }

    @GetMapping("/profile")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_ADMIN', 'SUPER_ADMIN')")
    public BillingProfile profile(@AuthenticationPrincipal CustomUserPrincipal principal) {
        return accounts.profile(companyId(principal));
    }

    @PutMapping("/profile")
    @PreAuthorize("hasRole('COMPANY_ADMIN')")
    public BillingProfile updateProfile(@AuthenticationPrincipal CustomUserPrincipal principal,
                                        @Valid @RequestBody BillingProfileRequest request) {
        return accounts.updateProfile(ownCompanyId(principal), request);
    }

    /** Tenant selected for the request (SUPER_ADMIN may pick one via X-Company-Id), else the user's company. */
    private static Long companyId(CustomUserPrincipal principal) {
        Long tenant = TenantContext.getCurrentTenant();
        Long companyId = tenant != null ? tenant : principal.getCompanyId();
        if (companyId == null) throw new BadRequestException("Select a company to view billing.");
        return companyId;
    }

    /** Payments and changes always act on the admin's own company. */
    private static Long ownCompanyId(CustomUserPrincipal principal) {
        if (principal.getCompanyId() == null) throw new BadRequestException("Your account is not linked to a company.");
        return principal.getCompanyId();
    }
}
