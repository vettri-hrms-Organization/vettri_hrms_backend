package com.haodaone.recruitment.controller;

import com.haodaone.recruitment.dto.CloseRequisitionRequest;
import com.haodaone.recruitment.dto.JobOpeningDTO;
import com.haodaone.recruitment.service.JobOpeningService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/recruitment/requisitions")
public class RequisitionController {
    private final JobOpeningService jobOpeningService;

    public RequisitionController(JobOpeningService jobOpeningService) {
        this.jobOpeningService = jobOpeningService;
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAuthority('RECRUITMENT_MANAGE')")
    public JobOpeningDTO close(@PathVariable Long id, @Valid @RequestBody CloseRequisitionRequest request) {
        return jobOpeningService.close(id, request);
    }
}