package com.haodaone.billing.controller;

import com.haodaone.billing.dto.InvoiceResponse;
import com.haodaone.billing.entity.Invoice;
import com.haodaone.billing.service.InvoiceService;
import com.haodaone.security.CustomUserPrincipal;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/billing/invoices")
@PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'HR_ADMIN', 'SUPER_ADMIN')")
public class InvoiceController {
    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) { this.invoiceService = invoiceService; }

    @GetMapping
    public List<InvoiceResponse> list(@AuthenticationPrincipal CustomUserPrincipal principal,
                                      @RequestParam(defaultValue = "ALL") String status,
                                      @RequestParam(required = false) String search,
                                      @RequestParam(defaultValue = "50") int limit) {
        return invoiceService.list(principal.getCompanyId(), status, search, limit);
    }

    @GetMapping("/{id}")
    public InvoiceResponse get(@PathVariable Long id, @AuthenticationPrincipal CustomUserPrincipal principal) {
        return InvoiceResponse.from(invoiceService.get(id, principal.getCompanyId()));
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id, @AuthenticationPrincipal CustomUserPrincipal principal) {
        Invoice invoice = invoiceService.get(id, principal.getCompanyId());
        String filename = invoice.getInvoiceNumber() + ".pdf";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment().filename(filename).build());
        return ResponseEntity.ok().headers(headers).body(invoiceService.pdf(id, principal.getCompanyId()));
    }

    @PostMapping("/{id}/email")
    public Map<String, String> email(@PathVariable Long id, @AuthenticationPrincipal CustomUserPrincipal principal) {
        invoiceService.email(id, principal.getCompanyId(), principal.getUser().getEmail(), principal.getUser().getFullName());
        return Map.of("status", "sent", "message", "Invoice emailed successfully.");
    }
}
