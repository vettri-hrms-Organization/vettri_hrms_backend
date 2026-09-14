package com.haodaone.performance.controller;

import com.haodaone.performance.dto.GoalDTO;
import com.haodaone.performance.service.GoalService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/goals")
public class GoalController {

    private final GoalService goalService;

    public GoalController(GoalService goalService) {
        this.goalService = goalService;
    }

    @GetMapping("/employee/{employeeId}")
    @PreAuthorize("hasAuthority('PERFORMANCE_VIEW')")
    public List<GoalDTO> byEmployee(@PathVariable Long employeeId) {
        return goalService.byEmployee(employeeId);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERFORMANCE_MANAGE')")
    public ResponseEntity<GoalDTO> create(@Valid @RequestBody GoalDTO.CreateRequest request) {
        return ResponseEntity.status(201).body(goalService.create(request));
    }

    @PatchMapping("/{id}/progress")
    @PreAuthorize("hasAuthority('PERFORMANCE_MANAGE')")
    public GoalDTO updateProgress(@PathVariable Long id, @Valid @RequestBody GoalDTO.ProgressUpdateRequest request) {
        return goalService.updateProgress(id, request);
    }
}
