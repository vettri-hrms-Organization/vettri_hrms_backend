package com.haodaone.billing.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Request / response records for subscription billing. */
public final class BillingDtos {

    private BillingDtos() {
    }

    /** Lightweight status for the global banner (visible to every signed-in user). */
    public record BillingStatusResponse(
            String status,
            String plan,
            String billingCycle,
            LocalDate renewalDate,
            Long daysLeft,
            LocalDate graceEndsOn,
            boolean readOnly,
            boolean trial,
            boolean canPay,
            boolean cancelAtPeriodEnd,
            String suspensionReason,
            Long dueInvoiceId,
            BigDecimal dueAmount
    ) {
    }

    public record PlanOption(String plan, String label, BigDecimal monthlyRate, BigDecimal quarterlyRate, BigDecimal annualRate) {
    }

    public record SubscriptionOverview(
            Long subscriptionId,
            String status,
            String plan,
            String billingCycle,
            BigDecimal rate,
            Integer paidSeats,
            int billableSeats,
            Integer employeeLimit,
            LocalDate periodStart,
            LocalDate renewalDate,
            LocalDate graceEndsOn,
            String pendingPlan,
            String pendingBillingCycle,
            boolean cancelAtPeriodEnd,
            boolean autoDebitActive,
            boolean autoDebitAvailable,
            BigDecimal unbilledAddOns,
            InvoiceResponse dueInvoice,
            List<PlanOption> plans,
            BillingProfile profile
    ) {
    }

    public record BillingProfile(
            String companyName,
            String legalName,
            String gstin,
            String billingAddress,
            String state,
            String stateCode,
            String poNumber
    ) {
    }

    public record BillingProfileRequest(
            @Size(max = 255) String legalName,
            @Size(max = 20) String gstin,
            @Size(max = 1000) String billingAddress,
            @Size(max = 100) String state,
            @Size(max = 2) String stateCode,
            @Size(max = 60) String poNumber
    ) {
    }

    public record ChangePlanRequest(@NotBlank String plan, @NotBlank String billingCycle) {
    }

    public record RenewalVerifyRequest(@NotBlank String razorpayOrderId, @NotBlank String razorpayPaymentId,
                                       @NotBlank String razorpaySignature) {
    }

    public record AdminCompanyBilling(
            Long companyId,
            String companyName,
            Long subscriptionId,
            String plan,
            String status,
            String billingCycle,
            LocalDate renewalDate,
            LocalDate graceEndsOn,
            Integer paidSeats,
            int billableSeats,
            BigDecimal mrr,
            boolean autoDebit
    ) {
    }

    public record ExtendRequest(@Min(1) @Max(366) int days, @Size(max = 500) String reason) {
    }

    public record ReasonRequest(@Size(max = 500) String reason) {
    }

    public record CreditNoteRequest(BigDecimal amount, @NotBlank @Size(max = 500) String reason) {
    }
}
