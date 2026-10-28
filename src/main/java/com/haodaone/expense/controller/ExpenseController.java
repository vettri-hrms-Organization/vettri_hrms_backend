package com.haodaone.expense.controller;

import com.haodaone.expense.dto.ExpenseDtos.CategoryRequest;
import com.haodaone.expense.dto.ExpenseDtos.CategoryView;
import com.haodaone.expense.dto.ExpenseDtos.ClaimRequest;
import com.haodaone.expense.dto.ExpenseDtos.ClaimView;
import com.haodaone.expense.dto.ExpenseDtos.DecisionRequest;
import com.haodaone.expense.dto.ExpenseDtos.RejectRequest;
import com.haodaone.expense.dto.ExpenseDtos.ReimburseRequest;
import com.haodaone.expense.dto.ExpenseDtos.SettingsRequest;
import com.haodaone.expense.dto.ExpenseDtos.SettingsView;
import com.haodaone.expense.service.ExpenseCategoryService;
import com.haodaone.expense.service.ExpenseClaimService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * Expense claims. Self-service endpoints need SELF_EXPENSE_SUBMIT (ownership
 * is enforced in the service); manager-stage review is open to reporting
 * managers and EXPENSE_APPROVE holders in scope; final approval,
 * reimbursement, reports and settings need EXPENSE_MANAGE at organization scope.
 */
@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {

    private static final String SELF = "hasAuthority('SELF_EXPENSE_SUBMIT')";
    private static final String FINANCE = "@authorizationService.hasOrganizationScope('EXPENSE_MANAGE')";

    private final ExpenseClaimService claimService;
    private final ExpenseCategoryService categoryService;

    public ExpenseController(ExpenseClaimService claimService, ExpenseCategoryService categoryService) {
        this.claimService = claimService;
        this.categoryService = categoryService;
    }

    // ---- categories & settings

    @GetMapping("/categories")
    @PreAuthorize("hasAuthority('SELF_EXPENSE_SUBMIT') or hasAuthority('EXPENSE_APPROVE') or hasAuthority('EXPENSE_MANAGE')")
    public List<CategoryView> categories(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return categoryService.list(!includeInactive);
    }

    @PostMapping("/categories")
    @PreAuthorize(FINANCE)
    public ResponseEntity<CategoryView> createCategory(@RequestBody CategoryRequest request) {
        return ResponseEntity.status(201).body(categoryService.create(request));
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize(FINANCE)
    public CategoryView updateCategory(@PathVariable Long id, @RequestBody CategoryRequest request) {
        return categoryService.update(id, request);
    }

    @GetMapping("/settings")
    @PreAuthorize("hasAuthority('SELF_EXPENSE_SUBMIT') or hasAuthority('EXPENSE_APPROVE') or hasAuthority('EXPENSE_MANAGE')")
    public SettingsView settings() {
        return categoryService.settings();
    }

    @PutMapping("/settings")
    @PreAuthorize(FINANCE)
    public SettingsView updateSettings(@RequestBody SettingsRequest request) {
        return categoryService.updateSettings(request);
    }

    // ---- self service

    @GetMapping("/claims/my")
    @PreAuthorize(SELF)
    public List<ClaimView> myClaims() {
        return claimService.myClaims();
    }

    @PostMapping("/claims")
    @PreAuthorize(SELF)
    public ResponseEntity<ClaimView> create(@RequestBody ClaimRequest request) {
        return ResponseEntity.status(201).body(claimService.create(request));
    }

    @PutMapping("/claims/{id}")
    @PreAuthorize(SELF)
    public ClaimView update(@PathVariable Long id, @RequestBody ClaimRequest request) {
        return claimService.update(id, request);
    }

    @DeleteMapping("/claims/{id}")
    @PreAuthorize(SELF)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        claimService.deleteDraft(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/claims/{id}/submit")
    @PreAuthorize(SELF)
    public ClaimView submit(@PathVariable Long id) {
        return claimService.submit(id);
    }

    @PostMapping(value = "/claims/{id}/lines/{lineId}/receipt", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(SELF)
    public ClaimView uploadReceipt(@PathVariable Long id, @PathVariable Long lineId, @RequestPart("file") MultipartFile file) {
        return claimService.uploadReceipt(id, lineId, file);
    }

    @DeleteMapping("/claims/{id}/lines/{lineId}/receipt")
    @PreAuthorize(SELF)
    public ClaimView removeReceipt(@PathVariable Long id, @PathVariable Long lineId) {
        return claimService.removeReceipt(id, lineId);
    }

    /** Owner, manager-stage approver in scope, or finance - checked in the service. */
    @GetMapping("/claims/{id}/lines/{lineId}/receipt")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<InputStreamResource> downloadReceipt(@PathVariable Long id, @PathVariable Long lineId) {
        ExpenseClaimService.ReceiptDownload download = claimService.receipt(id, lineId);
        MediaType type = download.contentType() == null ? MediaType.APPLICATION_OCTET_STREAM : MediaType.parseMediaType(download.contentType());
        String safeName = download.fileName().replaceAll("[\"\\r\\n\\\\]", "");
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + safeName + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .body(download.resource());
    }

    /** Owner, manager-stage approver in scope, or finance - checked in the service. */
    @GetMapping("/claims/{id}")
    @PreAuthorize("isAuthenticated()")
    public ClaimView get(@PathVariable Long id) {
        return claimService.get(id);
    }

    // ---- approvals

    @GetMapping("/approvals/manager")
    @PreAuthorize("hasAuthority('EXPENSE_APPROVE') or hasAuthority('EXPENSE_MANAGE')")
    public List<ClaimView> managerQueue() {
        return claimService.managerQueue();
    }

    @GetMapping("/approvals/finance")
    @PreAuthorize(FINANCE)
    public List<ClaimView> financeQueue() {
        return claimService.financeQueue();
    }

    /** Reporting manager or EXPENSE_APPROVE in scope (or finance) - checked in the service; never own claims. */
    @PostMapping("/claims/{id}/manager-approve")
    @PreAuthorize("isAuthenticated()")
    public ClaimView managerApprove(@PathVariable Long id, @RequestBody(required = false) DecisionRequest request) {
        return claimService.managerApprove(id, request == null ? null : request.note());
    }

    @PostMapping("/claims/{id}/approve")
    @PreAuthorize(FINANCE)
    public ClaimView approve(@PathVariable Long id, @RequestBody(required = false) DecisionRequest request) {
        return claimService.finalApprove(id, request == null ? null : request.note());
    }

    @PostMapping("/claims/{id}/reject")
    @PreAuthorize("isAuthenticated()")
    public ClaimView reject(@PathVariable Long id, @RequestBody RejectRequest request) {
        return claimService.reject(id, request == null ? null : request.reason());
    }

    @PostMapping("/claims/{id}/reimburse")
    @PreAuthorize(FINANCE)
    public ClaimView reimburse(@PathVariable Long id, @RequestBody ReimburseRequest request) {
        return claimService.markReimbursed(id, request == null ? null : request.paymentReference());
    }

    // ---- reports

    @GetMapping("/claims")
    @PreAuthorize(FINANCE)
    public List<ClaimView> list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                @RequestParam(required = false) String status,
                                @RequestParam(required = false) Long departmentId) {
        return claimService.list(from, to, status, departmentId);
    }

    @GetMapping("/claims/export")
    @PreAuthorize(FINANCE)
    public ResponseEntity<byte[]> export(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(required = false) Long departmentId) {
        String csv = claimService.exportCsv(from, to, status, departmentId);
        byte[] body = ("﻿" + csv).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"expense-claims.csv\"")
                .body(body);
    }
}
