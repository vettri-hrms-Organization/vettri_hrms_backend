package com.haodaone.attendance.controller;

import com.haodaone.attendance.dto.StartWorkRequest;
import com.haodaone.attendance.dto.WorkSessionDTO;
import com.haodaone.attendance.entity.WorkSession;
import com.haodaone.attendance.repository.WorkSessionRepository;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.monitoring.entity.MonitoredDevice;
import com.haodaone.monitoring.repository.MonitoredDeviceRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/worksession")
public class WorkSessionController {

    private final WorkSessionRepository workSessionRepository;
    private final EmployeeRepository employeeRepository;
    private final MonitoredDeviceRepository monitoredDeviceRepository;

    public WorkSessionController(WorkSessionRepository workSessionRepository,
                                 EmployeeRepository employeeRepository,
                                 MonitoredDeviceRepository monitoredDeviceRepository) {
        this.workSessionRepository = workSessionRepository;
        this.employeeRepository = employeeRepository;
        this.monitoredDeviceRepository = monitoredDeviceRepository;
    }

    private Employee currentEmployee() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        String username = a.getName();
        return employeeRepository.findByUser_UsernameAndDeletedFalse(username)
                .orElseThrow(() -> new BadRequestException("Current login is not linked to an employee"));
    }

    @PostMapping("/start")
    @PreAuthorize("hasAuthority('EMPLOYEE_VIEW') or hasRole('EMPLOYEE')")
    @Transactional
    public WorkSessionDTO start(@RequestBody StartWorkRequest request) {
        Employee me = currentEmployee();
        if (me.getCompany() == null) throw new BadRequestException("Employee has no company assigned");

        String mode = request.getWorkingMode();
        if (mode == null) throw new BadRequestException("workingMode is required");
        if (!mode.equals("OFFICE") && !mode.equals("WFH")) throw new BadRequestException("Unknown workingMode: " + mode);

        // If WFH, require device verification
        MonitoredDevice device = null;
        if (mode.equals("WFH")) {
            if (request.getDeviceId() == null) throw new BadRequestException("deviceId is required for WFH");
            device = monitoredDeviceRepository.findByDeviceIdAndDeletedFalse(request.getDeviceId())
                    .orElseThrow(() -> new BadRequestException("Unknown device: " + request.getDeviceId()));
            // tenant match
            if (device.getCompany() == null || me.getCompany() == null || !device.getCompany().getId().equals(me.getCompany().getId())) {
                throw new BadRequestException("Device does not belong to your company");
            }
            // active
            if (!device.isActive()) throw new BadRequestException("Device is not active");
            // online
            if (!device.isOnline()) throw new BadRequestException("Agent is offline on this device");
            // assigned to same employee - prevent Employee A using Employee B's device
            if (device.getEmployee() == null || !device.getEmployee().getId().equals(me.getId())) {
                throw new BadRequestException("Device is not assigned to you");
            }
        }

        LocalDate today = LocalDate.now();
        // prevent double start
        if (workSessionRepository.countByEmployee_IdAndStatusAndSessionDate(me.getId(), "ACTIVE", today) > 0) {
            throw new BadRequestException("You already have an active session for today");
        }

        WorkSession ws = new WorkSession();
        ws.setEmployee(me);
        ws.setCompany(me.getCompany());
        ws.setSessionDate(today);
        ws.setLoginTime(LocalDateTime.now());
        ws.setWorkingMode(mode);
        ws.setStatus("ACTIVE");
        workSessionRepository.save(ws);

        return WorkSessionDTO.from(ws);
    }

    @PostMapping("/stop")
    @PreAuthorize("hasAuthority('EMPLOYEE_VIEW') or hasRole('EMPLOYEE')")
    @Transactional
    public WorkSessionDTO stop() {
        Employee me = currentEmployee();
        LocalDate today = LocalDate.now();
        WorkSession ws = workSessionRepository.findByEmployee_IdAndStatusAndSessionDate(me.getId(), "ACTIVE", today)
                .orElseThrow(() -> new BadRequestException("No active session found for today"));
        ws.setLogoutTime(LocalDateTime.now());
        ws.setStatus("COMPLETED");
        long minutes = java.time.Duration.between(ws.getLoginTime(), ws.getLogoutTime()).toMinutes();
        ws.setTotalWorkingMinutes((int) minutes);
        workSessionRepository.save(ws);
        return WorkSessionDTO.from(ws);
    }

    @GetMapping("/me/today")
    @PreAuthorize("hasAuthority('EMPLOYEE_VIEW') or hasRole('EMPLOYEE')")
    public WorkSessionDTO todayForCurrentEmployee() {
        Employee me = currentEmployee();
        return workSessionRepository.findByEmployee_IdAndStatusAndSessionDate(me.getId(), "ACTIVE", LocalDate.now())
                .map(WorkSessionDTO::from)
                .orElse(null);
    }

    @GetMapping
    @PreAuthorize("!hasRole('EMPLOYEE') and hasAuthority('ATTENDANCE_VIEW')")
    public List<WorkSessionDTO> list(@RequestParam(required = false) String date,
                                     @RequestParam(required = false) String mode) {
        LocalDate target = date != null ? LocalDate.parse(date) : LocalDate.now();
        Long companyId = null;
        // company scoping: if tenant context present, apply it
        Long currentTenant = com.haodaone.tenant.TenantContext.getCurrentTenant();
        if (currentTenant != null) companyId = currentTenant;

        List<WorkSession> rows;
        if (companyId != null) {
            if (mode == null || mode.equalsIgnoreCase("ALL")) {
                rows = workSessionRepository.findAllByCompany_IdAndSessionDateOrderByLoginTimeDesc(companyId, target);
            } else {
                rows = workSessionRepository.findAllByCompany_IdAndWorkingModeAndSessionDateOrderByLoginTimeDesc(companyId, mode.toUpperCase(), target);
            }
        } else {
            // No tenant context - return empty to avoid leaking across tenants
            rows = List.of();
        }
        return rows.stream().map(WorkSessionDTO::from).collect(Collectors.toList());
    }
}
