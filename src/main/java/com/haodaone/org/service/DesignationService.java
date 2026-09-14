package com.haodaone.org.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.org.dto.DesignationDTO;
import com.haodaone.org.entity.Department;
import com.haodaone.org.entity.Designation;
import com.haodaone.org.repository.DepartmentRepository;
import com.haodaone.org.repository.DesignationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DesignationService {

    private final DesignationRepository designationRepository;
    private final DepartmentRepository departmentRepository;
    private final AuditLogService auditLogService;

    public DesignationService(DesignationRepository designationRepository, DepartmentRepository departmentRepository,
                               AuditLogService auditLogService) {
        this.designationRepository = designationRepository;
        this.departmentRepository = departmentRepository;
        this.auditLogService = auditLogService;
    }

    public List<DesignationDTO> listAll() {
        Long companyId = requiredTenant();
        return designationRepository.findAllByDeletedFalseOrderByTitleAsc().stream()
                .filter(d -> d.getDepartment() == null || (d.getDepartment().getCompany() != null && d.getDepartment().getCompany().getId() != null && d.getDepartment().getCompany().getId().equals(companyId)))
                .map(DesignationDTO::from)
                .toList();
    }

    @Transactional
    public DesignationDTO create(DesignationDTO.CreateRequest request) {
        Long companyId = requiredTenant();
        Designation designation = new Designation();
        designation.setTitle(request.getTitle());
        designation.setLevel(request.getLevel());
        if (request.getDepartmentId() != null) {
            Department department = departmentRepository.findByIdAndCompany_IdAndDeletedFalse(request.getDepartmentId(), companyId)
                    .orElseThrow(() -> new BadRequestException("Unknown department: " + request.getDepartmentId()));
            designation.setDepartment(department);
        }

        Designation saved = designationRepository.save(designation);
        auditLogService.log("Designation", saved.getId(), "CREATE", "Created designation '" + saved.getTitle() + "'");
        return DesignationDTO.from(saved);
    }

    private Long requiredTenant() {
        Long tenant = com.haodaone.tenant.TenantContext.getCurrentTenant();
        if (tenant == null) {
            throw new BadRequestException("Company context is required");
        }
        return tenant;
    }
}
