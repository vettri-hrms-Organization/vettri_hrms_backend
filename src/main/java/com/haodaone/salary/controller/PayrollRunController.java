package com.haodaone.salary.controller;

import com.haodaone.salary.dto.*;
import com.haodaone.salary.service.PayrollService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Payroll Processing: run lifecycle (DRAFT -> PROCESSED -> PAID) and
 * per-employee hold toggling.
 *
 * Authorization: viewing runs requires SALARY_VIEW; creating, processing,
 * marking paid, holding an item, or cancelling a run requires
 * SALARY_MANAGE - see DataSeeder for how these map onto roles.
 */
@RestController
@RequestMapping("/api/salary/payroll-runs")
public class PayrollRunController {

    private final PayrollService payrollService;

    public PayrollRunController(PayrollService payrollService) {
        this.payrollService = payrollService;
    }

    @PreAuthorize("hasAuthority('SALARY_VIEW')")
    @GetMapping
    public List<PayrollRunSummaryDTO> listRuns() {
        return payrollService.listRuns();
    }

    @PreAuthorize("hasAuthority('SALARY_VIEW')")
    @GetMapping("/{runId}")
    public PayrollRunDTO getRun(@PathVariable Long runId) {
        return payrollService.getRun(runId);
    }

    @PreAuthorize("hasAuthority('SALARY_MANAGE')")
    @PostMapping
    public ResponseEntity<PayrollRunDTO> createRun(@Valid @RequestBody CreatePayrollRunRequest request) {
        return ResponseEntity.status(201).body(payrollService.createRun(request));
    }

    @PreAuthorize("hasAuthority('SALARY_MANAGE')")
    @PatchMapping("/{runId}/items/{itemId}/hold")
    public PayrollItemDTO setItemHold(@PathVariable Long runId, @PathVariable Long itemId,
                                       @RequestBody UpdatePayrollItemRequest request) {
        return payrollService.setItemHold(runId, itemId, request);
    }

    @PreAuthorize("hasAuthority('SALARY_MANAGE')")
    @PostMapping("/{runId}/process")
    public PayrollRunDTO process(@PathVariable Long runId) {
        return payrollService.process(runId);
    }

    @PreAuthorize("hasAuthority('SALARY_MANAGE')")
    @PostMapping("/{runId}/mark-paid")
    public PayrollRunDTO markPaid(@PathVariable Long runId, @RequestBody(required = false) MarkPaidRequest request) {
        return payrollService.markPaid(runId, request != null ? request : new MarkPaidRequest());
    }

    @PreAuthorize("hasAuthority('SALARY_MANAGE')")
    @DeleteMapping("/{runId}")
    public ResponseEntity<Void> cancel(@PathVariable Long runId) {
        payrollService.cancel(runId);
        return ResponseEntity.noContent().build();
    }
}
