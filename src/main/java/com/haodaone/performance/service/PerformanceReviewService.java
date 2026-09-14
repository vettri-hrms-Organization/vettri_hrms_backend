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

    public PerformanceReviewService(PerformanceReviewRepository performanceReviewRepository,
                                     EmployeeRepository employeeRepository, AuditLogService auditLogService) {
        this.performanceReviewRepository = performanceReviewRepository;
        this.employeeRepository = employeeRepository;
        this.auditLogService = auditLogService;
    }

    public List<PerformanceReviewDTO> listAll() {
        return performanceReviewRepository.findAllByCompany_IdAndDeletedFalseOrderByCreatedAtDesc(requiredTenant()).stream()
                .map(PerformanceReviewDTO::from)
                .toList();
    }

    public List<PerformanceReviewDTO> byEmployee(Long employeeId) {
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

        if (!review.getStatus().equals("SUBMITTED")) {
            throw new BadRequestException("Only submitted reviews can be acknowledged (current status: " + review.getStatus() + ")");
        }

        review.setStatus("ACKNOWLEDGED");
        PerformanceReview saved = performanceReviewRepository.save(review);
        auditLogService.log("PerformanceReview", saved.getId(), "ACKNOWLEDGE", "Review acknowledged by employee");
        return PerformanceReviewDTO.from(saved);
    }

    private Long requiredTenant() { Long tenant = TenantContext.getCurrentTenant(); if (tenant == null) throw new BadRequestException("Company context is required"); return tenant; }
}
