package com.haodaone.imports.service;

import com.haodaone.common.exception.ResourceNotFoundException;

import java.util.Locale;

/** The imports offered by the data import hub, the URL slug for each and the permission (ORGANIZATION scope) it needs. */
public enum ImportType {
    SALARY_STRUCTURES("salary-structures", "Salary structures",
            "Define or revise each employee's monthly salary structure in bulk.", "SALARY_MANAGE"),
    LEAVE_BALANCES("leave-balances", "Opening leave balances",
            "Bring over leave balances from your previous system as of the import date.", "LEAVE_MANAGE"),
    HOLIDAYS("holidays", "Holidays",
            "Add the company holiday calendar. Dates that already have a holiday are skipped.", "LEAVE_MANAGE"),
    OPENING_YTD("opening-ytd", "Opening payroll YTD",
            "Salary and TDS already paid this financial year before moving payroll to Vettri.", "SALARY_MANAGE"),
    BANK_DETAILS("bank-details", "Bank & statutory details",
            "Update bank account, IFSC, PAN, UAN and ESI numbers for many employees at once.", "EMPLOYEE_MANAGE");

    private final String slug;
    private final String title;
    private final String description;
    private final String permission;

    ImportType(String slug, String title, String description, String permission) {
        this.slug = slug;
        this.title = title;
        this.description = description;
        this.permission = permission;
    }

    public String slug() { return slug; }
    public String title() { return title; }
    public String description() { return description; }
    public String permission() { return permission; }

    public static ImportType fromSlug(String value) {
        if (value != null) {
            String normalized = value.trim().toLowerCase(Locale.ROOT).replace('_', '-');
            for (ImportType type : values()) {
                if (type.slug.equals(normalized)) return type;
            }
        }
        throw new ResourceNotFoundException("Unknown import type: " + value);
    }
}
