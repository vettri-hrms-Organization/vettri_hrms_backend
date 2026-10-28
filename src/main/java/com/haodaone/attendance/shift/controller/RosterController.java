package com.haodaone.attendance.shift.controller;

import com.haodaone.attendance.shift.dto.DailyAttendanceDTO;
import com.haodaone.attendance.shift.dto.LateEarlyReportDTO;
import com.haodaone.attendance.shift.dto.RosterCopyWeekRequest;
import com.haodaone.attendance.shift.dto.RosterDTO;
import com.haodaone.attendance.shift.dto.RosterUpdateRequest;
import com.haodaone.attendance.shift.service.DailyAttendanceService;
import com.haodaone.attendance.shift.service.ShiftService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Roster grid, daily shift evaluation and the late & early report. */
@RestController
@RequestMapping("/api/attendance")
public class RosterController {

    private final ShiftService shiftService;
    private final DailyAttendanceService dailyAttendanceService;

    public RosterController(ShiftService shiftService, DailyAttendanceService dailyAttendanceService) {
        this.shiftService = shiftService;
        this.dailyAttendanceService = dailyAttendanceService;
    }

    @GetMapping("/roster")
    @PreAuthorize("hasAuthority('ATTENDANCE_VIEW')")
    public RosterDTO roster(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                            @RequestParam(required = false) Long departmentId) {
        return shiftService.roster(from, to, departmentId);
    }

    @PutMapping("/roster")
    @PreAuthorize("hasAuthority('ATTENDANCE_MANAGE')")
    public Map<String, Integer> updateRoster(@Valid @RequestBody RosterUpdateRequest request) {
        return Map.of("updated", shiftService.updateRoster(request));
    }

    @PostMapping("/roster/copy-week")
    @PreAuthorize("hasAuthority('ATTENDANCE_MANAGE')")
    public Map<String, Integer> copyWeek(@Valid @RequestBody RosterCopyWeekRequest request) {
        return Map.of("updated", shiftService.copyWeek(request));
    }

    @GetMapping("/daily/me")
    @PreAuthorize("@authorizationService.canAccessOwnAttendance()")
    public List<DailyAttendanceDTO> myDaily(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return dailyAttendanceService.mine(from, to);
    }

    @GetMapping("/daily/employee/{employeeId}")
    @PreAuthorize("@authorizationService.isAllowed('ATTENDANCE_VIEW', 'EMPLOYEE', #employeeId) or @employeeSecurity.isSelf(#employeeId)")
    public List<DailyAttendanceDTO> employeeDaily(@PathVariable Long employeeId,
                                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return dailyAttendanceService.forEmployee(employeeId, from, to);
    }

    @GetMapping("/late-early")
    @PreAuthorize("hasAuthority('ATTENDANCE_VIEW')")
    public LateEarlyReportDTO lateEarly(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return dailyAttendanceService.lateEarly(from, to);
    }
}
