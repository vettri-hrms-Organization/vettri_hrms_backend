package com.haodaone.recruitment.controller;

import com.haodaone.recruitment.dto.CandidateDTO;
import com.haodaone.recruitment.dto.PublicJobOpeningDTO;
import com.haodaone.recruitment.service.PublicCareersService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * No authentication required - see SecurityConfig's "/api/careers/**"
 * permitAll rule. Every method here must stay read-only for job data (no
 * internal pipeline metrics like candidate/hired counts - see
 * PublicJobOpeningDTO) and write-only for a brand new application; nothing
 * here should ever let an anonymous caller read or modify another
 * candidate's data.
 */
@RestController
@RequestMapping("/api/careers")
public class PublicCareersController {

    private final PublicCareersService publicCareersService;

    public PublicCareersController(PublicCareersService publicCareersService) {
        this.publicCareersService = publicCareersService;
    }

    @GetMapping("/jobs")
    public List<PublicJobOpeningDTO> listOpenJobs() {
        return publicCareersService.listOpenJobs();
    }

    @GetMapping("/jobs/{id}")
    public PublicJobOpeningDTO getJob(@PathVariable Long id) {
        return publicCareersService.getOpenJob(id);
    }

    /**
     * multipart/form-data: the JSON application fields arrive as a single
     * "application" part (see CandidateDTO.PublicApplicationRequest), the
     * resume file as a separate "resume" part. Kept as two parts rather
     * than flattened request params so the resume stays a normal file
     * upload from the browser's perspective (drag-drop, file picker).
     */
    @PostMapping(value = "/apply", consumes = "multipart/form-data")
    public ResponseEntity<CandidateDTO> apply(
            @Valid @RequestPart("application") CandidateDTO.PublicApplicationRequest application,
            @RequestPart(value = "resume", required = false) MultipartFile resume) {
        return ResponseEntity.status(201).body(publicCareersService.apply(application, resume));
    }
}
