package com.haodaone.imports.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The validation outcome for one spreadsheet row, plus whatever the handler needs to apply it. */
public class RowPlan {

    private final int rowNumber;
    private final Map<String, String> values = new LinkedHashMap<>();
    private final List<String> errors = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private String action;
    private boolean skip;
    private Object payload;

    public RowPlan(int rowNumber) {
        this.rowNumber = rowNumber;
    }

    public int rowNumber() { return rowNumber; }
    public Map<String, String> values() { return values; }
    public List<String> errors() { return errors; }
    public List<String> warnings() { return warnings; }
    public String action() { return action; }
    public boolean skip() { return skip; }
    public Object payload() { return payload; }
    public boolean valid() { return errors.isEmpty(); }

    public RowPlan value(String column, String value) { values.put(column, value == null ? "" : value); return this; }
    public RowPlan error(String message) { errors.add(message); return this; }
    public RowPlan warning(String message) { warnings.add(message); return this; }
    public RowPlan action(String action) { this.action = action; return this; }
    public RowPlan skip(String reason) { this.skip = true; this.action = reason; return this; }
    public RowPlan payload(Object payload) { this.payload = payload; return this; }

    @SuppressWarnings("unchecked")
    public <T> T payloadAs(Class<T> type) { return (T) payload; }
}
