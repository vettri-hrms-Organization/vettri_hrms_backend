package com.haodaone.org.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.org.dto.DepartmentDTO;
import com.haodaone.org.entity.Department;
import com.haodaone.org.repository.DepartmentRepository;
import com.haodaone.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final EmployeeRepository employeeRepository;
    private final CompanyRepository companyRepository;
    private final AuditLogService auditLogService;

    public DepartmentService(DepartmentRepository departmentRepository, EmployeeRepository employeeRepository,
                              CompanyRepository companyRepository, AuditLogService auditLogService) {
        this.departmentRepository = departmentRepository;
        this.employeeRepository = employeeRepository;
        this.companyRepository = companyRepository;
        this.auditLogService = auditLogService;
    }

    public List<DepartmentDTO> listAll() {
        Long companyId = requiredTenant();
        return departmentRepository.findAllByCompany_IdAndDeletedFalseOrderByNameAsc(companyId).stream()
                .map(this::toEnrichedDTO)
                .toList();
    }

    @Transactional
    public DepartmentDTO create(DepartmentDTO.CreateRequest request) {
        Long companyId = requiredTenant();
        if (departmentRepository.existsByCompany_IdAndCodeAndDeletedFalse(companyId, request.getCode())) {
            throw new BadRequestException("Department code '" + request.getCode() + "' is already in use in this company");
        }

        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new BadRequestException("Company not found: " + companyId));

        Department department = new Department();
        department.setName(request.getName());
        department.setCode(request.getCode());
        department.setDescription(request.getDescription());
        department.setCompany(company);
        department.setHeadEmployeeId(request.getHeadEmployeeId());
        if (request.getParentDepartmentId() != null) {
            Department parent = departmentRepository.findByIdAndCompany_IdAndDeletedFalse(request.getParentDepartmentId(), companyId)
                    .orElseThrow(() -> new BadRequestException("Unknown parent department: " + request.getParentDepartmentId()));
            department.setParentDepartment(parent);
        }

        Department saved = departmentRepository.save(department);
        auditLogService.log("Department", saved.getId(), "CREATE", "Created department '" + saved.getName() + "'");
        return toEnrichedDTO(saved);
    }

    @Transactional
    public void setActive(Long id, boolean active) {
        Long companyId = requiredTenant();
        Department department = departmentRepository.findByIdAndCompany_IdAndDeletedFalse(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Department not found: " + id));
        department.setActive(active);
        departmentRepository.save(department);
        auditLogService.log("Department", id, active ? "ACTIVATE" : "DEACTIVATE", "active: " + active);
    }

    private DepartmentDTO toEnrichedDTO(Department department) {
        DepartmentDTO dto = DepartmentDTO.from(department);
        dto.setEmployeeCount(employeeRepository.countByDepartmentIdAndDeletedFalse(department.getId()));
        if (department.getHeadEmployeeId() != null) {
            employeeRepository.findById(department.getHeadEmployeeId())
                    .map(Employee::getFullName)
                    .ifPresent(dto::setHeadEmployeeName);
        }
        return dto;
    }

    private Long requiredTenant() {
        Long tenant = TenantContext.getCurrentTenant();
        if (tenant == null) {
            throw new BadRequestException("Company context is required");
        }
        return tenant;
    }
}
