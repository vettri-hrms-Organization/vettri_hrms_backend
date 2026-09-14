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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import com.haodaone.tenant.TenantContext;

@Service
public class LeaveRequestService {

    private static final Set<String> DECIDABLE = Set.of("PENDING");

    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final HolidayRepository holidayRepository;
    private final EmployeeRepository employeeRepository;
    private final AuditLogService auditLogService;

    public LeaveRequestService(LeaveRequestRepository leaveRequestRepository, LeaveTypeRepository leaveTypeRepository,
                                LeaveBalanceRepository leaveBalanceRepository, HolidayRepository holidayRepository,
                                EmployeeRepository employeeRepository, AuditLogService auditLogService) {
        this.leaveRequestRepository = leaveRequestRepository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.holidayRepository = holidayRepository;
        this.employeeRepository = employeeRepository;
        this.auditLogService = auditLogService;
    }

    public List<LeaveRequestDTO> listAll(String status) {
        Long companyId = requiredTenant();
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
    public List<LeaveRequestDTO> listForEmployees(List<Long> employeeIds, String status) {
        if (employeeIds == null || employeeIds.isEmpty()) {
            return List.of();
        }
        List<LeaveRequest> requests = (status == null || status.isBlank())
                ? leaveRequestRepository.findAllByEmployeeIdInOrderByStartDateDesc(employeeIds)
                : leaveRequestRepository.findAllByEmployeeIdInAndStatusOrderByStartDateAsc(employeeIds, status.toUpperCase());
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
    public List<LeaveRequestDTO> listForManagerTeam(String username, String status) {
        Employee me = employeeRepository.findByUser_UsernameAndDeletedFalse(username).orElse(null);
        if (me == null) {
            return List.of();
        }
        List<Long> teamIds = employeeRepository.findAllByReportingManagerIdAndDeletedFalse(me.getId())
                .stream().map(Employee::getId).toList();
        return listForEmployees(teamIds, status);
    }

    public List<LeaveRequestDTO> listByEmployee(Long employeeId) {
        return leaveRequestRepository.findAllByEmployee_Company_IdAndEmployeeIdOrderByStartDateDesc(requiredTenant(), employeeId).stream()
                .map(LeaveRequestDTO::from)
                .toList();
    }

    public List<LeaveBalanceDTO> getBalances(Long employeeId, int year) {
        Long companyId = requiredTenant();
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
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BadRequestException("End date cannot be before start date");
        }

        Employee employee = employeeRepository.findById(request.getEmployeeId())
                .orElseThrow(() -> new BadRequestException("Unknown employee: " + request.getEmployeeId()));
        Long companyId = requiredTenant();
        if (employee.getCompany() == null || !companyId.equals(employee.getCompany().getId())) throw new BadRequestException("Employee is not in this company");
        LeaveType leaveType = leaveTypeRepository.findByIdAndCompany_IdAndDeletedFalse(request.getLeaveTypeId(), companyId)
                .orElseThrow(() -> new BadRequestException("Unknown leave type: " + request.getLeaveTypeId()));

        if (!leaveRequestRepository.findOverlapping(employee.getId(), request.getStartDate(), request.getEndDate()).isEmpty()) {
            throw new BadRequestException("This employee already has a pending or approved leave request overlapping these dates");
        }

        double requestedDays = countBusinessDays(companyId, request.getStartDate(), request.getEndDate());
        if (requestedDays <= 0) {
            throw new BadRequestException("The selected date range contains no working days");
        }

        List<LeaveBalanceDTO> balances = getBalances(employee.getId(), request.getStartDate().getYear());
        LeaveBalanceDTO balance = balances.stream().filter(b -> b.getLeaveTypeId().equals(leaveType.getId())).findFirst().orElse(null);
        if (balance != null && requestedDays > balance.getRemainingDays()) {
            throw new BadRequestException(String.format(
                    "Insufficient balance: requesting %.1f day(s) but only %.1f day(s) of %s remain",
                    requestedDays, balance.getRemainingDays(), leaveType.getName()));
        }

        LeaveRequest leaveRequest = new LeaveRequest();
        leaveRequest.setEmployee(employee);
        leaveRequest.setCompany(employee.getCompany());
        leaveRequest.setLeaveType(leaveType);
        leaveRequest.setStartDate(request.getStartDate());
        leaveRequest.setEndDate(request.getEndDate());
        leaveRequest.setDays(requestedDays);
        leaveRequest.setReason(request.getReason());
        boolean autoApproved = leaveType.isAutoApprove();
        leaveRequest.setStatus(autoApproved ? "APPROVED" : "PENDING");
        if (autoApproved) {
            leaveRequest.setDecidedAt(LocalDateTime.now());
            leaveRequest.setDecisionNote("Automatically approved by leave policy");
        }

        LeaveRequest saved = leaveRequestRepository.save(leaveRequest);
        auditLogService.log("LeaveRequest", saved.getId(), "CREATE",
                String.format("%s applied for %.1f day(s) of %s (%s to %s)", employee.getFullName(), requestedDays,
                        leaveType.getName(), request.getStartDate(), request.getEndDate()));
        if (autoApproved) {
            auditLogService.log("LeaveRequest", saved.getId(), "AUTO_APPROVE",
                "Automatically approved by leave type policy '" + leaveType.getName() + "'");
        }
        return LeaveRequestDTO.from(saved);
    }

    @Transactional
    public LeaveRequestDTO decide(Long id, boolean approve, String note) {
        LeaveRequest leaveRequest = leaveRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request not found: " + id));
        if (leaveRequest.getCompany() == null || !requiredTenant().equals(leaveRequest.getCompany().getId())) throw new ResourceNotFoundException("Leave request not found: " + id);

        if (!DECIDABLE.contains(leaveRequest.getStatus())) {
            throw new BadRequestException("Only pending requests can be approved or rejected (current status: " + leaveRequest.getStatus() + ")");
        }

        leaveRequest.setStatus(approve ? "APPROVED" : "REJECTED");
        leaveRequest.setDecidedAt(LocalDateTime.now());
        leaveRequest.setDecisionNote(note);
        currentEmployee().ifPresent(leaveRequest::setDecidedBy);

        LeaveRequest saved = leaveRequestRepository.save(leaveRequest);
        auditLogService.log("LeaveRequest", saved.getId(), approve ? "APPROVE" : "REJECT",
                (note != null && !note.isBlank()) ? note : "No note provided");
        return LeaveRequestDTO.from(saved);
    }

    @Transactional
    public LeaveRequestDTO cancel(Long id) {
        LeaveRequest leaveRequest = leaveRequestRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request not found: " + id));
        if (leaveRequest.getCompany() == null || !requiredTenant().equals(leaveRequest.getCompany().getId())) throw new ResourceNotFoundException("Leave request not found: " + id);

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

    /** Excludes weekends and any date on the company Holiday calendar. */
    private double countBusinessDays(Long companyId, LocalDate start, LocalDate end) {
        List<LocalDate> holidays = holidayRepository.findAllByCompany_IdAndDateBetweenAndDeletedFalse(companyId, start, end).stream()
                .map(h -> h.getDate()).toList();

        double count = 0;
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            boolean isWeekend = date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
            if (!isWeekend && !holidays.contains(date)) {
                count++;
            }
        }
        return count;
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
