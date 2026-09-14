package com.haodaone.recruitment.controller;

import com.haodaone.recruitment.dto.JobOpeningDTO;
import com.haodaone.recruitment.dto.CloseRequisitionRequest;
import com.haodaone.recruitment.service.JobOpeningService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/job-openings")
public class JobOpeningController {

    private final JobOpeningService jobOpeningService;

    public JobOpeningController(JobOpeningService jobOpeningService) {
        this.jobOpeningService = jobOpeningService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('RECRUITMENT_VIEW')")
    public List<JobOpeningDTO> listAll() {
        return jobOpeningService.listAll();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('RECRUITMENT_MANAGE')")
    public ResponseEntity<JobOpeningDTO> create(@Valid @RequestBody JobOpeningDTO.CreateRequest request) {
        return ResponseEntity.status(201).body(jobOpeningService.create(request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('RECRUITMENT_MANAGE')")
    public JobOpeningDTO setStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return jobOpeningService.setStatus(id, body.get("status"));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('RECRUITMENT_MANAGE')")
    public JobOpeningDTO close(@PathVariable Long id, @Valid @RequestBody CloseRequisitionRequest request) {
        return jobOpeningService.close(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('RECRUITMENT_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        jobOpeningService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
