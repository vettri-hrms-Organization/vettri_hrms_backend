package com.haodaone.salary.revision;

import com.haodaone.security.CustomUserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Salary revisions with arrears. Viewing needs SALARY_VIEW; revising needs SALARY_MANAGE (checked per employee too). */
@RestController
@RequestMapping("/api/salary-revisions")
public class SalaryRevisionController {

    private final SalaryRevisionService service;

    public SalaryRevisionController(SalaryRevisionService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SALARY_VIEW') or hasAuthority('SALARY_MANAGE')")
    public List<SalaryRevisionService.RevisionDTO> list(@RequestParam(required = false) Long employeeId) {
        return service.list(employeeId);
    }

    @PostMapping("/preview")
    @PreAuthorize("hasAuthority('SALARY_MANAGE')")
    public SalaryRevisionService.Preview preview(@RequestBody SalaryRevisionService.RevisionRequest request) {
        return service.preview(request);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SALARY_MANAGE')")
    public SalaryRevisionService.RevisionDTO create(@RequestBody SalaryRevisionService.RevisionRequest request,
                                                    @AuthenticationPrincipal CustomUserPrincipal principal) {
        return service.create(request, name(principal));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('SALARY_MANAGE')")
    public SalaryRevisionService.RevisionDTO cancel(@PathVariable Long id, @AuthenticationPrincipal CustomUserPrincipal principal) {
        return service.cancel(id, name(principal));
    }

    private static String name(CustomUserPrincipal principal) {
        if (principal == null || principal.getUser() == null) return "Payroll admin";
        String full = principal.getUser().getFullName();
        return full == null || full.isBlank() ? principal.getUsername() : full;
    }
}
