package com.haodaone.billing.controller;

import com.haodaone.billing.dto.BillingDtos.*;
import com.haodaone.billing.dto.InvoiceResponse;
import com.haodaone.billing.service.BillingAccountService;
import com.haodaone.billing.service.BillingLifecycleService;
import com.haodaone.company.dto.SubscriptionAdminDTO;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Platform (SUPER_ADMIN) billing: company list with plan / status / renewal / MRR and audited overrides. */
@RestController
@RequestMapping("/api/admin/billing")
@PreAuthorize("@companySecurity.isSuperAdmin()")
public class PlatformBillingController {

    private final BillingAccountService accounts;
    private final BillingLifecycleService lifecycle;

    public PlatformBillingController(BillingAccountService accounts, BillingLifecycleService lifecycle) {
        this.accounts = accounts;
        this.lifecycle = lifecycle;
    }

    @GetMapping("/companies")
    public List<AdminCompanyBilling> companies() {
        return accounts.adminCompanies();
    }

    @PostMapping("/subscriptions/{id}/extend")
    public SubscriptionAdminDTO extend(@PathVariable Long id, @Valid @RequestBody ExtendRequest request) {
        return SubscriptionAdminDTO.from(lifecycle.extend(accounts.subscriptionById(id), request.days(), request.reason()));
    }

    @PostMapping("/subscriptions/{id}/suspend")
    public SubscriptionAdminDTO suspend(@PathVariable Long id, @Valid @RequestBody(required = false) ReasonRequest request) {
        return SubscriptionAdminDTO.from(lifecycle.suspend(accounts.subscriptionById(id), request == null ? null : request.reason()));
    }

    @PostMapping("/subscriptions/{id}/reactivate")
    public SubscriptionAdminDTO reactivate(@PathVariable Long id, @Valid @RequestBody(required = false) ReasonRequest request) {
        return SubscriptionAdminDTO.from(lifecycle.reactivate(accounts.subscriptionById(id), request == null ? null : request.reason()));
    }

    @PostMapping("/invoices/{id}/credit-note")
    public InvoiceResponse creditNote(@PathVariable Long id, @Valid @RequestBody CreditNoteRequest request) {
        return accounts.issueCreditNote(id, request.amount(), request.reason());
    }

    /** Runs today's lifecycle sweep now (idempotent). */
    @PostMapping("/run-lifecycle")
    public Map<String, Object> runLifecycle() {
        return Map.of("processed", lifecycle.runDaily(BillingLifecycleService.today()));
    }
}
