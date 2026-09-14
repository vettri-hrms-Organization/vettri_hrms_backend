package com.haodaone.performance.controller;

import com.haodaone.performance.dto.PerformanceReviewDTO;
import com.haodaone.performance.service.PerformanceReviewService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/performance-reviews")
public class PerformanceReviewController {

    private final PerformanceReviewService performanceReviewService;

    public PerformanceReviewController(PerformanceReviewService performanceReviewService) {
        this.performanceReviewService = performanceReviewService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERFORMANCE_VIEW')")
    public List<PerformanceReviewDTO> listAll() {
        return performanceReviewService.listAll();
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("hasAuthority('PERFORMANCE_VIEW')")
    public List<PerformanceReviewDTO> byEmployee(@PathVariable Long employeeId) {
        return performanceReviewService.byEmployee(employeeId);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERFORMANCE_MANAGE')")
    public ResponseEntity<PerformanceReviewDTO> create(@Valid @RequestBody PerformanceReviewDTO.CreateRequest request) {
        return ResponseEntity.status(201).body(performanceReviewService.create(request));
    }

    @PatchMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('PERFORMANCE_MANAGE')")
    public PerformanceReviewDTO submit(@PathVariable Long id) {
        return performanceReviewService.submit(id);
    }

    @PatchMapping("/{id}/acknowledge")
    @PreAuthorize("hasAuthority('PERFORMANCE_VIEW') or hasAuthority('PERFORMANCE_MANAGE')")
    public PerformanceReviewDTO acknowledge(@PathVariable Long id) {
        return performanceReviewService.acknowledge(id);
    }
}
