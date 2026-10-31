package com.haodaone.reports.builder;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Custom report builder. Each dataset needs its own permission; the service limits rows to the caller's scope. */
@RestController
@RequestMapping("/api/report-builder")
@PreAuthorize("isAuthenticated()")
public class ReportBuilderController {

    private final ReportBuilderService service;

    public ReportBuilderController(ReportBuilderService service) {
        this.service = service;
    }

    @GetMapping("/datasets")
    public List<ReportBuilderService.Dataset> datasets() { return service.datasets(); }

    @PostMapping("/run")
    public ReportBuilderService.Result run(@RequestBody ReportBuilderService.Definition def) { return service.run(def); }

    @PostMapping("/export")
    public ResponseEntity<byte[]> export(@RequestBody ReportBuilderService.Definition def) {
        byte[] body = ("﻿" + service.csv(def)).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + def.dataset().toLowerCase() + "-report.csv\"")
                .body(body);
    }

    @GetMapping("/saved")
    public List<ReportBuilderService.SavedDTO> saved() { return service.savedReports(); }

    @PostMapping("/saved")
    public ReportBuilderService.SavedDTO create(@RequestBody ReportBuilderService.SaveRequest r) { return service.save(null, r); }

    @PutMapping("/saved/{id}")
    public ReportBuilderService.SavedDTO update(@PathVariable Long id, @RequestBody ReportBuilderService.SaveRequest r) { return service.save(id, r); }

    @DeleteMapping("/saved/{id}")
    public void delete(@PathVariable Long id) { service.delete(id); }
}
