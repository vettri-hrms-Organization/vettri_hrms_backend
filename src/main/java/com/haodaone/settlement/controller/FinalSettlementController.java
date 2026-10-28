package com.haodaone.settlement.controller;

import com.haodaone.security.AuthorizationService;
import com.haodaone.settlement.dto.SettlementDTOs.*;
import com.haodaone.settlement.service.FinalSettlementService;
import com.haodaone.settlement.service.SettlementPdfService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Full & Final settlement and gratuity.
 * HR: viewing needs SALARY_VIEW (organization scope); creating, editing, reviewing, approving,
 * paying and cancelling need SALARY_MANAGE (organization scope). Employees see their own
 * APPROVED/PAID settlements and statement PDF.
 */
@RestController
@RequestMapping("/api/settlements")
public class FinalSettlementController {

    private final FinalSettlementService service;
    private final SettlementPdfService pdfService;
    private final AuthorizationService authorizationService;

    public FinalSettlementController(FinalSettlementService service, SettlementPdfService pdfService,
                                     AuthorizationService authorizationService) {
        this.service = service;
        this.pdfService = pdfService;
        this.authorizationService = authorizationService;
    }

    @GetMapping
    @PreAuthorize("@authorizationService.hasOrganizationScope('SALARY_VIEW')")
    public List<SettlementDTO> list() {
        return service.list();
    }

    @GetMapping("/eligible")
    @PreAuthorize("@authorizationService.hasOrganizationScope('SALARY_MANAGE')")
    public List<EligibleEmployeeDTO> eligible() {
        return service.eligibleEmployees();
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public List<SettlementDTO> mine() {
        return service.mine();
    }

    @GetMapping("/settings")
    @PreAuthorize("@authorizationService.hasOrganizationScope('SALARY_VIEW')")
    public SettingsDTO settings() {
        return service.getSettings();
    }

    @PutMapping("/settings")
    @PreAuthorize("@authorizationService.hasOrganizationScope('SALARY_MANAGE')")
    public SettingsDTO updateSettings(@RequestBody SettingsDTO request) {
        return service.updateSettings(request);
    }

    /** Estimated gratuity for the employee profile (HR only). */
    @GetMapping("/gratuity/employee/{employeeId}")
    @PreAuthorize("@authorizationService.isAllowed('SALARY_VIEW', 'EMPLOYEE', #employeeId)")
    public GratuityEstimateDTO gratuity(@PathVariable Long employeeId) {
        return service.gratuityEstimate(employeeId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@authorizationService.hasOrganizationScope('SALARY_VIEW')")
    public SettlementDTO get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("@authorizationService.hasOrganizationScope('SALARY_MANAGE')")
    public ResponseEntity<SettlementDTO> create(@RequestBody SettlementRequest request) {
        return ResponseEntity.status(201).body(service.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@authorizationService.hasOrganizationScope('SALARY_MANAGE')")
    public SettlementDTO update(@PathVariable Long id, @RequestBody SettlementRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/review")
    @PreAuthorize("@authorizationService.hasOrganizationScope('SALARY_MANAGE')")
    public SettlementDTO review(@PathVariable Long id) {
        return service.review(id);
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("@authorizationService.hasOrganizationScope('SALARY_MANAGE')")
    public SettlementDTO reopen(@PathVariable Long id) {
        return service.reopen(id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("@authorizationService.hasOrganizationScope('SALARY_MANAGE')")
    public SettlementDTO approve(@PathVariable Long id) {
        return service.approve(id);
    }

    @PostMapping("/{id}/mark-paid")
    @PreAuthorize("@authorizationService.hasOrganizationScope('SALARY_MANAGE')")
    public SettlementDTO markPaid(@PathVariable Long id, @RequestBody MarkPaidRequest request) {
        return service.markPaid(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@authorizationService.hasOrganizationScope('SALARY_MANAGE')")
    public ResponseEntity<Void> cancel(@PathVariable Long id) {
        service.cancel(id);
        return ResponseEntity.noContent().build();
    }

    /** HR (SALARY_VIEW org scope) any time; the employee for their own APPROVED/PAID settlement. */
    @GetMapping("/{id}/statement.pdf")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> statement(@PathVariable Long id) {
        boolean hr = authorizationService.hasOrganizationScope("SALARY_VIEW");
        FinalSettlementService.StatementFile file = service.statementPdf(id, hr, pdfService);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.filename()).build().toString())
                .body(file.content());
    }
}
