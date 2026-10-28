package com.haodaone.salary.accounting;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Accounting export for payroll runs and the ledger mapping (SALARY_MANAGE, organization scope). */
@RestController
@RequestMapping("/api/salary/accounting")
public class PayrollAccountingController {

    private final PayrollAccountingService service;

    public PayrollAccountingController(PayrollAccountingService service) {
        this.service = service;
    }

    @GetMapping("/ledgers")
    @PreAuthorize("@authorizationService.hasOrganizationScope('SALARY_MANAGE')")
    public PayrollAccountingService.LedgerSettingsDTO ledgers() {
        return service.getSettings();
    }

    @PutMapping("/ledgers")
    @PreAuthorize("@authorizationService.hasOrganizationScope('SALARY_MANAGE')")
    public PayrollAccountingService.LedgerSettingsDTO updateLedgers(@RequestBody PayrollAccountingService.LedgerSettingsDTO request) {
        return service.updateSettings(request);
    }

    @GetMapping("/payroll-runs/{runId}/journal")
    @PreAuthorize("@authorizationService.hasOrganizationScope('SALARY_MANAGE')")
    public PayrollAccountingService.JournalPreviewDTO preview(@PathVariable Long runId) {
        return service.preview(runId);
    }

    /** format = journal | tally | zoho */
    @GetMapping("/payroll-runs/{runId}/export")
    @PreAuthorize("@authorizationService.hasOrganizationScope('SALARY_MANAGE')")
    public ResponseEntity<byte[]> export(@PathVariable Long runId, @RequestParam(defaultValue = "journal") String format) {
        PayrollAccountingService.ExportFile file = service.export(runId, format);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.filename()).build().toString())
                .body(file.content());
    }
}
