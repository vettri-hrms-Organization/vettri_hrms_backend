package com.haodaone.company.service;

import com.haodaone.company.entity.Company;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * A company's weekly-off pattern, stored on {@link Company#getWeeklyOffDays()} as a comma list.
 * Each token is either a weekday name ("SUNDAY" = off every week) or a weekday plus the
 * occurrences in the month that are off ("SATURDAY#2", "SATURDAY#4" = 2nd and 4th Saturday off,
 * the common alternate-Saturday pattern). A blank value means every day is a working day.
 */
public final class WorkingWeek {

    public static final String DEFAULT_WEEKLY_OFF_DAYS = "SATURDAY,SUNDAY";

    /** Day -> occurrences in month that are off; an empty set means "every week". */
    private final Map<DayOfWeek, TreeSet<Integer>> offDays;

    private WorkingWeek(Map<DayOfWeek, TreeSet<Integer>> offDays) {
        this.offDays = offDays;
    }

    public static WorkingWeek defaultWeek() {
        return parse(DEFAULT_WEEKLY_OFF_DAYS);
    }

    /** Uses the company's configured week; falls back to Saturday/Sunday when unset or unreadable. */
    public static WorkingWeek of(Company company) {
        if (company == null || company.getWeeklyOffDays() == null) {
            return defaultWeek();
        }
        try {
            return parse(company.getWeeklyOffDays());
        } catch (IllegalArgumentException invalid) {
            return defaultWeek();
        }
    }

    /** Strict parse; throws IllegalArgumentException with a user-facing message for bad tokens. */
    public static WorkingWeek parse(String raw) {
        Map<DayOfWeek, TreeSet<Integer>> result = new EnumMap<>(DayOfWeek.class);
        if (raw == null || raw.isBlank()) {
            return new WorkingWeek(result);
        }
        for (String part : raw.split(",")) {
            String token = part.trim().toUpperCase(Locale.ROOT);
            if (token.isEmpty()) continue;
            String dayName = token;
            Integer occurrence = null;
            int hash = token.indexOf('#');
            if (hash >= 0) {
                dayName = token.substring(0, hash).trim();
                try {
                    occurrence = Integer.parseInt(token.substring(hash + 1).trim());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid weekly off value: " + part.trim());
                }
                if (occurrence < 1 || occurrence > 5) {
                    throw new IllegalArgumentException("Week of month must be between 1 and 5: " + part.trim());
                }
            }
            DayOfWeek day;
            try {
                day = DayOfWeek.valueOf(dayName);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid weekday: " + part.trim());
            }
            TreeSet<Integer> existing = result.get(day);
            if (occurrence == null) {
                result.put(day, new TreeSet<>()); // every week wins over specific occurrences
            } else if (existing == null) {
                TreeSet<Integer> occurrences = new TreeSet<>();
                occurrences.add(occurrence);
                result.put(day, occurrences);
            } else if (!existing.isEmpty()) {
                existing.add(occurrence);
            }
        }
        boolean everyDayOff = result.size() == 7 && result.values().stream().allMatch(TreeSet::isEmpty);
        if (everyDayOff) {
            throw new IllegalArgumentException("At least one day of the week must be a working day.");
        }
        return new WorkingWeek(result);
    }

    /** Validates and canonicalises a list of tokens into the stored comma form. */
    public static String normalize(Collection<String> tokens) {
        String joined = tokens == null ? "" : String.join(",", tokens);
        return parse(joined).toStorageValue();
    }

    public boolean isWeeklyOff(LocalDate date) {
        TreeSet<Integer> occurrences = offDays.get(date.getDayOfWeek());
        if (occurrences == null) return false;
        if (occurrences.isEmpty()) return true;
        return occurrences.contains((date.getDayOfMonth() - 1) / 7 + 1);
    }

    public List<String> tokens() {
        List<String> tokens = new ArrayList<>();
        for (Map.Entry<DayOfWeek, TreeSet<Integer>> entry : offDays.entrySet()) {
            if (entry.getValue().isEmpty()) {
                tokens.add(entry.getKey().name());
            } else {
                for (Integer occurrence : entry.getValue()) {
                    tokens.add(entry.getKey().name() + "#" + occurrence);
                }
            }
        }
        return tokens;
    }

    public String toStorageValue() {
        return String.join(",", tokens());
    }
}
