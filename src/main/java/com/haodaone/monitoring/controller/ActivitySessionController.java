package com.haodaone.monitoring.controller;

import com.haodaone.monitoring.dto.ActivitySessionDTO;
import com.haodaone.monitoring.service.MonitoringQueryService;
import com.haodaone.employee.dto.EmployeeOptionDTO;
import com.haodaone.employee.service.EmployeeService;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/** Admin-facing read views over recorded activity - mirrors audit.controller.AuditController's pagination shape. */
@RestController
@RequestMapping("/api/monitoring/sessions")
public class ActivitySessionController {

    private final MonitoringQueryService monitoringQueryService;
    private final EmployeeService employeeService;

    public ActivitySessionController(MonitoringQueryService monitoringQueryService, EmployeeService employeeService) {
        this.monitoringQueryService = monitoringQueryService;
        this.employeeService = employeeService;
    }

    @GetMapping("/employee-options")
    @PreAuthorize("hasAuthority('MONITORING_VIEW')")
    public java.util.List<EmployeeOptionDTO> employeeOptions() {
        return employeeService.listSelectorOptions("MONITORING_VIEW");
    }

    @GetMapping("/search")
    @PreAuthorize("hasAuthority('MONITORING_VIEW')")
    public Page<ActivitySessionDTO> search(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
                                            @RequestParam(required = false) Long employeeId,
                                            @RequestParam(required = false) String employeeCode,
                                            @RequestParam(required = false) Long deviceId,
                                            @RequestParam(required = false) String windowTitle,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "50") int size) {
        return monitoringQueryService.search(from, to, employeeId, employeeCode, deviceId, windowTitle, page, size);
    }

    @GetMapping("/device/{deviceId}")
    @PreAuthorize("hasAuthority('MONITORING_VIEW')")
    public Page<ActivitySessionDTO> byDevice(@PathVariable Long deviceId,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "50") int size) {
        return monitoringQueryService.byDevice(deviceId, page, size);
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("hasAuthority('MONITORING_VIEW')")
    public Page<ActivitySessionDTO> byEmployee(@PathVariable Long employeeId,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "50") int size) {
        return monitoringQueryService.byEmployee(employeeId, page, size);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('MONITORING_VIEW')")
    public Page<ActivitySessionDTO> byDateRange(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
                                                 @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "50") int size) {
        return monitoringQueryService.byDateRange(from, to, page, size);
    }
}
