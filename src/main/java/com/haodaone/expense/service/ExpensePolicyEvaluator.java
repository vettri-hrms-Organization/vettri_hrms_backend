package com.haodaone.expense.service;

import com.haodaone.expense.dto.ExpenseDtos.PolicyIssue;
import com.haodaone.expense.entity.ExpenseCategory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pure policy rules for a claim's lines - no persistence, easy to unit test.
 *
 * - Per-claim category limit: the sum of all lines in a category on one
 *   claim must not exceed the category's perClaimLimit.
 * - Receipt rule: any line above receiptRequiredAbove needs a receipt.
 *
 * Each rule reports WARN or BLOCK according to the category configuration.
 * BLOCK issues stop submission; WARN issues are recorded on the claim so
 * approvers see them.
 */
public final class ExpensePolicyEvaluator {

    public static final String SEVERITY_WARN = "WARN";
    public static final String SEVERITY_BLOCK = "BLOCK";
    public static final String CODE_LIMIT_EXCEEDED = "LIMIT_EXCEEDED";
    public static final String CODE_RECEIPT_MISSING = "RECEIPT_MISSING";

    private ExpensePolicyEvaluator() {
    }

    /** One line as the evaluator sees it; lineNumber is 1-based for messages. */
    public record PolicyLine(Long lineId, int lineNumber, ExpenseCategory category, BigDecimal amount, boolean hasReceipt) {
    }

    public static List<PolicyIssue> evaluate(List<PolicyLine> lines) {
        List<PolicyIssue> issues = new ArrayList<>();
        Map<ExpenseCategory, BigDecimal> totals = new LinkedHashMap<>();
        for (PolicyLine line : lines) {
            ExpenseCategory category = line.category();
            if (category == null) continue;
            BigDecimal amount = line.amount() == null ? BigDecimal.ZERO : line.amount();
            totals.merge(category, amount, BigDecimal::add);

            BigDecimal threshold = category.getReceiptRequiredAbove();
            if (threshold != null && amount.compareTo(threshold) > 0 && !line.hasReceipt()) {
                issues.add(new PolicyIssue(line.lineId(), line.lineNumber(), category.getName(), CODE_RECEIPT_MISSING,
                        severity(category.getReceiptAction()),
                        "Line " + line.lineNumber() + " (" + category.getName() + ", " + inr(amount)
                                + ") needs a receipt for amounts above " + inr(threshold) + "."));
            }
        }
        for (Map.Entry<ExpenseCategory, BigDecimal> entry : totals.entrySet()) {
            ExpenseCategory category = entry.getKey();
            BigDecimal limit = category.getPerClaimLimit();
            if (limit != null && entry.getValue().compareTo(limit) > 0) {
                issues.add(new PolicyIssue(null, null, category.getName(), CODE_LIMIT_EXCEEDED,
                        severity(category.getLimitAction()),
                        category.getName() + " total " + inr(entry.getValue()) + " exceeds the per-claim limit of "
                                + inr(limit) + "."));
            }
        }
        return issues;
    }

    public static boolean hasBlocking(List<PolicyIssue> issues) {
        return issues.stream().anyMatch(issue -> SEVERITY_BLOCK.equals(issue.severity()));
    }

    private static String severity(String action) {
        return ExpenseCategory.ACTION_BLOCK.equalsIgnoreCase(action) ? SEVERITY_BLOCK : SEVERITY_WARN;
    }

    static String inr(BigDecimal amount) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.of("en", "IN"));
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return "Rs. " + format.format(amount.setScale(2, RoundingMode.HALF_UP));
    }
}
