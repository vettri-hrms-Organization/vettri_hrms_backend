package com.haodaone.notifications.whatsapp;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/** app.whatsapp.* - everything defaults to off/empty. */
@Component
public class WhatsAppProperties {

    private final boolean enabled;
    private final String apiBaseUrl;
    private final String phoneNumberId;
    private final String accessToken;
    private final String language;
    private final Map<WhatsAppEvent, String> templates = new EnumMap<>(WhatsAppEvent.class);

    public WhatsAppProperties(@Value("${app.whatsapp.enabled:false}") boolean enabled,
                              @Value("${app.whatsapp.api-base-url:https://graph.facebook.com/v21.0}") String apiBaseUrl,
                              @Value("${app.whatsapp.phone-number-id:}") String phoneNumberId,
                              @Value("${app.whatsapp.access-token:}") String accessToken,
                              @Value("${app.whatsapp.language:en}") String language,
                              @Value("${app.whatsapp.templates.leave-decision:}") String leaveDecision,
                              @Value("${app.whatsapp.templates.payslip-published:}") String payslipPublished,
                              @Value("${app.whatsapp.templates.announcement-urgent:}") String announcementUrgent,
                              @Value("${app.whatsapp.templates.expense-decision:}") String expenseDecision,
                              @Value("${app.whatsapp.templates.check-in-reminder:}") String checkInReminder) {
        this.enabled = enabled;
        this.apiBaseUrl = trim(apiBaseUrl).replaceAll("/+$", "");
        this.phoneNumberId = trim(phoneNumberId);
        this.accessToken = trim(accessToken);
        this.language = trim(language).isEmpty() ? "en" : trim(language);
        put(WhatsAppEvent.LEAVE_DECISION, leaveDecision);
        put(WhatsAppEvent.PAYSLIP_PUBLISHED, payslipPublished);
        put(WhatsAppEvent.ANNOUNCEMENT_URGENT, announcementUrgent);
        put(WhatsAppEvent.EXPENSE_DECISION, expenseDecision);
        put(WhatsAppEvent.CHECK_IN_REMINDER, checkInReminder);
    }

    /** Server-wide switch plus credentials present. */
    public boolean isActive() {
        return enabled && !phoneNumberId.isEmpty() && !accessToken.isEmpty();
    }

    public String template(WhatsAppEvent event) {
        return templates.get(event);
    }

    public String apiBaseUrl() { return apiBaseUrl; }
    public String phoneNumberId() { return phoneNumberId; }
    public String accessToken() { return accessToken; }
    public String language() { return language; }

    private void put(WhatsAppEvent event, String template) {
        String value = trim(template);
        if (!value.isEmpty()) templates.put(event, value);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
