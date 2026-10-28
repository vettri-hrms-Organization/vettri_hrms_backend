package com.haodaone.customfield.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.customfield.dto.CustomFieldDTO;
import com.haodaone.customfield.entity.CustomFieldDefinition;
import com.haodaone.customfield.entity.CustomFieldType;
import com.haodaone.customfield.entity.EmployeeCustomFieldValue;
import com.haodaone.customfield.repository.CustomFieldDefinitionRepository;
import com.haodaone.customfield.repository.EmployeeCustomFieldValueRepository;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Company-defined employee custom fields: definitions (HR with organisation
 * EMPLOYEE_MANAGE) and per-employee values (HR, or the employee for fields
 * marked editable by employee). Every write is validated by CustomFieldValidator.
 */
@Service
public class CustomFieldService {

    public static final int MAX_FIELDS_PER_COMPANY = 100;
    public static final int MAX_OPTIONS = 100;

    private final CustomFieldDefinitionRepository definitions;
    private final EmployeeCustomFieldValueRepository values;
    private final CompanyRepository companies;
    private final EmployeeRepository employees;
    private final AuditLogService auditLogService;

    public CustomFieldService(CustomFieldDefinitionRepository definitions, EmployeeCustomFieldValueRepository values,
                              CompanyRepository companies, EmployeeRepository employees, AuditLogService auditLogService) {
        this.definitions = definitions;
        this.values = values;
        this.companies = companies;
        this.employees = employees;
        this.auditLogService = auditLogService;
    }

    // ------------------------------------------------------------------ definitions

    @Transactional(readOnly = true)
    public List<CustomFieldDTO.Definition> listDefinitions(boolean employeeView) {
        return activeDefinitions(tenant()).stream()
                .filter(d -> !employeeView || d.isVisibleToEmployee())
                .map(CustomFieldDTO.Definition::from)
                .toList();
    }

    @Transactional
    public CustomFieldDTO.Definition createDefinition(CustomFieldDTO.DefinitionRequest request) {
        Long companyId = tenant();
        List<CustomFieldDefinition> existing = activeDefinitions(companyId);
        if (existing.size() >= MAX_FIELDS_PER_COMPANY) {
            throw new BadRequestException("A company can have at most " + MAX_FIELDS_PER_COMPANY + " custom fields");
        }
        String key = request.key() == null || request.key().isBlank()
                ? CustomFieldValidator.keyFromLabel(request.label())
                : request.key().trim().toLowerCase(Locale.ROOT);
        if (!CustomFieldValidator.isValidKey(key)) {
            throw new BadRequestException("Key must start with a letter and use only lowercase letters, digits and underscores (max 60)");
        }
        if (definitions.existsByCompany_IdAndFieldKeyAndDeletedFalse(companyId, key)) {
            throw new BadRequestException("A custom field with key '" + key + "' already exists");
        }
        String label = request.label().trim();
        if (existing.stream().anyMatch(d -> d.getLabel().equalsIgnoreCase(label))) {
            throw new BadRequestException("A custom field labelled '" + label + "' already exists");
        }
        Company company = companies.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found"));
        CustomFieldDefinition definition = new CustomFieldDefinition();
        definition.setCompany(company);
        definition.setFieldKey(key);
        apply(definition, request);
        definition.setSortOrder(existing.stream().mapToInt(CustomFieldDefinition::getSortOrder).max().orElse(-1) + 1);
        CustomFieldDefinition saved = definitions.save(definition);
        auditLogService.log("CustomFieldDefinition", saved.getId(), "CREATE",
                "Created custom field '" + saved.getLabel() + "' (" + saved.getFieldType() + ")");
        return CustomFieldDTO.Definition.from(saved);
    }

    @Transactional
    public CustomFieldDTO.Definition updateDefinition(Long id, CustomFieldDTO.DefinitionRequest request) {
        Long companyId = tenant();
        CustomFieldDefinition definition = definitions.findByIdAndCompany_IdAndDeletedFalse(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Custom field not found"));
        if (request.key() != null && !request.key().isBlank()
                && !request.key().trim().equalsIgnoreCase(definition.getFieldKey())) {
            throw new BadRequestException("The key of a custom field cannot be changed");
        }
        String label = request.label().trim();
        if (activeDefinitions(companyId).stream()
                .anyMatch(d -> !d.getId().equals(id) && d.getLabel().equalsIgnoreCase(label))) {
            throw new BadRequestException("A custom field labelled '" + label + "' already exists");
        }
        CustomFieldType previousType = definition.getFieldType();
        apply(definition, request);
        if (previousType != definition.getFieldType() && !values.findAllByDefinition_IdAndDeletedFalse(id).isEmpty()) {
            throw new BadRequestException("The type cannot be changed once employees have values. Create a new field instead.");
        }
        CustomFieldDefinition saved = definitions.save(definition);
        auditLogService.log("CustomFieldDefinition", saved.getId(), "UPDATE", "Updated custom field '" + saved.getLabel() + "'");
        return CustomFieldDTO.Definition.from(saved);
    }

    @Transactional
    public void deleteDefinition(Long id) {
        Long companyId = tenant();
        CustomFieldDefinition definition = definitions.findByIdAndCompany_IdAndDeletedFalse(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Custom field not found"));
        LocalDateTime now = LocalDateTime.now();
        definition.setDeleted(true);
        definition.setDeletedAt(now);
        definitions.save(definition);
        for (EmployeeCustomFieldValue value : values.findAllByDefinition_IdAndDeletedFalse(id)) {
            value.setDeleted(true);
            value.setDeletedAt(now);
            values.save(value);
        }
        auditLogService.log("CustomFieldDefinition", id, "DELETE", "Deleted custom field '" + definition.getLabel() + "'");
    }

    @Transactional
    public List<CustomFieldDTO.Definition> reorder(List<Long> orderedIds) {
        Long companyId = tenant();
        List<CustomFieldDefinition> all = activeDefinitions(companyId);
        Map<Long, CustomFieldDefinition> byId = new HashMap<>();
        all.forEach(d -> byId.put(d.getId(), d));
        Set<Long> seen = new LinkedHashSet<>();
        for (Long id : orderedIds) {
            if (id == null || !byId.containsKey(id)) throw new BadRequestException("Unknown custom field id: " + id);
            seen.add(id);
        }
        int order = 0;
        for (Long id : seen) byId.get(id).setSortOrder(order++);
        // Anything not mentioned keeps its relative order after the listed ones.
        for (CustomFieldDefinition d : all) {
            if (!seen.contains(d.getId())) d.setSortOrder(order++);
        }
        definitions.saveAll(all);
        auditLogService.log("CustomFieldDefinition", null, "REORDER", "Reordered custom fields");
        return listDefinitions(false);
    }

    private void apply(CustomFieldDefinition definition, CustomFieldDTO.DefinitionRequest request) {
        CustomFieldType type;
        try {
            type = CustomFieldType.valueOf(request.type().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Unknown field type: " + request.type());
        }
        definition.setLabel(request.label().trim());
        definition.setFieldType(type);
        if (type.hasOptions()) {
            List<String> options = cleanOptions(request.options());
            if (options.isEmpty()) throw new BadRequestException("Add at least one option for a " + type + " field");
            definition.setOptions(CustomFieldValidator.writeOptions(options));
        } else {
            definition.setOptions(null);
        }
        definition.setRequired(Boolean.TRUE.equals(request.required()));
        definition.setSection(request.section() == null || request.section().isBlank() ? null : request.section().trim());
        boolean visible = request.visibleToEmployee() == null || request.visibleToEmployee();
        definition.setVisibleToEmployee(visible);
        // An employee can't edit what they can't see.
        definition.setEditableByEmployee(visible && Boolean.TRUE.equals(request.editableByEmployee()));
    }

    static List<String> cleanOptions(List<String> raw) {
        if (raw == null) return List.of();
        Map<String, String> unique = new LinkedHashMap<>();
        for (String option : raw) {
            if (option == null || option.isBlank()) continue;
            String trimmed = option.trim();
            if (trimmed.length() > 100) throw new BadRequestException("Options must be at most 100 characters");
            unique.putIfAbsent(trimmed.toLowerCase(Locale.ROOT), trimmed);
        }
        if (unique.size() > MAX_OPTIONS) throw new BadRequestException("At most " + MAX_OPTIONS + " options are allowed");
        return new ArrayList<>(unique.values());
    }

    // ------------------------------------------------------------------ values

    /** employeeView: the caller is the employee themself (not HR) - hidden fields are left out. */
    @Transactional(readOnly = true)
    public CustomFieldDTO.EmployeeValues getValues(Long employeeId, boolean employeeView, boolean canManage) {
        Long companyId = tenant();
        requireEmployee(employeeId, companyId);
        Map<Long, String> stored = new HashMap<>();
        values.findAllByCompany_IdAndEmployee_IdAndDeletedFalse(companyId, employeeId)
                .forEach(v -> stored.put(v.getDefinition().getId(), v.getValueText()));
        List<CustomFieldDTO.FieldValue> fields = new ArrayList<>();
        for (CustomFieldDefinition d : activeDefinitions(companyId)) {
            if (employeeView && !d.isVisibleToEmployee()) continue;
            boolean editable = canManage || (employeeView && d.isEditableByEmployee());
            fields.add(new CustomFieldDTO.FieldValue(CustomFieldDTO.Definition.from(d),
                    CustomFieldValidator.typed(d.getFieldType(), stored.get(d.getId())), editable));
        }
        return new CustomFieldDTO.EmployeeValues(employeeId, fields);
    }

    /**
     * Saves the given values (keyed by field key). selfService restricts the
     * caller to fields visible to and editable by employees. Required fields
     * the caller can edit must hold a value after the save.
     */
    @Transactional
    public CustomFieldDTO.EmployeeValues saveValues(Long employeeId, Map<String, Object> input, boolean selfService) {
        Long companyId = tenant();
        Employee employee = requireEmployee(employeeId, companyId);
        List<CustomFieldDefinition> defs = activeDefinitions(companyId);
        Map<String, CustomFieldDefinition> byKey = new HashMap<>();
        defs.forEach(d -> byKey.put(d.getFieldKey(), d));
        Map<String, Object> provided = input == null ? Map.of() : input;

        Map<Long, EmployeeCustomFieldValue> existing = new HashMap<>();
        values.findAllByCompany_IdAndEmployee_IdAndDeletedFalse(companyId, employeeId)
                .forEach(v -> existing.put(v.getDefinition().getId(), v));

        Map<String, String> errors = new LinkedHashMap<>();
        Map<CustomFieldDefinition, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : provided.entrySet()) {
            CustomFieldDefinition d = byKey.get(entry.getKey());
            if (d == null) {
                errors.put(entry.getKey(), "Unknown custom field '" + entry.getKey() + "'");
                continue;
            }
            if (selfService && !(d.isVisibleToEmployee() && d.isEditableByEmployee())) {
                errors.put(d.getFieldKey(), d.getLabel() + " can only be changed by HR");
                continue;
            }
            CustomFieldValidator.Result result = validate(d, entry.getValue());
            if (!result.valid()) errors.put(d.getFieldKey(), result.error());
            else normalized.put(d, result.value());
        }
        for (CustomFieldDefinition d : defs) {
            if (!d.isRequired() || provided.containsKey(d.getFieldKey())) continue;
            boolean callerCanEdit = !selfService || (d.isVisibleToEmployee() && d.isEditableByEmployee());
            EmployeeCustomFieldValue current = existing.get(d.getId());
            if (callerCanEdit && (current == null || current.getValueText() == null)) {
                errors.put(d.getFieldKey(), d.getLabel() + " is required");
            }
        }
        if (!errors.isEmpty()) {
            throw new BadRequestException(String.join("; ", errors.values()));
        }

        int changed = 0;
        for (Map.Entry<CustomFieldDefinition, String> entry : normalized.entrySet()) {
            CustomFieldDefinition d = entry.getKey();
            EmployeeCustomFieldValue value = existing.get(d.getId());
            String newValue = entry.getValue();
            if (value == null) {
                if (newValue == null) continue;
                value = new EmployeeCustomFieldValue();
                value.setCompany(d.getCompany());
                value.setEmployee(employee);
                value.setDefinition(d);
            } else if (java.util.Objects.equals(value.getValueText(), newValue)) {
                continue;
            }
            value.setValueText(newValue);
            values.save(value);
            changed++;
        }
        if (changed > 0) {
            auditLogService.log("Employee", employeeId, "CUSTOM_FIELDS_UPDATE",
                    "Updated " + changed + " custom field(s)" + (selfService ? " (self-service)" : ""));
        }
        return getValues(employeeId, selfService, !selfService);
    }

    /** Validates values (keyed by field key) without saving, e.g. before creating an employee. */
    @Transactional(readOnly = true)
    public CustomFieldDTO.ValidationResponse validateValues(Map<String, Object> input) {
        Map<String, Object> provided = input == null ? Map.of() : input;
        Map<String, String> errors = new LinkedHashMap<>();
        List<CustomFieldDefinition> defs = activeDefinitions(tenant());
        for (CustomFieldDefinition d : defs) {
            CustomFieldValidator.Result result = validate(d, provided.get(d.getFieldKey()));
            if (!result.valid()) errors.put(d.getFieldKey(), result.error());
        }
        provided.keySet().stream()
                .filter(key -> defs.stream().noneMatch(d -> d.getFieldKey().equals(key)))
                .forEach(key -> errors.put(key, "Unknown custom field '" + key + "'"));
        return new CustomFieldDTO.ValidationResponse(errors.isEmpty(), errors);
    }

    /** Display values for CSV export. employeeIds null = every employee of the company. */
    @Transactional(readOnly = true)
    public CustomFieldDTO.ExportResponse exportValues(Collection<Long> employeeIds) {
        Long companyId = tenant();
        List<CustomFieldDefinition> defs = activeDefinitions(companyId);
        List<EmployeeCustomFieldValue> rows = employeeIds == null
                ? values.findAllByCompany_IdAndDeletedFalse(companyId)
                : employeeIds.isEmpty() ? List.of()
                : values.findAllByCompany_IdAndEmployee_IdInAndDeletedFalse(companyId, employeeIds);
        Map<Long, Map<String, String>> result = new LinkedHashMap<>();
        for (EmployeeCustomFieldValue row : rows) {
            CustomFieldDefinition d = row.getDefinition();
            if (d.isDeleted()) continue;
            result.computeIfAbsent(row.getEmployee().getId(), id -> new LinkedHashMap<>())
                    .put(d.getFieldKey(), CustomFieldValidator.display(d.getFieldType(), row.getValueText()));
        }
        return new CustomFieldDTO.ExportResponse(defs.stream().map(CustomFieldDTO.Definition::from).toList(), result);
    }

    // ------------------------------------------------------------------ import support

    /**
     * Maps extra import header cells to definitions by label or key
     * (case-insensitive, a trailing "*" ignored). Unknown headers map to nothing.
     */
    @Transactional(readOnly = true)
    public Map<Integer, String> matchImportColumns(Long companyId, Map<Integer, String> headersByColumn) {
        List<CustomFieldDefinition> defs = activeDefinitions(companyId);
        Map<Integer, String> matched = new LinkedHashMap<>();
        headersByColumn.forEach((column, header) -> {
            if (header == null) return;
            String cleaned = header.replace("*", "").trim();
            defs.stream()
                    .filter(d -> d.getLabel().equalsIgnoreCase(cleaned) || d.getFieldKey().equalsIgnoreCase(cleaned))
                    .findFirst()
                    .ifPresent(d -> matched.put(column, d.getFieldKey()));
        });
        return matched;
    }

    /** Validation errors for one import row (values keyed by field key); required fields are enforced. */
    @Transactional(readOnly = true)
    public List<String> validateImportRow(Long companyId, Map<String, String> rowValues) {
        List<String> errors = new ArrayList<>();
        for (CustomFieldDefinition d : activeDefinitions(companyId)) {
            CustomFieldValidator.Result result = validate(d, rowValues == null ? null : rowValues.get(d.getFieldKey()));
            if (!result.valid()) errors.add(result.error());
        }
        return errors;
    }

    /** Stores already-validated import values for a freshly created employee. */
    @Transactional
    public void saveImportedValues(Long companyId, Long employeeId, Map<String, String> rowValues) {
        if (rowValues == null || rowValues.isEmpty()) return;
        Employee employee = requireEmployee(employeeId, companyId);
        for (CustomFieldDefinition d : activeDefinitions(companyId)) {
            CustomFieldValidator.Result result = validate(d, rowValues.get(d.getFieldKey()));
            if (!result.valid() || result.value() == null) continue;
            EmployeeCustomFieldValue value = new EmployeeCustomFieldValue();
            value.setCompany(d.getCompany());
            value.setEmployee(employee);
            value.setDefinition(d);
            value.setValueText(result.value());
            values.save(value);
        }
    }

    /** Header labels to append to the import template. */
    @Transactional(readOnly = true)
    public List<String> importHeaders(Long companyId) {
        return activeDefinitions(companyId).stream()
                .map(d -> d.isRequired() ? d.getLabel() + " *" : d.getLabel())
                .toList();
    }

    // ------------------------------------------------------------------ helpers

    private static CustomFieldValidator.Result validate(CustomFieldDefinition d, Object raw) {
        return CustomFieldValidator.normalize(d.getLabel(), d.getFieldType(),
                CustomFieldValidator.parseOptions(d.getOptions()), d.isRequired(), raw);
    }

    private List<CustomFieldDefinition> activeDefinitions(Long companyId) {
        return definitions.findAllByCompany_IdAndDeletedFalseOrderBySortOrderAscIdAsc(companyId);
    }

    private Employee requireEmployee(Long employeeId, Long companyId) {
        Optional<Employee> employee = employees.findByIdAndCompany_IdAndDeletedFalse(employeeId, companyId);
        return employee.orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
    }

    private static Long tenant() {
        Long companyId = TenantContext.getCurrentTenant();
        if (companyId == null) throw new BadRequestException("Company context is required");
        return companyId;
    }
}
