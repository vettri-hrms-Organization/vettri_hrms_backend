package com.haodaone.performance.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.performance.dto.GoalDTO;
import com.haodaone.performance.entity.Goal;
import com.haodaone.performance.repository.GoalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import com.haodaone.tenant.TenantContext;

@Service
public class GoalService {

    private static final Set<String> VALID_STATUSES = Set.of("NOT_STARTED", "IN_PROGRESS", "AT_RISK", "COMPLETED");

    private final GoalRepository goalRepository;
    private final EmployeeRepository employeeRepository;
    private final AuditLogService auditLogService;

    public GoalService(GoalRepository goalRepository, EmployeeRepository employeeRepository, AuditLogService auditLogService) {
        this.goalRepository = goalRepository;
        this.employeeRepository = employeeRepository;
        this.auditLogService = auditLogService;
    }

    public List<GoalDTO> byEmployee(Long employeeId) {
        return goalRepository.findAllByCompany_IdAndEmployeeIdAndDeletedFalseOrderByTargetDateAsc(requiredTenant(), employeeId).stream()
                .map(GoalDTO::from)
                .toList();
    }

    @Transactional
    public GoalDTO create(GoalDTO.CreateRequest request) {
        Employee employee = employeeRepository.findById(request.getEmployeeId())
                .orElseThrow(() -> new BadRequestException("Unknown employee: " + request.getEmployeeId()));
        Long companyId = requiredTenant();
        if (employee.getCompany() == null || !companyId.equals(employee.getCompany().getId())) throw new BadRequestException("Employee is not in this company");

        Goal goal = new Goal();
        goal.setEmployee(employee);
        goal.setCompany(employee.getCompany());
        goal.setTitle(request.getTitle());
        goal.setDescription(request.getDescription());
        goal.setTargetDate(request.getTargetDate());
        goal.setStatus("NOT_STARTED");

        Goal saved = goalRepository.save(goal);
        auditLogService.log("Goal", saved.getId(), "CREATE", "Set goal '" + saved.getTitle() + "' for " + employee.getFullName());
        return GoalDTO.from(saved);
    }

    @Transactional
    public GoalDTO updateProgress(Long id, GoalDTO.ProgressUpdateRequest request) {
        if (!VALID_STATUSES.contains(request.getStatus())) {
            throw new BadRequestException("Unknown status: " + request.getStatus() + ". Must be one of " + VALID_STATUSES);
        }
        Goal goal = goalRepository.findByIdAndCompany_IdAndDeletedFalse(id, requiredTenant())
                .orElseThrow(() -> new ResourceNotFoundException("Goal not found: " + id));

        goal.setProgressPercent(request.getProgressPercent());
        goal.setStatus(request.getStatus());

        Goal saved = goalRepository.save(goal);
        auditLogService.log("Goal", saved.getId(), "UPDATE",
                "Progress updated to " + request.getProgressPercent() + "% (" + request.getStatus() + ")");
        return GoalDTO.from(saved);
    }

    private Long requiredTenant() { Long tenant = TenantContext.getCurrentTenant(); if (tenant == null) throw new BadRequestException("Company context is required"); return tenant; }
}
