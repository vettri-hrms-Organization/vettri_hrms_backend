package com.haodaone.leave.service;

import com.haodaone.common.exception.BadRequestException;
import com.haodaone.leave.entity.Holiday;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Optional holiday quota and pick rules, plus the countsFor rule. */
class OptionalHolidayRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 28);

    @Test
    void pickWithinQuotaIsAllowed() {
        Holiday h = LocationHolidayLeaveCountTest.holiday(1L, "Diwali (restricted)", TODAY.plusDays(5), Holiday.OPTIONAL, Set.of());
        assertThatCode(() -> OptionalHolidayService.validatePick(h, null, TODAY, false, 1, 2, false)).doesNotThrowAnyException();
    }

    @Test
    void quotaExhaustedIsRejected() {
        Holiday h = LocationHolidayLeaveCountTest.holiday(1L, "Diwali (restricted)", TODAY.plusDays(5), Holiday.OPTIONAL, Set.of());
        assertThatThrownBy(() -> OptionalHolidayService.validatePick(h, null, TODAY, false, 2, 2, false))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("used all 2");
    }

    @Test
    void pastOrTodayMandatoryWrongLocationAndDuplicatesAreRejected() {
        Holiday today = LocationHolidayLeaveCountTest.holiday(1L, "Today", TODAY, Holiday.OPTIONAL, Set.of());
        assertThatThrownBy(() -> OptionalHolidayService.validatePick(today, null, TODAY, false, 0, 2, false))
                .hasMessageContaining("before their date");
        Holiday mandatory = LocationHolidayLeaveCountTest.holiday(2L, "Republic Day", TODAY.plusDays(3), Holiday.MANDATORY, Set.of());
        assertThatThrownBy(() -> OptionalHolidayService.validatePick(mandatory, null, TODAY, false, 0, 2, false))
                .hasMessageContaining("not an optional holiday");
        Holiday elsewhere = LocationHolidayLeaveCountTest.holiday(3L, "Onam", TODAY.plusDays(3), Holiday.OPTIONAL, Set.of(9L));
        assertThatThrownBy(() -> OptionalHolidayService.validatePick(elsewhere, 5L, TODAY, false, 0, 2, false))
                .hasMessageContaining("work location");
        Holiday picked = LocationHolidayLeaveCountTest.holiday(4L, "Pongal", TODAY.plusDays(3), Holiday.OPTIONAL, Set.of());
        assertThatThrownBy(() -> OptionalHolidayService.validatePick(picked, null, TODAY, true, 0, 2, false))
                .hasMessageContaining("already picked");
        assertThatThrownBy(() -> OptionalHolidayService.validatePick(picked, null, TODAY, false, 0, 2, true))
                .hasMessageContaining("leave applied");
    }

    @Test
    void countsForHonoursLocationAndPicks() {
        Holiday optional = LocationHolidayLeaveCountTest.holiday(7L, "Restricted", TODAY, Holiday.OPTIONAL, Set.of());
        Holiday local = LocationHolidayLeaveCountTest.holiday(8L, "Local", TODAY, Holiday.MANDATORY, Set.of(5L));
        assertThat(HolidayCalendarService.countsFor(optional, null, Set.of())).isFalse();
        assertThat(HolidayCalendarService.countsFor(optional, null, Set.of(7L))).isTrue();
        assertThat(HolidayCalendarService.countsFor(local, 5L, Set.of())).isTrue();
        assertThat(HolidayCalendarService.countsFor(local, 6L, Set.of())).isFalse();
        assertThat(HolidayCalendarService.countsFor(local, null, Set.of())).isFalse();
    }
}
