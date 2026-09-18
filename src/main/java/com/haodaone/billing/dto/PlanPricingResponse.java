package com.haodaone.billing.dto;

import java.math.BigDecimal;

public record PlanPricingResponse(
        String plan,
        String label,
        BigDecimal amount,
        String currency,
        Integer employeeLimit,
        Integer deviceLimit,
        String description
) {
}
