package com.haodaone.attendance.controller;

import com.haodaone.attendance.dto.AttendanceExceptionDTO;
import com.haodaone.attendance.dto.AttendanceRecordDTO;
import com.haodaone.attendance.repository.AttendanceRecordRepository;
import com.haodaone.attendance.service.AttendanceEventPublisher;
import com.haodaone.employee.dto.EmployeeSummaryDTO;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.leave.repository.HolidayRepository;
import com.haodaone.leave.repository.LeaveRequestRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import com.haodaone.tenant.TenantContext;

@RestController
@RequestMapping("/api/attendance")
public class AttendanceController {

    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AttendanceEventPublisher eventPublisher;
    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final HolidayRepository holidayRepository;

    public AttendanceController(AttendanceRecordRepository attendanceRecordRepository, AttendanceEventPublisher eventPublisher,
                                 EmployeeRepository employeeRepository, LeaveRequestRepository leaveRequestRepository,
                                 HolidayRepository holidayRepository) {
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.eventPublisher = eventPublisher;
        this.employeeRepository = employeeRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.holidayRepository = holidayRepository;
    }

    /** Defaults to today - the Live Attendance view's primary query. */
    @GetMapping
    @PreAuthorize("!hasRole('EMPLOYEE') and hasAuthority('ATTENDANCE_VIEW')")
    public List<AttendanceRecordDTO> byDate(@RequestParam(required = false) String date) {
        Long companyId = requiredTenant();
        LocalDate targetDate = date != null ? LocalDate.parse(date) : LocalDate.now();
        LocalDateTime start = targetDate.atStartOfDay();
        LocalDateTime end = start.plusDays(1);
        return attendanceRecordRepository.findAllByCompany_IdAndPunchTimeBetweenOrderByPunchTimeDesc(companyId, start, end).stream()
                .map(AttendanceRecordDTO::from)
                .toList();
    }

    /**
     * Active employees with no punch at all on a given working day, and
     * not on approved leave that day - see AttendanceExceptionDTO for why
     * "missing entirely" is the only exception type this can honestly
     * report without a shift/scheduled-hours concept to compare against.
     * Weekends and company holidays return workingDay=false with an empty
     * list rather than a misleading "everyone's missing" result.
     */
    @GetMapping("/exceptions")
    @PreAuthorize("!hasRole('EMPLOYEE') and hasAuthority('ATTENDANCE_VIEW')")
    @Transactional(readOnly = true)
    public AttendanceExceptionDTO exceptions(@RequestParam(required = false) String date) {
        Long companyId = requiredTenant();
        LocalDate targetDate = date != null ? LocalDate.parse(date) : LocalDate.now();

        boolean isWeekend = targetDate.getDayOfWeek() == DayOfWeek.SATURDAY || targetDate.getDayOfWeek() == DayOfWeek.SUNDAY;
        boolean isHoliday = !holidayRepository.findAllByCompany_IdAndDateBetweenAndDeletedFalse(companyId, targetDate, targetDate).isEmpty();
        boolean workingDay = !isWeekend && !isHoliday;

        if (!workingDay) {
            return new AttendanceExceptionDTO(targetDate, false, List.of());
        }

        Set<Long> punchedEmployeeIds = attendanceRecordRepository
                .findAllByCompany_IdAndPunchTimeBetweenOrderByPunchTimeDesc(companyId, targetDate.atStartOfDay(), targetDate.plusDays(1).atStartOfDay())
                .stream()
                .filter(r -> r.getEmployee() != null)
                .map(r -> r.getEmployee().getId())
                .collect(Collectors.toSet());

        Set<Long> onApprovedLeaveIds = leaveRequestRepository.findActiveOnForCompany(companyId, targetDate).stream()
                .map(lr -> lr.getEmployee().getId())
                .collect(Collectors.toSet());

        List<EmployeeSummaryDTO> missingPunch = employeeRepository.findAllByCompany_IdAndDeletedFalseOrderByFirstNameAsc(companyId).stream()
                .filter(e -> "Active".equals(e.getStatus()))
                .filter(e -> !punchedEmployeeIds.contains(e.getId()))
                .filter(e -> !onApprovedLeaveIds.contains(e.getId()))
                .map(EmployeeSummaryDTO::from)
                .toList();

        return new AttendanceExceptionDTO(targetDate, true, missingPunch);
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("hasAuthority('ATTENDANCE_VIEW') or @employeeSecurity.isSelf(#employeeId)")
    public List<AttendanceRecordDTO> byEmployee(@PathVariable Long employeeId) {
        Long companyId = requiredTenant();
        return attendanceRecordRepository.findAllByCompany_IdAndEmployee_IdOrderByPunchTimeDesc(companyId, employeeId).stream()
                .map(AttendanceRecordDTO::from)
                .toList();
    }

    /** Punches from PINs that haven't been mapped to an Employee yet - surfaces gaps in biometric enrollment. */
    @GetMapping("/unmapped")
    @PreAuthorize("hasAuthority('ATTENDANCE_MANAGE')")
    public List<AttendanceRecordDTO> unmapped() {
        return attendanceRecordRepository.findAllByCompany_IdAndEmployeeIsNullOrderByPunchTimeDesc(requiredTenant()).stream()
                .map(AttendanceRecordDTO::from)
                .toList();
    }

    @GetMapping("/stream")
    @PreAuthorize("!hasRole('EMPLOYEE') and hasAuthority('ATTENDANCE_VIEW')")
    public SseEmitter stream() {
        requiredTenant();
        return eventPublisher.subscribe();
    }

    private Long requiredTenant() {
        Long tenant = TenantContext.getCurrentTenant();
        if (tenant == null) throw new IllegalStateException("Company context is required");
        return tenant;
    }
}
