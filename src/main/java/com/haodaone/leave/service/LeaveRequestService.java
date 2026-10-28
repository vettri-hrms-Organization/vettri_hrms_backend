package com.haodaone.leave.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.leave.dto.ApplyLeaveRequest;
import com.haodaone.leave.dto.LeaveBalanceDTO;
import com.haodaone.leave.dto.LeaveRequestDTO;
import com.haodaone.leave.entity.LeaveBalance;
import com.haodaone.leave.entity.LeaveRequest;
import com.haodaone.leave.entity.LeaveType;
import com.haodaone.leave.repository.HolidayRepository;
import com.haodaone.leave.repository.LeaveBalanceRepository;
import com.haodaone.leave.repository.LeaveRequestRepository;
import com.haodaone.leave.repository.LeaveTypeRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.haodaone.notifications.service.NotificationService;

import com.haodaone.company.service.WorkingWeek;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import com.haodaone.tenant.TenantContext;

@Service
public class LeaveRequestService {

    private static final Set<String> DECIDABLE = Set.of("PENDING");
    private static final Set<String> HALF_DAY_SESSIONS = Set.of("FIRST_HALF", "SECOND_HALF");

    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final HolidayRepository holidayRepository;
    private final EmployeeRepository employeeRepository;
    private final AuditLogService auditLogService;
    private final com.haodaone.security.AuthorizationService authorizationService;
    private NotificationService notificationService;

    public LeaveRequestService(LeaveRequestRepository leaveRequestRepository, LeaveTypeRepository leaveTypeRepository,
                                LeaveBalanceRepository leaveBalanceRepository, HolidayRepository holidayRepository,
                                EmployeeRepository employeeRepository, AuditLogService auditLogService,
                                com.haodaone.security.AuthorizationService authorizationService) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.holidayRepository = holidayRepository;
        this.employeeRepository = employeeRepository;
        this.auditLogService = auditLogService;
        this.authorizationService = authorizationService;
    }

    @Autowired
    public void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<LeaveRequestDTO> listAll(String status) {
        Long companyId = requiredTenant();
        var scope = authorizationService.resolveEmployeeIdsForAny("LEAVE_VIEW", "LEAVE_APPROVE");
        if (scope.isPresent() && scope.get().isEmpty()) return List.of();
        if (scope.isPresent()) return listForEmployees(scope.get(), status);
        List<LeaveRequest> requests = (status == null || status.isBlank())
                ? leaveRequestRepository.findAllByCompany_IdOrderByStartDateDesc(companyId)
            : leaveRequestRepository.findAllByCompany_IdAndStatusOrderByStartDateAsc(companyId, status.toUpperCase());
        return requests.stream().map(LeaveRequestDTO::from).toList();
    }

    /** Same shape as listAll but scoped to a specific set of employee IDs -
     *  used for a manager's team-scoped approval queue. Empty employeeIds
     *  returns an empty list rather than falling back to "all", since an
     *  empty team should mean nothing to approve, not everything. Blank
     *  status means every status, mirroring listAll's own behavior. */
    @Transactional(readOnly = true)
    public List<LeaveRequestDTO> listForEmployees(java.util.Collection<Long> employeeIds, String status) {
        if (employeeIds == null || employeeIds.isEmpty()) {
            return List.of();
        }
        Long companyId = requiredTenant();
        List<Long> ids = new java.util.ArrayList<>(employeeIds);
        List<LeaveRequest> requests = (status == null || status.isBlank())
                ? leaveRequestRepository.findAllByCompany_IdAndEmployeeIdInOrderByStartDateDesc(companyId, ids)
                : leaveRequestRepository.findAllByCompany_IdAndEmployeeIdInAndStatusOrderByStartDateAsc(companyId, ids, status.toUpperCase());
        return requests.stream().map(LeaveRequestDTO::from).toList();
    }

    /**
     * Resolves "my team" from the given login (via the Employee.user link -
     * see EmployeeSecurity for the same pattern) and returns that team's
     * leave requests at the given status. Shared by DashboardController's
     * "My Team" widget and the Approval Center's team-scoped view so
     * "who is my team" is resolved exactly one way, not reimplemented
     * per caller.
     */
    @Transactional(readOnly = true)
    public List<LeaveRequestDTO> listForManagerTeam(String username, String status) {
        var scope = authorizationService.resolveEmployeeIds("LEAVE_APPROVE");
        if (scope.isPresent()) return listForEmployees(scope.get(), status);
        Long companyId = requiredTenant();
        List<LeaveRequest> requests = (status == null || status.isBlank())
                ? leaveRequestRepository.findAllByCompany_IdOrderByStartDateDesc(companyId)
                : leaveRequestRepository.findAllByCompany_IdAndStatusOrderByStartDateAsc(companyId, status.toUpperCase());
        return requests.stream().map(LeaveRequestDTO::from).toList();
    }

    @Transactional(readOnly = true)
    public List<LeaveRequestDTO> listByEmployee(Long employeeId) {
        return leaveRequestRepository.findAllByEmployee_Company_IdAndEmployeeIdOrderByStartDateDesc(requiredTenant(), employeeId).stream()
                .map(LeaveRequestDTO::from)
                .toList();
    }

    /** Policy-aware balances (accrual, cap, carry expiry, comp-off, adjustments, encashment - V45). Setter-injected so plain-constructor tests keep the legacy calculation below. */
    private LeaveBalanceService leaveBalanceService;

    @Autowired
    public void setLeaveBalanceService(LeaveBalanceService leaveBalanceService) {
        this.leaveBalanceService = leaveBalanceService;
    }

    @Transactional(readOnly = true)
    public List<LeaveBalanceDTO> getBalances(Long employeeId, int year) {
        Long companyId = requiredTenant();
        if (leaveBalanceService != null) {
            return leaveBalanceService.balancesFor(companyId, employeeId, year);
        }
        List<LeaveType> activeTypes = leaveTypeRepository.findAllByCompany_IdAndDeletedFalseOrderByNameAsc(companyId).stream()
                .filter(LeaveType::isActive)
                .toList();

        return activeTypes.stream().map(type -> {
            LeaveBalance balance = leaveBalanceRepository.findByEmployee_Company_IdAndEmployeeIdAndLeaveTypeIdAndYear(companyId, employeeId, type.getId(), year)
                    .orElse(null);
            double allocated = balance != null ? balance.getAllocatedDays() : type.getDefaultDaysPerYear();
            double carried = balance != null ? balance.getCarriedForwardDays() : 0;
            double used = leaveRequestRepository.sumApprovedDays(companyId, employeeId, type.getId(), year);
            return new LeaveBalanceDTO(type.getId(), type.getName(), year, allocated, carried, used);
        }).toList();
    }

    @Transactional
    public LeaveRequestDTO apply(ApplyLeaveRequest request) {
        ValidatedLeaveApplication validated = validateApplication(request);
        // Lock the employee, then validate again so two concurrent requests can't both pass the balance/overlap checks.
        employeeRepository.lockById(validated.employee().getId());
        validated = validateApplication(request);
        Employee employee = validated.employee();
        LeaveType leaveType = validated.leaveType();
        double requestedDays = validated.requestedDays();

        LeaveRequest leaveRequest = new LeaveRequest();
        leaveRequest.setEmployee(employee);
        leaveRequest.setCompany(employee.getCompany());
        leaveRequest.setLeaveType(leaveType);
        leaveRequest.setStartDate(request.getStartDate());
        leaveRequest.setEndDate(request.getEndDate());
        leaveRequest.setDays(requestedDays);
        boolean halfDay = Boolean.TRUE.equals(request.getHalfDay());
        leaveRequest.setHalfDay(halfDay);
        leaveRequest.setHalfDaySession(halfDay ? normalizeHalfDaySession(request) : null);
        leaveRequest.setReason(request.getReason());
        boolean autoApproved = leaveType.isAutoApprove();
        leaveRequest.setStatus(autoApproved ? "APPROVED" : "PENDING");
        if (autoApproved) {
            leaveRequest.setDecidedAt(LocalDateTime.now());
            leaveRequest.setDecisionNote("Automatically approved by leave policy");
        }

        LeaveRequest saved = leaveRequestRepository.save(leaveRequest);
        if (notificationService != null) {
            if (autoApproved) {
                if (employee.getUser() != null) {
                    notificationService.notifyUser(validated.companyId(), employee.getUser().getId(), "LEAVE_DECISION",
                            "Leave request approved", "Your leave request was automatically approved by the leave policy.",
                            "LeaveRequest", saved.getId(), "NORMAL");
                }
            } else {
                notificationService.notifyPermissionRecipients(validated.companyId(), employee.getId(), "LEAVE_APPROVE",
                        "LEAVE_SUBMITTED", "Leave request needs review",
                        employee.getFullName() + " submitted a leave request for " + saved.getStartDate()
                                + " to " + saved.getEndDate() + ".",
                        "LeaveRequest", saved.getId());
            }
        }
        auditLogService.log("LeaveRequest", saved.getId(), "CREATE",
                String.format("%s applied for %.1f day(s) of %s (%s to %s)%s", employee.getFullName(), requestedDays,
                        leaveType.getName(), request.getStartDate(), request.getEndDate(),
                        halfDay ? " - half day (" + leaveRequest.getHalfDaySession() + ")" : ""));
        if (autoApproved) {
            auditLogService.log("LeaveRequest", saved.getId(), "AUTO_APPROVE",
                "Automatically approved by leave type policy '" + leaveType.getName() + "'");
        }
        return LeaveRequestDTO.from(saved);
    }

    @Transactional(readOnly = true)
    public LeaveApplicationPreview preview(ApplyLeaveRequest request) {
        ValidatedLeaveApplication validated = validateApplication(request);
        return new LeaveApplicationPreview(
                validated.employee().getId(),
                validated.leaveType().getId(),
                validated.leaveType().getName(),
                request.getStartDate(),
                request.getEndDate(),
                validated.requestedDays(),
                validated.remainingDays()
        );
    }

    private ValidatedLeaveApplication validateApplication(ApplyLeaveRequest request) {
        if (request == null || request.getStartDate() == null || request.getEndDate() == null) {
            throw new BadRequestException("Leave dates are required");
        }
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BadRequestException("End date cannot be before start date");
        }

        Employee employee = resolveEmployeeForApply(request);
        Long companyId = requiredTenant();
        if (employee.getCompany() == null || !companyId.equals(employee.getCompany().getId())) throw new BadRequestException("Employee is not in this company");
        LeaveType leaveType = leaveTypeRepository.findByIdAndCompany_IdAndDeletedFalse(request.getLeaveTypeId(), companyId)
                .orElseThrow(() -> new BadRequestException("Unknown leave type: " + request.getLeaveTypeId()));

        boolean halfDay = Boolean.TRUE.equals(request.getHalfDay());
        String halfDaySession = halfDay ? normalizeHalfDaySession(request) : null;

        boolean overlapping = leaveRequestRepository.findOverlapping(employee.getId(), request.getStartDate(), request.getEndDate())
                .stream()
                .anyMatch(existing -> !(halfDay && existing.isHalfDay()
                        && existing.getHalfDaySession() != null
                        && !existing.getHalfDaySession().equalsIgnoreCase(halfDaySession)));
        if (overlapping) {
            throw new BadRequestException("This employee already has a pending or approved leave request overlapping these dates");
        }

        WorkingWeek workingWeek = WorkingWeek.of(employee.getCompany());
        double workingDays = countBusinessDays(companyId, employee, workingWeek, request.getStartDate(), request.getEndDate());
        if (workingDays <= 0) {
            throw new BadRequestException(halfDay
                    ? "Half-day leave can only be applied on a working day"
                    : "The selected date range contains no working days");
        }
        double requestedDays = halfDay ? 0.5 : workingDays;

        List<LeaveBalanceDTO> balances = getBalances(employee.getId(), request.getStartDate().getYear());
        LeaveBalanceDTO balance = balances.stream().filter(b -> b.getLeaveTypeId().equals(leaveType.getId())).findFirst().orElse(null);
        double pendingDays = LeaveBalanceService.isCompOff(leaveType)
                ? leaveRequestRepository.sumPendingDaysAllYears(companyId, employee.getId(), leaveType.getId())
                : leaveRequestRepository.sumPendingDays(companyId, employee.getId(), leaveType.getId(), request.getStartDate().getYear());
        // Policy negative allowance lets the balance dip to -N days (0 unless configured).
        double available = balance == null ? 0 : balance.getRemainingDays() + balance.getNegativeAllowanceDays() - pendingDays;
        if (balance != null && requestedDays > available) {
            throw new BadRequestException(String.format(
                    "Insufficient balance: requesting %.1f day(s) but only %.1f day(s) of %s are available%s",
                    requestedDays, Math.max(available, 0), leaveType.getName(),
                    pendingDays > 0 ? String.format(" (%.1f day(s) are in pending requests)", pendingDays) : ""));
        }

        return new ValidatedLeaveApplication(
                employee, leaveType, companyId, requestedDays, available
        );
    }

    private record ValidatedLeaveApplication(
            Employee employee,
            LeaveType leaveType,
            Long companyId,
            double requestedDays,
            double remainingDays
    ) {}

    public record LeaveApplicationPreview(
            Long employeeId,
            Long leaveTypeId,
            String leaveTypeName,
            LocalDate startDate,
            LocalDate endDate,
            double requestedDays,
            double remainingDays
    ) {}

    @Transactional
    public LeaveRequestDTO decide(Long id, boolean approve, String note) {
        LeaveRequest leaveRequest = leaveRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request not found: " + id));
        if (leaveRequest.getCompany() == null || !requiredTenant().equals(leaveRequest.getCompany().getId())) throw new ResourceNotFoundException("Leave request not found: " + id);
        if (!authorizationService.isAllowed("LEAVE_APPROVE", "EMPLOYEE", leaveRequest.getEmployee().getId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "LEAVE_APPROVE is not authorized for this employee.");
        }

        if (!DECIDABLE.contains(leaveRequest.getStatus())) {
            throw new BadRequestException("Only pending requests can be approved or rejected (current status: " + leaveRequest.getStatus() + ")");
        }
        boolean ownRequest = currentEmployee().map(me -> me.getId().equals(leaveRequest.getEmployee().getId())).orElse(false);
        if (ownRequest && !authorizationService.hasOrganizationScope("LEAVE_APPROVE")) {
            throw new org.springframework.security.access.AccessDeniedException("You can't approve or reject your own leave request.");
        }
        if (approve) {
            employeeRepository.lockById(leaveRequest.getEmployee().getId());
            int year = leaveRequest.getStartDate().getYear();
            double remaining = getBalances(leaveRequest.getEmployee().getId(), year).stream()
                    .filter(b -> b.getLeaveTypeId().equals(leaveRequest.getLeaveType().getId()))
                    .mapToDouble(b -> b.getRemainingDays() + b.getNegativeAllowanceDays())
                    .findFirst().orElse(Double.MAX_VALUE);
            if (leaveRequest.getDays() > remaining) {
                throw new BadRequestException(String.format(
                        "Approving would exceed the balance: %.1f day(s) requested, %.1f day(s) of %s left",
                        leaveRequest.getDays(), Math.max(remaining, 0), leaveRequest.getLeaveType().getName()));
            }
        }

        leaveRequest.setStatus(approve ? "APPROVED" : "REJECTED");
        leaveRequest.setDecidedAt(LocalDateTime.now());
        leaveRequest.setDecisionNote(note);
        currentEmployee().ifPresent(leaveRequest::setDecidedBy);

        LeaveRequest saved = leaveRequestRepository.save(leaveRequest);
        if (notificationService != null && saved.getEmployee().getUser() != null) {
            notificationService.notifyUser(saved.getCompany().getId(), saved.getEmployee().getUser().getId(),
                    "LEAVE_DECISION", approve ? "Leave request approved" : "Leave request declined",
                    approve ? "Your leave request was approved." : "Your leave request was declined.",
                    "LeaveRequest", saved.getId(), "NORMAL");
        }
        auditLogService.log("LeaveRequest", saved.getId(), approve ? "APPROVE" : "REJECT",
                (note != null && !note.isBlank()) ? note : "No note provided");
        return LeaveRequestDTO.from(saved);
    }

    @Transactional
    public LeaveRequestDTO cancel(Long id) {
        LeaveRequest leaveRequest = leaveRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request not found: " + id));
        if (leaveRequest.getCompany() == null || !requiredTenant().equals(leaveRequest.getCompany().getId())) throw new ResourceNotFoundException("Leave request not found: " + id);
        boolean owner = currentEmployee().map(employee -> employee.getId().equals(leaveRequest.getEmployee().getId())).orElse(false);
        boolean approver = authorizationService.isAllowed("LEAVE_APPROVE", "EMPLOYEE", leaveRequest.getEmployee().getId());
        if (!owner && !approver) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "Only the request owner or an authorized leave approver can cancel this request.");
        }

        if (leaveRequest.getStatus().equals("CANCELLED") || leaveRequest.getStatus().equals("REJECTED")) {
            throw new BadRequestException("This request is already " + leaveRequest.getStatus().toLowerCase());
        }
        if (leaveRequest.getStatus().equals("APPROVED") && leaveRequest.getStartDate().isBefore(LocalDate.now())) {
            throw new BadRequestException("Cannot cancel a leave that has already started");
        }

        leaveRequest.setStatus("CANCELLED");
        LeaveRequest saved = leaveRequestRepository.save(leaveRequest);
        auditLogService.log("LeaveRequest", saved.getId(), "CANCEL", "Leave request cancelled");
        return LeaveRequestDTO.from(saved);
    }

    private HolidayCalendarService holidayCalendarService;

    /** Employee-aware holiday rules (location + optional holidays); optional so unit tests can omit it. */
    @Autowired(required = false)
    public void setHolidayCalendarService(HolidayCalendarService holidayCalendarService) {
        this.holidayCalendarService = holidayCalendarService;
    }

    /**
     * Excludes the company's weekly offs (see WorkingWeek) and every holiday that applies to the
     * employee: holidays for their work location (or company-wide ones) and optional holidays they
     * picked. Without the calendar service every holiday on the calendar is excluded (legacy behaviour).
     */
    private double countBusinessDays(Long companyId, Employee employee, WorkingWeek workingWeek, LocalDate start, LocalDate end) {
        java.util.Collection<LocalDate> holidays = holidayCalendarService != null
                ? holidayCalendarService.holidayDatesFor(companyId, employee, start, end)
                : holidayRepository.findAllByCompany_IdAndDateBetweenAndDeletedFalse(companyId, start, end).stream()
                        .map(h -> h.getDate()).toList();

        double count = 0;
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            if (!workingWeek.isWeeklyOff(date) && !holidays.contains(date)) {
                count++;
            }
        }
        return count;
    }

    private String normalizeHalfDaySession(ApplyLeaveRequest request) {
        if (!request.getStartDate().equals(request.getEndDate())) {
            throw new BadRequestException("Half-day leave must start and end on the same date");
        }
        String session = request.getHalfDaySession() == null ? "" : request.getHalfDaySession().trim().toUpperCase(java.util.Locale.ROOT);
        if (!HALF_DAY_SESSIONS.contains(session)) {
            throw new BadRequestException("Choose the first half or the second half for a half-day leave");
        }
        return session;
    }

    private Employee resolveEmployeeForApply(ApplyLeaveRequest request) {
        if (request == null || request.getEmployeeId() == null) {
            throw new BadRequestException("Employee is required");
        }

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            java.util.Optional<Employee> currentEmployee = employeeRepository.findByUser_UsernameAndDeletedFalse(authentication.getName());
            if (currentEmployee.isPresent()) {
                Employee current = currentEmployee.get();
                if (request.getEmployeeId().equals(current.getId())) {
                    return current;
                }
                if (!authorizationService.isAllowed("LEAVE_APPLY", "EMPLOYEE", request.getEmployeeId())) {
                    throw new org.springframework.security.access.AccessDeniedException(
                            "LEAVE_APPLY is not authorized for this employee.");
                }
            }
        }

        Employee target = employeeRepository.findById(request.getEmployeeId())
                .orElseThrow(() -> new BadRequestException("Unknown employee: " + request.getEmployeeId()));
        if (target.getCompany() == null || !requiredTenant().equals(target.getCompany().getId())) {
            throw new BadRequestException("Employee is not in this company");
        }
        if (!authorizationService.isAllowed("LEAVE_APPLY", "EMPLOYEE", target.getId())) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "LEAVE_APPLY is not authorized for this employee.");
        }
        return target;
    }

    private Long requiredTenant() { Long tenant = TenantContext.getCurrentTenant(); if (tenant == null) throw new BadRequestException("Company context is required"); return tenant; }

    private java.util.Optional<Employee> currentEmployee() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return java.util.Optional.empty();
        }
        return employeeRepository.findByUser_UsernameAndDeletedFalse(authentication.getName());
    }
}
