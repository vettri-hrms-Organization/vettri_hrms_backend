package com.haodaone.monitoring.controller;

import com.haodaone.monitoring.dto.PublicDeviceEnrollmentDTO;
import com.haodaone.monitoring.service.DeviceOnboardingService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/device-enrollments")
public class PublicDeviceEnrollmentController {

    private final DeviceOnboardingService onboardingService;

    public PublicDeviceEnrollmentController(DeviceOnboardingService onboardingService) {
        this.onboardingService = onboardingService;
    }

    @GetMapping("/{token}")
    public ResponseEntity<PublicDeviceEnrollmentDTO> status(@PathVariable String token) {
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
}
