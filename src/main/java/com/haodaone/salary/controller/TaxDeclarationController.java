package com.haodaone.salary.controller;

import com.haodaone.salary.dto.TaxDeclarationDTO;
import com.haodaone.salary.dto.TaxProjectionDTO;
import com.haodaone.salary.service.Form16Service;
import com.haodaone.salary.service.TaxDeclarationService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Tax regime / investment declarations (self-service + HR) and Form 16
 * Part B downloads. {@code fy} is the FY start year (2026 = FY 2026-27);
 * omitted = current FY (previous FY for Form 16).
 */
@RestController
@RequestMapping("/api/salary")
public class TaxDeclarationController {

    private final TaxDeclarationService taxDeclarationService;
    private final Form16Service form16Service;

    public TaxDeclarationController(TaxDeclarationService taxDeclarationService, Form16Service form16Service) {
        this.taxDeclarationService = taxDeclarationService;
        this.form16Service = form16Service;
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/tax-declarations/me")
    public TaxDeclarationDTO getMine(@RequestParam(required = false) Integer fy) {
        return taxDeclarationService.getMine(fy);
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/tax-declarations/me")
    public TaxDeclarationDTO saveMine(@RequestParam(required = false) Integer fy, @Valid @RequestBody TaxDeclarationDTO request) {
        return taxDeclarationService.saveMine(fy, request);
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/tax-declarations/me/projection")
    public TaxProjectionDTO projectMine(@RequestParam(required = false) Integer fy) {
        return taxDeclarationService.projectMine(fy);
    }

    @PreAuthorize("@authorizationService.hasOrganizationScope('SALARY_VIEW')")
    @GetMapping("/tax-declarations")
    public List<TaxDeclarationDTO> list(@RequestParam(required = false) Integer fy) {
        return taxDeclarationService.listForCompany(fy);
    }

    @PreAuthorize("@authorizationService.isAllowed('SALARY_VIEW', 'EMPLOYEE', #employeeId) or @employeeSecurity.isSelf(#employeeId)")
    @GetMapping("/tax-declarations/employee/{employeeId}")
    public TaxDeclarationDTO getForEmployee(@PathVariable Long employeeId, @RequestParam(required = false) Integer fy) {
        return taxDeclarationService.getForEmployee(employeeId, fy);
    }

    @PreAuthorize("@authorizationService.isAllowed('SALARY_MANAGE', 'EMPLOYEE', #employeeId)")
    @PutMapping("/tax-declarations/employee/{employeeId}")
    public TaxDeclarationDTO saveForEmployee(@PathVariable Long employeeId, @RequestParam(required = false) Integer fy,
                                             @Valid @RequestBody TaxDeclarationDTO request) {
        return taxDeclarationService.saveForEmployee(employeeId, fy, request);
    }

    @PreAuthorize("@authorizationService.isAllowed('SALARY_VIEW', 'EMPLOYEE', #employeeId) or @employeeSecurity.isSelf(#employeeId)")
    @GetMapping("/tax-declarations/employee/{employeeId}/projection")
    public TaxProjectionDTO projectForEmployee(@PathVariable Long employeeId, @RequestParam(required = false) Integer fy) {
        return taxDeclarationService.projectForEmployee(employeeId, fy);
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/form16/me.pdf")
    public ResponseEntity<byte[]> myForm16(@RequestParam(required = false) Integer fy) {
        return StatutoryPayrollController.download(form16Service.forMe(fy), MediaType.APPLICATION_PDF);
    }

    @PreAuthorize("@authorizationService.isAllowed('SALARY_VIEW', 'EMPLOYEE', #employeeId) or @employeeSecurity.isSelf(#employeeId)")
    @GetMapping("/form16/employee/{employeeId}/form16.pdf")
    public ResponseEntity<byte[]> employeeForm16(@PathVariable Long employeeId, @RequestParam(required = false) Integer fy) {
        return StatutoryPayrollController.download(form16Service.forEmployee(employeeId, fy), MediaType.APPLICATION_PDF);
    }
}
