package com.haodaone.salary.lwf;

import com.haodaone.attendance.entity.OfficeLocation;
import com.haodaone.attendance.repository.OfficeLocationRepository;
import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.salary.adjustment.PayrollDeductionProvider;
import com.haodaone.salary.entity.PayrollItem;
import com.haodaone.salary.repository.PayrollItemRepository;
import com.haodaone.salary.repository.SalaryStructureRepository;
import com.haodaone.salary.repository.StatutorySettingsRepository;
import com.haodaone.salary.service.PayslipService;
import com.haodaone.security.AuthorizationService;
import com.haodaone.tenant.TenantContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.*;

/**
 * Labour Welfare Fund. HR adds the states the company operates in (seeded from {@link #DEFAULTS}, which must be
 * checked against the state's current notification - rates are revised every few years) and edits them freely.
 * In each rule's deduction months the employee share comes off pay as a post-tax deduction; when the run is
 * processed the contribution and employer share are recorded for the LWF return.
 *
 * <p>The employee's state is their office location's state, falling back to the company's statutory (PT) state
 * or its first office. Wages are the monthly gross of the active salary structure.</p>
 */
@Service
public class LwfService implements PayrollDeductionProvider {

    static final String SOURCE = "LWF";

    /** Default rules: state code -> (name, months, EE fixed, EE %, EE max, ER fixed, ER x EE). Verify before use. */
    record Default(String code, String name, String months, String eeFixed, String eePercent, String eeMax, String erFixed, String erMultiplier) { }

    static final List<Default> DEFAULTS = List.of(
            new Default("AP", "Andhra Pradesh", "12", "30", "0", null, "70", "0"),
            new Default("CG", "Chhattisgarh", "6,12", "15", "0", null, "45", "0"),
            new Default("CH", "Chandigarh", "ALL", "5", "0", null, "20", "0"),
            new Default("DL", "Delhi", "6,12", "0.75", "0", null, "2.25", "0"),
            new Default("GA", "Goa", "6,12", "60", "0", null, "180", "0"),
            new Default("GJ", "Gujarat", "6,12", "6", "0", null, "12", "0"),
            new Default("HR", "Haryana", "ALL", "0", "0.2", "31", "0", "2"),
            new Default("KA", "Karnataka", "12", "50", "0", null, "100", "0"),
            new Default("KL", "Kerala", "ALL", "50", "0", null, "50", "0"),
            new Default("MH", "Maharashtra", "6,12", "25", "0", null, "75", "0"),
            new Default("MP", "Madhya Pradesh", "6,12", "10", "0", null, "30", "0"),
            new Default("OD", "Odisha", "6,12", "10", "0", null, "20", "0"),
            new Default("PB", "Punjab", "ALL", "5", "0", null, "20", "0"),
            new Default("TN", "Tamil Nadu", "12", "20", "0", null, "40", "0"),
            new Default("TS", "Telangana", "12", "2", "0", null, "5", "0"),
            new Default("WB", "West Bengal", "6,12", "3", "0", null, "15", "0"));

    private final LwfRuleRepository rules;
    private final LwfContributionRepository contributions;
    private final EmployeeRepository employees;
    private final OfficeLocationRepository offices;
    private final StatutorySettingsRepository statutorySettings;
    private final SalaryStructureRepository structures;
    private final PayrollItemRepository payrollItems;
    private final AuthorizationService authorizationService;
    private final AuditLogService auditLogService;

    public LwfService(LwfRuleRepository rules, LwfContributionRepository contributions, EmployeeRepository employees,
                      OfficeLocationRepository offices, StatutorySettingsRepository statutorySettings,
                      SalaryStructureRepository structures, PayrollItemRepository payrollItems,
                      AuthorizationService authorizationService, AuditLogService auditLogService) {
        this.rules = rules;
        this.contributions = contributions;
        this.employees = employees;
        this.offices = offices;
        this.statutorySettings = statutorySettings;
        this.structures = structures;
        this.payrollItems = payrollItems;
        this.authorizationService = authorizationService;
        this.auditLogService = auditLogService;
    }

    public record RuleDTO(Long id, String stateCode, String stateName, String deductionMonths, BigDecimal employeeFixed,
                          BigDecimal employeePercent, BigDecimal employeeMax, BigDecimal employerFixed, BigDecimal employerMultiplier,
                          BigDecimal minWages, BigDecimal maxWages, boolean active) { }
    public record StateOption(String code, String name, boolean added) { }
    public record ReturnRow(String stateCode, String employeeCode, String employeeName, BigDecimal wages,
                            BigDecimal employeeAmount, BigDecimal employerAmount) { }
    public record ReturnSummary(String period, int employees, BigDecimal employeeTotal, BigDecimal employerTotal,
                                Map<String, BigDecimal[]> byState, List<ReturnRow> rows) { }

    // ------------------------------------------------------------------ rules (SALARY_MANAGE)

    @Transactional(readOnly = true)
    public List<RuleDTO> rules() {
        return rules.findAllByCompanyIdAndDeletedFalseOrderByStateNameAsc(companyId()).stream().map(LwfService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<StateOption> states() {
        Set<String> added = new HashSet<>();
        rules.findAllByCompanyIdAndDeletedFalseOrderByStateNameAsc(companyId()).forEach(r -> added.add(r.getStateCode()));
        return DEFAULTS.stream().map(d -> new StateOption(d.code(), d.name(), added.contains(d.code()))).toList();
    }

    @Transactional
    public RuleDTO addState(String code) {
        Long companyId = companyId();
        Default d = DEFAULTS.stream().filter(x -> x.code().equalsIgnoreCase(code)).findFirst()
                .orElseThrow(() -> new BadRequestException("Unknown state"));
        if (rules.findByCompanyIdAndStateCodeAndDeletedFalse(companyId, d.code()).isPresent()) throw new BadRequestException(d.name() + " is already added");
        LwfRule r = new LwfRule();
        r.setCompanyId(companyId);
        r.setStateCode(d.code());
        r.setStateName(d.name());
        r.setDeductionMonths(d.months());
        r.setEmployeeFixed(new BigDecimal(d.eeFixed()));
        r.setEmployeePercent(new BigDecimal(d.eePercent()));
        r.setEmployeeMax(d.eeMax() == null ? null : new BigDecimal(d.eeMax()));
        r.setEmployerFixed(new BigDecimal(d.erFixed()));
        r.setEmployerMultiplier(new BigDecimal(d.erMultiplier()));
        r = rules.save(r);
        auditLogService.log("LWF_RULE", r.getId(), "ADDED", "LWF rule added for " + d.name());
        return toDto(r);
    }

    @Transactional
    public RuleDTO update(Long id, RuleDTO body) {
        LwfRule r = rules.findByIdAndCompanyIdAndDeletedFalse(id, companyId()).orElseThrow(() -> new ResourceNotFoundException("Rule not found"));
        r.setDeductionMonths(months(body.deductionMonths()));
        r.setEmployeeFixed(nonNegative(body.employeeFixed(), "Employee amount"));
        r.setEmployeePercent(nonNegative(body.employeePercent(), "Employee %"));
        r.setEmployeeMax(body.employeeMax() == null ? null : nonNegative(body.employeeMax(), "Employee cap"));
        r.setEmployerFixed(nonNegative(body.employerFixed(), "Employer amount"));
        r.setEmployerMultiplier(nonNegative(body.employerMultiplier(), "Employer multiple"));
        r.setMinWages(body.minWages() == null ? null : nonNegative(body.minWages(), "Minimum wages"));
        r.setMaxWages(body.maxWages() == null ? null : nonNegative(body.maxWages(), "Maximum wages"));
        r.setActive(body.active());
        if (r.getEmployeeFixed().signum() == 0 && r.getEmployeePercent().signum() == 0) throw new BadRequestException("Set the employee contribution");
        r = rules.save(r);
        auditLogService.log("LWF_RULE", r.getId(), "UPDATED", "LWF rule updated for " + r.getStateName());
        return toDto(r);
    }

    // ------------------------------------------------------------------ calculation

    record Amounts(BigDecimal wages, BigDecimal employee, BigDecimal employer) { }

    /** Employee/employer shares for a month's wages under a rule; null when the rule doesn't apply this month. */
    static Amounts calculate(LwfRule r, BigDecimal wages, int month) {
        if (!r.isActive() || !appliesIn(r.getDeductionMonths(), month)) return null;
        if (r.getMinWages() != null && wages.compareTo(r.getMinWages()) < 0) return null;
        if (r.getMaxWages() != null && wages.compareTo(r.getMaxWages()) > 0) return null;
        BigDecimal ee = r.getEmployeeFixed().signum() > 0 ? r.getEmployeeFixed()
                : wages.multiply(r.getEmployeePercent()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        if (r.getEmployeeMax() != null && ee.compareTo(r.getEmployeeMax()) > 0) ee = r.getEmployeeMax();
        BigDecimal er = r.getEmployerFixed().signum() > 0 ? r.getEmployerFixed()
                : ee.multiply(r.getEmployerMultiplier()).setScale(2, RoundingMode.HALF_UP);
        if (ee.signum() <= 0) return null;
        return new Amounts(wages, ee.setScale(2, RoundingMode.HALF_UP), er.setScale(2, RoundingMode.HALF_UP));
    }

    static boolean appliesIn(String months, int month) {
        if (months == null) return false;
        if ("ALL".equalsIgnoreCase(months.trim())) return true;
        for (String m : months.split(",")) {
            if (m.trim().equals(String.valueOf(month))) return true;
        }
        return false;
    }

    @Override
    public Set<String> sourceTypes() {
        return Set.of(SOURCE);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PayrollDeduction> deductionsFor(Long companyId, Long employeeId, YearMonth period) {
        List<LwfRule> companyRules = rules.findAllByCompanyIdAndDeletedFalseOrderByStateNameAsc(companyId);
        if (companyRules.isEmpty()) return List.of();
        if (contributions.existsByEmployeeIdAndPeriodAndDeletedFalse(employeeId, period.toString())) return List.of();
        Employee e = employees.findByIdAndCompany_IdAndDeletedFalse(employeeId, companyId).orElse(null);
        if (e == null) return List.of();
        String state = stateOf(e, companyId);
        LwfRule rule = companyRules.stream().filter(r -> r.getStateCode().equals(state)).findFirst().orElse(null);
        if (rule == null) return List.of();
        BigDecimal wages = structures.findByEmployeeIdAndActiveTrueAndDeletedFalse(employeeId).map(s -> s.getGrossSalary()).orElse(BigDecimal.ZERO);
        Amounts a = calculate(rule, wages, period.getMonthValue());
        if (a == null) return List.of();
        return List.of(new PayrollDeduction("LWF", "Labour Welfare Fund (" + rule.getStateName() + ")", a.employee(), SOURCE, rule.getId()));
    }

    /** The run was processed: record the contribution with the employer share for the return. */
    @Override
    @Transactional
    public void markSettled(List<PayrollDeduction> deductions, Long payrollItemId) {
        PayrollItem item = payrollItems.findById(payrollItemId).orElse(null);
        if (item == null) return;
        YearMonth period = YearMonth.of(item.getPayrollRun().getPeriodYear(), item.getPayrollRun().getPeriodMonth());
        for (PayrollDeduction d : deductions) {
            if (!SOURCE.equals(d.sourceType())) continue;
            LwfRule rule = rules.findById(d.sourceId()).orElse(null);
            if (rule == null) continue;
            BigDecimal wages = structures.findByEmployeeIdAndActiveTrueAndDeletedFalse(item.getEmployee().getId())
                    .map(s -> s.getGrossSalary()).orElse(item.getGrossSalary());
            Amounts a = calculate(rule, wages, period.getMonthValue());
            LwfContribution c = new LwfContribution();
            c.setCompanyId(rule.getCompanyId());
            c.setEmployeeId(item.getEmployee().getId());
            c.setPayrollItemId(payrollItemId);
            c.setPeriod(period.toString());
            c.setStateCode(rule.getStateCode());
            c.setWages(wages);
            c.setEmployeeAmount(d.amount());
            c.setEmployerAmount(a == null ? BigDecimal.ZERO : a.employer());
            contributions.save(c);
        }
    }

    // ------------------------------------------------------------------ return

    @Transactional(readOnly = true)
    public ReturnSummary returnFor(YearMonth period) {
        Long companyId = companyId();
        List<LwfContribution> rows = contributions.findAllByCompanyIdAndPeriodAndDeletedFalseOrderByStateCodeAsc(companyId, period.toString());
        Map<Long, Employee> people = new HashMap<>();
        Set<Long> ids = new HashSet<>();
        rows.forEach(c -> ids.add(c.getEmployeeId()));
        if (!ids.isEmpty()) employees.findAllByCompany_IdAndIdInAndDeletedFalseOrderByFirstNameAsc(companyId, ids).forEach(e -> people.put(e.getId(), e));
        Map<String, BigDecimal[]> byState = new TreeMap<>();
        List<ReturnRow> out = new ArrayList<>();
        BigDecimal ee = BigDecimal.ZERO;
        BigDecimal er = BigDecimal.ZERO;
        for (LwfContribution c : rows) {
            Employee e = people.get(c.getEmployeeId());
            out.add(new ReturnRow(c.getStateCode(), e == null ? "" : e.getEmployeeCode(), e == null ? "Former employee" : e.getFullName(),
                    c.getWages(), c.getEmployeeAmount(), c.getEmployerAmount()));
            BigDecimal[] t = byState.computeIfAbsent(c.getStateCode(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            t[0] = t[0].add(BigDecimal.ONE);
            t[1] = t[1].add(c.getEmployeeAmount());
            t[2] = t[2].add(c.getEmployerAmount());
            ee = ee.add(c.getEmployeeAmount());
            er = er.add(c.getEmployerAmount());
        }
        return new ReturnSummary(period.toString(), out.size(), ee, er, byState, out);
    }

    @Transactional(readOnly = true)
    public PayslipService.PayslipFile returnCsv(YearMonth period) {
        ReturnSummary s = returnFor(period);
        StringBuilder csv = new StringBuilder("State,Employee Code,Employee Name,Wages,Employee Contribution,Employer Contribution,Total\n");
        for (ReturnRow r : s.rows()) {
            csv.append(r.stateCode()).append(',').append(r.employeeCode()).append(',').append(cell(r.employeeName())).append(',')
                    .append(r.wages().setScale(2, RoundingMode.HALF_UP).toPlainString()).append(',')
                    .append(r.employeeAmount().toPlainString()).append(',').append(r.employerAmount().toPlainString()).append(',')
                    .append(r.employeeAmount().add(r.employerAmount()).toPlainString()).append('\n');
        }
        csv.append("TOTAL,,").append(s.employees()).append(" employee(s),,").append(s.employeeTotal().toPlainString()).append(',')
                .append(s.employerTotal().toPlainString()).append(',').append(s.employeeTotal().add(s.employerTotal()).toPlainString()).append('\n');
        return new PayslipService.PayslipFile("lwf-" + period + ".csv", csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    // ------------------------------------------------------------------ helpers

    /** Employee's office state, else the company's statutory state, else its first office's state. */
    String stateOf(Employee e, Long companyId) {
        if (e.getOfficeLocationId() != null) {
            String code = offices.findByIdAndCompany_IdAndDeletedFalse(e.getOfficeLocationId(), companyId)
                    .map(OfficeLocation::getState).map(LwfService::stateCode).orElse(null);
            if (code != null) return code;
        }
        String statutory = statutorySettings.findByCompany_IdAndDeletedFalse(companyId).map(s -> s.getPtState()).map(LwfService::stateCode).orElse(null);
        if (statutory != null) return statutory;
        return offices.findAllByCompany_IdAndActiveTrueAndDeletedFalseOrderByNameAsc(companyId).stream()
                .map(OfficeLocation::getState).map(LwfService::stateCode).filter(Objects::nonNull).findFirst().orElse(null);
    }

    /** "Tamil Nadu", "tamilnadu", "TN" -> "TN". */
    static String stateCode(String value) {
        if (value == null || value.isBlank()) return null;
        String v = value.trim();
        String compact = v.replaceAll("[^A-Za-z]", "").toLowerCase(Locale.ROOT);
        for (Default d : DEFAULTS) {
            if (d.code().equalsIgnoreCase(v) || d.name().replaceAll("[^A-Za-z]", "").toLowerCase(Locale.ROOT).equals(compact)) return d.code();
        }
        if (compact.equals("orissa")) return "OD";
        if (compact.equals("newdelhi") || compact.equals("nctofdelhi")) return "DL";
        if (compact.equals("bangalore") || compact.equals("bengaluru")) return "KA";
        if (compact.equals("bombay") || compact.equals("mumbai") || compact.equals("pune")) return "MH";
        if (compact.equals("chennai")) return "TN";
        if (compact.equals("hyderabad")) return "TS";
        return null;
    }

    private static String months(String value) {
        if (value == null || value.isBlank()) throw new BadRequestException("Choose the deduction months");
        String v = value.replace(" ", "").toUpperCase(Locale.ROOT);
        if ("ALL".equals(v)) return v;
        TreeSet<Integer> months = new TreeSet<>();
        for (String part : v.split(",")) {
            try {
                int m = Integer.parseInt(part);
                if (m < 1 || m > 12) throw new NumberFormatException();
                months.add(m);
            } catch (NumberFormatException ex) {
                throw new BadRequestException("Months must be ALL or numbers 1-12 separated by commas");
            }
        }
        return String.join(",", months.stream().map(String::valueOf).toList());
    }

    private static BigDecimal nonNegative(BigDecimal v, String label) {
        if (v == null) return BigDecimal.ZERO;
        if (v.signum() < 0) throw new BadRequestException(label + " can't be negative");
        return v;
    }

    private static String cell(String v) {
        if (v == null) return "";
        String s = v.replace("\n", " ");
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        return s.contains(",") || s.contains("\"") ? "\"" + s.replace("\"", "\"\"") + "\"" : s;
    }

    private static RuleDTO toDto(LwfRule r) {
        return new RuleDTO(r.getId(), r.getStateCode(), r.getStateName(), r.getDeductionMonths(), r.getEmployeeFixed(), r.getEmployeePercent(),
                r.getEmployeeMax(), r.getEmployerFixed(), r.getEmployerMultiplier(), r.getMinWages(), r.getMaxWages(), r.isActive());
    }

    private Long companyId() {
        if (!authorizationService.hasOrganizationScope("SALARY_MANAGE")) throw new AccessDeniedException("SALARY_MANAGE requires ORGANIZATION scope.");
        Long companyId = TenantContext.getCurrentTenant();
        if (companyId == null) throw new BadRequestException("Company context is required");
        return companyId;
    }
}
