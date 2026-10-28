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
        type.setPaid(request.getPaid() == null || request.getPaid());
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
        if (type.isSystemManaged() && !type.getCode().equalsIgnoreCase(request.getCode())) {
            throw new BadRequestException("The code of the system leave type '" + type.getName() + "' can't be changed");
        }
        if (!type.getCode().equals(request.getCode()) && leaveTypeRepository.existsByCompany_IdAndCode(companyId, request.getCode())) {
            throw new BadRequestException("Leave type code '" + request.getCode() + "' is already in use");
        }
        type.setName(request.getName());
        type.setCode(request.getCode());
        type.setDefaultDaysPerYear(request.getDefaultDaysPerYear());
        type.setCarryForward(request.isCarryForward());
        type.setAutoApprove(request.isAutoApprove());
        if (request.getPaid() != null) type.setPaid(request.getPaid());
        LeaveType saved = leaveTypeRepository.save(type);
        auditLogService.log("LeaveType", saved.getId(), "UPDATE", "Updated leave type '" + saved.getName() + "'");
        return LeaveTypeDTO.from(saved);
    }

    /** Replaces the type's accrual / carry-forward / encashment policy (V45). Validates ranges; null numbers clear a limit. */
    @Transactional
    public LeaveTypeDTO updatePolicy(Long id, com.haodaone.leave.dto.LeavePolicyDTOs.PolicyRequest request) {
        Long companyId = requiredTenant();
        LeaveType type = leaveTypeRepository.findByIdAndCompany_IdAndDeletedFalse(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave type not found"));
        if (request == null) throw new BadRequestException("Policy is required");
        if (type.isSystemManaged()) {
            // Comp-off credits come from approvals; only the credit expiry is configurable.
            Integer expiry = request.getCreditExpiryDays();
            if (expiry != null && (expiry < 1 || expiry > 730)) throw new BadRequestException("Comp-off expiry must be between 1 and 730 days");
            type.setCreditExpiryDays(expiry);
        } else {
            String mode = request.getAccrualMode() == null ? "YEARLY_UPFRONT" : request.getAccrualMode().trim().toUpperCase(java.util.Locale.ROOT);
            if (!java.util.Set.of("YEARLY_UPFRONT", "MONTHLY", "QUARTERLY").contains(mode)) {
                throw new BadRequestException("Accrual mode must be YEARLY_UPFRONT, MONTHLY or QUARTERLY");
            }
            String rounding = request.getAccrualRounding() == null ? "HALF_DAY" : request.getAccrualRounding().trim().toUpperCase(java.util.Locale.ROOT);
            if (!rounding.equals("HALF_DAY") && !rounding.equals("EXACT")) throw new BadRequestException("Rounding must be HALF_DAY or EXACT");
            String basis = request.getEncashmentBasis() == null ? "BASIC" : request.getEncashmentBasis().trim().toUpperCase(java.util.Locale.ROOT);
            if (!basis.equals("BASIC") && !basis.equals("GROSS")) throw new BadRequestException("Encashment basis must be BASIC or GROSS");
            Integer cutoff = request.getJoiningCutoffDay();
            if (cutoff != null && (cutoff < 1 || cutoff > 31)) throw new BadRequestException("Joining cut-off day must be between 1 and 31");
            requireNonNegative(request.getMaxBalanceDays(), "Maximum balance");
            requireNonNegative(request.getNegativeBalanceDays(), "Negative balance allowance");
            requireNonNegative(request.getMaxCarryForwardDays(), "Maximum carry-forward");
            requireNonNegative(request.getMaxEncashableDaysPerYear(), "Maximum encashable days");
            if (request.getProbationDays() != null && (request.getProbationDays() < 0 || request.getProbationDays() > 730)) {
                throw new BadRequestException("Probation must be between 0 and 730 days");
            }
            if (request.getCarryForwardExpiryMonths() != null && (request.getCarryForwardExpiryMonths() < 1 || request.getCarryForwardExpiryMonths() > 12)) {
                throw new BadRequestException("Carry-forward expiry must be between 1 and 12 months");
            }
            type.setAccrualMode(mode);
            type.setAccrualRounding(rounding);
            type.setProrateOnJoining(request.isProrateOnJoining());
            type.setJoiningCutoffDay(cutoff);
            type.setMaxBalanceDays(positiveOrNull(request.getMaxBalanceDays()));
            type.setProbationDays(request.getProbationDays() != null && request.getProbationDays() > 0 ? request.getProbationDays() : null);
            type.setNegativeBalanceDays(positiveOrNull(request.getNegativeBalanceDays()));
            if (request.getCarryForward() != null) type.setCarryForward(request.getCarryForward());
            type.setMaxCarryForwardDays(request.getMaxCarryForwardDays());
            type.setCarryForwardExpiryMonths(request.getCarryForwardExpiryMonths());
            type.setEncashable(request.isEncashable());
            type.setMaxEncashableDaysPerYear(request.getMaxEncashableDaysPerYear());
            type.setEncashmentBasis(basis);
        }
        LeaveType saved = leaveTypeRepository.save(type);
        auditLogService.log("LeaveType", saved.getId(), "UPDATE_POLICY", String.format(java.util.Locale.ROOT,
                "Updated policy of '%s': accrual %s (%s), cap %s, probation %s, negative %s, carry %s max %s expiry %s month(s), encashable %s max %s on %s, comp-off expiry %s",
                saved.getName(), saved.getAccrualMode(), saved.getAccrualRounding(), saved.getMaxBalanceDays(), saved.getProbationDays(),
                saved.getNegativeBalanceDays(), saved.isCarryForward(), saved.getMaxCarryForwardDays(), saved.getCarryForwardExpiryMonths(),
                saved.isEncashable(), saved.getMaxEncashableDaysPerYear(), saved.getEncashmentBasis(), saved.getCreditExpiryDays()));
        return LeaveTypeDTO.from(saved);
    }

    private static void requireNonNegative(Double value, String label) {
        if (value != null && (value < 0 || value > 1000)) throw new BadRequestException(label + " must be between 0 and 1000 days");
    }

    private static Double positiveOrNull(Double value) {
        return value != null && value > 0 ? value : null;
    }

    private Long requiredTenant() { Long tenant = TenantContext.getCurrentTenant(); if (tenant == null) throw new BadRequestException("Company context is required"); return tenant; }
}
