package com.haodaone.billing.dto;

public record PaymentVerificationRequest(
        Long companyId,
        Long userId,
        String plan,
        String razorpayPaymentId,
        String razorpayOrderId,
        String razorpaySignature
) {
}
