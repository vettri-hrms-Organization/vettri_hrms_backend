package com.haodaone.privacy.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.privacy.dto.PrivacyDTO;
import com.haodaone.privacy.entity.CompanyPrivacySetting;
import com.haodaone.privacy.repository.CompanyPrivacySettingRepository;
import com.haodaone.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Company privacy settings: grievance officer, breach contact, retention, monitoring-consent policy. */
@Service
public class PrivacySettingsService {

    private final CompanyPrivacySettingRepository settingRepository;
    private final CompanyRepository companyRepository;
    private final AuditLogService auditLogService;

    public PrivacySettingsService(CompanyPrivacySettingRepository settingRepository, CompanyRepository companyRepository,
                                  AuditLogService auditLogService) {
        this.settingRepository = settingRepository;
        this.companyRepository = companyRepository;
        this.auditLogService = auditLogService;
    }

    /** Stored settings, or an unsaved default instance when the company has none yet. */
    @Transactional(readOnly = true)
    public CompanyPrivacySetting settingsFor(Long companyId) {
        if (companyId == null) {
            return new CompanyPrivacySetting();
        }
        return settingRepository.findByCompany_IdAndDeletedFalse(companyId).orElseGet(CompanyPrivacySetting::new);
    }

    @Transactional(readOnly = true)
    public PrivacyDTO.PrivacySettingsView get() {
        return toView(settingsFor(requiredTenant()));
    }

    @Transactional(readOnly = true)
    public PrivacyDTO.PrivacyContact contact() {
        Long companyId = requiredTenant();
        CompanyPrivacySetting s = settingsFor(companyId);
        String companyName = companyRepository.findById(companyId).map(Company::getName).orElse(null);
        return new PrivacyDTO.PrivacyContact(companyName, s.getGrievanceOfficerName(), s.getGrievanceOfficerEmail(),
                s.getGrievanceOfficerPhone(), s.getOtherDataRetentionMonths(), s.getPayrollRetentionYears());
    }

    @Transactional
    public PrivacyDTO.PrivacySettingsView update(PrivacyDTO.PrivacySettingsRequest request) {
        Long companyId = requiredTenant();
        CompanyPrivacySetting s = settingRepository.findByCompany_IdAndDeletedFalse(companyId).orElseGet(() -> {
            CompanyPrivacySetting created = new CompanyPrivacySetting();
            created.setCompany(companyRepository.findById(companyId)
                    .orElseThrow(() -> new BadRequestException("Company not found")));
            return created;
        });
        s.setGrievanceOfficerName(trim(request.grievanceOfficerName()));
        s.setGrievanceOfficerEmail(trim(request.grievanceOfficerEmail()));
        s.setGrievanceOfficerPhone(trim(request.grievanceOfficerPhone()));
        s.setBreachContactName(trim(request.breachContactName()));
        s.setBreachContactEmail(trim(request.breachContactEmail()));
        s.setBreachContactPhone(trim(request.breachContactPhone()));
        if (request.payrollRetentionYears() != null) {
            if (request.payrollRetentionYears() < CompanyPrivacySetting.DEFAULT_PAYROLL_RETENTION_YEARS) {
                throw new BadRequestException("Payroll and statutory records must be kept for at least 7 years.");
            }
            s.setPayrollRetentionYears(request.payrollRetentionYears());
        }
        if (request.otherDataRetentionMonths() != null) {
            if (request.otherDataRetentionMonths() < 0) {
                throw new BadRequestException("Retention cannot be negative.");
            }
            s.setOtherDataRetentionMonths(request.otherDataRetentionMonths());
        }
        if (request.monitoringConsentRequired() != null) {
            s.setMonitoringConsentRequired(request.monitoringConsentRequired());
        }
        if (request.stopMonitoringOnWithdrawal() != null) {
            s.setStopMonitoringOnWithdrawal(request.stopMonitoringOnWithdrawal());
        }
        CompanyPrivacySetting saved = settingRepository.save(s);
        auditLogService.log("CompanyPrivacySetting", saved.getId(), "UPDATE",
                "Updated privacy settings (monitoring consent required=" + saved.isMonitoringConsentRequired()
                        + ", stop on withdrawal=" + saved.isStopMonitoringOnWithdrawal()
                        + ", other data retention months=" + saved.getOtherDataRetentionMonths()
                        + ", payroll retention years=" + saved.getPayrollRetentionYears() + ")");
        return toView(saved);
    }

    static PrivacyDTO.PrivacySettingsView toView(CompanyPrivacySetting s) {
        return new PrivacyDTO.PrivacySettingsView(s.getGrievanceOfficerName(), s.getGrievanceOfficerEmail(),
                s.getGrievanceOfficerPhone(), s.getBreachContactName(), s.getBreachContactEmail(),
                s.getBreachContactPhone(), s.getPayrollRetentionYears(), s.getOtherDataRetentionMonths(),
                s.isMonitoringConsentRequired(), s.isStopMonitoringOnWithdrawal());
    }

    static Long requiredTenant() {
        Long companyId = TenantContext.getCurrentTenant();
        if (companyId == null) {
            throw new BadRequestException("Company context is required");
        }
        return companyId;
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
