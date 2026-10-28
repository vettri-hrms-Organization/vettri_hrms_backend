package com.haodaone.demo.controller;

import com.haodaone.demo.service.DemoCompanyService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Sales demo workspaces - SUPER_ADMIN only. Credentials are returned once and never stored in plain text. */
@RestController
@RequestMapping("/api/admin/demo-companies")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class DemoCompanyController {

    public record CreateRequest(String name) {
    }

    private final DemoCompanyService service;

    public DemoCompanyController(DemoCompanyService service) {
        this.service = service;
    }

    @GetMapping
    public List<DemoCompanyService.DemoCompanySummary> list() {
        return service.list();
    }

    @PostMapping
    public ResponseEntity<DemoCompanyService.DemoCredentials> create(@RequestBody(required = false) CreateRequest request) {
        return ResponseEntity.status(201).header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(service.create(request == null ? null : request.name()));
    }

    @PostMapping("/{companyId}/reset")
    public ResponseEntity<DemoCompanyService.DemoCredentials> reset(@PathVariable Long companyId) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(service.reset(companyId));
    }
}
