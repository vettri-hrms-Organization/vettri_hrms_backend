package com.haodaone.notifications.whatsapp;

import java.util.Optional;
import java.util.Set;

/**
 * Events a company can choose to also send over WhatsApp, and which in-app
 * notification types (NotificationService.notifyUser "type") map to them.
 */
public enum WhatsAppEvent {
    LEAVE_DECISION,
    PAYSLIP_PUBLISHED,
    ANNOUNCEMENT_URGENT,
    EXPENSE_DECISION,
    CHECK_IN_REMINDER;

    private static final Set<String> EXPENSE_TYPES = Set.of(
            "EXPENSE_APPROVED", "EXPENSE_REJECTED", "EXPENSE_REIMBURSED", "EXPENSE_MANAGER_APPROVED");

    public static Optional<WhatsAppEvent> fromNotification(String type, String priority) {
        if (type == null) return Optional.empty();
        return switch (type) {
            case "LEAVE_DECISION" -> Optional.of(LEAVE_DECISION);
            case "PAYROLL_PAID", "PAYSLIP_PUBLISHED" -> Optional.of(PAYSLIP_PUBLISHED);
            case "ANNOUNCEMENT" -> "HIGH".equals(priority) || "URGENT".equals(priority)
                    ? Optional.of(ANNOUNCEMENT_URGENT) : Optional.empty();
            case "ANNOUNCEMENT_URGENT" -> Optional.of(ANNOUNCEMENT_URGENT);
            case "CHECK_IN_REMINDER" -> Optional.of(CHECK_IN_REMINDER);
            default -> EXPENSE_TYPES.contains(type) ? Optional.of(EXPENSE_DECISION) : Optional.empty();
        };
    }

    public static Optional<WhatsAppEvent> parse(String value) {
        if (value == null) return Optional.empty();
        try {
            return Optional.of(valueOf(value.trim().toUpperCase(java.util.Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
