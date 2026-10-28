package com.haodaone.imports.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.imports.dto.ImportDTOs.OpeningYtdDTO;
import com.haodaone.imports.entity.PayrollOpeningYtd;
import com.haodaone.imports.repository.PayrollOpeningYtdRepository;
import com.haodaone.salary.service.OpeningYtdProvider;
import com.haodaone.salary.service.TaxDeclarationService;
import com.haodaone.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Serves imported opening payroll YTD figures to the statutory engine (TDS projection) and Form 16 via
 * {@link OpeningYtdProvider}, and lists/removes them for HR.
 */
@Service
public class OpeningYtdService implements OpeningYtdProvider {

    private final PayrollOpeningYtdRepository repository;
    private final AuditLogService auditLogService;

    public OpeningYtdService(PayrollOpeningYtdRepository repository, AuditLogService auditLogService) {
        this.repository = repository;
        this.auditLogService = auditLogService;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OpeningYtd> openingFor(Long companyId, Long employeeId, int financialYear) {
        if (companyId == null || employeeId == null) return Optional.empty();
        return repository.findFirstByCompany_IdAndEmployee_IdAndFinancialYearAndDeletedFalse(companyId, employeeId, financialYear)
                .map(OpeningYtdService::toOpening);
    }

    public static OpeningYtd toOpening(PayrollOpeningYtd y) {
        return new OpeningYtd(y.getGrossSalary(), y.getTaxableSalary(), y.getPfEmployee(), y.getProfessionalTax(),
                y.getTdsDeducted(), y.getMonthsCovered(), y.getPreviousEmployerIncome(), y.getPreviousEmployerTds());
    }

    @Transactional(readOnly = true)
    public List<OpeningYtdDTO> list(Integer fy) {
        int year = fy != null ? fy : TaxDeclarationService.currentFinancialYear();
        return repository.findAllByCompany_IdAndFinancialYearAndDeletedFalseOrderByIdAsc(requiredTenant(), year)
                .stream().map(OpeningYtdDTO::of).toList();
    }

    @Transactional
    public void delete(Long id) {
        PayrollOpeningYtd row = repository.findById(id)
                .filter(r -> !r.isDeleted() && r.getCompany() != null && requiredTenant().equals(r.getCompany().getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Opening YTD record not found: " + id));
        row.setDeleted(true);
        row.setDeletedAt(LocalDateTime.now(ZoneId.of("Asia/Kolkata")));
        repository.save(row);
        auditLogService.log("PayrollOpeningYtd", row.getId(), "DELETE", "Removed opening payroll YTD for '"
                + row.getEmployee().getFullName() + "' (FY " + row.getFinancialYear() + ")");
    }

    private static Long requiredTenant() {
        Long tenant = TenantContext.getCurrentTenant();
        if (tenant == null) throw new BadRequestException("Company context is required");
        return tenant;
    }
}
