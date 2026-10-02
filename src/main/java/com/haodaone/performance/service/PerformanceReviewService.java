package com.haodaone.performance.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.performance.dto.PerformanceReviewDTO;
import com.haodaone.performance.entity.PerformanceReview;
import com.haodaone.performance.repository.PerformanceReviewRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import com.haodaone.tenant.TenantContext;

@Service
public class PerformanceReviewService {

    private final PerformanceReviewRepository performanceReviewRepository;
    private final EmployeeRepository employeeRepository;
    private final AuditLogService auditLogService;
    private final com.haodaone.security.AuthorizationService authorizationService;
    private final com.haodaone.security.EmployeeSecurity employeeSecurity;

    public PerformanceReviewService(PerformanceReviewRepository performanceReviewRepository,
                                     EmployeeRepository employeeRepository, AuditLogService auditLogService,
                                     com.haodaone.security.AuthorizationService authorizationService,
                                     com.haodaone.security.EmployeeSecurity employeeSecurity) {
        this.performanceReviewRepository = performanceReviewRepository;
        this.employeeRepository = employeeRepository;
        this.auditLogService = auditLogService;
        this.authorizationService = authorizationService;
        this.employeeSecurity = employeeSecurity;
    }

    public List<PerformanceReviewDTO> listAll() {
        var scope = authorizationService.resolveEmployeeIds("PERFORMANCE_VIEW");
        if (scope.isPresent() && scope.get().isEmpty()) return List.of();
        var rows = scope.isEmpty() ? performanceReviewRepository.findAllByCompany_IdAndDeletedFalseOrderByCreatedAtDesc(requiredTenant()) : performanceReviewRepository.findAllByCompany_IdAndEmployeeIdInAndDeletedFalseOrderByCreatedAtDesc(requiredTenant(), scope.get());
        return rows.stream()
                .map(PerformanceReviewDTO::from)
                .toList();
    }

    public List<PerformanceReviewDTO> byEmployee(Long employeeId) {
        if (!authorizationService.isAllowed("PERFORMANCE_VIEW", "EMPLOYEE", employeeId)
                && !employeeSecurity.isSelf(employeeId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "PERFORMANCE_VIEW is not authorized for this employee.");
        }
        return performanceReviewRepository.findAllByCompany_IdAndEmployeeIdAndDeletedFalseOrderByCreatedAtDesc(requiredTenant(), employeeId).stream()
                .map(PerformanceReviewDTO::from)
                .toList();
    }

    @Transactional
    public PerformanceReviewDTO create(PerformanceReviewDTO.CreateRequest request) {
        Employee employee = employeeRepository.findById(request.getEmployeeId())
                .orElseThrow(() -> new BadRequestException("Unknown employee: " + request.getEmployeeId()));
        Long companyId = requiredTenant();
        if (employee.getCompany() == null || !companyId.equals(employee.getCompany().getId())) throw new BadRequestException("Employee is not in this company");
        requireScopedPermission("PERFORMANCE_MANAGE", employee.getId());

        PerformanceReview review = new PerformanceReview();
        review.setEmployee(employee);
        review.setCompany(employee.getCompany());
        review.setReviewPeriod(request.getReviewPeriod());
        review.setRating(request.getRating());
        review.setStrengths(request.getStrengths());
        review.setAreasForImprovement(request.getAreasForImprovement());
        review.setStatus("DRAFT");

        if (request.getReviewerId() != null) {
                Employee reviewer = employeeRepository.findById(request.getReviewerId())
                    .orElseThrow(() -> new BadRequestException("Unknown reviewer: " + request.getReviewerId()));
                if (reviewer.getCompany() == null || !companyId.equals(reviewer.getCompany().getId())) throw new BadRequestException("Reviewer is not in this company");
            review.setReviewer(reviewer);
        }

        PerformanceReview saved = performanceReviewRepository.save(review);
        auditLogService.log("PerformanceReview", saved.getId(), "CREATE",
                "Drafted " + saved.getReviewPeriod() + " review for " + employee.getFullName());
        return PerformanceReviewDTO.from(saved);
    }

    @Transactional
    public PerformanceReviewDTO submit(Long id) {
        PerformanceReview review = performanceReviewRepository.findByIdAndCompany_IdAndDeletedFalse(id, requiredTenant())
                .orElseThrow(() -> new ResourceNotFoundException("Performance review not found: " + id));
        requireScopedPermission("PERFORMANCE_MANAGE", review.getEmployee().getId());

        if (!review.getStatus().equals("DRAFT")) {
            throw new BadRequestException("Only draft reviews can be submitted (current status: " + review.getStatus() + ")");
        }

        review.setStatus("SUBMITTED");
        review.setSubmittedAt(LocalDateTime.now());
        PerformanceReview saved = performanceReviewRepository.save(review);
        auditLogService.log("PerformanceReview", saved.getId(), "SUBMIT", "Review submitted");
        return PerformanceReviewDTO.from(saved);
    }

    @Transactional
    public PerformanceReviewDTO acknowledge(Long id) {
        PerformanceReview review = performanceReviewRepository.findByIdAndCompany_IdAndDeletedFalse(id, requiredTenant())
                .orElseThrow(() -> new ResourceNotFoundException("Performance review not found: " + id));
        Long employeeId = review.getEmployee().getId();
        if (!employeeSecurity.isSelf(employeeId)
                && !authorizationService.isAllowed("PERFORMANCE_VIEW", "EMPLOYEE", employeeId)
                && !authorizationService.isAllowed("PERFORMANCE_MANAGE", "EMPLOYEE", employeeId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "PERFORMANCE_VIEW is not authorized for this employee.");
        }

        if (!review.getStatus().equals("SUBMITTED")) {
            throw new BadRequestException("Only submitted reviews can be acknowledged (current status: " + review.getStatus() + ")");
        }

        review.setStatus("ACKNOWLEDGED");
        PerformanceReview saved = performanceReviewRepository.save(review);
        auditLogService.log("PerformanceReview", saved.getId(), "ACKNOWLEDGE", "Review acknowledged by employee");
        return PerformanceReviewDTO.from(saved);
    }

    private Long requiredTenant() { Long tenant = TenantContext.getCurrentTenant(); if (tenant == null) throw new BadRequestException("Company context is required"); return tenant; }

    private void requireScopedPermission(String permissionCode, Long employeeId) {
        if (!authorizationService.isAllowed(permissionCode, "EMPLOYEE", employeeId)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    permissionCode + " is not authorized for this employee.");
        }
    }
}
