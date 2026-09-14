package com.haodaone.recruitment.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.org.repository.DepartmentRepository;
import com.haodaone.org.repository.DesignationRepository;
import com.haodaone.recruitment.dto.JobOpeningDTO;
import com.haodaone.recruitment.dto.CloseRequisitionRequest;
import com.haodaone.recruitment.entity.JobOpening;
import com.haodaone.recruitment.repository.CandidateRepository;
import com.haodaone.recruitment.repository.JobOpeningRepository;
import com.haodaone.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.time.LocalDateTime;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Service
public class JobOpeningService {

    // Allowed lifecycle statuses for job openings. Keep ON_HOLD for backward compatibility.
    private static final Set<String> VALID_STATUSES = Set.of("OPEN", "IN_PROGRESS", "ON_HOLD", "FILLED", "CLOSED", "REJECTED");
    // Close reasons are free-text in the UI (mandatory) — do not restrict to an enumerated set here.

    private final JobOpeningRepository jobOpeningRepository;
    private final CandidateRepository candidateRepository;
    private final DepartmentRepository departmentRepository;
    private final DesignationRepository designationRepository;
    private final EmployeeRepository employeeRepository;
    private final AuditLogService auditLogService;
    private final CompanyRepository companyRepository;

    public JobOpeningService(JobOpeningRepository jobOpeningRepository, CandidateRepository candidateRepository,
                              DepartmentRepository departmentRepository, DesignationRepository designationRepository,
                              EmployeeRepository employeeRepository, AuditLogService auditLogService, CompanyRepository companyRepository) {
        this.jobOpeningRepository = jobOpeningRepository;
        this.candidateRepository = candidateRepository;
        this.departmentRepository = departmentRepository;
        this.designationRepository = designationRepository;
        this.employeeRepository = employeeRepository;
        this.auditLogService = auditLogService;
        this.companyRepository = companyRepository;
    }

    /**
     * @Transactional(readOnly = true) is required: toEnrichedDTO() ->
     * JobOpeningDTO.from() calls department.getName() and
     * designation.getTitle(), both lazy relations. This job was only
     * "working" by coincidence whenever a listing's department/designation
     * happened to be null (see CandidateService.listAll()'s javadoc for the
     * full explanation - same root cause).
     */
    @Transactional(readOnly = true)
    public List<JobOpeningDTO> listAll() {
        Long tenant = TenantContext.getCurrentTenant();
        if (tenant == null) {
            throw new BadRequestException("Company context is required");
        }
        List<JobOpening> openings = jobOpeningRepository.findAllByCompany_IdAndDeletedFalseOrderByPostedDateDesc(tenant);
        return openings.stream()
                .map(this::toEnrichedDTO)
                .toList();
    }

    @Transactional
    public JobOpeningDTO create(JobOpeningDTO.CreateRequest request) {
        JobOpening opening = new JobOpening();
        Long tenant = TenantContext.getCurrentTenant();
        if (tenant == null) throw new BadRequestException("Company context is required");
        opening.setTitle(request.getTitle());
        opening.setEmploymentType(request.getEmploymentType() != null ? request.getEmploymentType() : "FULL_TIME");
        opening.setOpeningsCount(request.getOpeningsCount());
        opening.setDescription(request.getDescription());
        opening.setPostedDate(LocalDate.now());
        opening.setStatus("OPEN");
        opening.setCompany(companyRepository.findById(tenant)
            .orElseThrow(() -> new ResourceNotFoundException("Company not found: " + tenant)));

        if (request.getDepartmentId() != null) {
            opening.setDepartment(departmentRepository.findByIdAndCompany_IdAndDeletedFalse(request.getDepartmentId(), tenant)
                    .orElseThrow(() -> new BadRequestException("Unknown department: " + request.getDepartmentId())));
        }
        if (request.getDesignationId() != null) {
            opening.setDesignation(designationRepository.findById(request.getDesignationId())
                    .orElseThrow(() -> new BadRequestException("Unknown designation: " + request.getDesignationId())));
        }
        if (request.getRecruiterId() != null) {
            opening.setRecruiter(employeeRepository.findByIdAndCompany_IdAndDeletedFalse(request.getRecruiterId(), tenant)
                    .orElseThrow(() -> new BadRequestException("Unknown employee: " + request.getRecruiterId())));
        }

        JobOpening saved = jobOpeningRepository.save(opening);
        auditLogService.log("JobOpening", saved.getId(), "CREATE", "Opened requisition '" + saved.getTitle() + "'");
        return toEnrichedDTO(saved);
    }

    @Transactional
    public JobOpeningDTO setStatus(Long id, String status) {
        if (!VALID_STATUSES.contains(status)) {
            throw new BadRequestException("Unknown status: " + status + ". Must be one of " + VALID_STATUSES);
        }
        // Prevent callers from setting a "closure" status without providing a mandatory close reason.
        if (Set.of("CLOSED", "FILLED", "REJECTED").contains(status)) {
            throw new BadRequestException("Use the close requisition action with a reason when marking a requisition as CLOSED/FILLED/REJECTED");
        }
        JobOpening opening = findForTenant(id);
        String old = opening.getStatus();
        opening.setStatus(status);
        JobOpening saved = jobOpeningRepository.save(opening);
        auditLogService.log("JobOpening", saved.getId(), "STATUS_CHANGE", "status: " + old + " -> " + status);
        return toEnrichedDTO(saved);
    }

    @Transactional
    public JobOpeningDTO close(Long id, CloseRequisitionRequest request) {
        JobOpening opening = findForTenant(id);
        // Allow closing from OPEN or IN_PROGRESS states only
        if (!("OPEN".equals(opening.getStatus()) || "IN_PROGRESS".equals(opening.getStatus()))) {
            throw new BadRequestException("Only OPEN or IN_PROGRESS requisitions can be closed");
        }
        String reason = request.getReason() == null ? "" : request.getReason().trim();
        if (reason.isEmpty()) throw new BadRequestException("Close reason is required");

        // finalStatus is optional: allow callers to mark as FILLED/REJECTED/CLOSED explicitly; default to CLOSED.
        String finalStatus = request.getFinalStatus();
        if (finalStatus == null || finalStatus.isBlank()) {
            finalStatus = "CLOSED";
        }
        if (!Set.of("CLOSED", "FILLED", "REJECTED").contains(finalStatus)) {
            throw new BadRequestException("finalStatus must be one of: CLOSED, FILLED, REJECTED");
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null && auth.isAuthenticated() ? auth.getName() : "system";
        LocalDateTime now = LocalDateTime.now();
        opening.setStatus(finalStatus);
        opening.setClosedDate(now.toLocalDate());
        opening.setClosedReason(reason);
        opening.setClosedComments(request.getComments() == null || request.getComments().isBlank() ? null : request.getComments().trim());
        opening.setClosedBy(username);
        opening.setClosedAt(now);
        JobOpening saved = jobOpeningRepository.save(opening);
        auditLogService.log("JobOpening", saved.getId(), "CLOSED", "Closed requisition '" + saved.getTitle() + "' (reason: " + reason + ")");
        return toEnrichedDTO(saved);
    }

    @Transactional
    public void delete(Long id) {
        JobOpening opening = findForTenant(id);
        long candidateCount = candidateRepository.countByJobOpeningIdAndDeletedFalse(id);
        if (candidateCount > 0) {
            throw new BadRequestException("Cannot delete a job opening with candidates. Close it instead.");
        }
        opening.setDeleted(true);
        opening.setDeletedAt(java.time.LocalDateTime.now());
        jobOpeningRepository.save(opening);
        auditLogService.log("JobOpening", id, "DELETE", "Deleted requisition '" + opening.getTitle() + "'");
    }

    private JobOpeningDTO toEnrichedDTO(JobOpening opening) {
        JobOpeningDTO dto = JobOpeningDTO.from(opening);
        dto.setCandidateCount(candidateRepository.countByJobOpeningIdAndDeletedFalse(opening.getId()));
        dto.setHiredCount(candidateRepository.countByJobOpeningIdAndStageAndDeletedFalse(opening.getId(), "HIRED"));
        return dto;
    }

    private JobOpening findForTenant(Long id) {
        Long tenant = TenantContext.getCurrentTenant();
        if (tenant == null) throw new BadRequestException("Company context is required");
        return jobOpeningRepository.findById(id)
                .filter(opening -> opening.getCompany() != null && tenant.equals(opening.getCompany().getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Job opening not found: " + id));
    }
}
