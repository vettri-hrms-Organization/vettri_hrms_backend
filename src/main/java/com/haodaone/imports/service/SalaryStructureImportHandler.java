package com.haodaone.imports.service;

import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.imports.entity.DataImportBatch;
import com.haodaone.salary.dto.SalaryComponentsDTO;
import com.haodaone.salary.dto.SalaryStructureDTO;
import com.haodaone.salary.dto.UpsertSalaryStructureRequest;
import com.haodaone.salary.entity.SalaryStructure;
import com.haodaone.salary.repository.SalaryStructureRepository;
import com.haodaone.salary.service.SalaryStructureService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Salary structures, one row per employee. Each row goes through {@link SalaryStructureService#upsert}, so the
 * usual rule holds: the new structure becomes the employee's only active one and the previous one is kept as history.
 */
@Component
public class SalaryStructureImportHandler implements ImportHandler {

    static final String CODE = "Employee Code";
    static final String EFFECTIVE = "Effective From";
    static final String BASIC = "Basic";
    static final String HRA = "HRA";
    static final String SPECIAL = "Special Allowance";
    static final String MEDICAL = "Medical Allowance";
    static final String TRAVEL = "Travel Allowance";
    static final String BONUS = "Bonus";
    static final String INCENTIVES = "Incentives";
    static final String OVERTIME = "Overtime";
    static final String OTHER_DEDUCTIONS = "Other Deductions";
    static final String AUTO = "Auto Statutory";
    static final String PF = "PF";
    static final String ESI = "ESI";
    static final String PT = "Professional Tax";
    static final String TDS = "TDS";
    static final String NOTES = "Notes";

    private static final List<ImportSheet.Column> COLUMNS = List.of(
            new ImportSheet.Column(CODE, true, "Existing employee code, e.g. EMP0001"),
            new ImportSheet.Column(EFFECTIVE, true, "Date the structure applies from (YYYY-MM-DD)"),
            new ImportSheet.Column(BASIC, true, "Monthly basic salary in INR, greater than 0"),
            new ImportSheet.Column(HRA, false, "Monthly house rent allowance"),
            new ImportSheet.Column(SPECIAL, false, "Monthly special allowance"),
            new ImportSheet.Column(MEDICAL, false, "Monthly medical allowance"),
            new ImportSheet.Column(TRAVEL, false, "Monthly travel/conveyance allowance"),
            new ImportSheet.Column(BONUS, false, "Fixed monthly bonus"),
            new ImportSheet.Column(INCENTIVES, false, "Fixed monthly incentives"),
            new ImportSheet.Column(OVERTIME, false, "Fixed monthly overtime"),
            new ImportSheet.Column(OTHER_DEDUCTIONS, false, "Other monthly deductions (loan recovery, canteen...)"),
            new ImportSheet.Column(AUTO, false, "Y (default) = PF/ESI/PT/TDS calculated from Payroll Settings; N = use the amounts below"),
            new ImportSheet.Column(PF, false, "Monthly employee PF when Auto Statutory = N"),
            new ImportSheet.Column(ESI, false, "Monthly employee ESI when Auto Statutory = N"),
            new ImportSheet.Column(PT, false, "Monthly professional tax when Auto Statutory = N"),
            new ImportSheet.Column(TDS, false, "Monthly TDS when Auto Statutory = N"),
            new ImportSheet.Column(NOTES, false, "Optional note stored with the structure"));

    private final EmployeeRepository employeeRepository;
    private final SalaryStructureRepository salaryStructureRepository;
    private final SalaryStructureService salaryStructureService;

    public SalaryStructureImportHandler(EmployeeRepository employeeRepository, SalaryStructureRepository salaryStructureRepository,
                                        SalaryStructureService salaryStructureService) {
        this.employeeRepository = employeeRepository;
        this.salaryStructureRepository = salaryStructureRepository;
        this.salaryStructureService = salaryStructureService;
    }

    @Override
    public ImportType type() {
        return ImportType.SALARY_STRUCTURES;
    }

    @Override
    public List<ImportSheet.Column> columns() {
        return COLUMNS;
    }

    @Override
    public List<List<String>> exampleRows() {
        return List.of(
                List.of("EMP0001", "2026-04-01", "25000", "10000", "12000", "1250", "1600", "", "", "", "", "Y", "", "", "", "", "FY 26-27 revision"),
                List.of("EMP0002", "2026-04-01", "15000", "6000", "4000", "", "", "", "", "", "500", "N", "1800", "0", "200", "0", ""));
    }

    @Override
    public List<String> instructions() {
        return List.of(
                "One row per employee. All amounts are MONTHLY figures in INR (commas allowed, no negatives).",
                "Each row becomes the employee's active salary structure; the previous structure is kept in salary history.",
                "Effective From cannot be earlier than the employee's current structure.",
                "Auto Statutory Y (default): PF, ESI, professional tax and TDS are calculated by payroll from Payroll Settings, so the PF/ESI/PT/TDS columns are ignored.",
                "Auto Statutory N: payroll deducts exactly the PF/ESI/PT/TDS amounts you enter (blank = 0).",
                "Requires Salary Manage permission with organization scope.");
    }

    record Payload(Long employeeId, UpsertSalaryStructureRequest request, String employeeName) {
    }

    @Override
    public List<RowPlan> validate(Long companyId, List<ImportSheet.SheetRow> rows, ImportOptions options) {
        EmployeeLookup lookup = new EmployeeLookup(employeeRepository, companyId);
        List<RowPlan> plans = new ArrayList<>();
        for (ImportSheet.SheetRow row : rows) {
            RowPlan plan = new RowPlan(row.number());
            ImportSheet.Reader r = new ImportSheet.Reader(row, plan);
            String code = r.required(CODE);
            Employee employee = lookup.resolve(code, plan);
            LocalDate effective = r.date(EFFECTIVE, true);
            SalaryComponentsDTO c = new SalaryComponentsDTO();
            BigDecimal basic = r.amount(BASIC, true);
            c.setBasicSalary(basic);
            c.setHra(r.amount(HRA, false));
            c.setSpecialAllowance(r.amount(SPECIAL, false));
            c.setMedicalAllowance(r.amount(MEDICAL, false));
            c.setTravelAllowance(r.amount(TRAVEL, false));
            c.setBonus(r.amount(BONUS, false));
            c.setIncentives(r.amount(INCENTIVES, false));
            c.setOvertime(r.amount(OVERTIME, false));
            c.setOtherDeductions(r.amount(OTHER_DEDUCTIONS, false));
            Boolean auto = r.yesNo(AUTO, Boolean.TRUE);
            BigDecimal pf = r.amount(PF, false), esi = r.amount(ESI, false), pt = r.amount(PT, false), tds = r.amount(TDS, false);
            r.text(NOTES);
            if (Boolean.TRUE.equals(auto)) {
                if (pf != null || esi != null || pt != null || tds != null) {
                    plan.warning("PF/ESI/PT/TDS are ignored because Auto Statutory is Y");
                }
                c.setPf(BigDecimal.ZERO);
                c.setEsi(BigDecimal.ZERO);
                c.setProfessionalTax(BigDecimal.ZERO);
                c.setTds(BigDecimal.ZERO);
            } else {
                c.setPf(pf);
                c.setEsi(esi);
                c.setProfessionalTax(pt);
                c.setTds(tds);
            }
            if (basic != null && basic.signum() <= 0) plan.error("Basic must be greater than zero");

            if (employee != null) {
                lookup.duplicate(employee.getEmployeeCode(), plan, "employee " + employee.getEmployeeCode());
                if (EmployeeLookup.exited(employee)) plan.error("Employee has exited (" + employee.getStatus() + ")");
                SalaryStructure current = salaryStructureRepository
                        .findByEmployee_Company_IdAndEmployeeIdAndActiveTrueAndDeletedFalse(companyId, employee.getId()).orElse(null);
                if (current != null && effective != null && effective.isBefore(current.getEffectiveFrom())) {
                    plan.error("Effective From is before the current structure's date (" + current.getEffectiveFrom() + ")");
                }
                if (effective != null && employee.getDateOfJoining() != null && effective.isBefore(employee.getDateOfJoining())) {
                    plan.warning("Effective From is before the date of joining (" + employee.getDateOfJoining() + ")");
                }
                plan.action(current == null ? "Create structure" : "Revise (current structure kept as history)");
            }
            if (plan.valid() && employee != null) {
                BigDecimal gross = c.toEntity().grossSalary();
                BigDecimal deductions = c.toEntity().totalDeductions();
                if (deductions.compareTo(gross) > 0) plan.error("Deductions exceed gross salary");
                plan.value("Gross (computed)", gross.toPlainString());
                UpsertSalaryStructureRequest request = new UpsertSalaryStructureRequest();
                request.setEmployeeId(employee.getId());
                request.setEffectiveFrom(effective);
                request.setComponents(c);
                request.setAutoStatutory(auto);
                String notes = row.text().get(NOTES);
                request.setNotes(notes == null || notes.isBlank() ? "Imported" : notes.trim());
                plan.payload(new Payload(employee.getId(), request, employee.getFullName()));
            }
            plans.add(plan);
        }
        return plans;
    }

    @Override
    public String apply(Long companyId, RowPlan plan, DataImportBatch batch, ImportOptions options) {
        Payload payload = plan.payloadAs(Payload.class);
        UpsertSalaryStructureRequest request = payload.request();
        String notes = request.getNotes() + " (import batch #" + batch.getId() + ")";
        request.setNotes(notes.length() > 1000 ? notes.substring(0, 1000) : notes);
        SalaryStructureDTO saved = salaryStructureService.upsert(request);
        return "Salary structure saved for " + payload.employeeName() + (saved == null ? "" : " (net " + saved.getNetSalary() + ")");
    }
}
