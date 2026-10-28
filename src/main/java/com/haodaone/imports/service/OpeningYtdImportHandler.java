package com.haodaone.imports.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.imports.entity.DataImportBatch;
import com.haodaone.imports.entity.PayrollOpeningYtd;
import com.haodaone.imports.repository.PayrollOpeningYtdRepository;
import com.haodaone.salary.dto.TaxDeclarationDTO;
import com.haodaone.salary.service.TaxDeclarationService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Opening payroll YTD per employee per financial year, for companies moving payroll to Vettri mid-year. Stored in
 * {@link PayrollOpeningYtd}; the statutory engine adds it to projected income and TDS already deducted, and Form 16
 * includes it. One live row per employee/FY: a second import is rejected unless "replace" is chosen.
 */
@Component
public class OpeningYtdImportHandler implements ImportHandler {

    static final String CODE = "Employee Code";
    static final String FY = "Financial Year";
    static final String GROSS = "Gross Salary Paid";
    static final String TAXABLE = "Taxable Salary Paid";
    static final String PF = "PF Employee";
    static final String PT = "Professional Tax";
    static final String TDS = "TDS Deducted";
    static final String MONTHS = "Months Covered";
    static final String PREV_INCOME = "Previous Employer Income";
    static final String PREV_TDS = "Previous Employer TDS";

    private static final Pattern FY_PATTERN = Pattern.compile("^(?:FY\\s*)?(\\d{4})(?:\\s*[-/]\\s*(\\d{2}|\\d{4}))?$", Pattern.CASE_INSENSITIVE);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static final List<ImportSheet.Column> COLUMNS = List.of(
            new ImportSheet.Column(CODE, true, "Existing employee code"),
            new ImportSheet.Column(FY, true, "Financial year, e.g. 2026-27 (April 2026 - March 2027)"),
            new ImportSheet.Column(GROSS, true, "Gross salary paid by your company this FY before Vettri payroll"),
            new ImportSheet.Column(TAXABLE, false, "Taxable part of that salary (blank = same as gross)"),
            new ImportSheet.Column(PF, false, "Employee PF deducted so far"),
            new ImportSheet.Column(PT, false, "Professional tax deducted so far"),
            new ImportSheet.Column(TDS, true, "TDS deducted so far by your company (0 if none)"),
            new ImportSheet.Column(MONTHS, true, "Number of months (from April) covered by these figures, 0-12"),
            new ImportSheet.Column(PREV_INCOME, false, "Salary from a previous employer this FY (Form 12B), optional"),
            new ImportSheet.Column(PREV_TDS, false, "TDS deducted by the previous employer this FY, optional"));

    private final EmployeeRepository employeeRepository;
    private final PayrollOpeningYtdRepository repository;
    private final AuditLogService auditLogService;

    public OpeningYtdImportHandler(EmployeeRepository employeeRepository, PayrollOpeningYtdRepository repository,
                                   AuditLogService auditLogService) {
        this.employeeRepository = employeeRepository;
        this.repository = repository;
        this.auditLogService = auditLogService;
    }

    @Override
    public ImportType type() {
        return ImportType.OPENING_YTD;
    }

    @Override
    public List<ImportSheet.Column> columns() {
        return COLUMNS;
    }

    @Override
    public List<List<String>> exampleRows() {
        String fy = TaxDeclarationDTO.fyLabel(TaxDeclarationService.currentFinancialYear()).replace("FY ", "");
        return List.of(
                List.of("EMP0001", fy, "420000", "420000", "10800", "1200", "18000", "6", "", ""),
                List.of("EMP0002", fy, "150000", "", "5400", "600", "0", "3", "240000", "12000"));
    }

    @Override
    public List<String> instructions() {
        return List.of(
                "Use this when you start running payroll in Vettri part-way through a financial year (April-March).",
                "Enter the totals already paid/deducted this FY outside Vettri. Months Covered counts from April: e.g. 6 = April to September.",
                "Vettri adds these figures to the annual income and to 'TDS already deducted' when it calculates monthly TDS, and shows them in the tax projection and Form 16.",
                "Previous Employer Income/TDS are for employees who joined you mid-year and submitted Form 12B.",
                "Do not include months that were already processed in Vettri payroll - they are counted automatically.",
                "One row per employee per financial year. Importing the same employee and year again is rejected unless you tick 'Replace existing'.",
                "Requires Salary Manage permission with organization scope.");
    }

    record Payload(Long employeeId, int fy, BigDecimal gross, BigDecimal taxable, BigDecimal pf, BigDecimal pt,
                   BigDecimal tds, int months, BigDecimal prevIncome, BigDecimal prevTds, String label) {
    }

    @Override
    public List<RowPlan> validate(Long companyId, List<ImportSheet.SheetRow> rows, ImportOptions options) {
        EmployeeLookup lookup = new EmployeeLookup(employeeRepository, companyId);
        int currentFy = TaxDeclarationService.currentFinancialYear();
        List<RowPlan> plans = new ArrayList<>();
        for (ImportSheet.SheetRow row : rows) {
            RowPlan plan = new RowPlan(row.number());
            ImportSheet.Reader r = new ImportSheet.Reader(row, plan);
            Employee employee = lookup.resolve(r.required(CODE), plan);
            String fyText = r.required(FY);
            Integer fy = fyText == null ? null : parseFy(fyText);
            if (fyText != null && fy == null) plan.error("Financial Year '" + fyText + "' is not valid (use e.g. 2026-27)");
            if (fy != null && (fy < 2020 || fy > currentFy)) plan.error("Financial Year must be between 2020-21 and " + TaxDeclarationDTO.fyLabel(currentFy).replace("FY ", ""));
            BigDecimal gross = r.amount(GROSS, true);
            BigDecimal taxable = r.amount(TAXABLE, false);
            BigDecimal pf = r.amount(PF, false);
            BigDecimal pt = r.amount(PT, false);
            BigDecimal tds = r.amount(TDS, true);
            Integer months = r.integer(MONTHS, true);
            BigDecimal prevIncome = r.amount(PREV_INCOME, false);
            BigDecimal prevTds = r.amount(PREV_TDS, false);
            if (months != null && (months < 0 || months > 12)) plan.error("Months Covered must be between 0 and 12");
            if (gross != null && taxable != null && taxable.compareTo(gross) > 0) plan.error("Taxable Salary Paid cannot exceed Gross Salary Paid");
            if (gross != null && months != null && months == 0 && gross.signum() > 0) plan.error("Months Covered must be at least 1 when salary was paid");
            if (gross != null && tds != null && tds.compareTo(gross) > 0) plan.error("TDS Deducted cannot exceed Gross Salary Paid");
            if (prevTds != null && prevTds.signum() > 0 && (prevIncome == null || prevTds.compareTo(prevIncome) > 0)) {
                plan.error("Previous Employer TDS needs Previous Employer Income at least as large");
            }
            if (fy != null && months != null && fy == currentFy) {
                long elapsed = ChronoUnit.MONTHS.between(YearMonth.of(fy, 4), YearMonth.now(IST)) + 1;
                if (months > elapsed) plan.warning("Months Covered (" + months + ") is more than the " + elapsed + " month(s) elapsed in this FY");
            }
            if (employee != null && fy != null) {
                lookup.duplicate(employee.getId() + "|" + fy, plan, employee.getEmployeeCode() + " / " + fyText);
                PayrollOpeningYtd existing = repository
                        .findFirstByCompany_IdAndEmployee_IdAndFinancialYearAndDeletedFalse(companyId, employee.getId(), fy).orElse(null);
                if (existing != null && !options.replace()) {
                    plan.error("Opening YTD for " + TaxDeclarationDTO.fyLabel(fy) + " already exists for this employee. Tick 'Replace existing' to overwrite it.");
                }
                if (plan.valid()) {
                    plan.action(existing != null ? "Replace opening YTD" : "Add opening YTD");
                    plan.payload(new Payload(employee.getId(), fy, gross, taxable != null ? taxable : gross, pf, pt, tds,
                            months, prevIncome, prevTds, employee.getFullName() + " / " + TaxDeclarationDTO.fyLabel(fy)));
                }
            }
            plans.add(plan);
        }
        return plans;
    }

    static Integer parseFy(String value) {
        Matcher m = FY_PATTERN.matcher(value.trim());
        if (!m.matches()) return null;
        int start = Integer.parseInt(m.group(1));
        if (m.group(2) != null) {
            int end = Integer.parseInt(m.group(2));
            int expected = m.group(2).length() == 2 ? (start + 1) % 100 : start + 1;
            if (end != expected) return null;
        }
        return start;
    }

    @Override
    public String apply(Long companyId, RowPlan plan, DataImportBatch batch, ImportOptions options) {
        Payload p = plan.payloadAs(Payload.class);
        Employee employee = employeeRepository.findByIdAndCompany_IdAndDeletedFalse(p.employeeId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
        PayrollOpeningYtd existing = repository
                .findFirstByCompany_IdAndEmployee_IdAndFinancialYearAndDeletedFalse(companyId, employee.getId(), p.fy()).orElse(null);
        if (existing != null) {
            if (!options.replace()) throw new BadRequestException("Opening YTD already exists for " + p.label());
            existing.setDeleted(true);
            existing.setDeletedAt(LocalDateTime.now(IST));
            repository.saveAndFlush(existing); // before the insert: one live row per employee/FY (partial unique index)
        }
        PayrollOpeningYtd row = new PayrollOpeningYtd();
        row.setCompany(employee.getCompany());
        row.setEmployee(employee);
        row.setBatch(batch);
        row.setFinancialYear(p.fy());
        row.setGrossSalary(p.gross());
        row.setTaxableSalary(p.taxable());
        row.setPfEmployee(p.pf());
        row.setProfessionalTax(p.pt());
        row.setTdsDeducted(p.tds());
        row.setMonthsCovered(p.months());
        row.setPreviousEmployerIncome(p.prevIncome());
        row.setPreviousEmployerTds(p.prevTds());
        PayrollOpeningYtd saved = repository.save(row);
        String summary = "Opening payroll YTD " + (existing != null ? "replaced" : "imported") + " for " + p.label()
                + ": gross " + p.gross() + ", TDS " + p.tds() + ", " + p.months() + " month(s)";
        auditLogService.log("PayrollOpeningYtd", saved.getId(), existing != null ? "REPLACE" : "IMPORT", summary);
        return summary;
    }
}
