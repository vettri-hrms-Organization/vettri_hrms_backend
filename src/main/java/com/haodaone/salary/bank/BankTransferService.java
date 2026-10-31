package com.haodaone.salary.bank;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.employee.entity.Employee;
import com.haodaone.salary.entity.PayrollItem;
import com.haodaone.salary.entity.PayrollItemStatus;
import com.haodaone.salary.entity.PayrollRun;
import com.haodaone.salary.entity.PayrollRunStatus;
import com.haodaone.salary.repository.PayrollItemRepository;
import com.haodaone.salary.repository.PayrollRunRepository;
import com.haodaone.salary.service.PayslipService;
import com.haodaone.security.AuthorizationService;
import com.haodaone.tenant.TenantContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Bank transfer (bulk salary payment) files from a processed payroll run: one credit per employee for their
 * net pay. GENERIC is a plain NEFT/IMPS CSV any corporate banking portal can map; HDFC (ENet) and ICICI (CIB)
 * are presets of those banks' bulk-upload layouts - companies should check them against a sample from their
 * own portal before first use. Employees with missing or invalid bank details are left out and listed.
 */
@Service
public class BankTransferService {

    public static final List<String> FORMATS = List.of("GENERIC", "HDFC", "ICICI");
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

    private final PayrollRunRepository runs;
    private final PayrollItemRepository items;
    private final PayrollBankSettingsRepository settingsRepository;
    private final AuthorizationService authorizationService;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public BankTransferService(PayrollRunRepository runs, PayrollItemRepository items, PayrollBankSettingsRepository settingsRepository,
                               AuthorizationService authorizationService, AuditLogService auditLogService, Clock applicationClock) {
        this.runs = runs;
        this.items = items;
        this.settingsRepository = settingsRepository;
        this.authorizationService = authorizationService;
        this.auditLogService = auditLogService;
        this.clock = applicationClock;
    }

    public record SettingsDTO(String debitAccountNumber, String debitIfsc, String bankName, String defaultFormat, String narration) { }
    public record Problem(Long employeeId, String employeeName, String employeeCode, String issue) { }
    public record Row(Long employeeId, String employeeName, String employeeCode, String accountMasked, String ifsc,
                      BigDecimal amount, String mode) { }
    public record Preview(Long runId, String period, String runStatus, int payable, BigDecimal totalAmount, List<Row> rows,
                          List<Problem> problems, boolean debitAccountSet, String defaultFormat, List<String> formats) { }

    // ------------------------------------------------------------------ settings

    @Transactional(readOnly = true)
    public SettingsDTO settings() {
        PayrollBankSettings s = settingsFor(companyId());
        return new SettingsDTO(s.getDebitAccountNumber(), s.getDebitIfsc(), s.getBankName(), s.getDefaultFormat(), s.getNarration());
    }

    @Transactional
    public SettingsDTO updateSettings(SettingsDTO r) {
        Long companyId = companyId();
        PayrollBankSettings s = settingsRepository.findByCompanyIdAndDeletedFalse(companyId).orElseGet(() -> {
            PayrollBankSettings created = new PayrollBankSettings();
            created.setCompanyId(companyId);
            return created;
        });
        String account = r.debitAccountNumber() == null ? null : r.debitAccountNumber().replaceAll("\\s", "");
        if (account != null && !account.isEmpty() && !account.matches("^[0-9A-Za-z]{6,34}$")) throw new BadRequestException("Debit account number must be 6-34 letters or digits");
        String ifsc = r.debitIfsc() == null ? null : r.debitIfsc().trim().toUpperCase(Locale.ROOT);
        if (ifsc != null && !ifsc.isEmpty() && !ifsc.matches("^[A-Z]{4}0[A-Z0-9]{6}$")) throw new BadRequestException("IFSC must look like HDFC0001234");
        String format = r.defaultFormat() == null ? "GENERIC" : r.defaultFormat().trim().toUpperCase(Locale.ROOT);
        if (!FORMATS.contains(format)) throw new BadRequestException("Format must be one of " + FORMATS);
        String narration = r.narration() == null ? null : r.narration().trim();
        if (narration != null && narration.length() > 30) throw new BadRequestException("Narration must be 30 characters or fewer");
        s.setDebitAccountNumber(blankToNull(account));
        s.setDebitIfsc(blankToNull(ifsc));
        s.setBankName(blankToNull(r.bankName() == null ? null : r.bankName().trim()));
        s.setDefaultFormat(format);
        s.setNarration(blankToNull(narration));
        s = settingsRepository.save(s);
        auditLogService.log("PAYROLL_BANK_SETTINGS", s.getId(), "UPDATED", "Salary debit account / bank file settings updated");
        return settings();
    }

    // ------------------------------------------------------------------ preview + file

    @Transactional(readOnly = true)
    public Preview preview(Long runId) {
        Loaded data = load(runId);
        PayrollBankSettings s = settingsFor(data.run().getCompany().getId());
        List<Row> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Payee p : data.payees()) {
            rows.add(new Row(p.employee().getId(), p.employee().getFullName(), p.employee().getEmployeeCode(), mask(p.account()),
                    p.ifsc(), p.amount(), mode(p.ifsc(), s.getDebitIfsc())));
            total = total.add(p.amount());
        }
        return new Preview(runId, period(data.run()).format(MONTH), data.run().getStatus(), rows.size(), total, rows, data.problems(),
                s.getDebitAccountNumber() != null, s.getDefaultFormat(), FORMATS);
    }

    @Transactional(readOnly = true)
    public PayslipService.PayslipFile file(Long runId, String requestedFormat, LocalDate valueDate) {
        Loaded data = load(runId);
        PayrollBankSettings s = settingsFor(data.run().getCompany().getId());
        String format = requestedFormat == null || requestedFormat.isBlank() ? s.getDefaultFormat() : requestedFormat.trim().toUpperCase(Locale.ROOT);
        if (!FORMATS.contains(format)) throw new BadRequestException("Format must be one of " + FORMATS);
        if (!"GENERIC".equals(format) && s.getDebitAccountNumber() == null) {
            throw new BadRequestException("Add the company's salary debit account in Bank file settings first");
        }
        if (data.payees().isEmpty()) throw new BadRequestException("No employee in this run has complete bank details");
        YearMonth period = period(data.run());
        LocalDate date = valueDate == null ? LocalDate.now(clock) : valueDate;
        String narration = (s.getNarration() == null ? "SALARY {MONTH}" : s.getNarration())
                .replace("{MONTH}", period.format(MONTH).toUpperCase(Locale.ROOT));
        narration = narration.length() > 30 ? narration.substring(0, 30) : narration;
        String content = switch (format) {
            case "HDFC" -> hdfc(data.payees(), s, narration, date);
            case "ICICI" -> icici(data.payees(), s, narration, date, period);
            default -> generic(data.payees(), s, narration);
        };
        BigDecimal total = data.payees().stream().map(Payee::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        auditLogService.log("PayrollRun", runId, "BANK_FILE",
                "Bank transfer file (" + format + ") for " + data.payees().size() + " employee(s), total " + total.toPlainString()
                        + (data.problems().isEmpty() ? "" : "; " + data.problems().size() + " left out"));
        String name = "salary-" + period + "-" + format.toLowerCase(Locale.ROOT) + ("HDFC".equals(format) ? ".txt" : ".csv");
        return new PayslipService.PayslipFile(name, content.getBytes(StandardCharsets.UTF_8));
    }

    /** Plain CSV with a header: works with any portal's "map columns" bulk upload. */
    static String generic(List<Payee> payees, PayrollBankSettings s, String narration) {
        StringBuilder out = new StringBuilder("S.No,Employee Code,Beneficiary Name,Account Number,IFSC,Bank,Amount,Payment Mode,Narration,Email\n");
        int n = 1;
        for (Payee p : payees) {
            out.append(n++).append(',').append(csv(p.employee().getEmployeeCode())).append(',').append(csv(name(p.employee().getFullName(), 60)))
                    .append(',').append(p.account()).append(',').append(p.ifsc()).append(',').append(csv(p.employee().getBankName()))
                    .append(',').append(money(p.amount())).append(',').append(mode(p.ifsc(), s.getDebitIfsc()))
                    .append(',').append(csv(narration)).append(',').append(csv(p.employee().getEmail())).append('\n');
        }
        return out.toString();
    }

    /**
     * HDFC ENet bulk upload preset (comma-separated, no header): transaction type (I = within HDFC, N = NEFT),
     * beneficiary code, account, amount, name, drawee/print location, 5 address lines, instruction and customer
     * reference, 7 payment detail lines, cheque no., transaction date, MICR, IFSC, bank name, branch, email.
     */
    static String hdfc(List<Payee> payees, PayrollBankSettings s, String narration, LocalDate date) {
        StringBuilder out = new StringBuilder();
        String day = date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        for (Payee p : payees) {
            boolean internal = p.ifsc().startsWith("HDFC");
            List<String> f = new ArrayList<>();
            f.add(internal ? "I" : "N");
            f.add(code(p.employee().getEmployeeCode()));
            f.add(p.account());
            f.add(money(p.amount()));
            f.add(name(p.employee().getFullName(), 40).toUpperCase(Locale.ROOT));
            f.add(""); f.add("");
            for (int i = 0; i < 5; i++) f.add("");
            f.add(code(p.employee().getEmployeeCode()));
            f.add(code(p.employee().getEmployeeCode()));
            f.add(clean(narration, 30));
            for (int i = 0; i < 6; i++) f.add("");
            f.add("");
            f.add(day);
            f.add("");
            f.add(internal ? "" : p.ifsc());
            f.add(internal ? "" : clean(p.employee().getBankName() == null ? "" : p.employee().getBankName(), 40));
            f.add("");
            f.add(p.employee().getEmail() == null ? "" : p.employee().getEmail());
            out.append(String.join(",", f)).append('\n');
        }
        return out.toString();
    }

    /** ICICI CIB bulk payment preset (CSV with header). FT within ICICI, otherwise NEFT. */
    static String icici(List<Payee> payees, PayrollBankSettings s, String narration, LocalDate date, YearMonth period) {
        StringBuilder out = new StringBuilder("PYMT_PROD_TYPE_CODE,PYMT_MODE,DEBIT_ACC_NO,BNF_NAME,BENE_ACC_NO,BENE_IFSC,AMOUNT,DEBIT_NARR,CREDIT_NARR,MOBILE_NUM,EMAIL_ID,REMARK,PYMT_DATE,REF_NO\n");
        String day = date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
        for (Payee p : payees) {
            boolean internal = p.ifsc().startsWith("ICIC");
            out.append("PAB_VENDOR,").append(internal ? "FT" : "NEFT").append(',').append(s.getDebitAccountNumber()).append(',')
                    .append(csv(name(p.employee().getFullName(), 40))).append(',').append(p.account()).append(',').append(p.ifsc()).append(',')
                    .append(money(p.amount())).append(',').append(csv(narration)).append(',').append(csv(narration)).append(',')
                    .append(p.employee().getPhone() == null ? "" : p.employee().getPhone().replaceAll("[^0-9]", "")).append(',')
                    .append(csv(p.employee().getEmail())).append(',').append(csv(code(p.employee().getEmployeeCode()))).append(',')
                    .append(day).append(',').append(code("SAL" + period.toString().replace("-", "") + p.employee().getEmployeeCode())).append('\n');
        }
        return out.toString();
    }

    // ------------------------------------------------------------------ helpers

    record Payee(Employee employee, String account, String ifsc, BigDecimal amount) { }

    private record Loaded(PayrollRun run, List<Payee> payees, List<Problem> problems) { }

    private Loaded load(Long runId) {
        Long companyId = companyId();
        PayrollRun run = runs.findByIdAndCompany_IdAndDeletedFalse(runId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Payroll run not found"));
        if (!PayrollRunStatus.PROCESSED.equals(run.getStatus()) && !PayrollRunStatus.PAID.equals(run.getStatus())) {
            throw new BadRequestException("Process the payroll run before creating the bank file");
        }
        List<Payee> payees = new ArrayList<>();
        List<Problem> problems = new ArrayList<>();
        for (PayrollItem i : items.findByPayrollRun_Company_IdAndPayrollRunIdAndDeletedFalseOrderByEmployee_FirstNameAsc(companyId, runId)) {
            if (!PayrollItemStatus.PROCESSED.equals(i.getStatus()) && !PayrollItemStatus.PAID.equals(i.getStatus())) continue;
            Employee e = i.getEmployee();
            BigDecimal net = i.getNetSalary() == null ? BigDecimal.ZERO : i.getNetSalary().setScale(2, RoundingMode.HALF_UP);
            if (net.signum() <= 0) continue;
            String account = e.getBankAccountNumber() == null ? "" : e.getBankAccountNumber().replaceAll("\\s", "");
            String ifsc = e.getBankIfsc() == null ? "" : e.getBankIfsc().trim().toUpperCase(Locale.ROOT);
            String issue = account.isEmpty() ? "No bank account number"
                    : !account.matches("^[0-9A-Za-z]{6,34}$") ? "Account number looks invalid"
                    : ifsc.isEmpty() ? "No IFSC"
                    : !ifsc.matches("^[A-Z]{4}0[A-Z0-9]{6}$") ? "IFSC looks invalid" : null;
            if (issue != null) {
                problems.add(new Problem(e.getId(), e.getFullName(), e.getEmployeeCode(), issue));
                continue;
            }
            payees.add(new Payee(e, account, ifsc, net));
        }
        return new Loaded(run, payees, problems);
    }

    private PayrollBankSettings settingsFor(Long companyId) {
        return settingsRepository.findByCompanyIdAndDeletedFalse(companyId).orElseGet(() -> {
            PayrollBankSettings defaults = new PayrollBankSettings();
            defaults.setCompanyId(companyId);
            return defaults;
        });
    }

    private Long companyId() {
        if (!authorizationService.hasOrganizationScope("SALARY_MANAGE")) throw new AccessDeniedException("SALARY_MANAGE requires ORGANIZATION scope.");
        Long companyId = TenantContext.getCurrentTenant();
        if (companyId == null) throw new BadRequestException("Company context is required");
        return companyId;
    }

    private static YearMonth period(PayrollRun run) {
        return YearMonth.of(run.getPeriodYear(), run.getPeriodMonth());
    }

    /** Same-bank transfers when the first four IFSC letters match the debit account's; otherwise NEFT. */
    static String mode(String ifsc, String debitIfsc) {
        return debitIfsc != null && ifsc.length() >= 4 && debitIfsc.startsWith(ifsc.substring(0, 4)) ? "IFT" : "NEFT";
    }

    static String money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String mask(String account) {
        return account.length() <= 4 ? account : "XXXX" + account.substring(account.length() - 4);
    }

    /** Bank files reject most punctuation in names; keep letters, digits, spaces and dots. */
    static String name(String value, int max) {
        String v = value == null ? "" : value.replaceAll("[^A-Za-z0-9 .]", " ").replaceAll("\\s+", " ").trim();
        return v.length() > max ? v.substring(0, max) : v;
    }

    private static String clean(String value, int max) {
        String v = value == null ? "" : value.replaceAll("[,\"\\r\\n]", " ").trim();
        return v.length() > max ? v.substring(0, max) : v;
    }

    private static String code(String value) {
        String v = value == null ? "" : value.replaceAll("[^A-Za-z0-9]", "");
        return v.length() > 20 ? v.substring(0, 20) : v;
    }

    private static String csv(String value) {
        if (value == null) return "";
        String v = value.replace("\r", " ").replace("\n", " ");
        // Also neutralise spreadsheet formulas.
        if (!v.isEmpty() && "=+-@".indexOf(v.charAt(0)) >= 0) v = "'" + v;
        return v.contains(",") || v.contains("\"") ? "\"" + v.replace("\"", "\"\"") + "\"" : v;
    }

    private static String blankToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}
