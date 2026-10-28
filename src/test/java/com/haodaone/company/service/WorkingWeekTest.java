package com.haodaone.company.service;

import com.haodaone.company.entity.Company;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkingWeekTest {

    @Test
    void newCompaniesDefaultToSaturdaySundayOff() {
        WorkingWeek week = WorkingWeek.of(new Company());

        assertThat(week.isWeeklyOff(LocalDate.of(2026, 10, 10))).isTrue();  // Saturday
        assertThat(week.isWeeklyOff(LocalDate.of(2026, 10, 11))).isTrue();  // Sunday
        assertThat(week.isWeeklyOff(LocalDate.of(2026, 10, 12))).isFalse(); // Monday
    }

    @Test
    void alternateSaturdaysOnlyMarkTheConfiguredOccurrences() {
        WorkingWeek week = WorkingWeek.parse("sunday, SATURDAY#2,SATURDAY#4");

        assertThat(week.isWeeklyOff(LocalDate.of(2026, 10, 3))).isFalse();  // 1st Saturday
        assertThat(week.isWeeklyOff(LocalDate.of(2026, 10, 10))).isTrue();  // 2nd Saturday
        assertThat(week.isWeeklyOff(LocalDate.of(2026, 10, 17))).isFalse(); // 3rd Saturday
        assertThat(week.isWeeklyOff(LocalDate.of(2026, 10, 24))).isTrue();  // 4th Saturday
        assertThat(week.isWeeklyOff(LocalDate.of(2026, 10, 31))).isFalse(); // 5th Saturday
        assertThat(week.tokens()).containsExactly("SATURDAY#2", "SATURDAY#4", "SUNDAY");
    }

    @Test
    void normalizeCanonicalisesAndValidates() {
        assertThat(WorkingWeek.normalize(List.of("sunday", "SATURDAY#4", "SATURDAY#2"))).isEqualTo("SATURDAY#2,SATURDAY#4,SUNDAY");
        assertThat(WorkingWeek.normalize(List.of("SATURDAY#2", "SATURDAY"))).isEqualTo("SATURDAY");
        assertThat(WorkingWeek.normalize(List.of())).isEmpty();
        assertThatThrownBy(() -> WorkingWeek.normalize(List.of("FUNDAY"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WorkingWeek.normalize(List.of("SATURDAY#6"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WorkingWeek.normalize(List.of(
                "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unreadableStoredValueFallsBackToDefault() {
        Company company = new Company();
        company.setWeeklyOffDays("garbage");

        assertThat(WorkingWeek.of(company).tokens()).containsExactly("SATURDAY", "SUNDAY");
    }
}
