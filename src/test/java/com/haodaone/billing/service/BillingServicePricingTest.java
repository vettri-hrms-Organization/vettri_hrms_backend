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

        Method verificationMethod = BillingService.class.getDeclaredMethod("calculateVerificationAmount");
        verificationMethod.setAccessible(true);
        assertEquals(new BigDecimal("1.00"), verificationMethod.invoke(service));
    }

    @Test
    void shouldDefaultToFreeTrialWhenNoExplicitPaidPlanIsSelected() {
        assertEquals("TRIAL", BillingPricing.resolveSignupPlan(null));
        assertEquals("TRIAL", BillingPricing.resolveSignupPlan(""));
        assertEquals("TRIAL", BillingPricing.resolveSignupPlan("TRIAL"));
    }

    @Test
    void shouldAcceptExplicitPaidPlansAndRejectLegacyPlanNames() {
        assertEquals("STARTER", BillingPricing.resolveSignupPlan("STARTER"));
        assertEquals("BUSINESS", BillingPricing.resolveSignupPlan("business"));
        assertEquals("ENTERPRISE", BillingPricing.resolveSignupPlan("Enterprise"));

        assertEquals(Boolean.TRUE, BillingPricing.isExplicitPaidPlan("STARTER"));
        assertEquals(Boolean.TRUE, BillingPricing.isExplicitPaidPlan("BUSINESS"));
        assertEquals(Boolean.TRUE, BillingPricing.isExplicitPaidPlan("ENTERPRISE"));

        org.junit.jupiter.api.Assertions.assertThrows(
                com.haodaone.common.exception.BadRequestException.class,
                () -> BillingPricing.resolveSignupPlan("VETTRI_HRMS")
        );
    }
}
