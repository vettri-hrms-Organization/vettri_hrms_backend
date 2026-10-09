package com.haodaone.assistant;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AssistantLeaveDateParser {
    private static final Pattern ISO_DATE = Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}\\b");
    private static final Pattern DAY_MONTH = Pattern.compile(
            "(?i)\\b(\\d{1,2})(?:st|nd|rd|th)?\\s+(" + monthPattern() + ")(?:,?\\s+(\\d{4}))?\\b");
    private static final Pattern MONTH_DAY = Pattern.compile(
            "(?i)\\b(" + monthPattern() + ")\\s+(\\d{1,2})(?:st|nd|rd|th)?(?:,?\\s+(\\d{4}))?\\b");
    private static final Pattern RELATIVE = Pattern.compile("(?i)\\b(day after tomorrow|tomorrow|today)\\b");
    private static final Pattern WEEKDAY = Pattern.compile(
            "(?i)\\b(?:next\\s+|this\\s+)?(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b");
    private static final Pattern BARE_ORDINAL = Pattern.compile(
            "(?i)\\b(?:the\\s+)?\\d{1,2}(?:st|nd|rd|th)\\b");

    private AssistantLeaveDateParser() {}

    static DateRange parse(String text, LocalDate today) {
        List<DateToken> tokens = tokens(text, today);
        tokens.sort(Comparator.comparingInt(DateToken::start));
        List<DateToken> distinct = new ArrayList<>();
        int lastEnd = -1;
        for (DateToken token : tokens) {
            if (token.start() >= lastEnd) {
                distinct.add(token);
                lastEnd = token.end();
            }
        }

        if (distinct.isEmpty()) {
            if (BARE_ORDINAL.matcher(text).find()) throw new AmbiguousDateException();
            return null;
        }
        if (distinct.size() > 2) throw new AmbiguousDateException();

        LocalDate start = distinct.get(0).resolve(today, null);
        LocalDate end = distinct.size() == 2
                ? distinct.get(1).resolve(today, start)
                : start;
        if (end.isBefore(start)) throw new AmbiguousDateException();
        return new DateRange(start, end);
    }

    private static List<DateToken> tokens(String text, LocalDate today) {
        List<DateToken> tokens = new ArrayList<>();
        addMatches(tokens, ISO_DATE, text, match -> {
            try {
                return DateToken.exact(match.start(), match.end(), LocalDate.parse(match.group()));
            } catch (RuntimeException ex) {
                throw new AmbiguousDateException();
            }
        });
        addMatches(tokens, DAY_MONTH, text, match -> monthDay(
                match.start(), match.end(), Integer.parseInt(match.group(1)),
                Month.valueOf(match.group(2).toUpperCase(Locale.ROOT)),
                match.group(3) == null ? null : Integer.valueOf(match.group(3))));
        addMatches(tokens, MONTH_DAY, text, match -> monthDay(
                match.start(), match.end(), Integer.parseInt(match.group(2)),
                Month.valueOf(match.group(1).toUpperCase(Locale.ROOT)),
                match.group(3) == null ? null : Integer.valueOf(match.group(3))));
        addMatches(tokens, RELATIVE, text, match -> {
            String relative = match.group(1).toLowerCase(Locale.ROOT);
            LocalDate date = switch (relative) {
                case "today" -> today;
                case "tomorrow" -> today.plusDays(1);
                default -> today.plusDays(2);
            };
            return DateToken.exact(match.start(), match.end(), date);
        });
        addMatches(tokens, WEEKDAY, text, match -> {
            DayOfWeek day = DayOfWeek.valueOf(match.group(1).toUpperCase(Locale.ROOT));
            LocalDate date = today.with(TemporalAdjusters.next(day));
            return DateToken.exact(match.start(), match.end(), date);
        });
        return tokens;
    }

    private static void addMatches(
            List<DateToken> tokens,
            Pattern pattern,
            String text,
            java.util.function.Function<Matcher, DateToken> tokenFactory
    ) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) tokens.add(tokenFactory.apply(matcher));
    }

    private static DateToken monthDay(int start, int end, int day, Month month, Integer year) {
        if (day < 1 || day > 31) throw new AmbiguousDateException();
        return DateToken.monthDay(start, end, month, day, year);
    }

    private static String monthPattern() {
        return "january|february|march|april|may|june|july|august|september|october|november|december";
    }

    record DateRange(LocalDate startDate, LocalDate endDate) {}

    static final class AmbiguousDateException extends RuntimeException {}

    private record DateToken(int start, int end, LocalDate exact, Month month, int day, Integer year) {
        static DateToken exact(int start, int end, LocalDate date) {
            return new DateToken(start, end, date, null, 0, null);
        }

        static DateToken monthDay(int start, int end, Month month, int day, Integer year) {
            return new DateToken(start, end, null, month, day, year);
        }

        LocalDate resolve(LocalDate today, LocalDate startDate) {
            if (exact != null) return exact;
            int resolvedYear = year != null ? year : startDate == null ? today.getYear() : startDate.getYear();
            LocalDate resolved;
            try {
                resolved = LocalDate.of(resolvedYear, month, day);
            } catch (RuntimeException ex) {
                throw new AmbiguousDateException();
            }
            if (year == null && startDate == null && resolved.isBefore(today)) {
                throw new AmbiguousDateException();
            }
            if (startDate != null && resolved.isBefore(startDate) && year == null) {
                resolved = LocalDate.of(resolvedYear + 1, month, day);
            }
            return resolved;
        }
    }
}
