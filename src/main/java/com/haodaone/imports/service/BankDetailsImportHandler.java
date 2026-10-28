package com.haodaone.imports.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.employee.dto.CreateEmployeeRequest;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.employee.service.EmployeeService;
import com.haodaone.imports.entity.DataImportBatch;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * Bulk update of bank account / IFSC / bank name / PAN / UAN / ESI number. Validation reuses
 * {@link EmployeeService#applyBankAndStatutoryDetails} field by field on a scratch employee, so the rules are
 * exactly the ones the employee form enforces. Blank cells leave the stored value unchanged.
 */
@Component
public class BankDetailsImportHandler implements ImportHandler {

    static final String CODE = "Employee Code";
    static final String ACCOUNT = "Bank Account Number";
    static final String IFSC = "IFSC";
    static final String BANK = "Bank Name";
    static final String PAN = "PAN";
    static final String UAN = "UAN";
    static final String ESI = "ESI Number";

    private static final List<ImportSheet.Column> COLUMNS = List.of(
            new ImportSheet.Column(CODE, true, "Existing employee code"),
            new ImportSheet.Column(ACCOUNT, false, "6-34 letters or digits. Format the column as Text so Excel keeps every digit"),
            new ImportSheet.Column(IFSC, false, "11 characters, e.g. HDFC0001234"),
            new ImportSheet.Column(BANK, false, "Bank name, e.g. HDFC Bank"),
            new ImportSheet.Column(PAN, false, "10 characters, e.g. ABCDE1234F"),
            new ImportSheet.Column(UAN, false, "12-digit EPFO Universal Account Number"),
            new ImportSheet.Column(ESI, false, "10-17 digit ESI insurance number"));

    private static final Map<String, BiConsumer<CreateEmployeeRequest, String>> SETTERS = new LinkedHashMap<>();

    static {
        SETTERS.put(ACCOUNT, CreateEmployeeRequest::setBankAccountNumber);
        SETTERS.put(IFSC, CreateEmployeeRequest::setBankIfsc);
        SETTERS.put(BANK, CreateEmployeeRequest::setBankName);
        SETTERS.put(PAN, CreateEmployeeRequest::setPanNumber);
        SETTERS.put(UAN, CreateEmployeeRequest::setUanNumber);
        SETTERS.put(ESI, CreateEmployeeRequest::setEsiNumber);
    }

    private final EmployeeRepository employeeRepository;
    private final EmployeeService employeeService;
    private final AuditLogService auditLogService;

    public BankDetailsImportHandler(EmployeeRepository employeeRepository, EmployeeService employeeService,
                                    AuditLogService auditLogService) {
        this.employeeRepository = employeeRepository;
        this.employeeService = employeeService;
        this.auditLogService = auditLogService;
    }

    @Override
    public ImportType type() {
        return ImportType.BANK_DETAILS;
    }

    @Override
    public List<ImportSheet.Column> columns() {
        return COLUMNS;
    }

    @Override
    public List<List<String>> exampleRows() {
        return List.of(
                List.of("EMP0001", "50100012345678", "HDFC0001234", "HDFC Bank", "ABCDE1234F", "100200300400", ""),
                List.of("EMP0002", "", "", "", "PQRSX6789K", "", "3100123456"));
    }

    @Override
    public List<String> instructions() {
        return List.of(
                "One row per employee. Only filled cells are updated - leave a cell blank to keep the current value.",
                "The same rules as the employee form apply: IFSC like HDFC0001234, PAN like ABCDE1234F, UAN 12 digits, ESI 10-17 digits.",
                "Format the Bank Account Number and UAN columns as Text in Excel so long numbers are not rounded.",
                "A PAN can belong to only one employee.",
                "Requires Employee Manage permission with organization scope.");
    }

    record Payload(Long employeeId, CreateEmployeeRequest request, List<String> fields, String label) {
    }

    @Override
    public List<RowPlan> validate(Long companyId, List<ImportSheet.SheetRow> rows, ImportOptions options) {
        EmployeeLookup lookup = new EmployeeLookup(employeeRepository, companyId);
        Map<String, Long> panOwners = new HashMap<>();
        for (Employee employee : lookup.all()) {
            if (employee.getPanNumber() != null) panOwners.put(employee.getPanNumber().toUpperCase(Locale.ROOT), employee.getId());
        }
        Map<String, Integer> panInFile = new HashMap<>();
        List<RowPlan> plans = new ArrayList<>();
        for (ImportSheet.SheetRow row : rows) {
            RowPlan plan = new RowPlan(row.number());
            ImportSheet.Reader r = new ImportSheet.Reader(row, plan);
            Employee employee = lookup.resolve(r.required(CODE), plan);
            CreateEmployeeRequest request = new CreateEmployeeRequest();
            List<String> fields = new ArrayList<>();
            for (Map.Entry<String, BiConsumer<CreateEmployeeRequest, String>> setter : SETTERS.entrySet()) {
                String value = r.text(setter.getKey());
                if (value == null) continue;
                CreateEmployeeRequest single = new CreateEmployeeRequest();
                setter.getValue().accept(single, value);
                try {
                    employeeService.applyBankAndStatutoryDetails(new Employee(), single);
                    setter.getValue().accept(request, value);
                    fields.add(setter.getKey());
                } catch (BadRequestException e) {
                    plan.error(e.getMessage());
                }
            }
            String account = row.text().get(ACCOUNT);
            if (account != null && !account.isBlank()) plan.value(ACCOUNT, mask(account.trim()));
            if (fields.isEmpty() && plan.valid()) plan.error("Nothing to update - fill at least one of bank account, IFSC, bank name, PAN, UAN or ESI");
            String pan = request.getPanNumber() == null ? null : request.getPanNumber().replaceAll("\\s", "").toUpperCase(Locale.ROOT);
            if (pan != null && !pan.isEmpty()) {
                Integer earlier = panInFile.putIfAbsent(pan, row.number());
                if (earlier != null) plan.error("PAN " + pan + " is also on row " + earlier);
                Long owner = panOwners.get(pan);
                if (employee != null && owner != null && !owner.equals(employee.getId())) plan.error("PAN " + pan + " already belongs to another employee");
            }
            if (employee != null) {
                lookup.duplicate(employee.getEmployeeCode(), plan, "employee " + employee.getEmployeeCode());
                if (plan.valid()) {
                    plan.action("Update " + String.join(", ", fields));
                    plan.payload(new Payload(employee.getId(), request, fields, employee.getFullName() + " (" + employee.getEmployeeCode() + ")"));
                }
            }
            plans.add(plan);
        }
        return plans;
    }

    @Override
    public String apply(Long companyId, RowPlan plan, DataImportBatch batch, ImportOptions options) {
        Payload p = plan.payloadAs(Payload.class);
        Employee employee = employeeRepository.findByIdAndCompany_IdAndDeletedFalse(p.employeeId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
        employeeService.applyBankAndStatutoryDetails(employee, p.request());
        employeeRepository.save(employee);
        // Values are sensitive: the audit trail records which fields changed, not the numbers.
        auditLogService.log("Employee", employee.getId(), "BANK_DETAILS_IMPORT",
                "Updated " + String.join(", ", p.fields()) + " for " + p.label() + " via import batch #" + batch.getId());
        return "Updated " + String.join(", ", p.fields());
    }

    static String mask(String account) {
        String compact = account.replaceAll("\\s", "");
        if (compact.length() <= 4) return compact;
        return "XXXX" + compact.substring(compact.length() - 4);
    }
}
