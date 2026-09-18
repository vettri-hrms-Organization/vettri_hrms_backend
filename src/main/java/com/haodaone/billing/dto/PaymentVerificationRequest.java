package com.haodaone.billing.dto;

public record PaymentVerificationRequest(
        Long companyId,
        Long userId,
        String plan,
        String billingCycle,
        Integer employeeCount,
        String razorpayPaymentId,
        String razorpayOrderId,
        String razorpaySignature
) {
}
