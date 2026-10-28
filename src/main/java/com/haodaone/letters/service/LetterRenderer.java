package com.haodaone.letters.service;

import com.haodaone.common.exception.BadRequestException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves {{placeholder}} tokens in a letter body. Pure and stateless so
 * it can be unit-tested without Spring.
 *
 * Rules:
 * - Only placeholders in {@link #KNOWN_PLACEHOLDERS} or with the "custom."
 *   prefix are allowed; anything else is a validation error listing them.
 * - Values are substituted in a single pass, so a value that itself
 *   contains "{{...}}" is printed literally and never re-expanded.
 * - Values are sanitised to what the PDF's standard fonts can print
 *   (control characters removed, common Unicode punctuation mapped).
 */
public final class LetterRenderer {

    public static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_.]+)\\s*\\}\\}");
    public static final String CUSTOM_PREFIX = "custom.";

    public static final List<String> KNOWN_PLACEHOLDERS = List.of(
            "employee.fullName", "employee.firstName", "employee.code", "employee.email",
            "employee.designation", "employee.department", "employee.dateOfJoining",
            "employee.lastWorkingDate", "employee.address",
            "company.name", "company.address",
            "salary.annualCtc", "salary.monthlyGross",
            "today",
            "signatory.name", "signatory.designation",
            "candidate.fullName", "candidate.firstName", "candidate.email", "candidate.position",
            "candidate.department", "candidate.offerAmount", "candidate.joiningDate");

    private static final Set<String> KNOWN = Set.copyOf(KNOWN_PLACEHOLDERS);
    private static final Pattern CUSTOM_NAME = Pattern.compile("custom\\.[A-Za-z][A-Za-z0-9_]{0,59}");

    private LetterRenderer() {
    }

    /** All distinct placeholder names used in the body, in order of first appearance. */
    public static Set<String> placeholdersIn(String body) {
        Set<String> names = new LinkedHashSet<>();
        if (body == null) return names;
        Matcher m = PLACEHOLDER.matcher(body);
        while (m.find()) names.add(m.group(1));
        return names;
    }

    /** Custom field names (without the "custom." prefix) the body expects to be filled at generation time. */
    public static List<String> customFieldsIn(String body) {
        List<String> fields = new ArrayList<>();
        for (String name : placeholdersIn(body)) {
            if (name.startsWith(CUSTOM_PREFIX)) fields.add(name.substring(CUSTOM_PREFIX.length()));
        }
        return fields;
    }

    /** Throws a BadRequestException naming every placeholder that isn't recognised. */
    public static void validateTemplate(String body) {
        if (body == null || body.isBlank()) throw new BadRequestException("Letter body cannot be empty.");
        List<String> unknown = new ArrayList<>();
        for (String name : placeholdersIn(body)) {
            boolean ok = KNOWN.contains(name) || CUSTOM_NAME.matcher(name).matches();
            if (!ok) unknown.add(name);
        }
        if (!unknown.isEmpty()) {
            throw new BadRequestException("Unknown placeholder(s): " + String.join(", ", unknown)
                    + ". Use the placeholder picker, or prefix free-text fields with 'custom.'.");
        }
    }

    /**
     * Renders the body. {@code values} holds the resolved built-in values
     * (null/blank = not available for this letter); {@code customValues}
     * the free fields keyed WITHOUT the "custom." prefix.
     */
    public static String render(String body, Map<String, String> values, Map<String, String> customValues) {
        validateTemplate(body);
        List<String> missing = new ArrayList<>();
        Matcher m = PLACEHOLDER.matcher(body);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String name = m.group(1);
            String value = name.startsWith(CUSTOM_PREFIX)
                    ? (customValues == null ? null : customValues.get(name.substring(CUSTOM_PREFIX.length())))
                    : (values == null ? null : values.get(name));
            if (value == null || value.isBlank()) {
                if (!missing.contains(name)) missing.add(name);
                value = "";
            }
            m.appendReplacement(out, Matcher.quoteReplacement(sanitize(value)));
        }
        m.appendTail(out);
        if (!missing.isEmpty()) {
            throw new BadRequestException("No value available for: " + String.join(", ", missing)
                    + ". Fill the custom fields, or update the employee/candidate record first.");
        }
        return sanitize(out.toString());
    }

    /** Keeps printable Latin-1 plus newlines; maps common Unicode punctuation and the rupee sign. */
    public static String sanitize(String value) {
        if (value == null) return "";
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\r' -> { }
                case '\n' -> sb.append('\n');
                case '\t' -> sb.append("    ");
                case '‘', '’' -> sb.append('\'');
                case '“', '”' -> sb.append('"');
                case '–', '—' -> sb.append('-');
                case '…' -> sb.append("...");
                case '₹' -> sb.append("Rs.");
                case ' ' -> sb.append(' ');
                default -> {
                    if ((c >= 0x20 && c <= 0x7E) || (c >= 0xA1 && c <= 0xFF)) sb.append(c);
                    else if (!Character.isISOControl(c)) sb.append('?');
                }
            }
        }
        return sb.toString();
    }
}
