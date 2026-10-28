package com.haodaone.customfield.entity;

/** Supported employee custom field types (see CustomFieldValidator for the rules of each). */
public enum CustomFieldType {
    TEXT,
    NUMBER,
    DATE,
    SELECT,
    MULTISELECT,
    BOOLEAN,
    EMAIL,
    PHONE;

    public boolean hasOptions() {
        return this == SELECT || this == MULTISELECT;
    }
}
