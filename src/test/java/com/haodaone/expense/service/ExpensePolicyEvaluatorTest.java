package com.haodaone.expense.service;

import com.haodaone.expense.dto.ExpenseDtos.PolicyIssue;
import com.haodaone.expense.entity.ExpenseCategory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExpensePolicyEvaluatorTest {

    private static ExpenseCategory category(String name, String limit, String limitAction, String receiptAbove, String receiptAction) {
        ExpenseCategory category = new ExpenseCategory();
        category.setName(name);
        category.setPerClaimLimit(limit == null ? null : new BigDecimal(limit));
        category.setLimitAction(limitAction);
        category.setReceiptRequiredAbove(receiptAbove == null ? null : new BigDecimal(receiptAbove));
        category.setReceiptAction(receiptAction);
        return category;
    }

    @Test
    void noRulesMeansNoIssues() {
        ExpenseCategory other = category("Other", null, "WARN", null, "WARN");
        List<PolicyIssue> issues = ExpensePolicyEvaluator.evaluate(List.of(
                new ExpensePolicyEvaluator.PolicyLine(1L, 1, other, new BigDecimal("99999"), false)));
        assertThat(issues).isEmpty();
    }

    @Test
    void perClaimLimitIsCheckedAgainstTheCategoryTotalNotEachLine() {
        ExpenseCategory food = category("Food", "1000", "BLOCK", null, "WARN");
        List<PolicyIssue> issues = ExpensePolicyEvaluator.evaluate(List.of(
                new ExpensePolicyEvaluator.PolicyLine(1L, 1, food, new BigDecimal("600"), true),
                new ExpensePolicyEvaluator.PolicyLine(2L, 2, food, new BigDecimal("500"), true)));

        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).code()).isEqualTo(ExpensePolicyEvaluator.CODE_LIMIT_EXCEEDED);
        assertThat(issues.get(0).severity()).isEqualTo(ExpensePolicyEvaluator.SEVERITY_BLOCK);
        assertThat(ExpensePolicyEvaluator.hasBlocking(issues)).isTrue();
    }

    @Test
    void limitExactlyReachedIsAllowed() {
        ExpenseCategory food = category("Food", "1000", "BLOCK", null, "WARN");
        assertThat(ExpensePolicyEvaluator.evaluate(List.of(
                new ExpensePolicyEvaluator.PolicyLine(1L, 1, food, new BigDecimal("1000.00"), false)))).isEmpty();
    }

    @Test
    void missingReceiptAboveThresholdWarnsOrBlocksPerCategory() {
        ExpenseCategory travel = category("Travel", null, "WARN", "500", "WARN");
        ExpenseCategory stay = category("Accommodation", null, "WARN", "500", "BLOCK");
        List<PolicyIssue> issues = ExpensePolicyEvaluator.evaluate(List.of(
                new ExpensePolicyEvaluator.PolicyLine(1L, 1, travel, new BigDecimal("800"), false),
                new ExpensePolicyEvaluator.PolicyLine(2L, 2, stay, new BigDecimal("2500"), false),
                new ExpensePolicyEvaluator.PolicyLine(3L, 3, stay, new BigDecimal("2500"), true),
                new ExpensePolicyEvaluator.PolicyLine(4L, 4, travel, new BigDecimal("500"), false)));

        assertThat(issues).extracting(PolicyIssue::lineId).containsExactly(1L, 2L);
        assertThat(issues).extracting(PolicyIssue::severity)
                .containsExactly(ExpensePolicyEvaluator.SEVERITY_WARN, ExpensePolicyEvaluator.SEVERITY_BLOCK);
        assertThat(issues.get(1).message()).contains("Line 2").contains("receipt");
    }

    @Test
    void warningsAloneAreNotBlocking() {
        ExpenseCategory travel = category("Travel", "100", "WARN", "50", "WARN");
        List<PolicyIssue> issues = ExpensePolicyEvaluator.evaluate(List.of(
                new ExpensePolicyEvaluator.PolicyLine(1L, 1, travel, new BigDecimal("150"), false)));
        assertThat(issues).hasSize(2);
        assertThat(ExpensePolicyEvaluator.hasBlocking(issues)).isFalse();
    }
}
