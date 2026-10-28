package com.haodaone.billing.enforcement;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haodaone.billing.service.BillingLifecycleService;
import com.haodaone.common.dto.ApiError;
import com.haodaone.company.entity.Subscription;
import com.haodaone.company.entity.SubscriptionStatus;
import com.haodaone.company.repository.SubscriptionRepository;
import com.haodaone.security.CustomUserPrincipal;
import com.haodaone.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Plan enforcement: a company whose subscription is SUSPENDED, EXPIRED
 * (trial ended unpaid) or CANCELLED is read-only. Every non-GET API call by
 * its users gets HTTP 402 with code SUBSCRIPTION_INACTIVE, except
 * authentication / logout / password / 2FA (/api/auth/**), billing
 * (/api/billing/**), notifications (mark read) and support. SUPER_ADMIN is
 * exempt. Device / agent ingestion (agent token principals, /api/agent/**,
 * /agent/**, /iclock/**) is not a user action and is never blocked, so no
 * attendance or monitoring data is lost while a company is suspended.
 *
 * Runs as a Spring MVC interceptor, i.e. after the security filter chain has
 * authenticated the user and TenantResolverFilter has set the tenant.
 */
@Component
public class SubscriptionEnforcementInterceptor implements HandlerInterceptor {

    public static final String CODE = "SUBSCRIPTION_INACTIVE";
    public static final int PAYMENT_REQUIRED = 402;
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private static final List<String> EXEMPT_PREFIXES = List.of(
            "/api/auth/", "/api/billing/", "/api/admin/", "/api/notifications", "/api/support",
            "/api/agent/", "/agent/", "/iclock/", "/api/device-enrollments/", "/actuator/");

    private final SubscriptionRepository subscriptions;
    private com.haodaone.company.repository.CompanyRepository companyRepository;

    /** Demo workspaces are exempt from enforcement. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setCompanyRepository(com.haodaone.company.repository.CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }
    private final ObjectMapper objectMapper;

    public SubscriptionEnforcementInterceptor(SubscriptionRepository subscriptions, ObjectMapper objectMapper) {
        this.subscriptions = subscriptions;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) throws Exception {
        if (SAFE_METHODS.contains(request.getMethod().toUpperCase())) return true;
        String path = request.getRequestURI();
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && path.startsWith(context)) path = path.substring(context.length());
        if (isExempt(path)) return true;

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserPrincipal principal)) return true;
        if (principal.getRoleNames().contains("SUPER_ADMIN")) return true;

        Long companyId = TenantContext.getCurrentTenant() != null ? TenantContext.getCurrentTenant() : principal.getCompanyId();
        if (companyId == null) return true;
        Optional<Subscription> subscription = subscriptions.findByCompany_IdAndDeletedFalse(companyId);
        if (subscription.isEmpty() || !BillingLifecycleService.isReadOnly(subscription.get().getStatus())) return true;
        if (companyRepository != null && companyRepository.findById(companyId).map(c -> c.isDemo()).orElse(false)) return true;

        SubscriptionStatus status = subscription.get().getStatus();
        String message = status == SubscriptionStatus.EXPIRED
                ? "Your free trial has ended, so this workspace is read-only. A company admin can pay under Billing to restore full access."
                : status == SubscriptionStatus.CANCELLED
                ? "This subscription is cancelled, so the workspace is read-only. A company admin can renew under Billing."
                : "Your subscription is suspended for non-payment, so the workspace is read-only. A company admin can pay under Billing to restore full access.";
        ApiError error = new ApiError(PAYMENT_REQUIRED, "Payment Required", message, path);
        error.setCode(CODE);
        response.setStatus(PAYMENT_REQUIRED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(error));
        return false;
    }

    static boolean isExempt(String path) {
        if (path == null) return true;
        for (String prefix : EXEMPT_PREFIXES) {
            if (path.startsWith(prefix)) return true;
        }
        return false;
    }
}
