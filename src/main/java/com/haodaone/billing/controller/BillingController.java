package com.haodaone.billing.controller;

import com.haodaone.billing.dto.CreateOrderRequest;
import com.haodaone.billing.dto.PaymentVerificationRequest;
import com.haodaone.billing.dto.PlanPricingResponse;
import com.haodaone.billing.service.BillingService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.security.CustomUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
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

    /**
     * Checkout is reachable without a session only for the signup flow, where the
     * new owner's account is still PENDING_PAYMENT and the caller must also know the
     * account email. Signed-in callers always pay for their own company, and only
     * a company admin may change the subscription.
     */
    @PostMapping("/create-order")
    public ResponseEntity<Map<String, Object>> createOrder(@AuthenticationPrincipal CustomUserPrincipal principal,
                                                           @Valid @RequestBody CreateOrderRequest request) {
        if (principal != null) {
            requireCompanyAdmin(principal);
            return ResponseEntity.ok(billingService.createOrder(principal.getCompanyId(), principal.getId(), request.plan(),
                    request.billingCycle(), request.employeeCount(), false, null));
        }
        if (request.companyId() == null || request.userId() == null || request.customerEmail() == null || request.customerEmail().isBlank()) {
            throw new BadRequestException("Company, user and email are required.");
        }
        return ResponseEntity.ok(billingService.createOrder(request.companyId(), request.userId(), request.plan(),
                request.billingCycle(), request.employeeCount(), true, request.customerEmail()));
    }

    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verifyPayment(@AuthenticationPrincipal CustomUserPrincipal principal,
                                                            @Valid @RequestBody PaymentVerificationRequest request) {
        Long effectiveCompanyId = request.companyId();
        Long effectiveUserId = request.userId();
        boolean signupCheckout = principal == null;

        if (principal != null) {
            requireCompanyAdmin(principal);
            effectiveCompanyId = principal.getCompanyId();
            effectiveUserId = principal.getId();
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
                request.razorpaySignature(),
                signupCheckout);
        return ResponseEntity.ok(verified);
    }

    private static void requireCompanyAdmin(CustomUserPrincipal principal) {
        boolean admin = principal.getRoleNames().stream()
                .anyMatch(role -> "COMPANY_ADMIN".equals(role) || "SUPER_ADMIN".equals(role));
        if (!admin || principal.getCompanyId() == null) {
            throw new AccessDeniedException("Only a company admin can change the subscription.");
        }
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(@RequestHeader(value = "X-Razorpay-Signature", required = false) String razorpaySignature,
                                              @RequestHeader(value = "X-Razorpay-Event-Id", required = false) String razorpayEventId,
                                              @RequestBody String rawBody) {
        billingService.processWebhookEvent(rawBody, razorpaySignature, razorpayEventId);
        return ResponseEntity.ok("ok");
    }
}
