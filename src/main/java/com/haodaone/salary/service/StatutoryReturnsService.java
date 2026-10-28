package com.haodaone.salary.service;

import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.employee.entity.Employee;
import com.haodaone.salary.entity.PayrollItem;
import com.haodaone.salary.entity.PayrollItemStatus;
import com.haodaone.salary.entity.PayrollRun;
import com.haodaone.salary.entity.SalaryComponents;
import com.haodaone.salary.repository.PayrollItemRepository;
import com.haodaone.salary.repository.PayrollRunRepository;
import com.haodaone.salary.statutory.ProfessionalTaxSlabs;
import com.haodaone.security.AuthorizationService;
import com.haodaone.tenant.TenantContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Monthly statutory return files for one payroll run (SALARY_MANAGE,
 * organization scope): EPFO ECR 2.0 text, ESI contribution CSV, PT summary
 * CSV and TDS summary CSV, plus a JSON summary with data-quality warnings.
 * Held items are always excluded; DRAFT runs are allowed so HR can preview,
 * but the files should be filed from a PROCESSED/PAID run.
 */
@Service
public class StatutoryReturnsService {

    /** ECR 2.0 field separator. */
    static final String ECR_SEPARATOR = "#~#";
    private static final BigDecimal MANUAL_PF_CEILING = new BigDecimal("15000");

    private final PayrollRunRepository payrollRunRepository;
    private final PayrollItemRepository payrollItemRepository;
    private final AuthorizationService authorizationService;

    public StatutoryReturnsService(PayrollRunRepository payrollRunRepository, PayrollItemRepository payrollItemRepository,
                                   AuthorizationService authorizationService) {
        this.payrollRunRepository = payrollRunRepository;
        this.payrollItemRepository = payrollItemRepository;
        this.authorizationService = authorizationService;
    }

    public record Warning(Long employeeId, String employeeCode, String employeeName, String message) {
    }

    public record Summary(Long runId, int periodMonth, int periodYear, String runStatus, int employees,
                          BigDecimal pfEmployee, BigDecimal pfEmployerEpf, BigDecimal pfEmployerEps, int pfMembers,
                          BigDecimal esiEmployee, BigDecimal esiEmployer, int esiMembers,
                          BigDecimal professionalTax, BigDecimal tds, BigDecimal employerContributions,
                          List<Warning> warnings) {
    }

    @Transactional(readOnly = true)
    public Summary summary(Long runId) {
        RunData data = load(runId);
        BigDecimal pfEe = BigDecimal.ZERO, pfEpf = BigDecimal.ZERO, pfEps = BigDecimal.ZERO;
        BigDecimal esiEe = BigDecimal.ZERO, esiEr = BigDecimal.ZERO, pt = BigDecimal.ZERO, tds = BigDecimal.ZERO;
        int pfMembers = 0, esiMembers = 0;
        List<Warning> warnings = new ArrayList<>();
        for (PayrollItem item : data.items()) {
            SalaryComponents c = item.getComponents();
            Employee e = item.getEmployee();
            if (hasPf(item)) {
                pfMembers++;
                pfEe = pfEe.add(nz(c.getPf()));
                pfEpf = pfEpf.add(nz(item.getEmployerEpf()));
                pfEps = pfEps.add(nz(item.getEmployerEps()));
                if (blank(e.getUanNumber())) warnings.add(warning(e, "No UAN - excluded from the PF ECR file"));
            }
            if (nz(c.getEsi()).signum() > 0 || nz(item.getEmployerEsi()).signum() > 0) {
                esiMembers++;
                esiEe = esiEe.add(nz(c.getEsi()));
                esiEr = esiEr.add(nz(item.getEmployerEsi()));
                if (blank(e.getEsiNumber())) warnings.add(warning(e, "No ESI IP number"));
            }
            pt = pt.add(nz(c.getProfessionalTax()));
            if (nz(c.getTds()).signum() > 0) {
                tds = tds.add(c.getTds());
                if (blank(e.getPanNumber())) warnings.add(warning(e, "TDS deducted but no PAN on file (s.206AA: 20% TDS applies without PAN)"));
            }
        }
        PayrollRun run = data.run();
        return new Summary(run.getId(), run.getPeriodMonth(), run.getPeriodYear(), run.getStatus(), data.items().size(),
                pfEe, pfEpf, pfEps, pfMembers, esiEe, esiEr, esiMembers, pt, tds,
                pfEpf.add(pfEps).add(esiEr), warnings);
    }

    /**
     * EPFO ECR 2.0 text: one line per member, fields separated by {@code #~#}, no header:
     * UAN, member name, gross wages, EPF wages, EPS wages, EDLI wages, EE share, EPS contribution,
     * ER share (EPF difference), NCP days, refund of advances. Whole rupees. Members without a UAN
     * are skipped (see {@link #summary} warnings).
     */
    @Transactional(readOnly = true)
    public PayslipService.PayslipFile ecr(Long runId) {
        RunData data = load(runId);
        StringBuilder out = new StringBuilder();
        for (PayrollItem item : data.items()) {
            if (!hasPf(item)) continue;
            Employee e = item.getEmployee();
            if (blank(e.getUanNumber())) continue;
            // Auto-calculated items carry their wage bases; manual ones assume basic capped at the 15,000 ceiling.
            BigDecimal pfWages = item.getPfWages().signum() > 0 ? item.getPfWages()
                    : nz(item.getComponents().getBasicSalary()).min(MANUAL_PF_CEILING);
            BigDecimal epsWages = item.getEpsWages().signum() > 0 ? item.getEpsWages() : pfWages.min(MANUAL_PF_CEILING);
            BigDecimal edliWages = item.getEdliWages().signum() > 0 ? item.getEdliWages() : epsWages;
            BigDecimal eps = item.getEmployerEps();
            BigDecimal erEpf = item.getEmployerEpf();
            if (!item.isStatutoryAuto() && eps.signum() == 0 && erEpf.signum() == 0) {
                // Manually entered PF: assume the employer matched the employee share (12% = 12%),
                // split as EPS 8.33% of the capped wage and the remainder to EPF.
                BigDecimal ee = nz(item.getComponents().getPf());
                eps = epsWages.multiply(new BigDecimal("8.33")).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP).min(ee);
                erEpf = ee.subtract(eps);
            }
            String[] fields = {
                    e.getUanNumber().trim(),
                    ecrName(e.getFullName()),
                    rupees(item.getGrossSalary()),
                    rupees(pfWages),
                    rupees(epsWages),
                    rupees(edliWages),
                    rupees(item.getComponents().getPf()),
                    rupees(eps),
                    rupees(erEpf),
                    item.getLopDays() == null ? "0" : item.getLopDays().setScale(0, RoundingMode.HALF_UP).toPlainString(),
                    "0"
            };
            out.append(String.join(ECR_SEPARATOR, fields)).append("\r\n");
        }
        return new PayslipService.PayslipFile(name("pf-ecr", data.run(), "txt"), out.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** ESI monthly contribution: IP number, name, days paid, total wages, employee contribution (+ employer, for reconciliation). */
    @Transactional(readOnly = true)
    public PayslipService.PayslipFile esiCsv(Long runId) {
        RunData data = load(runId);
        StringBuilder csv = new StringBuilder("IP Number,IP Name,No of Days for which wages paid,Total Monthly Wages,Employee Contribution,Employer Contribution\r\n");
        for (PayrollItem item : data.items()) {
            if (nz(item.getComponents().getEsi()).signum() <= 0 && nz(item.getEmployerEsi()).signum() <= 0) continue;
            Employee e = item.getEmployee();
            csv.append(PayslipService.cell(e.getEsiNumber())).append(',')
                    .append(PayslipService.cell(e.getFullName())).append(',')
                    .append(days(item)).append(',')
                    .append(money(item.effectiveTaxableGross())).append(',')
                    .append(money(item.getComponents().getEsi())).append(',')
                    .append(money(item.getEmployerEsi())).append("\r\n");
        }
        return new PayslipService.PayslipFile(name("esi-contribution", data.run(), "csv"), csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** PT summary grouped by state and monthly amount (slab), then the employee detail. */
    @Transactional(readOnly = true)
    public PayslipService.PayslipFile ptCsv(Long runId) {
        RunData data = load(runId);
        Map<String, BigDecimal[]> groups = new TreeMap<>();
        for (PayrollItem item : data.items()) {
            BigDecimal pt = nz(item.getComponents().getProfessionalTax());
            String key = stateLabel(item.getPtState()) + "|" + pt.setScale(2, RoundingMode.HALF_UP).toPlainString();
            BigDecimal[] totals = groups.computeIfAbsent(key, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            totals[0] = totals[0].add(BigDecimal.ONE);
            totals[1] = totals[1].add(nz(item.getGrossSalary()));
            totals[2] = totals[2].add(pt);
        }
        StringBuilder csv = new StringBuilder("State,PT per employee,Employees,Total gross,Total PT\r\n");
        for (var entry : groups.entrySet()) {
            String[] key = entry.getKey().split("\\|", 2);
            BigDecimal[] t = entry.getValue();
            csv.append(PayslipService.cell(key[0])).append(',').append(key[1]).append(',')
                    .append(t[0].toPlainString()).append(',').append(money(t[1])).append(',').append(money(t[2])).append("\r\n");
        }
        csv.append("\r\nEmployee Code,Employee Name,State,Gross,Professional Tax\r\n");
        for (PayrollItem item : data.items()) {
            Employee e = item.getEmployee();
            csv.append(PayslipService.cell(e.getEmployeeCode())).append(',')
                    .append(PayslipService.cell(e.getFullName())).append(',')
                    .append(PayslipService.cell(stateLabel(item.getPtState()))).append(',')
                    .append(money(item.getGrossSalary())).append(',')
                    .append(money(item.getComponents().getProfessionalTax())).append("\r\n");
        }
        return new PayslipService.PayslipFile(name("professional-tax", data.run(), "csv"), csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** TDS (section 192) monthly summary: employee, PAN, gross, taxable gross, TDS. */
    @Transactional(readOnly = true)
    public PayslipService.PayslipFile tdsCsv(Long runId) {
        RunData data = load(runId);
        StringBuilder csv = new StringBuilder("Employee Code,Employee Name,PAN,Gross Salary,Taxable Salary,TDS\r\n");
        BigDecimal total = BigDecimal.ZERO;
        for (PayrollItem item : data.items()) {
            Employee e = item.getEmployee();
            BigDecimal tds = nz(item.getComponents().getTds());
            total = total.add(tds);
            csv.append(PayslipService.cell(e.getEmployeeCode())).append(',')
                    .append(PayslipService.cell(e.getFullName())).append(',')
                    .append(PayslipService.cell(e.getPanNumber())).append(',')
                    .append(money(item.getGrossSalary())).append(',')
                    .append(money(item.effectiveTaxableGross())).append(',')
                    .append(money(tds)).append("\r\n");
        }
        csv.append(",\"Total\",,,,").append(money(total)).append("\r\n");
        return new PayslipService.PayslipFile(name("tds-summary", data.run(), "csv"), csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private record RunData(PayrollRun run, List<PayrollItem> items) {
    }

    private RunData load(Long runId) {
        if (!authorizationService.hasOrganizationScope("SALARY_MANAGE")) {
            throw new AccessDeniedException("SALARY_MANAGE requires ORGANIZATION scope.");
        }
        Long companyId = TenantContext.getCurrentTenant();
        if (companyId == null) throw new BadRequestException("Company context is required");
        PayrollRun run = payrollRunRepository.findByIdAndCompany_IdAndDeletedFalse(runId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Payroll run not found: " + runId));
        List<PayrollItem> items = payrollItemRepository
                .findByPayrollRun_Company_IdAndPayrollRunIdAndDeletedFalseOrderByEmployee_FirstNameAsc(companyId, runId).stream()
                .filter(i -> !PayrollItemStatus.ON_HOLD.equals(i.getStatus()))
                .toList();
        return new RunData(run, items);
    }

    private static boolean hasPf(PayrollItem item) {
        return nz(item.getComponents().getPf()).signum() > 0 || nz(item.getEmployerEpf()).signum() > 0
                || nz(item.getEmployerEps()).signum() > 0;
    }

    private static String stateLabel(String code) {
        if (code == null) return "Manual";
        if ("FIXED".equals(code)) return "Fixed amount";
        var table = ProfessionalTaxSlabs.table(code);
        return table != null ? table.name() : code;
    }

    private static Warning warning(Employee e, String message) {
        return new Warning(e.getId(), e.getEmployeeCode(), e.getFullName(), message);
    }

    /** ECR member names: upper case letters, digits, space and dot only, max 85 chars. */
    static String ecrName(String name) {
        String cleaned = name == null ? "" : name.toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z0-9 .]", " ").replaceAll("\\s+", " ").trim();
        return cleaned.length() > 85 ? cleaned.substring(0, 85) : cleaned;
    }

    private static String days(PayrollItem item) {
        BigDecimal paid = item.getPaidDays();
        if (paid == null) {
            PayrollRun run = item.getPayrollRun();
            return String.valueOf(java.time.YearMonth.of(run.getPeriodYear(), run.getPeriodMonth()).lengthOfMonth());
        }
        return paid.setScale(0, RoundingMode.FLOOR).toPlainString();
    }

    private static String rupees(BigDecimal value) {
        return nz(value).setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    private static String money(BigDecimal value) {
        return nz(value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String name(String prefix, PayrollRun run, String ext) {
        return String.format("%s-%d-%02d.%s", prefix, run.getPeriodYear(), run.getPeriodMonth(), ext);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
