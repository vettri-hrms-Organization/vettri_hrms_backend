package com.haodaone.announcement.service;

import com.haodaone.announcement.entity.Announcement;
import com.haodaone.employee.entity.EmploymentStatus;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Pure audience and text helpers for announcements. */
public final class AnnouncementAudience {

    /** Employees who have left never receive announcements. */
    public static final List<String> EXCLUDED_STATUSES = List.of(EmploymentStatus.RESIGNED, EmploymentStatus.TERMINATED);

    private static final Pattern HTML_TAG = Pattern.compile("</?[A-Za-z!][^>]*>");
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");

    private AnnouncementAudience() {
    }

    /** One potential recipient: employee, login, department (nullable). */
    public record Member(Long employeeId, Long userId, Long departmentId) {
    }

    public static List<Member> toMembers(List<Object[]> rows) {
        List<Member> members = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            members.add(new Member(asLong(row[0]), asLong(row[1]), asLong(row[2])));
        }
        return members;
    }

    public static boolean includes(Announcement announcement, Long departmentId) {
        if (!Announcement.AUDIENCE_DEPARTMENTS.equals(announcement.getAudienceType())) return true;
        return departmentId != null && parseIds(announcement.getAudienceDepartmentIds()).contains(departmentId);
    }

    public static List<Member> audienceOf(Announcement announcement, List<Member> members) {
        return members.stream().filter(member -> includes(announcement, member.departmentId())).toList();
    }

    public static Set<Long> parseIds(String csv) {
        Set<Long> ids = new LinkedHashSet<>();
        if (csv == null || csv.isBlank()) return ids;
        for (String part : csv.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            try {
                ids.add(Long.parseLong(trimmed));
            } catch (NumberFormatException ignored) {
                // tolerate stray values; they simply match nothing
            }
        }
        return ids;
    }

    public static String joinIds(Set<Long> ids) {
        return ids.stream().filter(Objects::nonNull).map(String::valueOf).collect(Collectors.joining(","));
    }

    /**
     * Announcements are plain text: HTML tags are stripped, control
     * characters removed, and line breaks normalised. The client renders the
     * result as text (React escaping) and only linkifies http(s) URLs.
     */
    public static String sanitize(String text) {
        if (text == null) return null;
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        normalized = HTML_TAG.matcher(normalized).replaceAll("");
        normalized = CONTROL_CHARS.matcher(normalized).replaceAll("");
        return normalized.strip();
    }

    private static Long asLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }
}
