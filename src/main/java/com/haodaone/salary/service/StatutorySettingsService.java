package com.haodaone.salary.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.salary.dto.StatutorySettingsDTO;
import com.haodaone.salary.entity.StatutorySettings;
import com.haodaone.salary.repository.StatutorySettingsRepository;
import com.haodaone.salary.statutory.ProfessionalTaxSlabs;
import com.haodaone.security.AuthorizationService;
import com.haodaone.tenant.TenantContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read/update the company's statutory payroll settings (SALARY_MANAGE, organization scope). */
@Service
public class StatutorySettingsService {

    private final StatutorySettingsRepository repository;
    private final CompanyRepository companyRepository;
    private final StatutoryPayrollEngine engine;
    private final AuditLogService auditLogService;
    private final AuthorizationService authorizationService;

    public StatutorySettingsService(StatutorySettingsRepository repository, CompanyRepository companyRepository,
                                    StatutoryPayrollEngine engine, AuditLogService auditLogService,
                                    AuthorizationService authorizationService) {
        this.repository = repository;
        this.companyRepository = companyRepository;
        this.engine = engine;
        this.auditLogService = auditLogService;
        this.authorizationService = authorizationService;
    }

    @Transactional(readOnly = true)
    public StatutorySettingsDTO get() {
        requireManage();
        Long companyId = requiredTenant();
        var existing = repository.findByCompany_IdAndDeletedFalse(companyId);
        StatutorySettings settings = existing.orElseGet(StatutorySettings::new);
        return StatutorySettingsDTO.from(settings, existing.isPresent(), engine.resolvePtState(companyId, settings));
    }

    @Transactional
    public StatutorySettingsDTO update(StatutorySettingsDTO request) {
        requireManage();
        Long companyId = requiredTenant();
        if (request.getPtState() != null && !request.getPtState().isBlank()
                && ProfessionalTaxSlabs.table(request.getPtState()) == null) {
            throw new BadRequestException("Unknown professional tax state: " + request.getPtState());
        }
        if (request.isPtFixedOverride() && (request.getPtFixedAmount() == null || request.getPtFixedAmount().signum() < 0)) {
            throw new BadRequestException("Enter the fixed monthly professional tax amount");
        }
        if (request.getEpsRate().compareTo(request.getPfEmployerRate()) > 0) {
            throw new BadRequestException("EPS rate cannot exceed the employer PF rate");
        }
        StatutorySettings settings = repository.findByCompany_IdAndDeletedFalse(companyId).orElseGet(() -> {
            StatutorySettings created = new StatutorySettings();
            created.setCompany(companyRepository.findById(companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Company not found")));
            return created;
        });
        request.applyTo(settings);
        StatutorySettings saved = repository.save(settings);
        auditLogService.log("StatutorySettings", saved.getId(), "UPDATE",
                "Updated statutory payroll settings: PF " + (saved.isPfEnabled() ? "on" : "off")
                        + ", ESI " + (saved.isEsiEnabled() ? "on" : "off")
                        + ", PT " + (saved.isPtEnabled() ? (saved.isPtFixedOverride() ? "fixed " + saved.getPtFixedAmount() : String.valueOf(saved.getPtState())) : "off")
                        + ", TDS " + (saved.isTdsEnabled() ? "on" : "off"));
        return StatutorySettingsDTO.from(saved, true, engine.resolvePtState(companyId, saved));
    }

    private void requireManage() {
        if (!authorizationService.hasOrganizationScope("SALARY_MANAGE")) {
            throw new AccessDeniedException("SALARY_MANAGE requires ORGANIZATION scope.");
        }
    }

    private Long requiredTenant() {
        Long tenant = TenantContext.getCurrentTenant();
        if (tenant == null) throw new BadRequestException("Company context is required");
        return tenant;
    }
}
