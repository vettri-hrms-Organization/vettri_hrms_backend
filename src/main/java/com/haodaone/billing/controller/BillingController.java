package com.haodaone.billing.controller;

import com.haodaone.billing.dto.CreateOrderRequest;
import com.haodaone.billing.dto.PaymentVerificationRequest;
import com.haodaone.billing.dto.PlanPricingResponse;
import com.haodaone.billing.service.BillingService;
import com.haodaone.common.exception.BadRequestException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
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
        return ResponseEntity.ok(billingService.createOrder(request.companyId(), request.userId(), request.plan()));
    }

    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verifyPayment(@Valid @RequestBody PaymentVerificationRequest request) {
        Map<String, Object> verified = billingService.verifyPayment(
                request.companyId(),
                request.userId(),
                request.plan(),
                request.razorpayPaymentId(),
                request.razorpayOrderId(),
                request.razorpaySignature());
        return ResponseEntity.ok(verified);
    }
}
