package com.haodaone.salary.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.salary.dto.SalaryStructureDTO;
import com.haodaone.salary.dto.UpsertSalaryStructureRequest;
import com.haodaone.salary.entity.SalaryStructure;
import com.haodaone.salary.repository.SalaryStructureRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import com.haodaone.tenant.TenantContext;

/**
 * Owns the versioned salary-structure lifecycle: defining an employee's pay
 * for the first time, and revising it later. Revisions never edit a past
 * row in place - see {@link SalaryStructure}'s class doc - so payroll runs
 * that already snapshot an old structure are untouched by a later raise.
 */
@Service
public class SalaryStructureService {

    private final SalaryStructureRepository salaryStructureRepository;
    private final EmployeeRepository employeeRepository;
    private final AuditLogService auditLogService;

    public SalaryStructureService(SalaryStructureRepository salaryStructureRepository, EmployeeRepository employeeRepository,
                                   AuditLogService auditLogService) {
        this.salaryStructureRepository = salaryStructureRepository;
        this.employeeRepository = employeeRepository;
        this.auditLogService = auditLogService;
    }

    public SalaryStructureDTO getCurrent(Long employeeId) {
        return salaryStructureRepository.findByEmployee_Company_IdAndEmployeeIdAndActiveTrueAndDeletedFalse(requiredTenant(), employeeId)
                .map(SalaryStructureDTO::from)
                .orElse(null);
    }

    public List<SalaryStructureDTO> getHistory(Long employeeId) {
        return salaryStructureRepository.findByEmployee_Company_IdAndEmployeeIdAndDeletedFalseOrderByEffectiveFromDescCreatedAtDesc(requiredTenant(), employeeId)
                .stream().map(SalaryStructureDTO::from).toList();
    }

    /**
     * Creates a new active structure for the employee and, if one already
     * existed, deactivates it. Both writes happen in the same transaction
     * so the "exactly one active structure per employee" invariant (backed
     * by the partial unique index in V7) can never be observed as broken.
     */
    @Transactional
    public SalaryStructureDTO upsert(UpsertSalaryStructureRequest request) {
        Employee employee = employeeRepository.findById(request.getEmployeeId())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + request.getEmployeeId()));
        if (employee.isDeleted()) {
            throw new ResourceNotFoundException("Employee not found: " + request.getEmployeeId());
        }
        Long companyId = requiredTenant();
        if (employee.getCompany() == null || !companyId.equals(employee.getCompany().getId())) throw new BadRequestException("Employee is not in this company");

        salaryStructureRepository.findByEmployeeIdAndActiveTrueAndDeletedFalse(employee.getId())
                .ifPresent(previous -> {
                    previous.setActive(false);
                    salaryStructureRepository.save(previous);
                });

        SalaryStructure structure = new SalaryStructure();
        structure.setEmployee(employee);
        structure.setCompany(employee.getCompany());
        structure.setEffectiveFrom(request.getEffectiveFrom());
        structure.setActive(true);
        structure.setComponents(request.getComponents().toEntity());
        structure.setNotes(request.getNotes());
        structure.recalculate();

        if (structure.getComponents().getBasicSalary().signum() <= 0) {
            throw new BadRequestException("Basic salary must be greater than zero");
        }

        SalaryStructure saved = salaryStructureRepository.save(structure);

        boolean isRevision = salaryStructureRepository.countByEmployeeId(employee.getId()) > 1;
        auditLogService.log("SalaryStructure", saved.getId(), isRevision ? "REVISE" : "CREATE",
                (isRevision ? "Revised" : "Defined") + " salary structure for '" + employee.getFullName() + "' - net salary "
                        + saved.getNetSalary() + " effective " + saved.getEffectiveFrom());

        return SalaryStructureDTO.from(saved);
    }

    private Long requiredTenant() { Long tenant = TenantContext.getCurrentTenant(); if (tenant == null) throw new BadRequestException("Company context is required"); return tenant; }
}
