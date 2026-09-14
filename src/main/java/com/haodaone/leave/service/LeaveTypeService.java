package com.haodaone.leave.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.leave.dto.LeaveTypeDTO;
import com.haodaone.leave.entity.LeaveType;
import com.haodaone.leave.repository.LeaveTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import com.haodaone.tenant.TenantContext;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.company.repository.CompanyRepository;

@Service
public class LeaveTypeService {

    private final LeaveTypeRepository leaveTypeRepository;
    private final AuditLogService auditLogService;
    private final CompanyRepository companyRepository;

    public LeaveTypeService(LeaveTypeRepository leaveTypeRepository, AuditLogService auditLogService, CompanyRepository companyRepository) {
        this.leaveTypeRepository = leaveTypeRepository;
        this.auditLogService = auditLogService;
        this.companyRepository = companyRepository;
    }

    public List<LeaveTypeDTO> listAll() {
        return leaveTypeRepository.findAllByCompany_IdAndDeletedFalseOrderByNameAsc(requiredTenant()).stream().map(LeaveTypeDTO::from).toList();
    }

    @Transactional
    public LeaveTypeDTO create(LeaveTypeDTO.CreateRequest request) {
        Long companyId = requiredTenant();
        if (leaveTypeRepository.existsByCompany_IdAndCode(companyId, request.getCode())) {
            throw new BadRequestException("Leave type code '" + request.getCode() + "' is already in use");
        }
        LeaveType type = new LeaveType();
        type.setName(request.getName());
        type.setCode(request.getCode());
        type.setDefaultDaysPerYear(request.getDefaultDaysPerYear());
        type.setCarryForward(request.isCarryForward());
        type.setAutoApprove(request.isAutoApprove());
        type.setCompany(companyRepository.findById(companyId).orElseThrow(() -> new ResourceNotFoundException("Company not found")));

        LeaveType saved = leaveTypeRepository.save(type);
        auditLogService.log("LeaveType", saved.getId(), "CREATE", "Created leave type '" + saved.getName() + "'");
        return LeaveTypeDTO.from(saved);
    }

    @Transactional
    public LeaveTypeDTO update(Long id, LeaveTypeDTO.CreateRequest request) {
        Long companyId = requiredTenant();
        LeaveType type = leaveTypeRepository.findByIdAndCompany_IdAndDeletedFalse(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave type not found"));
        if (!type.getCode().equals(request.getCode()) && leaveTypeRepository.existsByCompany_IdAndCode(companyId, request.getCode())) {
            throw new BadRequestException("Leave type code '" + request.getCode() + "' is already in use");
        }
        type.setName(request.getName());
        type.setCode(request.getCode());
        type.setDefaultDaysPerYear(request.getDefaultDaysPerYear());
        type.setCarryForward(request.isCarryForward());
        type.setAutoApprove(request.isAutoApprove());
        LeaveType saved = leaveTypeRepository.save(type);
        auditLogService.log("LeaveType", saved.getId(), "UPDATE", "Updated leave type '" + saved.getName() + "'");
        return LeaveTypeDTO.from(saved);
    }

    private Long requiredTenant() { Long tenant = TenantContext.getCurrentTenant(); if (tenant == null) throw new BadRequestException("Company context is required"); return tenant; }
}
