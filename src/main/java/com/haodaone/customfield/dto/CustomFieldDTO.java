package com.haodaone.customfield.dto;

import com.haodaone.customfield.entity.CustomFieldDefinition;
import com.haodaone.customfield.service.CustomFieldValidator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;

/** Request/response shapes for /api/custom-fields. */
public final class CustomFieldDTO {

    private CustomFieldDTO() {
    }

    public record Definition(Long id, String key, String label, String type, List<String> options, boolean required,
                             String section, boolean visibleToEmployee, boolean editableByEmployee, int sortOrder) {
        public static Definition from(CustomFieldDefinition d) {
            return new Definition(d.getId(), d.getFieldKey(), d.getLabel(), d.getFieldType().name(),
                    CustomFieldValidator.parseOptions(d.getOptions()), d.isRequired(), d.getSection(),
                    d.isVisibleToEmployee(), d.isEditableByEmployee(), d.getSortOrder());
        }
    }

    public record DefinitionRequest(
            @Size(max = 60) String key,
            @NotBlank @Size(max = 120) String label,
            @NotBlank String type,
            List<String> options,
            Boolean required,
            @Size(max = 80) String section,
            Boolean visibleToEmployee,
            Boolean editableByEmployee) {
    }

    public record ReorderRequest(@NotEmpty List<Long> ids) {
    }

    /** A field as shown on one employee's profile: definition + current value + whether the caller may edit it. */
    public record FieldValue(Definition definition, Object value, boolean editable) {
    }

    public record EmployeeValues(Long employeeId, List<FieldValue> fields) {
    }

    /** Values keyed by field key; MULTISELECT may be a list, BOOLEAN a boolean, the rest strings. */
    public record SaveValuesRequest(@NotNull Map<String, Object> values) {
    }

    /** errors keyed by field key; empty when valid. */
    public record ValidationResponse(boolean valid, Map<String, String> errors) {
    }

    /** For CSV export: column definitions plus display values per employee id. */
    public record ExportResponse(List<Definition> fields, Map<Long, Map<String, String>> values) {
    }
}
