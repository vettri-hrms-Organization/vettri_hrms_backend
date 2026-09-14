package com.haodaone.monitoring.controller;

import com.haodaone.monitoring.dto.MonitoredDeviceDTO;
import com.haodaone.monitoring.service.DeviceEnrollmentService;
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

    public MonitoredDeviceController(DeviceEnrollmentService deviceEnrollmentService) {
        this.deviceEnrollmentService = deviceEnrollmentService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('MONITORING_VIEW')")
    public List<MonitoredDeviceDTO> listAll() {
        return deviceEnrollmentService.listAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MONITORING_VIEW') and (@companySecurity.canViewDevice(#id) or @companySecurity.isSuperAdmin())")
    public MonitoredDeviceDTO get(@PathVariable Long id) {
        return deviceEnrollmentService.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MONITORING_MANAGE') and (@companySecurity.isSuperAdmin() or @companySecurity.isCompanyAdmin() or @companySecurity.isHrAdmin())")
    public ResponseEntity<MonitoredDeviceDTO.EnrollResponse> enroll(@Valid @RequestBody MonitoredDeviceDTO.EnrollRequest request) {
        return ResponseEntity.status(201).body(deviceEnrollmentService.enroll(request));
    }

    @PutMapping("/{id}/assignment")
    @PreAuthorize("hasAuthority('MONITORING_MANAGE') and (@companySecurity.canManageDevice(#id) or @companySecurity.isSuperAdmin())")
    public MonitoredDeviceDTO updateAssignment(@PathVariable Long id, @RequestBody MonitoredDeviceDTO.AssignmentRequest request) {
        return deviceEnrollmentService.updateAssignment(id, request);
    }

    @PatchMapping("/{id}/directive")
    @PreAuthorize("hasAuthority('MONITORING_MANAGE') and (@companySecurity.canManageDevice(#id) or @companySecurity.isSuperAdmin())")
    public MonitoredDeviceDTO applyDirective(@PathVariable Long id, @RequestBody MonitoredDeviceDTO.DirectiveRequest request) {
        return deviceEnrollmentService.applyDirective(id, request);
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('MONITORING_MANAGE') and (@companySecurity.canManageDevice(#id) or @companySecurity.isSuperAdmin())")
    public ResponseEntity<Void> activate(@PathVariable Long id) {
        deviceEnrollmentService.setActive(id, true);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('MONITORING_MANAGE') and (@companySecurity.canManageDevice(#id) or @companySecurity.isSuperAdmin())")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        deviceEnrollmentService.setActive(id, false);
        return ResponseEntity.noContent().build();
    }
}
