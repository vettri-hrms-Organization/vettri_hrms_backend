package com.haodaone.supportaccess;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.tenant.TenantContext;
import com.haodaone.user.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Customer-controlled support access. Vettri platform staff (SUPER_ADMIN) only see business data
 * (plans, payments, counts) by default; opening a company's HR data needs an active grant from
 * that company's admin. Demo workspaces (Vettri's own sales demos) are always open to staff.
 * Grants, revokes and every support session are written to the company's own audit log.
 */
@Service
public class SupportAccessService {

    static final int MAX_HOURS = 72;

    private final SupportAccessGrantRepository grants;
    private final CompanyRepository companies;
    private final UserRepository users;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public SupportAccessService(SupportAccessGrantRepository grants, CompanyRepository companies, UserRepository users,
                                AuditLogService auditLogService, Clock applicationClock) {
        this.grants = grants;
        this.companies = companies;
        this.users = users;
        this.auditLogService = auditLogService;
        this.clock = applicationClock;
    }

    public record Status(boolean active, String accessLevel, LocalDateTime expiresAt, String grantedByName,
                         LocalDateTime grantedAt, String reason) {
        static Status none() { return new Status(false, null, null, null, null, null); }
        static Status of(SupportAccessGrant grant) {
            return new Status(true, grant.getAccessLevel(), grant.getExpiresAt(), grant.getGrantedByName(), grant.getCreatedAt(), grant.getReason());
        }
    }

    public record AccessibleCompany(Long companyId, String name, boolean demo, String accessLevel, LocalDateTime expiresAt) { }

    @Transactional(readOnly = true)
    public Status status(Long companyId) {
        if (companyId == null) return Status.none();
        return grants.findFirstByCompany_IdAndRevokedAtIsNullAndExpiresAtAfterAndDeletedFalseOrderByExpiresAtDesc(companyId, now())
                .map(Status::of).orElse(Status.none());
    }

    /** Replaces any current grant for the company with a new one. */
    @Transactional
    public Status grant(Long companyId, Long userId, int hours, String accessLevel, String reason) {
        if (companyId == null) throw new BadRequestException("Company context is required");
        if (hours < 1 || hours > MAX_HOURS) throw new BadRequestException("Support access can last between 1 and " + MAX_HOURS + " hours");
        String level = accessLevel == null ? SupportAccessGrant.READ_ONLY : accessLevel.trim().toUpperCase(Locale.ROOT);
        if (!SupportAccessGrant.READ_ONLY.equals(level) && !SupportAccessGrant.FULL.equals(level)) {
            throw new BadRequestException("Access level must be READ_ONLY or FULL");
        }
        Company company = companies.findById(companyId).orElseThrow(() -> new BadRequestException("Company not found"));
        String grantedBy = userName(userId);
        revokeActive(companyId, grantedBy);

        SupportAccessGrant grant = new SupportAccessGrant();
        grant.setCompany(company);
        grant.setGrantedByUserId(userId);
        grant.setGrantedByName(grantedBy);
        grant.setAccessLevel(level);
        grant.setReason(reason == null || reason.isBlank() ? null : reason.trim());
        grant.setExpiresAt(now().plusHours(hours));
        SupportAccessGrant saved = grants.save(grant);
        auditLogService.log("SupportAccessGrant", saved.getId(), "SUPPORT_ACCESS_GRANTED",
                "Vettri support access granted by " + grantedBy + " (" + level + ") until " + saved.getExpiresAt()
                        + (saved.getReason() == null ? "" : "; reason: " + saved.getReason()));
        return Status.of(saved);
    }

    @Transactional
    public Status revoke(Long companyId, Long userId) {
        if (companyId == null) throw new BadRequestException("Company context is required");
        String by = userName(userId);
        Long revokedId = revokeActive(companyId, by);
        if (revokedId != null) {
            auditLogService.log("SupportAccessGrant", revokedId, "SUPPORT_ACCESS_REVOKED", "Vettri support access revoked by " + by);
        }
        return Status.none();
    }

    /** What the SUPER_ADMIN company switcher may offer: companies with an active grant, plus demo workspaces. */
    @Transactional(readOnly = true)
    public List<AccessibleCompany> accessibleCompanies() {
        Map<Long, AccessibleCompany> result = new LinkedHashMap<>();
        for (SupportAccessGrant grant : grants.findAllByRevokedAtIsNullAndExpiresAtAfterAndDeletedFalseOrderByExpiresAtAsc(now())) {
            Company company = grant.getCompany();
            if (company == null || company.isDeleted()) continue;
            result.putIfAbsent(company.getId(), new AccessibleCompany(company.getId(), company.getName(), company.isDemo(), grant.getAccessLevel(), grant.getExpiresAt()));
        }
        for (Company company : companies.findAll()) {
            if (company.isDemo() && !company.isDeleted()) {
                result.putIfAbsent(company.getId(), new AccessibleCompany(company.getId(), company.getName(), true, SupportAccessGrant.FULL, null));
            }
        }
        return new ArrayList<>(result.values());
    }

    /** Records that a platform admin opened the company; refused without a grant. */
    @Transactional
    public AccessibleCompany openSession(Long companyId, Long userId) {
        AccessibleCompany access = accessibleCompanies().stream().filter(item -> item.companyId().equals(companyId)).findFirst()
                .orElseThrow(() -> new AccessDeniedException("This company has not granted Vettri support access."));
        Long previous = TenantContext.getCurrentTenant();
        try {
            TenantContext.setCurrentTenant(companyId); // write the entry into the customer's own audit log
            auditLogService.log("Company", companyId, "SUPPORT_SESSION_OPENED",
                    "Vettri platform admin " + userName(userId) + " opened this workspace"
                            + (access.demo() ? " (demo workspace)" : " with " + access.accessLevel() + " support access until " + access.expiresAt()));
        } finally {
            if (previous == null) TenantContext.clear(); else TenantContext.setCurrentTenant(previous);
        }
        return access;
    }

    /**
     * Gate used by TenantResolverFilter for a SUPER_ADMIN selecting a company.
     * @return null when allowed, otherwise the refusal message
     */
    @Transactional(readOnly = true)
    public String refusalFor(Long companyId, String httpMethod) {
        Company company = companies.findById(companyId).orElse(null);
        if (company == null) return "Selected company does not exist";
        if (company.isDemo()) return null;
        SupportAccessGrant grant = grants
                .findFirstByCompany_IdAndRevokedAtIsNullAndExpiresAtAfterAndDeletedFalseOrderByExpiresAtDesc(companyId, now())
                .orElse(null);
        if (grant == null) return "This company has not granted Vettri support access. Ask its admin to allow it under Settings > Privacy & Data.";
        boolean read = httpMethod == null || List.of("GET", "HEAD", "OPTIONS").contains(httpMethod.toUpperCase(Locale.ROOT));
        if (grant.isReadOnly() && !read) return "Support access for this company is read-only.";
        return null;
    }

    /** Revokes the company's active grants; returns the id of one revoked grant (for the audit entry), or null. */
    private Long revokeActive(Long companyId, String by) {
        Long revoked = null;
        LocalDateTime now = now();
        for (SupportAccessGrant existing : grants.findAllByCompany_IdAndRevokedAtIsNullAndDeletedFalse(companyId)) {
            if (!existing.isActiveAt(now)) continue;
            existing.setRevokedAt(now);
            existing.setRevokedByName(by);
            grants.save(existing);
            revoked = existing.getId();
        }
        return revoked;
    }

    private String userName(Long userId) {
        if (userId == null) return "an administrator";
        return users.findById(userId).map(user -> user.getFullName() != null ? user.getFullName() : user.getUsername()).orElse("an administrator");
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
