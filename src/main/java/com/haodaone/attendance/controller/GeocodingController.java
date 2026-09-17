package com.haodaone.attendance.controller;

import com.haodaone.attendance.dto.GeocodingResultDTO;
import com.haodaone.attendance.service.GeocodingService;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/attendance/geocoding")
@Validated
public class GeocodingController {
    private final GeocodingService geocodingService;

    public GeocodingController(GeocodingService geocodingService) {
        this.geocodingService = geocodingService;
    }

    @GetMapping("/search")
    @PreAuthorize("hasAuthority('ATTENDANCE_MANAGE')")
    public List<GeocodingResultDTO> search(@RequestParam @NotBlank String query) {
        if (query.trim().length() < 3) return List.of();
        return geocodingService.search(query.trim());
    }

    @GetMapping("/reverse")
    @PreAuthorize("hasAuthority('ATTENDANCE_MANAGE')")
    public GeocodingResultDTO reverse(
            @RequestParam @DecimalMin("-90.0") @DecimalMax("90.0") double latitude,
            @RequestParam @DecimalMin("-180.0") @DecimalMax("180.0") double longitude) {
        return geocodingService.reverse(latitude, longitude);
    }
}
