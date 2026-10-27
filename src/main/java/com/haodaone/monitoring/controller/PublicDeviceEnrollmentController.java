package com.haodaone.monitoring.controller;

import com.haodaone.monitoring.dto.PublicDeviceEnrollmentDTO;
import com.haodaone.monitoring.dto.EmployeeDeviceInventoryDTO;
import com.haodaone.monitoring.dto.InventoryScanRequestDTO;
import com.haodaone.monitoring.service.DeviceOnboardingService;
import com.haodaone.monitoring.service.DeviceInventoryService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import java.net.URI;

@RestController
@RequestMapping("/api/device-enrollments")
public class PublicDeviceEnrollmentController {

    private final DeviceOnboardingService onboardingService;
    private final DeviceInventoryService inventoryService;

    public PublicDeviceEnrollmentController(DeviceOnboardingService onboardingService,
                                            DeviceInventoryService inventoryService) {
        this.onboardingService = onboardingService;
        this.inventoryService = inventoryService;
    }

    @GetMapping("/{token}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PublicDeviceEnrollmentDTO> status(@PathVariable String token) {
        inventoryService.authorizeEmployeeEnrollment(token);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer")
                .body(onboardingService.status(token));
    }

    @GetMapping("/{token}/installer")
    public ResponseEntity<Void> downloadInstaller(@PathVariable String token) {
        String url = onboardingService.installerDownloadUrl(token);
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(url))
                .cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer")
                .build();
    }

    @GetMapping("/{token}/inventory")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<EmployeeDeviceInventoryDTO> inventory(
            @PathVariable String token, @RequestParam(required = false) Long deviceId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(inventoryService.getEmployeeInventory(token, deviceId));
    }

    @PostMapping("/{token}/inventory")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<EmployeeDeviceInventoryDTO> requestInventory(
            @PathVariable String token, @Valid @RequestBody InventoryScanRequestDTO request) {
        return ResponseEntity.accepted()
                .cacheControl(CacheControl.noStore())
                .body(inventoryService.requestEmployeeScan(token, request.getDeviceId()));
    }
}
