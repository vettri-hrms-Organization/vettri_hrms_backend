package com.haodaone.expense.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/** Request/response shapes for the expense module. Timestamps are IST offset date-times. */
public final class ExpenseDtos {

    private ExpenseDtos() {
    }

    public record CategoryView(
            Long id,
            String code,
            String name,
            boolean active,
            BigDecimal perClaimLimit,
            String limitAction,
            BigDecimal receiptRequiredAbove,
            String receiptAction,
            BigDecimal mileageRatePerKm,
            int sortOrder) {
    }

    public record CategoryRequest(
            String name,
            Boolean active,
            BigDecimal perClaimLimit,
            String limitAction,
            BigDecimal receiptRequiredAbove,
            String receiptAction,
            BigDecimal mileageRatePerKm,
            Integer sortOrder) {
    }

    public record SettingsView(String reimbursementMode) {
    }

    public record SettingsRequest(String reimbursementMode) {
    }

    public record LineRequest(
            Long id,
            LocalDate expenseDate,
            Long categoryId,
            String description,
            BigDecimal amount,
            BigDecimal mileageKm,
            BigDecimal mileageRate) {
    }

    public record ClaimRequest(String title, String purpose, List<LineRequest> lines) {
    }

    public record DecisionRequest(String note) {
    }

    public record RejectRequest(String reason) {
    }

    public record ReimburseRequest(String paymentReference) {
    }

    public record PolicyIssue(
            Long lineId,
            Integer lineNumber,
            String categoryName,
            String code,
            String severity,
            String message) {
    }

    public record LineView(
            Long id,
            LocalDate expenseDate,
            Long categoryId,
            String categoryName,
            String description,
            BigDecimal amount,
            BigDecimal mileageKm,
            BigDecimal mileageRate,
            boolean hasReceipt,
            String receiptFileName) {
    }

    public record ClaimView(
            Long id,
            Long employeeId,
            String employeeName,
            String employeeCode,
            String departmentName,
            String title,
            String purpose,
            String status,
            BigDecimal totalAmount,
            OffsetDateTime createdAt,
            OffsetDateTime submittedAt,
            String managerApprovedByName,
            OffsetDateTime managerApprovedAt,
            String managerNote,
            String approvedByName,
            OffsetDateTime approvedAt,
            String approverNote,
            String rejectedByName,
            OffsetDateTime rejectedAt,
            String rejectionReason,
            String rejectedStage,
            OffsetDateTime reimbursedAt,
            String reimbursementMethod,
            String paymentReference,
            Long payrollItemId,
            List<PolicyIssue> policyIssues,
            List<LineView> lines) {
    }
}
