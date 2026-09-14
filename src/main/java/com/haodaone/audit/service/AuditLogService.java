package com.haodaone.audit.service;

import com.haodaone.audit.entity.AuditLog;
import com.haodaone.audit.repository.AuditLogRepository;
import com.haodaone.tenant.TenantContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Deliberately explicit rather than AOP-based: every module's service
 * layer calls auditLogService.log(...) directly at the point of change.
 * This is more code per call site, but it means the audit entry can
 * include a real, specific "what changed" message instead of a generic
 * "method X was called" - and it's trivial to trace from any service
 * method straight to what gets recorded.
 */
@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @PersistenceContext
    private EntityManager entityManager;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void log(String entityName, Long entityId, String action, String details) {
        AuditLog entry = new AuditLog();
        entry.setEntityName(entityName);
        entry.setEntityId(entityId);
        Long companyId = TenantContext.getCurrentTenant();
        if (companyId != null) {
            entry.setCompany(entityManager.getReference(com.haodaone.company.entity.Company.class, companyId));
        }
        entry.setAction(action);
        entry.setDetails(details);
        entry.setPerformedBy(currentUsername());
        entry.setIpAddress(currentIpAddress());
        auditLogRepository.save(entry);
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return "system";
        }
        return authentication.getName();
    }

    private String currentIpAddress() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        return attrs.getRequest().getRemoteAddr();
    }
}
