package com.haodaone.leave.service;

import com.haodaone.attendance.repository.AttendanceRecordRepository;
import com.haodaone.attendance.repository.AttendanceSessionRepository;
import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.company.service.WorkingWeek;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.leave.dto.LeavePolicyDTOs.CompOffApplyRequest;
import com.haodaone.leave.dto.LeavePolicyDTOs.CompOffDTO;
import com.haodaone.leave.entity.CompOffRequest;
import com.haodaone.leave.entity.LeaveType;
import com.haodaone.leave.repository.CompOffRequestRepository;
import com.haodaone.leave.repository.HolidayRepository;
import com.haodaone.leave.repository.LeaveTypeRepository;
import com.haodaone.notifications.service.NotificationService;
import com.haodaone.security.AuthorizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Comp-off: an employee claims credit for working on a weekly off / company holiday, a manager with
 * LEAVE_APPROVE over that employee decides (same self-approval guard as leave), and approval credits the
 * system "Compensatory Off" (CO) leave type until workDate + creditExpiryDays (default 90). Unused credit
 * lapses after expiry - computed in LeaveBalanceService, so no job is needed.
 */
@Service
public class CompOffService {

    public static final int DEFAULT_EXPIRY_DAYS = 90;
    private static final Set<String> OPEN_STATUSES = Set.of("PENDING", "APPROVED");

    private final CompOffRequestRepository compOffRequestRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final EmployeeRepository employeeRepository;
    private final HolidayRepository holidayRepository;
    private final AttendanceSessionRepository attendanceSessionRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final CompanyRepository companyRepository;
    private final AuthorizationService authorizationService;
    private final AuditLogService auditLogService;
    private final LeaveBalanceService leaveBalanceService;
    private NotificationService notificationService;

    public CompOffService(CompOffRequestRepository compOffRequestRepository, LeaveTypeRepository leaveTypeRepository,
                          EmployeeRepository employeeRepository, HolidayRepository holidayRepository,
                          AttendanceSessionRepository attendanceSessionRepository,
                          AttendanceRecordRepository attendanceRecordRepository, CompanyRepository companyRepository,
                          AuthorizationService authorizationService, AuditLogService auditLogService,
                          LeaveBalanceService leaveBalanceService) {
        this.compOffRequestRepository = compOffRequestRepository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.employeeRepository = employeeRepository;
        this.holidayRepository = holidayRepository;
        this.attendanceSessionRepository = attendanceSessionRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.companyRepository = companyRepository;
        this.authorizationService = authorizationService;
        this.auditLogService = auditLogService;
        this.leaveBalanceService = leaveBalanceService;
    }

    private HolidayCalendarService holidayCalendarService;

    @Autowired(required = false)
    public void setHolidayCalendarService(HolidayCalendarService holidayCalendarService) {
        this.holidayCalendarService = holidayCalendarService;
    }

    @Autowired
    public void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /** Finds or creates the company's "Compensatory Off" (CO) system leave type. An existing CO-coded type is adopted. */
    @Transactional
    public LeaveType ensureCompOffType(Long companyId) {
        return leaveTypeRepository.findFirstByCompany_IdAndCodeIgnoreCaseAndDeletedFalse(companyId, LeaveBalanceService.COMP_OFF_CODE)
                .map(existing -> {
                    if (existing.isSystemManaged()) return existing;
                    existing.setSystemManaged(true);
                    if (existing.getCreditExpiryDays() == null) existing.setCreditExpiryDays(DEFAULT_EXPIRY_DAYS);
                    return leaveTypeRepository.save(existing);
                })
                .orElseGet(() -> {
                    LeaveType type = new LeaveType();
                    type.setCompany(companyRepository.findById(companyId)
                            .orElseThrow(() -> new ResourceNotFoundException("Company not found")));
                    type.setName("Compensatory Off");
                    type.setCode(LeaveBalanceService.COMP_OFF_CODE);
                    type.setDefaultDaysPerYear(0);
                    type.setCarryForward(false);
                    type.setAutoApprove(false);
                    type.setPaid(true);
                    type.setSystemManaged(true);
                    type.setCreditExpiryDays(DEFAULT_EXPIRY_DAYS);
                    LeaveType saved = leaveTypeRepository.save(type);
                    auditLogService.log("LeaveType", saved.getId(), "CREATE", "Created system leave type 'Compensatory Off' (CO)");
                    return saved;
                });
    }

    /** FULL_DAY = 1, HALF_DAY = 0.5, HOURS: 8h+ = 1 day, 4h to under 8h = 0.5 day, under 4h is not creditable. */
    public static double creditDays(String duration, Double hours) {
        String value = duration == null ? "" : duration.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "FULL_DAY" -> 1.0;
            case "HALF_DAY" -> 0.5;
            case "HOURS" -> {
                if (hours == null || hours <= 0 || hours > 24) {
                    throw new BadRequestException("Enter the hours worked (between 0 and 24)");
                }
                if (hours >= 8) yield 1.0;
                if (hours >= 4) yield 0.5;
                throw new BadRequestException("At least 4 hours of work are needed for a half-day comp-off");
            }
            default -> throw new BadRequestException("Duration must be FULL_DAY, HALF_DAY or HOURS");
        };
    }

    @Transactional
    public CompOffDTO apply(CompOffApplyRequest request) {
        Long companyId = LeaveBalanceService.requiredTenant();
        if (request == null || request.getEmployeeId() == null || request.getWorkDate() == null) {
            throw new BadRequestException("Employee and the date worked are required");
        }
        Employee employee = employeeRepository.findByIdAndCompany_IdAndDeletedFalse(request.getEmployeeId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
        LocalDate workDate = request.getWorkDate();
        LocalDate today = LeaveBalanceService.today();
        if (workDate.isAfter(today)) {
            throw new BadRequestException("Comp-off can only be claimed for a day already worked");
        }
        if (employee.getDateOfJoining() != null && workDate.isBefore(employee.getDateOfJoining())) {
            throw new BadRequestException("The date is before the employee's date of joining");
        }
        LeaveType type = ensureCompOffType(companyId);
        int expiryDays = type.getCreditExpiryDays() != null && type.getCreditExpiryDays() > 0 ? type.getCreditExpiryDays() : DEFAULT_EXPIRY_DAYS;
        if (workDate.plusDays(expiryDays).isBefore(today)) {
            throw new BadRequestException("Comp-off must be claimed within " + expiryDays + " days of the day worked");
        }
        // Employee-aware when available: only holidays for their location / optional holidays they picked count.
        boolean holiday = holidayCalendarService != null
                ? holidayCalendarService.isHolidayFor(companyId, employee, workDate)
                : holidayRepository.existsByCompany_IdAndDateAndDeletedFalse(companyId, workDate);
        boolean weeklyOff = WorkingWeek.of(employee.getCompany()).isWeeklyOff(workDate);
        if (!holiday && !weeklyOff) {
            throw new BadRequestException(workDate + " was a working day - comp-off can only be claimed for a weekly off or a company holiday");
        }
        if (compOffRequestRepository.existsByCompany_IdAndEmployee_IdAndWorkDateAndStatusInAndDeletedFalse(companyId, employee.getId(), workDate, OPEN_STATUSES)) {
            throw new BadRequestException("A comp-off request for " + workDate + " already exists");
        }
        String duration = request.getDuration() == null ? "" : request.getDuration().trim().toUpperCase(Locale.ROOT);
        double days = creditDays(duration, request.getHours());
        Boolean attendance = attendanceOn(companyId, employee.getId(), workDate);
        if (Boolean.FALSE.equals(attendance)) {
            throw new BadRequestException("No attendance is recorded for " + workDate + " - regularise attendance for that day first");
        }

        CompOffRequest entity = new CompOffRequest();
        entity.setCompany(employee.getCompany());
        entity.setEmployee(employee);
        entity.setWorkDate(workDate);
        entity.setDuration(duration);
        entity.setHours("HOURS".equals(duration) ? request.getHours() : null);
        entity.setDays(days);
        entity.setDayType(holiday ? "HOLIDAY" : "WEEKLY_OFF");
        entity.setAttendanceVerified(attendance);
        entity.setReason(trim(request.getReason()));
        entity.setStatus("PENDING");
        CompOffRequest saved = compOffRequestRepository.save(entity);

        auditLogService.log("CompOffRequest", saved.getId(), "CREATE", String.format(Locale.ROOT,
                "%s claimed %s day(s) comp-off for working on %s (%s)", employee.getFullName(),
                LeaveBalanceService.fmt(days), workDate, saved.getDayType()));
        if (notificationService != null) {
            notificationService.notifyPermissionRecipients(companyId, employee.getId(), "LEAVE_APPROVE",
                    "COMP_OFF_SUBMITTED", "Comp-off request needs review",
                    employee.getFullName() + " claimed comp-off for working on " + workDate + ".",
                    "CompOffRequest", saved.getId());
        }
        return CompOffDTO.from(saved);
    }

    /**
     * Best effort: TRUE if any check-in session or device punch exists that day, FALSE if the employee has
     * attendance data around that date but none on it, NULL if there is no attendance data to check against.
     */
    Boolean attendanceOn(Long companyId, Long employeeId, LocalDate date) {
        if (!attendanceSessionRepository.findAllByCompany_IdAndEmployee_IdAndDeletedFalseAndAttendanceDateBetweenOrderByAttendanceDateDescCheckInTimeDesc(
                companyId, employeeId, date, date).isEmpty()) {
            return true;
        }
        if (!attendanceRecordRepository.findAllByCompany_IdAndEmployee_IdAndDeletedFalseAndPunchTimeBetweenOrderByPunchTimeDesc(
                companyId, employeeId, date.atStartOfDay(), date.plusDays(1).atStartOfDay().minusNanos(1)).isEmpty()) {
            return true;
        }
        LocalDate from = date.minusDays(60);
        LocalDate to = date.plusDays(30);
        boolean anyData = !attendanceSessionRepository.findAllByCompany_IdAndEmployee_IdAndDeletedFalseAndAttendanceDateBetweenOrderByAttendanceDateDescCheckInTimeDesc(
                companyId, employeeId, from, to).isEmpty()
                || !attendanceRecordRepository.findAllByCompany_IdAndEmployee_IdAndDeletedFalseAndPunchTimeBetweenOrderByPunchTimeDesc(
                companyId, employeeId, from.atStartOfDay(), to.plusDays(1).atStartOfDay()).isEmpty();
        return anyData ? Boolean.FALSE : null;
    }

    @Transactional
    public CompOffDTO decide(Long id, boolean approve, String note) {
        Long companyId = LeaveBalanceService.requiredTenant();
        CompOffRequest request = compOffRequestRepository.findByIdAndCompany_IdAndDeletedFalse(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Comp-off request not found: " + id));
        Long employeeId = request.getEmployee().getId();
        if (!authorizationService.isAllowed("LEAVE_APPROVE", "EMPLOYEE", employeeId)) {
            throw new AccessDeniedException("LEAVE_APPROVE is not authorized for this employee.");
        }
        if (!"PENDING".equals(request.getStatus())) {
            throw new BadRequestException("Only pending requests can be approved or rejected (current status: " + request.getStatus() + ")");
        }
        boolean ownRequest = leaveBalanceService.currentEmployee().map(me -> me.getId().equals(employeeId)).orElse(false);
        if (ownRequest && !authorizationService.hasOrganizationScope("LEAVE_APPROVE")) {
            throw new AccessDeniedException("You can't approve or reject your own comp-off request.");
        }
        if (approve) {
            LeaveType type = ensureCompOffType(companyId);
            int expiryDays = type.getCreditExpiryDays() != null && type.getCreditExpiryDays() > 0 ? type.getCreditExpiryDays() : DEFAULT_EXPIRY_DAYS;
            request.setExpiresOn(request.getWorkDate().plusDays(expiryDays));
        }
        request.setStatus(approve ? "APPROVED" : "REJECTED");
        request.setDecidedAt(LocalDateTime.now(LeaveBalanceService.IST));
        request.setDecisionNote(trim(note));
        leaveBalanceService.currentEmployee().ifPresent(request::setDecidedBy);
        CompOffRequest saved = compOffRequestRepository.save(request);

        auditLogService.log("CompOffRequest", saved.getId(), approve ? "APPROVE" : "REJECT",
                (approve ? "Approved " + LeaveBalanceService.fmt(saved.getDays()) + " day(s) comp-off, expires " + saved.getExpiresOn() : "Rejected comp-off")
                        + (note != null && !note.isBlank() ? ": " + note : ""));
        if (notificationService != null && saved.getEmployee().getUser() != null) {
            notificationService.notifyUser(companyId, saved.getEmployee().getUser().getId(), "COMP_OFF_DECISION",
                    approve ? "Comp-off approved" : "Comp-off declined",
                    approve ? "Your comp-off for " + saved.getWorkDate() + " was approved. Use it by " + saved.getExpiresOn() + "."
                            : "Your comp-off request for " + saved.getWorkDate() + " was declined.",
                    "CompOffRequest", saved.getId(), "NORMAL");
        }
        return CompOffDTO.from(saved);
    }

    @Transactional
    public CompOffDTO cancel(Long id) {
        Long companyId = LeaveBalanceService.requiredTenant();
        CompOffRequest request = compOffRequestRepository.findByIdAndCompany_IdAndDeletedFalse(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Comp-off request not found: " + id));
        boolean owner = leaveBalanceService.currentEmployee().map(me -> me.getId().equals(request.getEmployee().getId())).orElse(false);
        boolean approver = authorizationService.isAllowed("LEAVE_APPROVE", "EMPLOYEE", request.getEmployee().getId());
        if (!owner && !approver) {
            throw new AccessDeniedException("Only the requester or an authorized approver can cancel this request.");
        }
        if (!"PENDING".equals(request.getStatus())) {
            throw new BadRequestException("Only pending comp-off requests can be cancelled");
        }
        request.setStatus("CANCELLED");
        CompOffRequest saved = compOffRequestRepository.save(request);
        auditLogService.log("CompOffRequest", saved.getId(), "CANCEL", "Comp-off request cancelled");
        return CompOffDTO.from(saved);
    }

    @Transactional(readOnly = true)
    public List<CompOffDTO> listForEmployee(Long employeeId) {
        Long companyId = LeaveBalanceService.requiredTenant();
        return compOffRequestRepository.findAllByCompany_IdAndEmployee_IdAndDeletedFalseOrderByWorkDateDesc(companyId, employeeId)
                .stream().map(CompOffDTO::from).toList();
    }

    /** Requests the caller may decide: LEAVE_APPROVE scope (team for managers, everyone for org scope). */
    @Transactional(readOnly = true)
    public List<CompOffDTO> listForApprover(String status) {
        Long companyId = LeaveBalanceService.requiredTenant();
        var scope = authorizationService.resolveEmployeeIds("LEAVE_APPROVE");
        if (scope.isPresent() && scope.get().isEmpty()) return List.of();
        List<CompOffRequest> requests = scope.isPresent()
                ? compOffRequestRepository.findAllByCompany_IdAndEmployee_IdInAndDeletedFalseOrderByWorkDateDesc(companyId, scope.get())
                : compOffRequestRepository.findAllByCompany_IdAndDeletedFalseOrderByWorkDateDesc(companyId);
        String wanted = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        return requests.stream()
                .filter(r -> wanted.isEmpty() || wanted.equals(r.getStatus()))
                .map(CompOffDTO::from)
                .toList();
    }

    private static String trim(String value) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        return trimmed.length() > 500 ? trimmed.substring(0, 500) : trimmed;
    }
}
