package com.haodaone.audit.controller;

import com.haodaone.audit.dto.AuditLogDTO;
import com.haodaone.audit.entity.LoginHistory;
import com.haodaone.audit.repository.AuditLogRepository;
import com.haodaone.audit.repository.LoginHistoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.tenant.TenantContext;

@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditLogRepository auditLogRepository;
    private final LoginHistoryRepository loginHistoryRepository;

    public AuditController(AuditLogRepository auditLogRepository, LoginHistoryRepository loginHistoryRepository) {
        this.auditLogRepository = auditLogRepository;
        this.loginHistoryRepository = loginHistoryRepository;
    }

    @GetMapping("/logs")
    @PreAuthorize("hasAuthority('AUDIT_VIEW')")
    public Page<AuditLogDTO> logs(@RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "50") int size,
                                @RequestParam(required = false) String entityName) {
        PageRequest pageable = PageRequest.of(page, size);
        Long companyId = TenantContext.getCurrentTenant();
        if (companyId == null) throw new BadRequestException("Company context is required");
        Page<com.haodaone.audit.entity.AuditLog> logs = entityName != null
            ? auditLogRepository.findByCompany_IdAndEntityNameOrderByPerformedAtDesc(companyId, entityName, pageable)
            : auditLogRepository.findByCompany_IdOrderByPerformedAtDesc(companyId, pageable);
        return logs.map(AuditLogDTO::from);
    }

    @GetMapping("/login-history")
    @PreAuthorize("hasAuthority('AUDIT_VIEW')")
    public Page<LoginHistory> loginHistory(@RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "50") int size) {
        return loginHistoryRepository.findAllByOrderByAttemptedAtDesc(PageRequest.of(page, size));
    }
}
