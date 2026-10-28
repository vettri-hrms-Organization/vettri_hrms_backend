package com.haodaone.customfield.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haodaone.customfield.entity.CustomFieldType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Pure validation + normalisation of custom field values. Every value is
 * stored as a canonical string so reads, exports and comparisons are stable:
 * TEXT trimmed, NUMBER plain decimal, DATE ISO yyyy-MM-dd, BOOLEAN true/false,
 * EMAIL lowercase, PHONE +digits/digits, SELECT the option's own spelling,
 * MULTISELECT a JSON array of option spellings.
 */
public final class CustomFieldValidator {

    public static final int MAX_TEXT_LENGTH = 1000;
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9]{7,15}$");
    private static final Pattern KEY = Pattern.compile("^[a-z][a-z0-9_]{0,59}$");
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/uuuu"),
            DateTimeFormatter.ofPattern("dd-MM-uuuu"));
    private static final Set<String> TRUE_VALUES = Set.of("true", "yes", "y", "1");
    private static final Set<String> FALSE_VALUES = Set.of("false", "no", "n", "0");

    private CustomFieldValidator() {
    }

    /** value is the normalised string (null = empty); error is a human-readable message, or null when valid. */
    public record Result(String value, String error) {
        public boolean valid() {
            return error == null;
        }
    }

    public static Result normalize(String label, CustomFieldType type, List<String> options, boolean required, Object raw) {
        String name = label == null || label.isBlank() ? "This field" : label;
        if (isEmpty(raw)) {
            return required ? new Result(null, name + " is required") : new Result(null, null);
        }
        try {
            String value = switch (type) {
                case TEXT -> text(name, raw);
                case NUMBER -> number(name, raw);
                case DATE -> date(name, raw);
                case BOOLEAN -> bool(name, raw);
                case EMAIL -> email(name, raw);
                case PHONE -> phone(name, raw);
                case SELECT -> select(name, options, raw);
                case MULTISELECT -> multiSelect(name, options, raw);
            };
            if (value == null && required) {
                return new Result(null, name + " is required");
            }
            return new Result(value, null);
        } catch (IllegalArgumentException ex) {
            return new Result(null, ex.getMessage());
        }
    }

    /** "Blood group" -> "blood_group". Returns null when nothing usable remains. */
    public static String keyFromLabel(String label) {
        if (label == null) return null;
        String key = label.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (key.isEmpty()) return null;
        if (!Character.isLetter(key.charAt(0))) key = "f_" + key;
        return key.length() > 60 ? key.substring(0, 60) : key;
    }

    public static boolean isValidKey(String key) {
        return key != null && KEY.matcher(key).matches();
    }

    public static List<String> parseOptions(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return JSON.readValue(json, new TypeReference<List<String>>() { });
        } catch (Exception ex) {
            return List.of();
        }
    }

    public static String writeOptions(Collection<String> options) {
        try {
            return JSON.writeValueAsString(options == null ? List.of() : new ArrayList<>(options));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not serialise options", ex);
        }
    }

    /** Human-readable form of a stored value (MULTISELECT joined with "; "), for CSV export. */
    public static String display(CustomFieldType type, String stored) {
        if (stored == null) return "";
        if (type == CustomFieldType.MULTISELECT) {
            return String.join("; ", parseOptions(stored));
        }
        if (type == CustomFieldType.BOOLEAN) {
            return "true".equals(stored) ? "Yes" : "No";
        }
        return stored;
    }

    /** Typed value for JSON responses (MULTISELECT -> list, BOOLEAN -> boolean, others string). */
    public static Object typed(CustomFieldType type, String stored) {
        if (stored == null) return type == CustomFieldType.MULTISELECT ? List.of() : null;
        return switch (type) {
            case MULTISELECT -> parseOptions(stored);
            case BOOLEAN -> Boolean.valueOf(stored);
            default -> stored;
        };
    }

    private static boolean isEmpty(Object raw) {
        if (raw == null) return true;
        if (raw instanceof String s) return s.isBlank();
        if (raw instanceof Collection<?> c) return c.isEmpty();
        return false;
    }

    private static String text(String name, Object raw) {
        String value = String.valueOf(raw).trim();
        if (value.length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException(name + " must be at most " + MAX_TEXT_LENGTH + " characters");
        }
        return value;
    }

    private static String number(String name, Object raw) {
        String value = raw instanceof Number n ? n.toString() : String.valueOf(raw).trim().replace(",", "");
        try {
            BigDecimal decimal = new BigDecimal(value);
            BigDecimal stripped = decimal.stripTrailingZeros();
            return stripped.scale() < 0 ? stripped.setScale(0).toPlainString() : stripped.toPlainString();
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(name + " must be a number");
        }
    }

    private static String date(String name, Object raw) {
        if (raw instanceof LocalDate d) return d.toString();
        String value = String.valueOf(raw).trim();
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return LocalDate.parse(value, format).toString();
            } catch (DateTimeParseException ignored) {
                // try the next accepted format
            }
        }
        throw new IllegalArgumentException(name + " must be a date (YYYY-MM-DD)");
    }

    private static String bool(String name, Object raw) {
        if (raw instanceof Boolean b) return b.toString();
        String value = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
        if (TRUE_VALUES.contains(value)) return "true";
        if (FALSE_VALUES.contains(value)) return "false";
        throw new IllegalArgumentException(name + " must be Yes or No");
    }

    private static String email(String name, Object raw) {
        String value = String.valueOf(raw).trim().toLowerCase(Locale.ROOT);
        if (value.length() > 150 || !EMAIL.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be a valid email address");
        }
        return value;
    }

    private static String phone(String name, Object raw) {
        String value = String.valueOf(raw).trim().replaceAll("[\\s().-]", "");
        if (!PHONE.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be a valid phone number (7-15 digits, optional +country code)");
        }
        return value;
    }

    private static String select(String name, List<String> options, Object raw) {
        if (raw instanceof Collection<?>) {
            throw new IllegalArgumentException(name + " accepts a single option");
        }
        return matchOption(name, options, String.valueOf(raw).trim());
    }

    private static String multiSelect(String name, List<String> options, Object raw) {
        List<String> parts = new ArrayList<>();
        if (raw instanceof Collection<?> collection) {
            collection.forEach(item -> { if (item != null) parts.add(String.valueOf(item)); });
        } else {
            String value = String.valueOf(raw).trim();
            if (value.startsWith("[")) {
                parts.addAll(parseOptions(value));
            } else {
                for (String part : value.split("[;,|]")) parts.add(part);
            }
        }
        Set<String> chosen = new LinkedHashSet<>();
        for (String part : parts) {
            if (part == null || part.isBlank()) continue;
            chosen.add(matchOption(name, options, part.trim()));
        }
        if (chosen.isEmpty()) return null;
        // Keep the definition's option order so stored values are canonical.
        List<String> ordered = new ArrayList<>();
        for (String option : options == null ? List.<String>of() : options) {
            if (chosen.contains(option)) ordered.add(option);
        }
        return writeOptions(ordered);
    }

    private static String matchOption(String name, List<String> options, String value) {
        if (options != null) {
            for (String option : options) {
                if (option != null && option.equalsIgnoreCase(value)) return option;
            }
        }
        throw new IllegalArgumentException(name + ": \"" + value + "\" is not one of the allowed options");
    }
}
