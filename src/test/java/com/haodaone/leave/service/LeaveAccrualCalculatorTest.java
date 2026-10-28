package com.haodaone.leave.service;

import com.haodaone.common.exception.BadRequestException;
import com.haodaone.leave.service.LeaveAccrualCalculator.CompOffCredit;
import com.haodaone.leave.service.LeaveAccrualCalculator.Debit;
import com.haodaone.leave.service.LeaveAccrualCalculator.Policy;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LeaveAccrualCalculatorTest {

    private static Policy policy(double annual, String mode, String rounding) {
        return new Policy(annual, mode, rounding, false, null, null);
    }

    @Test
    void yearlyUpfrontGivesFullEntitlementOnJanFirstForExistingEmployees() {
        Policy p = policy(12, "YEARLY_UPFRONT", "HALF_DAY");
        assertThat(LeaveAccrualCalculator.allocatedAsOf(p, LocalDate.of(2020, 5, 1), 2026, LocalDate.of(2026, 1, 1))).isEqualTo(12.0);
        assertThat(LeaveAccrualCalculator.allocatedAsOf(p, null, 2026, LocalDate.of(2026, 7, 1))).isEqualTo(12.0);
    }

    @Test
    void yearlyUpfrontProratesMidYearJoinerOnlyWhenEnabled() {
        LocalDate joining = LocalDate.of(2026, 7, 10);
        Policy plain = policy(12, "YEARLY_UPFRONT", "HALF_DAY");
        Policy prorated = new Policy(12, "YEARLY_UPFRONT", "HALF_DAY", true, null, null);
        assertThat(LeaveAccrualCalculator.allocatedAsOf(plain, joining, 2026, LocalDate.of(2026, 12, 31))).isEqualTo(12.0);
        // July..December = 6 months -> 6 days
        assertThat(LeaveAccrualCalculator.allocatedAsOf(prorated, joining, 2026, LocalDate.of(2026, 12, 31))).isEqualTo(6.0);
        // nothing before joining
        assertThat(LeaveAccrualCalculator.allocatedAsOf(prorated, joining, 2026, LocalDate.of(2026, 7, 9))).isZero();
    }

    @Test
    void monthlyAccrualCreditsOnTheFirstAndRoundsCumulativelyToHalfDays() {
        Policy p = policy(15, "MONTHLY", "HALF_DAY"); // 1.25/month
        LocalDate joining = LocalDate.of(2019, 1, 1);
        assertThat(LeaveAccrualCalculator.allocatedAsOf(p, joining, 2026, LocalDate.of(2026, 1, 1))).isEqualTo(1.5);  // 1.25 -> 1.5
        assertThat(LeaveAccrualCalculator.allocatedAsOf(p, joining, 2026, LocalDate.of(2026, 2, 28))).isEqualTo(2.5); // 2.5
        assertThat(LeaveAccrualCalculator.allocatedAsOf(p, joining, 2026, LocalDate.of(2026, 3, 1))).isEqualTo(4.0);  // 3.75 -> 4
        assertThat(LeaveAccrualCalculator.allocatedAsOf(p, joining, 2026, LocalDate.of(2026, 12, 31))).isEqualTo(15.0);
        assertThat(LeaveAccrualCalculator.schedule(p, joining, 2026, LocalDate.of(2026, 12, 31))).hasSize(12);
    }

    @Test
    void monthlyAccrualExactRoundingKeepsTwoDecimals() {
        Policy p = policy(10, "MONTHLY", "EXACT"); // 0.8333/month
        assertThat(LeaveAccrualCalculator.allocatedAsOf(p, null, 2026, LocalDate.of(2026, 1, 15))).isEqualTo(0.83);
        assertThat(LeaveAccrualCalculator.allocatedAsOf(p, null, 2026, LocalDate.of(2026, 5, 1))).isEqualTo(4.17);
        assertThat(LeaveAccrualCalculator.allocatedAsOf(p, null, 2026, LocalDate.of(2026, 12, 1))).isEqualTo(10.0);
    }

    @Test
    void monthlyMidYearJoinerStartsFromJoiningMonthWithOptionalCutoff() {
        Policy p = policy(12, "MONTHLY", "HALF_DAY");
        Policy cutoff = new Policy(12, "MONTHLY", "HALF_DAY", false, 15, null);
        LocalDate joining = LocalDate.of(2026, 4, 20);
        // April..December = 9 months
        assertThat(LeaveAccrualCalculator.allocatedAsOf(p, joining, 2026, LocalDate.of(2026, 12, 31))).isEqualTo(9.0);
        // the April credit is given on the joining date, not April 1
        assertThat(LeaveAccrualCalculator.schedule(p, joining, 2026, LocalDate.of(2026, 12, 31)).get(0).date()).isEqualTo(joining);
        assertThat(LeaveAccrualCalculator.allocatedAsOf(p, joining, 2026, LocalDate.of(2026, 4, 19))).isZero();
        // joined after the 15th -> no credit for April
        assertThat(LeaveAccrualCalculator.allocatedAsOf(cutoff, joining, 2026, LocalDate.of(2026, 4, 30))).isZero();
        assertThat(LeaveAccrualCalculator.allocatedAsOf(cutoff, joining, 2026, LocalDate.of(2026, 12, 31))).isEqualTo(8.0);
        // joined on/before the 15th -> the month counts
        assertThat(LeaveAccrualCalculator.allocatedAsOf(cutoff, LocalDate.of(2026, 4, 15), 2026, LocalDate.of(2026, 4, 30))).isEqualTo(1.0);
    }

    @Test
    void quarterlyAccrualProratesTheJoiningQuarter() {
        Policy p = policy(12, "QUARTERLY", "HALF_DAY");
        assertThat(LeaveAccrualCalculator.allocatedAsOf(p, null, 2026, LocalDate.of(2026, 3, 31))).isEqualTo(3.0);
        assertThat(LeaveAccrualCalculator.allocatedAsOf(p, null, 2026, LocalDate.of(2026, 4, 1))).isEqualTo(6.0);
        // joined in May: May+June of Q2 (2) + Q3 (3) + Q4 (3) = 8
        assertThat(LeaveAccrualCalculator.allocatedAsOf(p, LocalDate.of(2026, 5, 5), 2026, LocalDate.of(2026, 12, 31))).isEqualTo(8.0);
        assertThat(LeaveAccrualCalculator.allocatedAsOf(p, LocalDate.of(2026, 5, 5), 2026, LocalDate.of(2026, 5, 5))).isEqualTo(2.0);
    }

    @Test
    void probationDelaysAccrual() {
        Policy p = new Policy(12, "MONTHLY", "HALF_DAY", false, null, 90);
        LocalDate joining = LocalDate.of(2026, 1, 10); // eligible from 2026-04-10
        assertThat(LeaveAccrualCalculator.allocatedAsOf(p, joining, 2026, LocalDate.of(2026, 4, 9))).isZero();
        assertThat(LeaveAccrualCalculator.allocatedAsOf(p, joining, 2026, LocalDate.of(2026, 4, 10))).isEqualTo(1.0);
        assertThat(LeaveAccrualCalculator.allocatedAsOf(p, joining, 2026, LocalDate.of(2026, 12, 31))).isEqualTo(9.0);
        // employee joining late in the year whose probation ends next year earns nothing this year
        assertThat(LeaveAccrualCalculator.allocatedAsOf(p, LocalDate.of(2026, 11, 1), 2026, LocalDate.of(2026, 12, 31))).isZero();
    }

    @Test
    void futureYearsAndUnjoinedEmployeesAccrueNothingYet() {
        Policy p = policy(12, "MONTHLY", "HALF_DAY");
        assertThat(LeaveAccrualCalculator.allocatedAsOf(p, null, 2027, LocalDate.of(2026, 12, 31))).isZero();
        assertThat(LeaveAccrualCalculator.allocatedAsOf(p, LocalDate.of(2027, 2, 1), 2026, LocalDate.of(2026, 12, 31))).isZero();
    }

    @Test
    void capStopsAccrualAboveMaximumBalance() {
        // 20 accrued + 10 carried, nothing used, cap 25 -> only 15 of the accrual counts
        assertThat(LeaveAccrualCalculator.capAllocation(20, 10, 0, 25.0)).isEqualTo(15.0);
        // usage frees room under the cap
        assertThat(LeaveAccrualCalculator.capAllocation(20, 10, 8, 25.0)).isEqualTo(20.0);
        // carried alone above the cap -> no accrual, carried untouched
        assertThat(LeaveAccrualCalculator.capAllocation(5, 30, 0, 25.0)).isZero();
        assertThat(LeaveAccrualCalculator.capAllocation(20, 10, 0, null)).isEqualTo(20.0);
    }

    @Test
    void carriedDaysLapseAtExpiryUnlessUsedFirst() {
        assertThat(LeaveAccrualCalculator.carryForwardLapsed(6, 2, true)).isEqualTo(4.0);
        assertThat(LeaveAccrualCalculator.carryForwardLapsed(6, 8, true)).isZero();
        assertThat(LeaveAccrualCalculator.carryForwardLapsed(6, 0, false)).isZero();
    }

    @Test
    void yearEndSplitsClosingIntoCarriedLapsedAndEncashable() {
        var r = LeaveAccrualCalculator.yearEnd(14, true, 10.0, null, true, 3.0, 1.0);
        assertThat(r.carried()).isEqualTo(10.0);
        assertThat(r.lapsed()).isEqualTo(4.0);
        assertThat(r.encashable()).isEqualTo(2.0); // 3 per year - 1 already encashed
        var noCarry = LeaveAccrualCalculator.yearEnd(5, false, null, null, false, null, 0);
        assertThat(noCarry.carried()).isZero();
        assertThat(noCarry.lapsed()).isEqualTo(5.0);
        var negative = LeaveAccrualCalculator.yearEnd(-2, true, null, null, true, null, 0);
        assertThat(negative.carried()).isZero();
        assertThat(negative.lapsed()).isZero();
        var capped = LeaveAccrualCalculator.yearEnd(40, true, null, 30.0, false, null, 0);
        assertThat(capped.carried()).isEqualTo(30.0);
    }

    @Test
    void compOffIsConsumedEarliestExpiryFirstAndUnusedCreditsLapse() {
        List<CompOffCredit> credits = List.of(
                new CompOffCredit(1L, LocalDate.of(2026, 1, 4), LocalDate.of(2026, 4, 4), 1),
                new CompOffCredit(2L, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 5, 2), 1));
        List<Debit> debits = List.of(new Debit(LocalDate.of(2026, 3, 10), 1));
        var beforeExpiry = LeaveAccrualCalculator.compOffFifo(credits, debits, LocalDate.of(2026, 4, 1));
        assertThat(beforeExpiry.available()).isEqualTo(1.0);
        assertThat(beforeExpiry.lapsed()).isZero();
        // the March leave used credit #1; credit #2 lapses after 2 May
        var afterExpiry = LeaveAccrualCalculator.compOffFifo(credits, debits, LocalDate.of(2026, 5, 3));
        assertThat(afterExpiry.lapsed()).isEqualTo(1.0);
        assertThat(afterExpiry.available()).isZero();
        assertThat(afterExpiry.lapses()).extracting(LeaveAccrualCalculator.CompOffLapse::creditId).containsExactly(2L);
    }

    @Test
    void compOffCreditDaysFromDuration() {
        assertThat(CompOffService.creditDays("FULL_DAY", null)).isEqualTo(1.0);
        assertThat(CompOffService.creditDays("half_day", null)).isEqualTo(0.5);
        assertThat(CompOffService.creditDays("HOURS", 9.0)).isEqualTo(1.0);
        assertThat(CompOffService.creditDays("HOURS", 5.0)).isEqualTo(0.5);
        assertThatThrownBy(() -> CompOffService.creditDays("HOURS", 3.0)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> CompOffService.creditDays("WEEK", null)).isInstanceOf(BadRequestException.class);
    }

    @Test
    void encashmentRateIsMonthlyBasisOverThirty() {
        BigDecimal rate = LeaveEncashmentService.perDayRate(new BigDecimal("25000.00"));
        assertThat(rate).isEqualByComparingTo("833.33");
        assertThat(LeaveEncashmentService.amountFor(rate, 2.5)).isEqualByComparingTo("2083.33");
        assertThat(LeaveEncashmentService.perDayRate(null)).isEqualByComparingTo("0");
    }
}
