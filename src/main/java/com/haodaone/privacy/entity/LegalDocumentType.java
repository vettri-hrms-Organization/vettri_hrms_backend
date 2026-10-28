package com.haodaone.privacy.entity;

/** The legal documents Vettri publishes and tracks acceptance of. */
public enum LegalDocumentType {
    TERMS("Terms of Service"),
    PRIVACY("Privacy Policy"),
    DPA("Data Processing Agreement"),
    ACCEPTABLE_USE("Acceptable Use Policy"),
    MONITORING_NOTICE("Employee Monitoring Notice");

    private final String title;

    LegalDocumentType(String title) {
        this.title = title;
    }

    public String title() {
        return title;
    }

    public static LegalDocumentType parse(String value) {
        if (value == null) return null;
        try {
            return LegalDocumentType.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
