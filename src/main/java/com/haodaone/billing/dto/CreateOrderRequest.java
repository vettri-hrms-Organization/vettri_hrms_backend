package com.haodaone.billing.dto;

public record CreateOrderRequest(
        Long companyId,
        Long userId,
        String plan,
        String customerName,
        String customerEmail,
        String organizationName
) {
}
