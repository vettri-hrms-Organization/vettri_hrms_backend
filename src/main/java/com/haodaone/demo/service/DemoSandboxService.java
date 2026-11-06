package com.haodaone.demo.service;

import com.haodaone.auth.dto.LoginResponse;
import com.haodaone.auth.service.AuthService;
import com.haodaone.billing.service.BillingLifecycleService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.company.entity.Company;
import com.haodaone.company.entity.Subscription;
import com.haodaone.company.repository.SubscriptionRepository;
import com.haodaone.security.CustomUserPrincipal;
import com.haodaone.user.entity.User;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Customer self-service demo workspace. A Company Admin / HR Admin of a real company can open one
 * sandbox company (is_demo, linked by demo_parent_company_id) seeded by DemoCompanyService. The real
 * company never receives demo rows, so demo payroll, attendance and reports cannot mix with real data.
 *
 * Demo logins are generated per sandbox (Admin, HR, Manager, Employee) with random passwords nobody
 * is told; the explorer switches between them through {@link #openSession}, which signs in through
 * AuthService like any login. The sandbox expires with the customer's trial (14 days for companies
 * that are already paying) and {@link #cleanupExpired} removes it once expired.
 */
@Service
public class DemoSandboxService {

    private static final Logger log = LoggerFactory.getLogger(DemoSandboxService.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    static final int PAID_COMPANY_DEMO_DAYS = 14;

    /** Role key the explorer picks -> role whose demo login is opened. */
    private static final Map<String, String> LOGIN_ROLES = new LinkedHashMap<>();
    static {
        LOGIN_ROLES.put("ADMIN", "COMPANY_ADMIN");
        LOGIN_ROLES.put("HR", "HR_ADMIN");
        LOGIN_ROLES.put("MANAGER", "MANAGER");
        LOGIN_ROLES.put("EMPLOYEE", "EMPLOYEE");
    }

    public record Status(String choice, String state, Long demoCompanyId, String demoCompanyName,
                         LocalDateTime expiresAt, long employees) { }

    public record Current(boolean demo, String companyName, LocalDateTime expiresAt, boolean expired,
                          String viewingAs, List<String> roles, boolean customerSandbox) { }

    @PersistenceContext
    private EntityManager em;

    private final DemoCompanyService demoCompanies;
    private final AuthService authService;
    private final SubscriptionRepository subscriptions;
    private final TransactionTemplate tx;
    private final Clock clock;

    public DemoSandboxService(DemoCompanyService demoCompanies, AuthService authService, SubscriptionRepository subscriptions,
                              TransactionTemplate tx, Clock applicationClock) {
        this.demoCompanies = demoCompanies;
        this.authService = authService;
        this.subscriptions = subscriptions;
        this.tx = tx;
        this.clock = applicationClock;
    }

    // ------------------------------------------------------------------ real company side

    @Transactional(readOnly = true)
    public Status status() {
        Company parent = realCompanyOfCaller();
        return statusFor(parent, latestSandbox(parent.getId()));
    }

    /** Opens the demo workspace; repeated calls return the same live sandbox instead of creating another. */
    @Transactional
    public Status create() {
        Company parent = realCompanyOfCaller();
        // Serialise concurrent clicks for the same company so only one sandbox is ever seeded.
        parent = em.find(Company.class, parent.getId(), LockModeType.PESSIMISTIC_WRITE);
        LocalDateTime now = LocalDateTime.now(clock.withZone(IST));
        LocalDateTime expiresAt = expiryFor(parent, now);
        if (!expiresAt.isAfter(now)) throw new BadRequestException("Your trial has ended, so a demo workspace can't be opened.");

        Company sandbox = latestSandbox(parent.getId());
        if (sandbox != null && isLive(sandbox, now)) {
            parent.setOnboardingChoice("DEMO");
            return statusFor(parent, sandbox);
        }
        sandbox = sandbox == null
                ? demoCompanies.createSandbox(parent, expiresAt)
                : demoCompanies.reseedSandbox(sandbox.getId(), expiresAt);
        parent.setOnboardingChoice("DEMO");
        return statusFor(parent, sandbox);
    }

    @Transactional
    public Status recordChoice(String choice) {
        String normalized = choice == null ? "" : choice.trim().toUpperCase(Locale.ROOT);
        if (!normalized.equals("SETUP") && !normalized.equals("DEMO")) throw new BadRequestException("Choice must be SETUP or DEMO");
        Company parent = em.find(Company.class, realCompanyOfCaller().getId());
        parent.setOnboardingChoice(normalized);
        return statusFor(parent, latestSandbox(parent.getId()));
    }

    // ------------------------------------------------------------------ both sides

    /** What the signed-in person is looking at: drives the Demo Mode banner. */
    @Transactional(readOnly = true)
    public Current current() {
        CustomUserPrincipal principal = principal();
        Company company = principal.getCompanyId() == null ? null : em.find(Company.class, principal.getCompanyId());
        if (company == null || !company.isDemo()) return new Current(false, null, null, false, null, List.of(), false);
        LocalDateTime now = LocalDateTime.now(clock.withZone(IST));
        String viewingAs = LOGIN_ROLES.entrySet().stream()
                .filter(entry -> principal.getRoleNames().contains(entry.getValue()) && !"EMPLOYEE".equals(entry.getKey()))
                .map(Map.Entry::getKey).findFirst().orElse("EMPLOYEE");
        return new Current(true, company.getName(), company.getDemoExpiresAt(), !isLive(company, now), viewingAs,
                List.copyOf(LOGIN_ROLES.keySet()), company.getDemoParentCompanyId() != null);
    }

    /**
     * Signs into the sandbox's login for one role. Allowed for an admin of the sandbox's real company
     * (entering the demo) or for someone already inside that demo company (switching role).
     */
    @Transactional
    public LoginResponse openSession(String roleKey, HttpServletRequest request) {
        String roleName = LOGIN_ROLES.get(roleKey == null ? "" : roleKey.trim().toUpperCase(Locale.ROOT));
        if (roleName == null) throw new BadRequestException("Role must be one of " + LOGIN_ROLES.keySet());
        CustomUserPrincipal principal = principal();
        Company callerCompany = principal.getCompanyId() == null ? null : em.find(Company.class, principal.getCompanyId());
        if (callerCompany == null) throw new BadRequestException("A company workspace is required");

        Company sandbox;
        if (callerCompany.isDemo()) {
            sandbox = callerCompany;
        } else {
            requireAdmin(principal);
            sandbox = latestSandbox(callerCompany.getId());
            if (sandbox == null) throw new ResourceNotFoundException("Open the demo workspace first");
        }
        if (!isLive(sandbox, LocalDateTime.now(clock.withZone(IST)))) {
            throw new BadRequestException("This demo workspace has ended. Subscribe to keep using Vettri HRMS.");
        }
        User target = loginFor(sandbox.getId(), roleName);
        return authService.issueDemoSession(target, principal.getUsername(), request);
    }

    // ------------------------------------------------------------------ cleanup

    /** Hourly job: removes every expired customer sandbox. Each runs in its own transaction; failures are kept and retried. */
    public int cleanupExpired() {
        LocalDateTime now = LocalDateTime.now(clock.withZone(IST));
        List<Long> due = tx.execute(status -> em.createQuery(
                        "SELECT c.id FROM Company c WHERE c.demo = true AND c.demoParentCompanyId IS NOT NULL "
                                + "AND c.demoCleanedAt IS NULL AND c.demoExpiresAt < :now AND c.deleted = false ORDER BY c.id", Long.class)
                .setParameter("now", now).getResultList());
        int cleaned = 0;
        for (Long id : due == null ? List.<Long>of() : due) {
            try {
                demoCompanies.cleanupSandbox(id);
                cleaned++;
            } catch (RuntimeException e) {
                log.warn("Demo workspace {} cleanup failed; will retry next run: {}", id, e.getMessage());
                String message = String.valueOf(e.getMessage());
                tx.executeWithoutResult(status -> {
                    Company company = em.find(Company.class, id);
                    if (company != null) company.setDemoCleanupError(message.length() > 500 ? message.substring(0, 500) : message);
                });
            }
        }
        return cleaned;
    }

    // ------------------------------------------------------------------ helpers

    LocalDateTime expiryFor(Company parent, LocalDateTime now) {
        Subscription subscription = subscriptions.findByCompany_IdAndDeletedFalse(parent.getId()).orElse(null);
        if (subscription != null && BillingLifecycleService.isTrial(subscription.getStatus()) && subscription.getRenewalDate() != null) {
            return subscription.getRenewalDate().atTime(LocalTime.of(23, 59, 59));
        }
        return now.plusDays(PAID_COMPANY_DEMO_DAYS);
    }

    private static boolean isLive(Company sandbox, LocalDateTime now) {
        return sandbox.getDemoCleanedAt() == null && (sandbox.getDemoExpiresAt() == null || sandbox.getDemoExpiresAt().isAfter(now));
    }

    private Status statusFor(Company parent, Company sandbox) {
        LocalDateTime now = LocalDateTime.now(clock.withZone(IST));
        if (sandbox == null) return new Status(parent.getOnboardingChoice(), "NONE", null, null, null, 0);
        long employees = em.createQuery("SELECT COUNT(e) FROM Employee e WHERE e.company.id = :c AND e.deleted = false", Long.class)
                .setParameter("c", sandbox.getId()).getSingleResult();
        return new Status(parent.getOnboardingChoice(), isLive(sandbox, now) ? "ACTIVE" : "EXPIRED",
                sandbox.getId(), sandbox.getName(), sandbox.getDemoExpiresAt(), employees);
    }

    private Company latestSandbox(Long parentId) {
        return em.createQuery("SELECT c FROM Company c WHERE c.demoParentCompanyId = :p AND c.demo = true AND c.deleted = false "
                        + "ORDER BY c.id DESC", Company.class)
                .setParameter("p", parentId).setMaxResults(1).getResultStream().findFirst().orElse(null);
    }

    private User loginFor(Long companyId, String roleName) {
        // The employee view uses someone who holds only the Employee role.
        String onlyEmployee = "EMPLOYEE".equals(roleName) ? " AND SIZE(u.roles) = 1" : "";
        return em.createQuery("SELECT u FROM User u JOIN u.roles r WHERE u.company.id = :c AND r.name = :role "
                        + "AND u.active = true AND u.deleted = false" + onlyEmployee + " ORDER BY u.id", User.class)
                .setParameter("c", companyId).setParameter("role", roleName).setMaxResults(1)
                .getResultStream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("This demo workspace has no " + roleName + " login"));
    }

    private Company realCompanyOfCaller() {
        CustomUserPrincipal principal = principal();
        requireAdmin(principal);
        Company company = principal.getCompanyId() == null ? null : em.find(Company.class, principal.getCompanyId());
        if (company == null || company.isDeleted()) throw new BadRequestException("A company workspace is required");
        if (company.isDemo()) throw new BadRequestException("You are already in a demo workspace");
        return company;
    }

    private static void requireAdmin(CustomUserPrincipal principal) {
        if (!principal.getRoleNames().contains("COMPANY_ADMIN") && !principal.getRoleNames().contains("HR_ADMIN")) {
            throw new org.springframework.security.access.AccessDeniedException("Only a Company Admin or HR Admin can open the demo workspace");
        }
    }

    private static CustomUserPrincipal principal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserPrincipal principal)) {
            throw new org.springframework.security.access.AccessDeniedException("Sign in first");
        }
        return principal;
    }
}
