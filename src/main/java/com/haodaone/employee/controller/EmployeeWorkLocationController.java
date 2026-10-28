package com.haodaone.employee.controller;

import com.haodaone.attendance.entity.OfficeLocation;
import com.haodaone.attendance.repository.OfficeLocationRepository;
import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.tenant.TenantContext;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * An employee's work location (office_location_id), which decides the location-specific
 * holidays that apply to them. Viewing follows EMPLOYEE_VIEW scope / self; changing needs EMPLOYEE_MANAGE.
 */
@RestController
@RequestMapping("/api/employees/{id}/work-location")
public class EmployeeWorkLocationController {

    public record WorkLocationDTO(Long employeeId, Long officeLocationId, String officeLocationName, List<Option> options) {
    }

    public record Option(Long id, String name) {
    }

    public record UpdateRequest(Long officeLocationId) {
    }

    private final EmployeeRepository employeeRepository;
    private final OfficeLocationRepository officeLocationRepository;
    private final AuditLogService auditLogService;

    public EmployeeWorkLocationController(EmployeeRepository employeeRepository, OfficeLocationRepository officeLocationRepository,
                                          AuditLogService auditLogService) {
        this.employeeRepository = employeeRepository;
        this.officeLocationRepository = officeLocationRepository;
        this.auditLogService = auditLogService;
    }

    @GetMapping
    @PreAuthorize("@authorizationService.isAllowed('EMPLOYEE_VIEW', 'EMPLOYEE', #id) or @employeeSecurity.isSelf(#id)")
    @Transactional(readOnly = true)
    public WorkLocationDTO get(@PathVariable Long id) {
        Long companyId = requiredTenant();
        return toDto(find(id, companyId), companyId);
    }

    @PutMapping
    @PreAuthorize("hasAuthority('EMPLOYEE_MANAGE') and (@companySecurity.canManageEmployee(#id) or @companySecurity.isSuperAdmin())")
    @Transactional
    public WorkLocationDTO update(@PathVariable Long id, @RequestBody UpdateRequest request) {
        Long companyId = requiredTenant();
        Employee employee = find(id, companyId);
        Long locationId = request == null ? null : request.officeLocationId();
        String label = "none";
        if (locationId != null) {
            OfficeLocation location = officeLocationRepository.findByIdAndCompany_IdAndDeletedFalse(locationId, companyId)
                    .orElseThrow(() -> new BadRequestException("Unknown office location: " + locationId));
            label = location.getName();
        }
        employee.setOfficeLocationId(locationId);
        employeeRepository.save(employee);
        auditLogService.log("Employee", employee.getId(), "WORK_LOCATION", "Work location set to " + label);
        return toDto(employee, companyId);
    }

    private WorkLocationDTO toDto(Employee employee, Long companyId) {
        List<Option> options = officeLocationRepository.findAllByCompany_IdAndDeletedFalseOrderByNameAsc(companyId).stream()
                .map(l -> new Option(l.getId(), l.getName())).toList();
        String name = options.stream().filter(o -> o.id().equals(employee.getOfficeLocationId()))
                .map(Option::name).findFirst().orElse(null);
        return new WorkLocationDTO(employee.getId(), employee.getOfficeLocationId(), name, options);
    }

    private Employee find(Long id, Long companyId) {
        return employeeRepository.findByIdAndCompany_IdAndDeletedFalse(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
    }

    private static Long requiredTenant() {
        Long tenant = TenantContext.getCurrentTenant();
        if (tenant == null) throw new BadRequestException("Company context is required");
        return tenant;
    }
}
