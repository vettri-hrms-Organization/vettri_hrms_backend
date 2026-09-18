package com.haodaone.auth.dto;

import java.math.BigDecimal;

public record SignupRegistrationResponse(
        boolean requiresPayment,
        Long companyId,
        Long userId,
        String plan,
        String currency,
        BigDecimal amount,
        String message
) {
}
