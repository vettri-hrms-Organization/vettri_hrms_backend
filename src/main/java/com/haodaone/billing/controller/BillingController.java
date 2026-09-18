package com.haodaone.billing.controller;

import com.haodaone.billing.dto.CreateOrderRequest;
import com.haodaone.billing.dto.PaymentVerificationRequest;
import com.haodaone.billing.dto.PlanPricingResponse;
import com.haodaone.billing.service.BillingService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.security.CustomUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/billing")
public class BillingController {

    private final BillingService billingService;

    public BillingController(BillingService billingService) {
        this.billingService = billingService;
    }

    @GetMapping("/plans")
    public List<PlanPricingResponse> plans() {
        return billingService.pricing();
    }

    @PostMapping("/create-order")
    public ResponseEntity<Map<String, Object>> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        if (request.companyId() == null || request.userId() == null) {
            throw new BadRequestException("Company and user identifiers are required.");
        }
        return ResponseEntity.ok(billingService.createOrder(request.companyId(), request.userId(), request.plan(), request.billingCycle(), request.employeeCount()));
    }

    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verifyPayment(@AuthenticationPrincipal CustomUserPrincipal principal,
                                                            @Valid @RequestBody PaymentVerificationRequest request) {
        Long effectiveCompanyId = request.companyId();
        Long effectiveUserId = request.userId();

        if (principal != null) {
            if (principal.getCompanyId() != null && (effectiveCompanyId == null || !principal.getCompanyId().equals(effectiveCompanyId))) {
                effectiveCompanyId = principal.getCompanyId();
            }
            if (principal.getId() != null && (effectiveUserId == null || !principal.getId().equals(effectiveUserId))) {
                effectiveUserId = principal.getId();
            }
        }

        if (effectiveCompanyId == null || effectiveUserId == null) {
            throw new BadRequestException("Company and user identifiers are required.");
        }

        Map<String, Object> verified = billingService.verifyPayment(
                effectiveCompanyId,
                effectiveUserId,
                request.plan(),
                request.billingCycle(),
                request.employeeCount(),
                request.razorpayPaymentId(),
                request.razorpayOrderId(),
                request.razorpaySignature());
        return ResponseEntity.ok(verified);
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(@RequestHeader(value = "X-Razorpay-Signature", required = false) String razorpaySignature,
                                              @RequestBody String rawBody) {
        billingService.processWebhookEvent(rawBody, razorpaySignature);
        return ResponseEntity.ok("ok");
    }
}
