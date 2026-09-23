package com.haodaone.billing.service;

import com.haodaone.common.exception.BadRequestException;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;

public final class BillingPricing {

    public static final String TRIAL = "TRIAL";
    public static final String STARTER = "STARTER";
    public static final String BUSINESS = "BUSINESS";
    public static final String ENTERPRISE = "ENTERPRISE";

    public static final String MONTHLY = "MONTHLY";
    public static final String QUARTERLY = "QUARTERLY";
    public static final String ANNUAL = "ANNUAL";

    private static final Map<String, Map<String, BigDecimal>> RATES = Map.of(
            TRIAL, Map.of(
                    MONTHLY, BigDecimal.ZERO,
                    QUARTERLY, BigDecimal.ZERO,
                    ANNUAL, BigDecimal.ZERO
            ),
            STARTER, Map.of(
                    MONTHLY, new BigDecimal("199"),
                    QUARTERLY, new BigDecimal("537"),
                    ANNUAL, new BigDecimal("2148")
            ),
            BUSINESS, Map.of(
                    MONTHLY, new BigDecimal("199"),
                    QUARTERLY, new BigDecimal("537"),
                    ANNUAL, new BigDecimal("2148")
            ),
            ENTERPRISE, Map.of(
                    MONTHLY, new BigDecimal("199"),
                    QUARTERLY, new BigDecimal("537"),
                    ANNUAL, new BigDecimal("2148")
            ),
            "VETTRI_HRMS", Map.of(
                    MONTHLY, new BigDecimal("199"),
                    QUARTERLY, new BigDecimal("537"),
                    ANNUAL, new BigDecimal("2148")
            )
    );

    private BillingPricing() {
    }

    public static BigDecimal rateFor(String plan, String billingCycle) {
        String normalizedPlan = normalizePlan(plan);
        String normalizedCycle = normalizeBillingCycle(billingCycle);
        if (normalizedPlan == null) {
            throw new BadRequestException("Plan not found.");
        }
        Map<String, BigDecimal> planRates = RATES.get(normalizedPlan);
        if (planRates == null) {
            throw new BadRequestException("Plan not found.");
        }
        BigDecimal rate = planRates.get(normalizedCycle);
        if (rate == null) {
            throw new BadRequestException("Unsupported billing cycle.");
        }
        return rate;
    }

    public static String resolveSignupPlan(String plan) {
        if (plan == null || plan.isBlank()) {
            return TRIAL;
        }
        String trimmed = plan.trim();
        if (trimmed.equalsIgnoreCase("VETTRI_HRMS") || trimmed.equalsIgnoreCase("VETTRI") || trimmed.equalsIgnoreCase("HRMS")) {
            throw new BadRequestException("Legacy plan selection is no longer valid for new signups. Please choose a valid paid plan or start a free trial.");
        }
        String normalizedPlan = normalizePlan(trimmed);
        if (normalizedPlan == null) {
            throw new BadRequestException("Unsupported plan selection.");
        }
        if (normalizedPlan.equals(TRIAL)) {
            return TRIAL;
        }
        if (isExplicitPaidPlan(normalizedPlan)) {
            return normalizedPlan;
        }
        throw new BadRequestException("Unsupported plan selection.");
    }

    public static boolean isExplicitPaidPlan(String plan) {
        if (plan == null || plan.isBlank()) {
            return false;
        }
        String normalizedPlan = normalizePlan(plan);
        if (normalizedPlan == null) {
            return false;
        }
        return switch (normalizedPlan) {
            case STARTER, BUSINESS, ENTERPRISE -> true;
            default -> false;
        };
    }

    public static String normalizePlan(String plan) {
        if (plan == null) {
            return null;
        }
        return switch (plan.trim().toUpperCase(Locale.ROOT)) {
            case "TRIAL" -> TRIAL;
            case STARTER, "STARTER_PLAN" -> STARTER;
            case BUSINESS, "BUSINESS_PLAN" -> BUSINESS;
            case ENTERPRISE, "ENTERPRISE_PLAN" -> ENTERPRISE;
            case "VETTRI_HRMS", "VETTRI", "HRMS" -> "VETTRI_HRMS";
            default -> null;
        };
    }

    public static String normalizeBillingCycle(String billingCycle) {
        if (billingCycle == null || billingCycle.isBlank()) {
            throw new BadRequestException("Billing cycle is required.");
        }
        return switch (billingCycle.trim().toUpperCase(Locale.ROOT)) {
            case MONTHLY -> MONTHLY;
            case QUARTERLY -> QUARTERLY;
            case ANNUAL -> ANNUAL;
            default -> throw new BadRequestException("Unsupported billing cycle.");
        };
    }

    public static int normalizeEmployeeCount(Integer employeeCount) {
        if (employeeCount == null || employeeCount < 1) {
            throw new BadRequestException("Employee count must be at least 1.");
        }
        return employeeCount;
    }
}
