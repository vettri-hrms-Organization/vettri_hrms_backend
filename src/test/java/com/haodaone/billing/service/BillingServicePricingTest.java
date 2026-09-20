package com.haodaone.billing.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BillingServicePricingTest {

    @Test
    void shouldCalculateSubscriptionAmountFromAuthoritativeRates() throws Exception {
        BillingService service = new BillingService(null, null, null, null, null);

        Method subscriptionMethod = BillingService.class.getDeclaredMethod("calculateSubscriptionAmount", String.class, Integer.class, String.class);
        subscriptionMethod.setAccessible(true);
        Object monthlySubscription = subscriptionMethod.invoke(service, "VETTRI_HRMS", 25, "MONTHLY");
        Object quarterlySubscription = subscriptionMethod.invoke(service, "VETTRI_HRMS", 25, "QUARTERLY");
        Object annualSubscription = subscriptionMethod.invoke(service, "VETTRI_HRMS", 25, "ANNUAL");

        assertEquals(new BigDecimal("4975.00"), monthlySubscription);
        assertEquals(new BigDecimal("13425.00"), quarterlySubscription);
        assertEquals(new BigDecimal("53700.00"), annualSubscription);
    }
}
