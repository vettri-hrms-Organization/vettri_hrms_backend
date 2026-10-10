package com.haodaone.monitoring.controller;

import com.haodaone.monitoring.dto.MonitoredDeviceDTO;
import com.haodaone.monitoring.service.DeviceEnrollmentService;
import com.haodaone.employee.dto.EmployeeOptionDTO;
import com.haodaone.employee.service.EmployeeService;
import com.haodaone.monitoring.dto.DeviceEnrollmentDTO;
import com.haodaone.monitoring.dto.DeviceEnrollmentRequest;
import com.haodaone.monitoring.service.DeviceOnboardingService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Admin-facing device lifecycle and tenant-bound Windows Agent onboarding. */
@RestController
@RequestMapping("/api/monitoring/devices")
public class MonitoredDeviceController {

    private final DeviceEnrollmentService deviceEnrollmentService;
    private final EmployeeService employeeService;
    private final DeviceOnboardingService onboardingService;

    public MonitoredDeviceController(DeviceEnrollmentService deviceEnrollmentService,
                                     EmployeeService employeeService,
                                     DeviceOnboardingService onboardingService) {
        this.deviceEnrollmentService = deviceEnrollmentService;
        this.employeeService = employeeService;
        this.onboardingService = onboardingService;
    }

    @GetMapping("/employee-options")
    @PreAuthorize("@authorizationService.isAllowed('IT_MANAGEMENT_ACCESS', null, null) and @authorizationService.hasOrganizationScope('IT_DEVICE_MAPPING_MANAGE')")
    public List<EmployeeOptionDTO> employeeOptions() {
        return employeeService.listSelectorOptions("IT_DEVICE_MAPPING_MANAGE");
    }

    @GetMapping
    @PreAuthorize("@authorizationService.isAllowed('IT_MANAGEMENT_ACCESS', null, null) and @authorizationService.hasOrganizationScope('IT_DEVICE_VIEW')")
    public List<MonitoredDeviceDTO> listAll() {
        return deviceEnrollmentService.listAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authorizationService.isAllowed('IT_MANAGEMENT_ACCESS', null, null) and @authorizationService.isAllowed('IT_DEVICE_VIEW', null, null) and (@companySecurity.canViewDevice(#id) or @companySecurity.isSuperAdmin())")
    public MonitoredDeviceDTO get(@PathVariable Long id) {
        return deviceEnrollmentService.get(id);
    }

    @PostMapping
    @PreAuthorize("@authorizationService.isAllowed('IT_MANAGEMENT_ACCESS', null, null) and @authorizationService.hasOrganizationScope('IT_DEVICE_ENROLL')")
    public ResponseEntity<DeviceEnrollmentDTO> enroll(@Valid @RequestBody DeviceEnrollmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .cacheControl(CacheControl.noStore())
                .body(onboardingService.create(request));
    }

    @GetMapping("/enrollments")
    @PreAuthorize("@authorizationService.isAllowed('IT_MANAGEMENT_ACCESS', null, null) and @authorizationService.hasOrganizationScope('IT_DEVICE_ENROLL')")
    public ResponseEntity<List<DeviceEnrollmentDTO>> enrollments() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(onboardingService.listForCurrentCompany());
    }

    @PostMapping("/enrollments/{id}/revoke")
    @PreAuthorize("@authorizationService.isAllowed('IT_MANAGEMENT_ACCESS', null, null) and @authorizationService.hasOrganizationScope('IT_DEVICE_ENROLL')")
    public ResponseEntity<Void> revokeEnrollment(@PathVariable Long id) {
        onboardingService.revoke(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/enrollments/{id}/send-email")
    @PreAuthorize("@authorizationService.isAllowed('IT_MANAGEMENT_ACCESS', null, null) and @authorizationService.hasOrganizationScope('IT_DEVICE_ENROLL')")
    public ResponseEntity<Void> sendEnrollmentEmail(@PathVariable Long id) {
        onboardingService.sendEnrollmentEmail(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/assignment")
    @PreAuthorize("@authorizationService.isAllowed('IT_MANAGEMENT_ACCESS', null, null) and @authorizationService.isAllowed('IT_DEVICE_MAPPING_MANAGE', null, null) and (@companySecurity.canManageDeviceMapping(#id) or @companySecurity.isSuperAdmin())")
    public MonitoredDeviceDTO updateAssignment(@PathVariable Long id, @RequestBody MonitoredDeviceDTO.AssignmentRequest request) {
        return deviceEnrollmentService.updateAssignment(id, request);
    }

    @PatchMapping("/{id}/directive")
    @PreAuthorize("@authorizationService.isAllowed('IT_MANAGEMENT_ACCESS', null, null) and @authorizationService.isAllowed('MONITORING_MANAGE', null, null) and (@companySecurity.canManageDevice(#id) or @companySecurity.isSuperAdmin())")
    public MonitoredDeviceDTO applyDirective(@PathVariable Long id, @RequestBody MonitoredDeviceDTO.DirectiveRequest request) {
        return deviceEnrollmentService.applyDirective(id, request);
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("@authorizationService.isAllowed('IT_MANAGEMENT_ACCESS', null, null) and @authorizationService.isAllowed('MONITORING_MANAGE', null, null) and (@companySecurity.canManageDevice(#id) or @companySecurity.isSuperAdmin())")
    public ResponseEntity<Void> activate(@PathVariable Long id) {
        deviceEnrollmentService.setActive(id, true);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("@authorizationService.isAllowed('IT_MANAGEMENT_ACCESS', null, null) and @authorizationService.isAllowed('MONITORING_MANAGE', null, null) and (@companySecurity.canManageDevice(#id) or @companySecurity.isSuperAdmin())")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        deviceEnrollmentService.setActive(id, false);
        return ResponseEntity.noContent().build();
    }
}
