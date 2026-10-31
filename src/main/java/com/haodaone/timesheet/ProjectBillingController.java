package com.haodaone.timesheet;

import com.haodaone.security.CustomUserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Project billing: rates, invoices from approved billable hours, and utilization. Needs TIMESHEET_MANAGE org-wide. */
@RestController
@RequestMapping("/api/timesheets/billing")
@PreAuthorize("hasAuthority('TIMESHEET_MANAGE')")
public class ProjectBillingController {

    private final ProjectBillingService service;

    public ProjectBillingController(ProjectBillingService service) {
        this.service = service;
    }

    @GetMapping("/rates")
    public List<ProjectBillingService.ProjectRates> rates() { return service.rates(); }

    @PutMapping("/rates/{projectId}")
    public ProjectBillingService.ProjectRates setRates(@PathVariable Long projectId, @RequestBody ProjectBillingService.RatesRequest r,
                                                       @AuthenticationPrincipal CustomUserPrincipal p) {
        return service.setRates(projectId, r, name(p));
    }

    @PostMapping("/invoices/preview")
    public ProjectBillingService.Preview preview(@RequestBody ProjectBillingService.InvoiceRequest r) { return service.preview(r); }

    @PostMapping("/invoices")
    public ProjectBillingService.InvoiceDTO create(@RequestBody ProjectBillingService.InvoiceRequest r, @AuthenticationPrincipal CustomUserPrincipal p) {
        return service.create(r, name(p));
    }

    @GetMapping("/invoices")
    public List<ProjectBillingService.InvoiceDTO> invoices() { return service.invoices(); }

    @PostMapping("/invoices/{id}/status")
    public ProjectBillingService.InvoiceDTO status(@PathVariable Long id, @RequestBody Map<String, String> body, @AuthenticationPrincipal CustomUserPrincipal p) {
        return service.setStatus(id, body.get("status"), name(p));
    }

    @GetMapping("/utilization")
    public ProjectBillingService.Utilization utilization(@RequestParam LocalDate from, @RequestParam LocalDate to) { return service.utilization(from, to); }

    private static String name(CustomUserPrincipal principal) {
        if (principal == null || principal.getUser() == null) return "Admin";
        String full = principal.getUser().getFullName();
        return full == null || full.isBlank() ? principal.getUsername() : full;
    }
}
