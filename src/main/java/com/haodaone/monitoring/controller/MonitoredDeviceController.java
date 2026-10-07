package com.haodaone.monitoring.controller;

import com.haodaone.monitoring.dto.MonitoredDeviceDTO;
import com.haodaone.monitoring.service.DeviceEnrollmentService;
import com.haodaone.employee.dto.EmployeeOptionDTO;
import com.haodaone.employee.service.EmployeeService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin-facing device lifecycle - list, enroll (mint a token before the
 * agent is installed on a machine), pause/resume, activate/deactivate,
 * token rotation through the OTP-gated AgentTokenController.
 */
@RestController
@RequestMapping("/api/monitoring/devices")
public class MonitoredDeviceController {

    private final DeviceEnrollmentService deviceEnrollmentService;
    private final EmployeeService employeeService;

    public MonitoredDeviceController(DeviceEnrollmentService deviceEnrollmentService, EmployeeService employeeService) {
        this.deviceEnrollmentService = deviceEnrollmentService;
        this.employeeService = employeeService;
    }

    @GetMapping("/employee-options")
    @PreAuthorize("@authorizationService.isAllowed('IT_MANAGEMENT_ACCESS', null, null) and hasAuthority('MONITORING_MANAGE')")
    public List<EmployeeOptionDTO> employeeOptions() {
        return employeeService.listSelectorOptions("MONITORING_MANAGE");
    }

    @GetMapping
    @PreAuthorize("@authorizationService.isAllowed('IT_MANAGEMENT_ACCESS', null, null) and @authorizationService.hasOrganizationScope('MONITORING_VIEW')")
    public List<MonitoredDeviceDTO> listAll() {
        return deviceEnrollmentService.listAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authorizationService.isAllowed('IT_MANAGEMENT_ACCESS', null, null) and hasAuthority('MONITORING_VIEW') and (@companySecurity.canViewDevice(#id) or @companySecurity.isSuperAdmin())")
    public MonitoredDeviceDTO get(@PathVariable Long id) {
        return deviceEnrollmentService.get(id);
    }

    @PostMapping
    @PreAuthorize("@authorizationService.isAllowed('IT_MANAGEMENT_ACCESS', null, null) and hasAuthority('MONITORING_MANAGE') and (@authorizationService.hasOrganizationScope('MONITORING_MANAGE') or @companySecurity.isSuperAdmin())")
    public ResponseEntity<MonitoredDeviceDTO.EnrollResponse> enroll(@Valid @RequestBody MonitoredDeviceDTO.EnrollRequest request) {
        return ResponseEntity.status(201).body(deviceEnrollmentService.enroll(request));
    }

    @PutMapping("/{id}/assignment")
    @PreAuthorize("@authorizationService.isAllowed('IT_MANAGEMENT_ACCESS', null, null) and hasAuthority('MONITORING_MANAGE') and (@companySecurity.canManageDevice(#id) or @companySecurity.isSuperAdmin())")
    public MonitoredDeviceDTO updateAssignment(@PathVariable Long id, @RequestBody MonitoredDeviceDTO.AssignmentRequest request) {
        return deviceEnrollmentService.updateAssignment(id, request);
    }

    @PatchMapping("/{id}/directive")
    @PreAuthorize("@authorizationService.isAllowed('IT_MANAGEMENT_ACCESS', null, null) and hasAuthority('MONITORING_MANAGE') and (@companySecurity.canManageDevice(#id) or @companySecurity.isSuperAdmin())")
    public MonitoredDeviceDTO applyDirective(@PathVariable Long id, @RequestBody MonitoredDeviceDTO.DirectiveRequest request) {
        return deviceEnrollmentService.applyDirective(id, request);
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("@authorizationService.isAllowed('IT_MANAGEMENT_ACCESS', null, null) and hasAuthority('MONITORING_MANAGE') and (@companySecurity.canManageDevice(#id) or @companySecurity.isSuperAdmin())")
    public ResponseEntity<Void> activate(@PathVariable Long id) {
        deviceEnrollmentService.setActive(id, true);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("@authorizationService.isAllowed('IT_MANAGEMENT_ACCESS', null, null) and hasAuthority('MONITORING_MANAGE') and (@companySecurity.canManageDevice(#id) or @companySecurity.isSuperAdmin())")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        deviceEnrollmentService.setActive(id, false);
        return ResponseEntity.noContent().build();
    }
}
