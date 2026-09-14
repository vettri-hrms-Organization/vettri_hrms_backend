package com.haodaone.monitoring.controller;

import com.haodaone.monitoring.service.DeviceEnrollmentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/devices")
public class AdminMonitoredDeviceController {

    private final DeviceEnrollmentService deviceEnrollmentService;

    public AdminMonitoredDeviceController(DeviceEnrollmentService deviceEnrollmentService) {
        this.deviceEnrollmentService = deviceEnrollmentService;
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('MONITORING_MANAGE') and (@companySecurity.canManageDevice(#id) or @companySecurity.isSuperAdmin())")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        deviceEnrollmentService.deleteDevice(id);
        return ResponseEntity.noContent().build();
    }

}