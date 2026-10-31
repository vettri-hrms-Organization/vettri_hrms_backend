package com.haodaone.salary.bank;

import com.haodaone.salary.service.PayslipService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/** Bank transfer files for a processed payroll run, and the company's salary debit account. */
@RestController
@PreAuthorize("@authorizationService.hasOrganizationScope('SALARY_MANAGE')")
public class BankTransferController {

    private final BankTransferService service;

    public BankTransferController(BankTransferService service) {
        this.service = service;
    }

    @GetMapping("/api/salary/bank-settings")
    public BankTransferService.SettingsDTO settings() {
        return service.settings();
    }

    @PutMapping("/api/salary/bank-settings")
    public BankTransferService.SettingsDTO updateSettings(@RequestBody BankTransferService.SettingsDTO request) {
        return service.updateSettings(request);
    }

    @GetMapping("/api/salary/payroll-runs/{runId}/bank-transfer")
    public BankTransferService.Preview preview(@PathVariable Long runId) {
        return service.preview(runId);
    }

    @GetMapping("/api/salary/payroll-runs/{runId}/bank-transfer/file")
    public ResponseEntity<byte[]> file(@PathVariable Long runId, @RequestParam(required = false) String format,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate valueDate) {
        PayslipService.PayslipFile file = service.file(runId, format, valueDate);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(file.filename().endsWith(".txt") ? MediaType.TEXT_PLAIN : MediaType.parseMediaType("text/csv"));
        headers.setContentDisposition(ContentDisposition.attachment().filename(file.filename()).build());
        headers.setCacheControl("no-store");
        return ResponseEntity.ok().headers(headers).body(file.content());
    }
}
