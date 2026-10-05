package com.haodaone.attendance.service;

import com.haodaone.attendance.dto.AttendanceRegularizationDTO;
import com.haodaone.attendance.dto.AttendanceRegularizationRequest;
import com.haodaone.attendance.entity.AttendanceRegularization;
import com.haodaone.attendance.entity.AttendanceSession;
import com.haodaone.attendance.repository.AttendanceRegularizationRepository;
import com.haodaone.attendance.repository.AttendanceSessionRepository;
import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.company.entity.Company;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.tenant.TenantContext;
import com.haodaone.security.AuthorizationService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service("attendanceRegularizationService")
public class AttendanceRegularizationService {

    private final AttendanceRegularizationRepository regularizationRepository;
    private final AttendanceSessionRepository sessionRepository;
    private final EmployeeRepository employeeRepository;
    private final AttendancePolicyService policyService;
    private final AuthorizationService authorizationService;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public AttendanceRegularizationService(AttendanceRegularizationRepository regularizationRepository,
                                           AttendanceSessionRepository sessionRepository,
                                           EmployeeRepository employeeRepository,
                                           AttendancePolicyService policyService,
                                           AuthorizationService authorizationService,
                                           AuditLogService auditLogService,
                                           Clock clock) {
        this.regularizationRepository = regularizationRepository;
        this.sessionRepository = sessionRepository;
        this.employeeRepository = employeeRepository;
        this.policyService = policyService;
        this.authorizationService = authorizationService;
        this.auditLogService = auditLogService;
        this.clock = clock;
    }

    @Transactional
    public AttendanceRegularizationDTO submit(AttendanceRegularizationRequest request) {
        Employee employee = currentEmployee();
        Company company = requireCompany(employee);
        LocalDate today = LocalDate.now(clock);
        if (request.attendanceDate().isAfter(today)) {
            throw new BadRequestException("Attendance regularization cannot be requested for a future date.");
        }
        if (request.checkOutTime() != null && !request.checkOutTime().isAfter(request.checkInTime())) {
            throw new BadRequestException("Check-out time must be after check-in time.");
        }
        if (!company.isManualRegularizationEnabled()) {
            throw new BadRequestException("Attendance regularization is disabled by your organization.");
        }
        if (regularizationRepository.existsByEmployee_IdAndCompany_IdAndAttendanceDateAndDeletedFalse(
                employee.getId(), company.getId(), request.attendanceDate())) {
            throw new BadRequestException("An attendance regularization request already exists for this date.");
        }

        AttendanceRegularization entity = new AttendanceRegularization();
        entity.setCompany(company);
        entity.setEmployee(employee);
        entity.setAttendanceDate(request.attendanceDate());
        entity.setRequestedCheckIn(request.checkInTime());
        entity.setRequestedCheckOut(request.checkOutTime());
        entity.setReason(request.reason().trim());
        boolean approvalRequired = company.isRegularizationApprovalRequired();
        entity.setStatus(approvalRequired ? "PENDING" : "APPROVED");
        entity.setReviewedAt(approvalRequired ? null : LocalDateTime.now(clock));
        AttendanceRegularization saved = regularizationRepository.save(entity);
        if (!approvalRequired) {
            applyToSession(saved);
        }
        auditLogService.log("AttendanceRegularization", saved.getId(), "CREATE",
                "Regularization " + saved.getStatus() + " for employee " + employee.getId()
                        + " on " + saved.getAttendanceDate());
        return AttendanceRegularizationDTO.from(saved);
    }

    @Transactional(readOnly = true)
    public List<AttendanceRegularizationDTO> mine() {
        Employee employee = currentEmployee();
        Company company = requireCompany(employee);
        return regularizationRepository
                .findAllByEmployee_IdAndCompany_IdAndDeletedFalseOrderByAttendanceDateDesc(employee.getId(), company.getId())
                .stream().map(AttendanceRegularizationDTO::from).toList();
    }

    @Transactional(readOnly = true)
    public List<AttendanceRegularizationDTO> pending() {
        Long companyId = requiredTenant();
        var scope = authorizationService.resolveEmployeeIds("ATTENDANCE_MANAGE");
        if (scope.isPresent() && scope.get().isEmpty()) return List.of();
        var requests = scope.isEmpty()
                ? regularizationRepository.findAllByCompany_IdAndStatusAndDeletedFalseOrderByAttendanceDateAsc(companyId, "PENDING")
                : regularizationRepository.findAllByCompany_IdAndEmployee_IdInAndStatusAndDeletedFalseOrderByAttendanceDateAsc(
                        companyId, scope.get(), "PENDING");
        return requests.stream().map(AttendanceRegularizationDTO::from).toList();
    }

    @Transactional(readOnly = true)
    public boolean canReview(Long id) {
        Long companyId = TenantContext.getCurrentTenant();
        if (companyId == null || id == null) return false;
        AttendanceRegularization request = regularizationRepository
                .findByIdAndCompany_IdAndDeletedFalse(id, companyId).orElse(null);
        return request != null
                && "PENDING".equals(request.getStatus())
                && authorizationService.isAllowed("ATTENDANCE_MANAGE", "EMPLOYEE", request.getEmployee().getId());
    }

    @Transactional
    public AttendanceRegularizationDTO review(Long id, boolean approved, String note) {
        Long companyId = requiredTenant();
        AttendanceRegularization request = regularizationRepository
                .findByIdAndCompany_IdAndDeletedFalse(id, companyId)
                .orElseThrow(() -> new BadRequestException("Attendance regularization was not found."));
        if (!authorizationService.isAllowed("ATTENDANCE_MANAGE", "EMPLOYEE", request.getEmployee().getId())) {
            throw new AccessDeniedException("You are not authorized to review this employee's attendance.");
        }
        if (!"PENDING".equals(request.getStatus())) {
            throw new BadRequestException("Only pending regularization requests can be reviewed.");
        }
        Employee reviewer = currentEmployee();
        request.setReviewedByEmployee(reviewer);
        request.setReviewedAt(LocalDateTime.now(clock));
        request.setReviewNote(note == null || note.isBlank() ? null : note.trim());
        request.setStatus(approved ? "APPROVED" : "REJECTED");
        AttendanceRegularization saved = regularizationRepository.save(request);
        if (approved) applyToSession(saved);
        auditLogService.log("AttendanceRegularization", saved.getId(), approved ? "APPROVE" : "REJECT",
                (approved ? "Approved" : "Rejected") + " attendance regularization for employee "
                        + saved.getEmployee().getId() + " on " + saved.getAttendanceDate());
        return AttendanceRegularizationDTO.from(saved);
    }

    private void applyToSession(AttendanceRegularization request) {
        LocalDate date = request.getAttendanceDate();
        LocalDateTime checkIn = date.atTime(request.getRequestedCheckIn());
        LocalDateTime checkOut = request.getRequestedCheckOut() == null
                ? null : date.atTime(request.getRequestedCheckOut());
        if (checkOut != null && !checkOut.isAfter(checkIn)) {
            throw new BadRequestException("Check-out time must be after check-in time.");
        }
        AttendanceSession session = sessionRepository
                .findTopByEmployee_IdAndCompany_IdAndAttendanceDateAndStatusInOrderByCheckInTimeDesc(
                        request.getEmployee().getId(), request.getCompany().getId(), date,
                        List.of("CHECKED_IN", "CHECKED_OUT"))
                .orElseGet(AttendanceSession::new);
        if (session.getId() == null) {
            session.setCompany(request.getCompany());
            session.setEmployee(request.getEmployee());
            session.setAttendanceDate(date);
        }
        session.setCheckInTime(checkIn);
        session.setCheckOutTime(checkOut);
        session.setStatus(checkOut == null ? "CHECKED_IN" : "CHECKED_OUT");
        session.setSource("REGULARIZATION");
        session.setLocationType("MANUAL");
        session.setLocationValidationStatus("REGULARIZED");
        session.setCheckInSourceDetails(request.getReason());
        session.setCheckOutSourceDetails(request.getReviewNote());
        session.setDurationMinutes(checkOut == null ? null : java.time.Duration.between(checkIn, checkOut).toMinutes());
        sessionRepository.save(session);
    }

    private Employee currentEmployee() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new BadRequestException("Authentication required");
        }
        return employeeRepository.findByUser_UsernameAndDeletedFalse(authentication.getName())
                .or(() -> employeeRepository.findByEmailIgnoreCaseAndDeletedFalse(authentication.getName()))
                .orElseThrow(() -> new BadRequestException("Current login is not linked to an employee."));
    }

    private Company requireCompany(Employee employee) {
        if (employee.getCompany() == null || !employee.getCompany().getId().equals(requiredTenant())) {
            throw new BadRequestException("Employee is not in this company.");
        }
        return employee.getCompany();
    }

    private Long requiredTenant() {
        Long companyId = TenantContext.getCurrentTenant();
        if (companyId == null) throw new BadRequestException("Company context is required.");
        return companyId;
    }
}
