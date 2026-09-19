package com.haodaone.billing.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BillingServicePricingTest {

    @Test
    void shouldSeparateTrialAmountFromSubscriptionAmount() throws Exception {
        BillingService service = new BillingService(null, null, null, null, null);

        Method subscriptionMethod = BillingService.class.getDeclaredMethod("calculateSubscriptionAmount", Integer.class, String.class);
        subscriptionMethod.setAccessible(true);
        Object monthlySubscription = subscriptionMethod.invoke(service, 25, "MONTHLY");
        Object quarterlySubscription = subscriptionMethod.invoke(service, 25, "QUARTERLY");
        Object annualSubscription = subscriptionMethod.invoke(service, 25, "ANNUAL");

        Method trialMethod = BillingService.class.getDeclaredMethod("calculateTrialAmount", String.class);
        trialMethod.setAccessible(true);
        Object trialAmount = trialMethod.invoke(service, "MONTHLY");

        assertEquals(new BigDecimal("2475.00"), monthlySubscription);
        assertEquals(new BigDecimal("6975.00"), quarterlySubscription);
        assertEquals(new BigDecimal("24975.00"), annualSubscription);
        assertEquals(new BigDecimal("1.00"), trialAmount);
    }
}
