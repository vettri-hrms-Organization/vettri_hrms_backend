package com.haodaone.salary.controller;

import com.haodaone.salary.service.PayslipService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * File downloads for payroll: an employee's payslip PDF and a run's bank
 * transfer CSV. The payslip's access rule depends on the item's employee and
 * status, so it is enforced in PayslipService rather than in @PreAuthorize.
 */
@RestController
public class PayslipController {

    private final PayslipService payslipService;

    public PayslipController(PayslipService payslipService) {
        this.payslipService = payslipService;
    }

    /** The employee themself (once processed) or SALARY_VIEW over that employee - checked in the service. */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/api/salary/payroll-items/{itemId}/payslip.pdf")
    public ResponseEntity<byte[]> payslipPdf(@PathVariable Long itemId) {
        PayslipService.PayslipFile file = payslipService.payslipPdf(itemId);
        return download(file, MediaType.APPLICATION_PDF);
    }

    @PreAuthorize("@authorizationService.hasOrganizationScope('SALARY_MANAGE')")
    @GetMapping("/api/salary/payroll-runs/{runId}/bank-transfer.csv")
    public ResponseEntity<byte[]> bankTransferCsv(@PathVariable Long runId) {
        PayslipService.PayslipFile file = payslipService.bankTransferCsv(runId);
        return download(file, new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8));
    }

    private ResponseEntity<byte[]> download(PayslipService.PayslipFile file, MediaType type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(type);
        headers.setContentDisposition(ContentDisposition.attachment().filename(file.filename()).build());
        headers.setCacheControl("no-store");
        return ResponseEntity.ok().headers(headers).body(file.content());
    }
}
