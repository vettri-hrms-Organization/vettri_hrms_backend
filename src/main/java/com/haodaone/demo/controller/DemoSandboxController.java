package com.haodaone.demo.controller;

import com.haodaone.auth.dto.LoginResponse;
import com.haodaone.demo.service.DemoSandboxService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Customer demo workspace (see DemoSandboxService). Opening, status and the first-login choice need a
 * Company Admin or HR Admin of a real company; "current" and role switching work for any signed-in user
 * and are re-checked in the service.
 */
@RestController
@RequestMapping("/api/demo-sandbox")
public class DemoSandboxController {

    public record ChoiceRequest(String choice) { }
    public record SessionRequest(String role) { }

    private final DemoSandboxService service;

    public DemoSandboxController(DemoSandboxService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_ADMIN')")
    public DemoSandboxService.Status status() {
        return service.status();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_ADMIN')")
    public DemoSandboxService.Status create() {
        return service.create();
    }

    @PostMapping("/choice")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_ADMIN')")
    public DemoSandboxService.Status choice(@RequestBody ChoiceRequest request) {
        return service.recordChoice(request == null ? null : request.choice());
    }

    @GetMapping("/current")
    @PreAuthorize("isAuthenticated()")
    public DemoSandboxService.Current current() {
        return service.current();
    }

    @PostMapping("/session")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<LoginResponse> session(@RequestBody SessionRequest request, HttpServletRequest httpRequest) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(service.openSession(request == null ? null : request.role(), httpRequest));
    }
}
