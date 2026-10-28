package com.haodaone.salary.accounting;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.salary.entity.PayrollItem;
import com.haodaone.salary.entity.PayrollRun;
import com.haodaone.salary.entity.PayrollRunStatus;
import com.haodaone.salary.repository.PayrollItemRepository;
import com.haodaone.salary.repository.PayrollRunRepository;
import com.haodaone.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

/** Payroll run -> accounting journal exports (summary CSV, Tally XML, Zoho Books CSV) and ledger mapping settings. */
@Service
public class PayrollAccountingService {

    public record LedgerSettingsDTO(String salariesLedger, String employerPfLedger, String employerEsiLedger,
                                    String reimbursementExpenseLedger, String salaryPayableLedger, String pfPayableLedger,
                                    String esiPayableLedger, String ptPayableLedger, String tdsPayableLedger,
                                    String reimbursementPayableLedger, String otherDeductionsLedger, String voucherType,
                                    String tallyCompanyName) {
        static LedgerSettingsDTO from(PayrollLedgerSettings s) {
            return new LedgerSettingsDTO(s.getSalariesLedger(), s.getEmployerPfLedger(), s.getEmployerEsiLedger(),
                    s.getReimbursementExpenseLedger(), s.getSalaryPayableLedger(), s.getPfPayableLedger(), s.getEsiPayableLedger(),
                    s.getPtPayableLedger(), s.getTdsPayableLedger(), s.getReimbursementPayableLedger(), s.getOtherDeductionsLedger(),
                    s.getVoucherType(), s.getTallyCompanyName());
        }
    }

    public record JournalPreviewDTO(Long runId, LocalDate date, String reference, String narration, int employees,
                                    List<PayrollJournal.Entry> entries, java.math.BigDecimal totalDebit,
                                    java.math.BigDecimal totalCredit) {
    }

    public record ExportFile(String filename, String contentType, byte[] content) {
    }

    private final PayrollLedgerSettingsRepository settingsRepository;
    private final PayrollRunRepository runRepository;
    private final PayrollItemRepository itemRepository;
    private final CompanyRepository companyRepository;
    private final AuditLogService auditLogService;

    public PayrollAccountingService(PayrollLedgerSettingsRepository settingsRepository, PayrollRunRepository runRepository,
                                    PayrollItemRepository itemRepository, CompanyRepository companyRepository,
                                    AuditLogService auditLogService) {
        this.settingsRepository = settingsRepository;
        this.runRepository = runRepository;
        this.itemRepository = itemRepository;
        this.companyRepository = companyRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public LedgerSettingsDTO getSettings() {
        return LedgerSettingsDTO.from(settings(requiredTenant()));
    }

    @Transactional
    public LedgerSettingsDTO updateSettings(LedgerSettingsDTO r) {
        Long companyId = requiredTenant();
        if (r == null) throw new BadRequestException("Ledger settings are required");
        PayrollLedgerSettings s = settingsRepository.findByCompany_IdAndDeletedFalse(companyId).orElseGet(() -> {
            PayrollLedgerSettings created = new PayrollLedgerSettings();
            created.setCompany(companyRepository.findById(companyId).orElseThrow(() -> new ResourceNotFoundException("Company not found")));
            return created;
        });
        s.setSalariesLedger(name(r.salariesLedger(), "Salaries ledger"));
        s.setEmployerPfLedger(name(r.employerPfLedger(), "Employer PF ledger"));
        s.setEmployerEsiLedger(name(r.employerEsiLedger(), "Employer ESI ledger"));
        s.setReimbursementExpenseLedger(name(r.reimbursementExpenseLedger(), "Reimbursement expense ledger"));
        s.setSalaryPayableLedger(name(r.salaryPayableLedger(), "Salary payable ledger"));
        s.setPfPayableLedger(name(r.pfPayableLedger(), "PF payable ledger"));
        s.setEsiPayableLedger(name(r.esiPayableLedger(), "ESI payable ledger"));
        s.setPtPayableLedger(name(r.ptPayableLedger(), "PT payable ledger"));
        s.setTdsPayableLedger(name(r.tdsPayableLedger(), "TDS payable ledger"));
        s.setReimbursementPayableLedger(name(r.reimbursementPayableLedger(), "Reimbursements payable ledger"));
        s.setOtherDeductionsLedger(name(r.otherDeductionsLedger(), "Other deductions ledger"));
        String vt = r.voucherType() == null || r.voucherType().isBlank() ? "Journal" : r.voucherType().trim();
        s.setVoucherType(vt.length() > 60 ? vt.substring(0, 60) : vt);
        String company = r.tallyCompanyName() == null || r.tallyCompanyName().isBlank() ? null : r.tallyCompanyName().trim();
        s.setTallyCompanyName(company != null && company.length() > 150 ? company.substring(0, 150) : company);
        PayrollLedgerSettings saved = settingsRepository.save(s);
        auditLogService.log("PayrollLedgerSettings", saved.getId(), "UPDATE", "Updated payroll accounting ledger mapping");
        return LedgerSettingsDTO.from(saved);
    }

    @Transactional(readOnly = true)
    public JournalPreviewDTO preview(Long runId) {
        PayrollJournal.Journal j = journal(runId);
        return new JournalPreviewDTO(runId, j.date(), j.reference(), j.narration(), j.employees(), j.entries(),
                j.totalDebit(), j.totalCredit());
    }

    /** {@code format}: journal (CSV), tally (XML) or zoho (CSV). */
    @Transactional(readOnly = true)
    public ExportFile export(Long runId, String format) {
        Long companyId = requiredTenant();
        PayrollLedgerSettings settings = settings(companyId);
        PayrollJournal.Journal j = journal(runId);
        String base = "payroll-" + j.reference().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "-");
        String f = format == null ? "journal" : format.toLowerCase(Locale.ROOT);
        ExportFile file = switch (f) {
            case "tally" -> new ExportFile(base + "-tally.xml", "application/xml",
                    AccountingExportFormats.tallyXml(j, settings.getVoucherType(), settings.getTallyCompanyName()));
            case "zoho" -> new ExportFile(base + "-zoho-journal.csv", "text/csv", AccountingExportFormats.zohoCsv(j));
            case "journal" -> new ExportFile(base + "-journal.csv", "text/csv", AccountingExportFormats.journalCsv(j));
            default -> throw new BadRequestException("Unknown export format: " + format + " (use journal, tally or zoho)");
        };
        auditLogService.log("PayrollRun", runId, "ACCOUNTING_EXPORT", "Exported payroll journal as " + f);
        return file;
    }

    private PayrollJournal.Journal journal(Long runId) {
        Long companyId = requiredTenant();
        PayrollRun run = runRepository.findByIdAndCompany_IdAndDeletedFalse(runId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Payroll run not found: " + runId));
        if (!PayrollRunStatus.PROCESSED.equals(run.getStatus()) && !PayrollRunStatus.PAID.equals(run.getStatus())) {
            throw new BadRequestException("Process the payroll run before exporting it to accounting");
        }
        List<PayrollItem> items = itemRepository.findByPayrollRun_Company_IdAndPayrollRunIdAndDeletedFalseOrderByEmployee_FirstNameAsc(companyId, runId);
        YearMonth period = YearMonth.of(run.getPeriodYear(), run.getPeriodMonth());
        LocalDate date = period.atEndOfMonth();
        String reference = "PAYROLL-" + period;
        String narration = "Salary for " + period.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + period.getYear()
                + " (payroll run #" + run.getId() + ")";
        return PayrollJournal.build(items, settings(companyId), date, reference, narration);
    }

    private PayrollLedgerSettings settings(Long companyId) {
        return settingsRepository.findByCompany_IdAndDeletedFalse(companyId).orElseGet(PayrollLedgerSettings::new);
    }

    private static String name(String value, String field) {
        if (value == null || value.isBlank()) throw new BadRequestException(field + " is required");
        String v = value.trim();
        if (v.length() > 150) throw new BadRequestException(field + " must be at most 150 characters");
        return v;
    }

    private static Long requiredTenant() {
        Long tenant = TenantContext.getCurrentTenant();
        if (tenant == null) throw new BadRequestException("Company context is required");
        return tenant;
    }
}
