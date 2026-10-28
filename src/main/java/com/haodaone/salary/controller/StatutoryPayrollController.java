package com.haodaone.salary.controller;

import com.haodaone.salary.dto.StatutorySettingsDTO;
import com.haodaone.salary.service.PayslipService;
import com.haodaone.salary.service.StatutoryReturnsService;
import com.haodaone.salary.service.StatutorySettingsService;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

/**
 * Statutory payroll configuration and monthly return files. Everything here
 * is SALARY_MANAGE with ORGANIZATION scope (re-checked in the services).
 */
@RestController
public class StatutoryPayrollController {

    private final StatutorySettingsService settingsService;
    private final StatutoryReturnsService returnsService;

    public StatutoryPayrollController(StatutorySettingsService settingsService, StatutoryReturnsService returnsService) {
        this.settingsService = settingsService;
        this.returnsService = returnsService;
    }

    @PreAuthorize("@authorizationService.hasOrganizationScope('SALARY_MANAGE')")
    @GetMapping("/api/salary/statutory-settings")
    public StatutorySettingsDTO getSettings() {
        return settingsService.get();
    }

    @PreAuthorize("@authorizationService.hasOrganizationScope('SALARY_MANAGE')")
    @PutMapping("/api/salary/statutory-settings")
    public StatutorySettingsDTO updateSettings(@Valid @RequestBody StatutorySettingsDTO request) {
        return settingsService.update(request);
    }

    @PreAuthorize("@authorizationService.hasOrganizationScope('SALARY_MANAGE')")
    @GetMapping("/api/salary/payroll-runs/{runId}/statutory/summary")
    public StatutoryReturnsService.Summary summary(@PathVariable Long runId) {
        return returnsService.summary(runId);
    }

    @PreAuthorize("@authorizationService.hasOrganizationScope('SALARY_MANAGE')")
    @GetMapping("/api/salary/payroll-runs/{runId}/statutory/pf-ecr.txt")
    public ResponseEntity<byte[]> pfEcr(@PathVariable Long runId) {
        return download(returnsService.ecr(runId), new MediaType("text", "plain", StandardCharsets.UTF_8));
    }

    @PreAuthorize("@authorizationService.hasOrganizationScope('SALARY_MANAGE')")
    @GetMapping("/api/salary/payroll-runs/{runId}/statutory/esi.csv")
    public ResponseEntity<byte[]> esi(@PathVariable Long runId) {
        return download(returnsService.esiCsv(runId), csv());
    }

    @PreAuthorize("@authorizationService.hasOrganizationScope('SALARY_MANAGE')")
    @GetMapping("/api/salary/payroll-runs/{runId}/statutory/pt.csv")
    public ResponseEntity<byte[]> pt(@PathVariable Long runId) {
        return download(returnsService.ptCsv(runId), csv());
    }

    @PreAuthorize("@authorizationService.hasOrganizationScope('SALARY_MANAGE')")
    @GetMapping("/api/salary/payroll-runs/{runId}/statutory/tds.csv")
    public ResponseEntity<byte[]> tds(@PathVariable Long runId) {
        return download(returnsService.tdsCsv(runId), csv());
    }

    private static MediaType csv() {
        return new MediaType("text", "csv", StandardCharsets.UTF_8);
    }

    static ResponseEntity<byte[]> download(PayslipService.PayslipFile file, MediaType type) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(type);
        headers.setContentDisposition(ContentDisposition.attachment().filename(file.filename()).build());
        headers.setCacheControl("no-store");
        return ResponseEntity.ok().headers(headers).body(file.content());
    }
}
