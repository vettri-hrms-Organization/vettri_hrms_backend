package com.haodaone.settlement.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class GratuityCalculatorTest {

    private static final BigDecimal WAGE = new BigDecimal("30000");
    private static final GratuityCalculator.Rules ACT = GratuityCalculator.Rules.defaults();

    @Test
    void tenYearsSevenMonthsRoundsUpToEleven() {
        // 1 Jan 2016 - 31 Jul 2026 = 10y 7m
        var r = GratuityCalculator.compute(LocalDate.of(2016, 1, 1), LocalDate.of(2026, 7, 31), WAGE, ACT, false);
        assertThat(r.eligible()).isTrue();
        assertThat(r.yearsCounted()).isEqualTo(11);
        // 15 x 30000 x 11 / 26 = 190384.62 -> 190385
        assertThat(r.amount()).isEqualByComparingTo("190385");
    }

    @Test
    void exactlySixMonthsDoesNotRoundUp() {
        // 1 Jan 2016 - 30 Jun 2026 = 10y 6m 0d
        var r = GratuityCalculator.compute(LocalDate.of(2016, 1, 1), LocalDate.of(2026, 6, 30), WAGE, ACT, false);
        assertThat(r.service().years()).isEqualTo(10);
        assertThat(r.service().months()).isEqualTo(6);
        assertThat(r.service().days()).isZero();
        assertThat(r.yearsCounted()).isEqualTo(10);
        // 6 months and 1 day rounds up
        var r2 = GratuityCalculator.compute(LocalDate.of(2016, 1, 1), LocalDate.of(2026, 7, 1), WAGE, ACT, false);
        assertThat(r2.yearsCounted()).isEqualTo(11);
    }

    @Test
    void oneDayShortOfFiveYearsIsNotEligible() {
        var r = GratuityCalculator.compute(LocalDate.of(2021, 11, 1), LocalDate.of(2026, 10, 30), WAGE, ACT, false);
        assertThat(r.service().years()).isEqualTo(4);
        assertThat(r.eligible()).isFalse();
        assertThat(r.amount()).isEqualByComparingTo("0");
        var exactly5 = GratuityCalculator.compute(LocalDate.of(2021, 11, 1), LocalDate.of(2026, 10, 31), WAGE, ACT, false);
        assertThat(exactly5.eligible()).isTrue();
        assertThat(exactly5.yearsCounted()).isEqualTo(5);
    }

    @Test
    void fourYears240DaysCountsWhenRuleEnabled() {
        var rules = new GratuityCalculator.Rules(true, true, GratuityCalculator.DEFAULT_CEILING, 240);
        // 4 completed years + 245 days into the 5th
        LocalDate doj = LocalDate.of(2022, 1, 1);
        LocalDate lwd = doj.plusYears(4).plusDays(244);
        var r = GratuityCalculator.compute(doj, lwd, WAGE, rules, false);
        assertThat(r.eligible()).isTrue();
        assertThat(r.yearsCounted()).isEqualTo(5);
        assertThat(GratuityCalculator.compute(doj, lwd, WAGE, ACT, false).eligible()).isFalse();
        // 200 days is not enough under the 240-day rule
        assertThat(GratuityCalculator.compute(doj, doj.plusYears(4).plusDays(199), WAGE, rules, false).eligible()).isFalse();
    }

    @Test
    void ceilingAppliesAndNotCoveredUsesThirtyDays() {
        var high = GratuityCalculator.compute(LocalDate.of(1990, 1, 1), LocalDate.of(2026, 1, 31), new BigDecimal("200000"), ACT, false);
        assertThat(high.capped()).isTrue();
        assertThat(high.amount()).isEqualByComparingTo("2000000");

        var notCovered = new GratuityCalculator.Rules(true, false, GratuityCalculator.DEFAULT_CEILING, 0);
        var r = GratuityCalculator.compute(LocalDate.of(2016, 1, 1), LocalDate.of(2026, 7, 31), WAGE, notCovered, false);
        assertThat(r.yearsCounted()).isEqualTo(10); // completed years only
        assertThat(r.amount()).isEqualByComparingTo("150000"); // 15 x 30000 x 10 / 30
    }

    @Test
    void notApplicableAndDeathWaiver() {
        var off = new GratuityCalculator.Rules(false, true, GratuityCalculator.DEFAULT_CEILING, 0);
        assertThat(GratuityCalculator.compute(LocalDate.of(2010, 1, 1), LocalDate.of(2026, 1, 1), WAGE, off, false).eligible()).isFalse();
        var death = GratuityCalculator.compute(LocalDate.of(2023, 1, 1), LocalDate.of(2026, 3, 31), WAGE, ACT, true);
        assertThat(death.eligible()).isTrue();
        assertThat(death.yearsCounted()).isEqualTo(3);
    }
}
