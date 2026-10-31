package com.haodaone.loan;

import com.haodaone.security.CustomUserPrincipal;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/** Loans and salary advances. /me is the employee's own; everything else needs SALARY_MANAGE (payroll admins). */
@RestController
@RequestMapping("/api/loans")
@PreAuthorize("isAuthenticated()")
public class LoanController {

    private static final String PAYROLL_ADMIN = "hasAuthority('SALARY_MANAGE')";

    private final LoanService service;

    public LoanController(LoanService service) {
        this.service = service;
    }

    @GetMapping("/me")
    public List<LoanService.LoanDTO> mine() {
        return service.mine();
    }

    @PostMapping("/me")
    public LoanService.LoanDTO request(@RequestBody LoanService.RequestBody body) {
        return service.request(body);
    }

    @PostMapping("/me/{id}/cancel")
    public LoanService.LoanDTO cancelMine(@PathVariable Long id) {
        return service.cancelMine(id);
    }

    @GetMapping("/preview")
    public LoanService.Preview preview(@RequestParam BigDecimal amount, @RequestParam Integer months,
                                       @RequestParam(required = false) BigDecimal rate, @RequestParam(required = false) String firstMonth) {
        return service.preview(amount, months, rate, firstMonth);
    }

    @GetMapping
    @PreAuthorize(PAYROLL_ADMIN)
    public List<LoanService.LoanDTO> list(@RequestParam(required = false) String status) {
        return service.list(status);
    }

    @PostMapping
    @PreAuthorize(PAYROLL_ADMIN)
    public LoanService.LoanDTO create(@RequestBody LoanService.CreateBody body, @AuthenticationPrincipal CustomUserPrincipal principal) {
        return service.create(body, name(principal));
    }

    @GetMapping("/{id}")
    @PreAuthorize(PAYROLL_ADMIN)
    public LoanService.LoanDTO get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize(PAYROLL_ADMIN)
    public LoanService.LoanDTO approve(@PathVariable Long id, @RequestBody(required = false) LoanService.ApproveBody body,
                                       @AuthenticationPrincipal CustomUserPrincipal principal) {
        return service.approve(id, body, name(principal));
    }

    public record NoteBody(String note) { }

    @PostMapping("/{id}/reject")
    @PreAuthorize(PAYROLL_ADMIN)
    public LoanService.LoanDTO reject(@PathVariable Long id, @RequestBody(required = false) NoteBody body,
                                      @AuthenticationPrincipal CustomUserPrincipal principal) {
        return service.reject(id, body == null ? null : body.note(), name(principal));
    }

    @PostMapping("/{id}/disburse")
    @PreAuthorize(PAYROLL_ADMIN)
    public LoanService.LoanDTO disburse(@PathVariable Long id, @RequestBody(required = false) LoanService.DisburseBody body,
                                        @AuthenticationPrincipal CustomUserPrincipal principal) {
        return service.markDisbursed(id, body, name(principal));
    }

    @PostMapping("/{id}/installments/{installmentId}/defer")
    @PreAuthorize(PAYROLL_ADMIN)
    public LoanService.LoanDTO defer(@PathVariable Long id, @PathVariable Long installmentId, @RequestBody(required = false) NoteBody body,
                                     @AuthenticationPrincipal CustomUserPrincipal principal) {
        return service.defer(id, installmentId, body == null ? null : body.note(), name(principal));
    }

    @PostMapping("/{id}/payments")
    @PreAuthorize(PAYROLL_ADMIN)
    public LoanService.LoanDTO payment(@PathVariable Long id, @RequestBody LoanService.PaymentBody body,
                                       @AuthenticationPrincipal CustomUserPrincipal principal) {
        return service.recordPayment(id, body, name(principal));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(PAYROLL_ADMIN)
    public LoanService.LoanDTO cancel(@PathVariable Long id, @RequestBody(required = false) NoteBody body,
                                      @AuthenticationPrincipal CustomUserPrincipal principal) {
        return service.cancelApproved(id, body == null ? null : body.note(), name(principal));
    }

    private static String name(CustomUserPrincipal principal) {
        if (principal == null || principal.getUser() == null) return "Payroll admin";
        String full = principal.getUser().getFullName();
        return full == null || full.isBlank() ? principal.getUsername() : full;
    }
}
