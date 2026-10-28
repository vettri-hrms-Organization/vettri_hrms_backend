package com.haodaone.settlement.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.entity.EmploymentStatus;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.leave.dto.LeavePolicyDTOs;
import com.haodaone.leave.service.LeaveEncashmentService;
import com.haodaone.notifications.service.NotificationService;
import com.haodaone.salary.adjustment.PayrollAdjustment;
import com.haodaone.salary.adjustment.PayrollAdjustmentProvider;
import com.haodaone.salary.entity.*;
import com.haodaone.salary.repository.EmployeeTaxDeclarationRepository;
import com.haodaone.salary.repository.PayrollItemRepository;
import com.haodaone.salary.repository.SalaryStructureRepository;
import com.haodaone.salary.service.PayrollProration;
import com.haodaone.salary.service.StatutoryPayrollEngine;
import com.haodaone.salary.statutory.IncomeTaxCalculator;
import com.haodaone.salary.statutory.ProfessionalTaxSlabs;
import com.haodaone.salary.statutory.StatutoryMath;
import com.haodaone.security.AuthorizationService;
import com.haodaone.settlement.dto.SettlementDTOs.*;
import com.haodaone.settlement.entity.FinalSettlement;
import com.haodaone.settlement.entity.FinalSettlementLine;
import com.haodaone.settlement.entity.SettlementSettings;
import com.haodaone.settlement.repository.FinalSettlementRepository;
import com.haodaone.settlement.repository.SettlementSettingsRepository;
import com.haodaone.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.*;

/**
 * Full & Final settlement for exiting employees (status Resigned/Terminated with a last working date).
 *
 * Statement (recomputed on every DRAFT save; manual lines are kept):
 *  Earnings  - salary for unpaid months up to the last working date (months after the latest
 *              PROCESSED/PAID payroll item, max 6), prorated with PayrollProration and cut at the LWD;
 *              leave encashment of remaining encashable leave (LeaveEncashmentService final-settlement preview);
 *              gratuity (GratuityCalculator); pending approved overtime / encashment / reimbursements from every
 *              PayrollAdjustmentProvider; manual bonus / other earnings.
 *  Deductions - PF / ESI / PT on the prorated salary (statutory engine settings for auto structures,
 *              structure figures otherwise), notice shortfall recovery (days x monthly BASIC or GROSS / 30),
 *              manual advances / loans / asset recovery, and TDS.
 *  TDS       - annual tax (finalized payroll this FY + taxable settlement income, declaration/regime honoured)
 *              minus TDS already deducted, never negative. Exempt: gratuity up to Rs 20 lakh (s.10(10)),
 *              leave encashment up to Rs 25 lakh (s.10(10AA)), reimbursements.
 *
 * Workflow: DRAFT -> REVIEWED -> APPROVED -> PAID (CANCELLED from DRAFT/REVIEWED). On APPROVE the
 * provider-sourced lines are marked paid through PayrollAdjustmentProvider.markSettled with a NEGATIVE
 * payroll item id (-settlementId) so normal payroll never pays them again, and the employee is excluded
 * from later payroll runs (SettledEmployeePayrollExclusion).
 */
@Service
public class FinalSettlementService {

    private static final Logger log = LoggerFactory.getLogger(FinalSettlementService.class);
    static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    static final int MAX_SALARY_MONTHS = 6;

    private final FinalSettlementRepository settlementRepository;
    private final SettlementSettingsRepository settingsRepository;
    private final EmployeeRepository employeeRepository;
    private final CompanyRepository companyRepository;
    private final SalaryStructureRepository salaryStructureRepository;
    private final PayrollItemRepository payrollItemRepository;
    private final EmployeeTaxDeclarationRepository declarationRepository;
    private final AuditLogService auditLogService;
    private final AuthorizationService authorizationService;

    private List<PayrollAdjustmentProvider> adjustmentProviders = List.of();
    private LeaveEncashmentService leaveEncashmentService;
    private StatutoryPayrollEngine statutoryPayrollEngine;
    private NotificationService notificationService;

    public FinalSettlementService(FinalSettlementRepository settlementRepository, SettlementSettingsRepository settingsRepository,
                                  EmployeeRepository employeeRepository, CompanyRepository companyRepository,
                                  SalaryStructureRepository salaryStructureRepository, PayrollItemRepository payrollItemRepository,
                                  EmployeeTaxDeclarationRepository declarationRepository, AuditLogService auditLogService,
                                  AuthorizationService authorizationService) {
        this.settlementRepository = settlementRepository;
        this.settingsRepository = settingsRepository;
        this.employeeRepository = employeeRepository;
        this.companyRepository = companyRepository;
        this.salaryStructureRepository = salaryStructureRepository;
        this.payrollItemRepository = payrollItemRepository;
        this.declarationRepository = declarationRepository;
        this.auditLogService = auditLogService;
        this.authorizationService = authorizationService;
    }

    @Autowired(required = false)
    public void setAdjustmentProviders(List<PayrollAdjustmentProvider> adjustmentProviders) {
        this.adjustmentProviders = adjustmentProviders != null ? adjustmentProviders : List.of();
    }

    @Autowired(required = false)
    public void setLeaveEncashmentService(LeaveEncashmentService leaveEncashmentService) {
        this.leaveEncashmentService = leaveEncashmentService;
    }

    @Autowired(required = false)
    public void setStatutoryPayrollEngine(StatutoryPayrollEngine statutoryPayrollEngine) {
        this.statutoryPayrollEngine = statutoryPayrollEngine;
    }

    @Autowired(required = false)
    public void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // ================================================================== settings

    @Transactional(readOnly = true)
    public SettingsDTO getSettings() {
        return SettingsDTO.from(settingsFor(requiredTenant()));
    }

    @Transactional
    public SettingsDTO updateSettings(SettingsDTO request) {
        Long companyId = requiredTenant();
        if (request == null) throw new BadRequestException("Settings are required");
        SettlementSettings s = settingsRepository.findByCompany_IdAndDeletedFalse(companyId).orElseGet(() -> {
            SettlementSettings created = new SettlementSettings();
            created.setCompany(company(companyId));
            return created;
        });
        if (request.gratuityCeiling() == null || request.gratuityCeiling().signum() <= 0) {
            throw new BadRequestException("Gratuity ceiling must be a positive amount");
        }
        int partial = request.gratuityPartialYearDays();
        if (partial != 0 && partial != 190 && partial != 240) {
            throw new BadRequestException("Partial-year rule must be 0 (off), 190 (5-day week) or 240 (6-day week) days");
        }
        if (request.defaultNoticePeriodDays() < 0 || request.defaultNoticePeriodDays() > 365) {
            throw new BadRequestException("Notice period must be between 0 and 365 days");
        }
        s.setGratuityApplicable(request.gratuityApplicable());
        s.setGratuityCoveredUnderAct(request.gratuityCoveredUnderAct());
        s.setGratuityCeiling(request.gratuityCeiling().setScale(2, RoundingMode.HALF_UP));
        s.setGratuityPartialYearDays(partial);
        s.setDefaultNoticePeriodDays(request.defaultNoticePeriodDays());
        s.setNoticeRecoveryBasis(basis(request.noticeRecoveryBasis()));
        SettlementSettings saved = settingsRepository.save(s);
        auditLogService.log("SettlementSettings", saved.getId(), "UPDATE",
                "Gratuity applicable=" + saved.isGratuityApplicable() + ", covered=" + saved.isGratuityCoveredUnderAct()
                        + ", ceiling=" + saved.getGratuityCeiling() + ", partial-year days=" + saved.getGratuityPartialYearDays()
                        + ", notice=" + saved.getDefaultNoticePeriodDays() + "d on " + saved.getNoticeRecoveryBasis());
        return SettingsDTO.from(saved);
    }

    SettlementSettings settingsFor(Long companyId) {
        return settingsRepository.findByCompany_IdAndDeletedFalse(companyId).orElseGet(SettlementSettings::new);
    }

    static GratuityCalculator.Rules rules(SettlementSettings s) {
        return new GratuityCalculator.Rules(s.isGratuityApplicable(), s.isGratuityCoveredUnderAct(),
                s.getGratuityCeiling(), s.getGratuityPartialYearDays());
    }

    // ================================================================== gratuity estimate

    /** Estimated gratuity as of the last working date (or today) on the current salary structure. */
    @Transactional(readOnly = true)
    public GratuityEstimateDTO gratuityEstimate(Long employeeId) {
        Long companyId = requiredTenant();
        Employee employee = employee(employeeId, companyId);
        SettlementSettings settings = settingsFor(companyId);
        LocalDate today = LocalDate.now(IST);
        LocalDate asOf = employee.getLastWorkingDate() != null ? employee.getLastWorkingDate() : today;
        Optional<SalaryStructure> structure = salaryStructureRepository
                .findByEmployee_Company_IdAndEmployeeIdAndActiveTrueAndDeletedFalse(companyId, employeeId);
        BigDecimal wage = structure.map(s -> s.getComponents().getBasicSalary()).orElse(BigDecimal.ZERO);
        GratuityCalculator.Result r = GratuityCalculator.compute(employee.getDateOfJoining(), asOf, wage, rules(settings), false);
        return new GratuityEstimateDTO(employee.getId(), employee.getFullName(), employee.getDateOfJoining(), asOf,
                settings.isGratuityApplicable(), settings.isGratuityCoveredUnderAct(), r.eligible(),
                r.service().years(), r.service().months(), r.service().days(), r.yearsCounted(), wage, r.amount(), r.capped(),
                structure.isPresent(),
                structure.isPresent() ? r.explanation() : "No active salary structure - add one to estimate gratuity.");
    }

    // ================================================================== queries

    @Transactional(readOnly = true)
    public List<SettlementDTO> list() {
        return settlementRepository.findAllByCompany_IdAndDeletedFalseOrderByIdDesc(requiredTenant()).stream()
                .map(SettlementDTO::from).toList();
    }

    @Transactional(readOnly = true)
    public SettlementDTO get(Long id) {
        return SettlementDTO.from(find(id, requiredTenant()));
    }

    /** The signed-in employee's settlements once APPROVED or PAID. */
    @Transactional(readOnly = true)
    public List<SettlementDTO> mine() {
        Employee me = authorizationService.resolveCurrentEmployee();
        if (me == null) return List.of();
        return settlementRepository.findAllByCompany_IdAndEmployee_IdAndDeletedFalseOrderByIdDesc(requiredTenant(), me.getId()).stream()
                .filter(s -> FinalSettlement.APPROVED.equals(s.getStatus()) || FinalSettlement.PAID.equals(s.getStatus()))
                .map(SettlementDTO::from).toList();
    }

    /** Resigned/Terminated employees with a last working date and no active settlement. */
    @Transactional(readOnly = true)
    public List<EligibleEmployeeDTO> eligibleEmployees() {
        Long companyId = requiredTenant();
        List<EligibleEmployeeDTO> result = new ArrayList<>();
        for (Employee e : employeeRepository.findAllByCompany_IdAndDeletedFalseOrderByFirstNameAsc(companyId)) {
            if (!isExited(e) || e.getLastWorkingDate() == null) continue;
            if (settlementRepository.existsByCompany_IdAndEmployee_IdAndStatusInAndDeletedFalse(companyId, e.getId(), FinalSettlement.ACTIVE)) continue;
            result.add(new EligibleEmployeeDTO(e.getId(), e.getFullName(), e.getEmployeeCode(), e.getStatus(),
                    e.getDateOfJoining(), e.getLastWorkingDate()));
        }
        return result;
    }

    public record StatementFile(String filename, byte[] content) {
    }

    /** The statement PDF, rendered inside the transaction. HR (hrAccess) or the employee once APPROVED/PAID. */
    @Transactional(readOnly = true)
    public StatementFile statementPdf(Long id, boolean hrAccess, SettlementPdfService pdfService) {
        FinalSettlement s = loadForStatement(id, hrAccess);
        String code = s.getEmployee().getEmployeeCode() == null ? String.valueOf(s.getEmployee().getId())
                : s.getEmployee().getEmployeeCode().replaceAll("[^A-Za-z0-9_-]", "");
        return new StatementFile("full-and-final-" + code + ".pdf", pdfService.generate(s));
    }

    /** For the PDF endpoint: HR, or the employee once APPROVED/PAID. */
    @Transactional(readOnly = true)
    public FinalSettlement loadForStatement(Long id, boolean hrAccess) {
        FinalSettlement s = find(id, requiredTenant());
        if (!hrAccess) {
            Employee me = authorizationService.resolveCurrentEmployee();
            boolean own = me != null && me.getId().equals(s.getEmployee().getId());
            boolean released = FinalSettlement.APPROVED.equals(s.getStatus()) || FinalSettlement.PAID.equals(s.getStatus());
            if (!own || !released) {
                throw new org.springframework.security.access.AccessDeniedException("You can't download this settlement statement");
            }
        }
        // Touch lazy data used by the PDF while the session is open.
        s.getLines().size();
        s.getEmployee().getFullName();
        if (s.getEmployee().getDepartment() != null) s.getEmployee().getDepartment().getName();
        if (s.getEmployee().getDesignation() != null) s.getEmployee().getDesignation().getTitle();
        if (s.getCompany() != null) s.getCompany().getName();
        return s;
    }

    // ================================================================== draft lifecycle

    @Transactional
    public SettlementDTO create(SettlementRequest request) {
        Long companyId = requiredTenant();
        if (request == null || request.employeeId() == null) throw new BadRequestException("Choose the employee to settle");
        Employee employee = employee(request.employeeId(), companyId);
        if (!isExited(employee)) {
            throw new BadRequestException(employee.getFullName() + " is " + employee.getStatus()
                    + ". Mark the employee Resigned or Terminated (with a last working date) first.");
        }
        if (settlementRepository.existsByCompany_IdAndEmployee_IdAndStatusInAndDeletedFalse(companyId, employee.getId(), FinalSettlement.ACTIVE)) {
            throw new BadRequestException("A settlement already exists for " + employee.getFullName() + ". Cancel it to start again.");
        }
        LocalDate lwd = request.lastWorkingDate() != null ? request.lastWorkingDate() : employee.getLastWorkingDate();
        if (lwd == null) throw new BadRequestException("Set the employee's last working date first");
        SettlementSettings settings = settingsFor(companyId);

        FinalSettlement s = new FinalSettlement();
        s.setCompany(company(companyId));
        s.setEmployee(employee);
        s.setStatus(FinalSettlement.DRAFT);
        s.setLastWorkingDate(lwd);
        s.setNoticePeriodDays(settings.getDefaultNoticePeriodDays());
        s.setNoticeRecoveryBasis(settings.getNoticeRecoveryBasis());
        Integer override = applyInputs(s, request);
        recompute(s, settings, override);
        FinalSettlement saved = settlementRepository.save(s);
        auditLogService.log("FinalSettlement", saved.getId(), "CREATE",
                "Drafted F&F for " + employee.getFullName() + " (LWD " + lwd + "), net " + saved.getNetPayable());
        return SettlementDTO.from(saved);
    }

    @Transactional
    public SettlementDTO update(Long id, SettlementRequest request) {
        Long companyId = requiredTenant();
        FinalSettlement s = find(id, companyId);
        requireStatus(s, FinalSettlement.DRAFT, "Only a DRAFT settlement can be edited. Reopen it first.");
        Integer override = applyInputs(s, request);
        recompute(s, settingsFor(companyId), override);
        FinalSettlement saved = settlementRepository.save(s);
        auditLogService.log("FinalSettlement", saved.getId(), "UPDATE", "Recalculated F&F, net " + saved.getNetPayable());
        return SettlementDTO.from(saved);
    }

    @Transactional
    public SettlementDTO review(Long id) {
        FinalSettlement s = find(id, requiredTenant());
        requireStatus(s, FinalSettlement.DRAFT, "Only a DRAFT settlement can be marked reviewed");
        s.setStatus(FinalSettlement.REVIEWED);
        s.setReviewedBy(currentUsername());
        s.setReviewedAt(LocalDateTime.now(IST));
        FinalSettlement saved = settlementRepository.save(s);
        auditLogService.log("FinalSettlement", saved.getId(), "REVIEW", "Reviewed F&F of " + saved.getEmployee().getFullName());
        return SettlementDTO.from(saved);
    }

    @Transactional
    public SettlementDTO reopen(Long id) {
        FinalSettlement s = find(id, requiredTenant());
        requireStatus(s, FinalSettlement.REVIEWED, "Only a REVIEWED settlement can be sent back to draft");
        s.setStatus(FinalSettlement.DRAFT);
        s.setReviewedBy(null);
        s.setReviewedAt(null);
        FinalSettlement saved = settlementRepository.save(s);
        auditLogService.log("FinalSettlement", saved.getId(), "REOPEN", "Sent F&F of " + saved.getEmployee().getFullName() + " back to draft");
        return SettlementDTO.from(saved);
    }

    /** REVIEWED -> APPROVED: verifies provider lines are still unpaid, then settles them against this settlement. */
    @Transactional
    public SettlementDTO approve(Long id) {
        Long companyId = requiredTenant();
        FinalSettlement s = find(id, companyId);
        requireStatus(s, FinalSettlement.REVIEWED, "Review the settlement before approving it");
        Long employeeId = s.getEmployee().getId();
        boolean inDraftRun = payrollItemRepository.findByEmployee_Company_IdAndEmployeeIdAndDeletedFalseOrderByCreatedAtDesc(companyId, employeeId)
                .stream().anyMatch(i -> PayrollItemStatus.PENDING.equals(i.getStatus()) && i.getPayrollRun() != null
                        && !i.getPayrollRun().isDeleted() && PayrollRunStatus.DRAFT.equals(i.getPayrollRun().getStatus()));
        if (inDraftRun) {
            throw new BadRequestException(s.getEmployee().getFullName() + " is still in a draft payroll run. Hold them in that run (or cancel it) before approving the settlement.");
        }
        settleProviderLines(s, companyId);
        s.setStatus(FinalSettlement.APPROVED);
        s.setApprovedBy(currentUsername());
        s.setApprovedAt(LocalDateTime.now(IST));
        FinalSettlement saved = settlementRepository.save(s);
        auditLogService.log("FinalSettlement", saved.getId(), "APPROVE",
                "Approved F&F of " + saved.getEmployee().getFullName() + ", net payable " + saved.getNetPayable());
        notifyEmployee(saved, "FNF_APPROVED", "Full & Final settlement approved",
                "Your full and final settlement (net " + saved.getNetPayable().toPlainString() + ") has been approved. You can download the statement.");
        return SettlementDTO.from(saved);
    }

    @Transactional
    public SettlementDTO markPaid(Long id, MarkPaidRequest request) {
        FinalSettlement s = find(id, requiredTenant());
        requireStatus(s, FinalSettlement.APPROVED, "Only an APPROVED settlement can be marked paid");
        String reference = request == null || request.paymentReference() == null ? "" : request.paymentReference().trim();
        if (reference.isEmpty()) throw new BadRequestException("Enter the payment reference (UTR / cheque number)");
        if (reference.length() > 120) reference = reference.substring(0, 120);
        s.setPaymentReference(reference);
        s.setPaymentDate(request.paymentDate() != null ? request.paymentDate() : LocalDate.now(IST));
        s.setPaidAt(LocalDateTime.now(IST));
        s.setStatus(FinalSettlement.PAID);
        FinalSettlement saved = settlementRepository.save(s);
        auditLogService.log("FinalSettlement", saved.getId(), "MARK_PAID",
                "Paid F&F of " + saved.getEmployee().getFullName() + " on " + saved.getPaymentDate() + " ref " + reference);
        notifyEmployee(saved, "FNF_PAID", "Full & Final settlement paid",
                "Your full and final settlement of " + saved.getNetPayable().toPlainString() + " was paid on " + saved.getPaymentDate()
                        + " (ref " + reference + ").");
        return SettlementDTO.from(saved);
    }

    @Transactional
    public void cancel(Long id) {
        FinalSettlement s = find(id, requiredTenant());
        if (!FinalSettlement.DRAFT.equals(s.getStatus()) && !FinalSettlement.REVIEWED.equals(s.getStatus())) {
            throw new BadRequestException("Only a DRAFT or REVIEWED settlement can be cancelled");
        }
        s.setStatus(FinalSettlement.CANCELLED);
        settlementRepository.save(s);
        auditLogService.log("FinalSettlement", s.getId(), "CANCEL", "Cancelled F&F of " + s.getEmployee().getFullName());
    }

    // ================================================================== computation

    /** Copies request inputs onto the draft; returns the notice shortfall override (may be null). */
    private Integer applyInputs(FinalSettlement s, SettlementRequest r) {
        if (r == null) return null;
        if (r.lastWorkingDate() != null) s.setLastWorkingDate(r.lastWorkingDate());
        if (r.resignationDate() != null) s.setResignationDate(r.resignationDate());
        if (s.getResignationDate() != null && s.getResignationDate().isAfter(s.getLastWorkingDate())) {
            throw new BadRequestException("Resignation date can't be after the last working date");
        }
        if (r.noticePeriodDays() != null) {
            if (r.noticePeriodDays() < 0 || r.noticePeriodDays() > 365) throw new BadRequestException("Notice period must be 0-365 days");
            s.setNoticePeriodDays(r.noticePeriodDays());
        }
        if (r.noticeWaived() != null) s.setNoticeWaived(r.noticeWaived());
        if (r.noticeRecoveryBasis() != null) s.setNoticeRecoveryBasis(basis(r.noticeRecoveryBasis()));
        if (r.deathOrDisablement() != null) s.setDeathOrDisablement(r.deathOrDisablement());
        if (r.notes() != null) s.setNotes(r.notes().length() > 1000 ? r.notes().substring(0, 1000) : r.notes());
        if (r.manualLines() != null) {
            s.getLines().removeIf(FinalSettlementLine::isManual);
            int order = 500;
            for (ManualLine m : r.manualLines()) {
                if (m == null || m.amount() == null || m.amount().signum() == 0) continue;
                if (m.amount().signum() < 0) throw new BadRequestException("Manual line amounts must be positive");
                String kind = "DEDUCTION".equalsIgnoreCase(m.kind()) ? FinalSettlementLine.DEDUCTION : FinalSettlementLine.EARNING;
                String code = m.code() == null || m.code().isBlank()
                        ? (FinalSettlementLine.EARNING.equals(kind) ? "OTHER_EARNING" : "OTHER_DEDUCTION")
                        : m.code().trim().toUpperCase(Locale.ROOT);
                String label = m.label() == null || m.label().isBlank() ? code.replace('_', ' ') : m.label().trim();
                FinalSettlementLine line = line(kind, code.length() > 50 ? code.substring(0, 50) : code,
                        label.length() > 200 ? label.substring(0, 200) : label, m.amount(), order++);
                boolean taxable = FinalSettlementLine.EARNING.equals(kind) && (m.taxable() == null || m.taxable());
                line.setTaxableAmount(taxable ? line.getAmount() : BigDecimal.ZERO);
                line.setManual(true);
                s.addLine(line);
            }
        }
        return r.noticeShortfallDaysOverride();
    }

    /** Rebuilds every computed line and the totals. Manual lines are kept as they are. */
    void recompute(FinalSettlement s, SettlementSettings settings, Integer noticeOverride) {
        Long companyId = s.getCompany().getId();
        Employee employee = s.getEmployee();
        LocalDate lwd = s.getLastWorkingDate();
        s.getLines().removeIf(l -> !l.isManual());
        List<String> notes = new ArrayList<>();

        Optional<SalaryStructure> structureOpt = salaryStructureRepository
                .findByEmployee_Company_IdAndEmployeeIdAndActiveTrueAndDeletedFalse(companyId, employee.getId());
        SalaryComponents full = structureOpt.map(SalaryStructure::getComponents).orElse(null);
        if (full == null) notes.add("No active salary structure - salary, gratuity and notice recovery are zero.");
        StatutoryPayrollEngine.Context ctx = statutoryPayrollEngine != null && structureOpt.map(SalaryStructure::isAutoStatutory).orElse(false)
                ? statutoryPayrollEngine.contextFor(companyId) : null;

        // ---- service + gratuity
        BigDecimal basic = full == null ? BigDecimal.ZERO : nz(full.getBasicSalary());
        GratuityCalculator.Result g = GratuityCalculator.compute(employee.getDateOfJoining(), lwd, basic, rules(settings), s.isDeathOrDisablement());
        s.setServiceYears(g.service().years());
        s.setServiceMonths(g.service().months());
        s.setServiceDays(g.service().days());
        s.setGratuityEligible(g.eligible());
        s.setGratuityYears(g.yearsCounted());
        s.setGratuityWage(basic.setScale(2, RoundingMode.HALF_UP));
        notes.add("Gratuity: " + g.explanation());

        int order = 0;
        BigDecimal settlementPt = BigDecimal.ZERO, settlementPf = BigDecimal.ZERO;
        // ---- salary for unpaid months
        if (full != null) {
            YearMonth end = YearMonth.from(lwd);
            YearMonth start = firstUnpaidMonth(companyId, employee);
            if (start.isBefore(end.minusMonths(MAX_SALARY_MONTHS - 1L))) {
                notes.add("Salary limited to the last " + MAX_SALARY_MONTHS + " months before the last working date; settle older months separately.");
                start = end.minusMonths(MAX_SALARY_MONTHS - 1L);
            }
            for (YearMonth m = start; !m.isAfter(end); m = m.plusMonths(1)) {
                LocalDate to = m.atEndOfMonth().isBefore(lwd) ? m.atEndOfMonth() : lwd;
                List<PayrollProration.LeaveSpan> leaves = payrollItemRepository.findApprovedUnpaidLeavesOverlapping(companyId, m.atDay(1), to)
                        .stream().filter(lr -> lr.getEmployee() != null && employee.getId().equals(lr.getEmployee().getId()))
                        .map(lr -> new PayrollProration.LeaveSpan(lr.getStartDate(), lr.getEndDate(), lr.getDays())).toList();
                PayrollProration.Result days = PayrollProration.compute(m, employee.getDateOfJoining(), leaves);
                BigDecimal paid = days.paidDays().subtract(BigDecimal.valueOf(SettlementMath.daysAfterExit(m, lwd))).max(BigDecimal.ZERO);
                if (paid.signum() <= 0) continue;
                SalaryComponents c = PayrollProration.prorate(full, paid, days.daysInPeriod());
                BigDecimal gross = c.grossSalary();
                String monthLabel = m.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + m.getYear();
                FinalSettlementLine salary = line(FinalSettlementLine.EARNING, "SALARY",
                        "Salary " + monthLabel + " (" + paid.stripTrailingZeros().toPlainString() + " of " + days.daysInPeriod() + " days)", gross, order++);
                salary.setTaxableAmount(salary.getAmount());
                s.addLine(salary);

                BigDecimal pf, esi, pt, other = BigDecimal.ZERO;
                if (ctx != null) {
                    var st = ctx.settings();
                    pf = st.isPfEnabled() ? StatutoryMath.pf(c.getBasicSalary(), st).employeeShare() : BigDecimal.ZERO;
                    esi = st.isEsiEnabled() && StatutoryMath.esiCovered(full.grossSalary(), st)
                            ? StatutoryMath.esi(true, gross, st).employeeShare() : BigDecimal.ZERO;
                    pt = !st.isPtEnabled() ? BigDecimal.ZERO
                            : st.isPtFixedOverride() ? nz(st.getPtFixedAmount())
                            : ProfessionalTaxSlabs.monthly(ctx.ptState(), gross, m.getMonthValue(), employee.getGender());
                } else {
                    pf = nz(c.getPf());
                    esi = nz(c.getEsi());
                    pt = nz(c.getProfessionalTax());
                    other = nz(c.getOtherDeductions());
                }
                addDeduction(s, "PF", "Provident fund - " + monthLabel, pf, 100 + order);
                addDeduction(s, "ESI", "ESI - " + monthLabel, esi, 100 + order);
                addDeduction(s, "PT", "Professional tax - " + monthLabel, pt, 100 + order);
                addDeduction(s, "OTHER_DEDUCTION", "Other deductions - " + monthLabel, other, 100 + order);
                settlementPf = settlementPf.add(pf);
                settlementPt = settlementPt.add(pt);
            }
        }

        // ---- leave encashment at exit
        BigDecimal encashExemptLeft = SettlementMath.LEAVE_ENCASHMENT_EXEMPTION;
        if (leaveEncashmentService != null) {
            try {
                LeavePolicyDTOs.FinalSettlementPreview preview = leaveEncashmentService.finalSettlementPreview(employee.getId());
                for (LeavePolicyDTOs.FinalSettlementLine l : preview.lines()) {
                    if (l.amount() == null || l.amount().signum() <= 0) continue;
                    FinalSettlementLine line = line(FinalSettlementLine.EARNING, "LEAVE_ENCASHMENT",
                            "Leave encashment - " + l.leaveTypeName() + " (" + l.encashableDays() + " d x " + l.perDayRate().toPlainString() + ")",
                            l.amount(), 40 + order++);
                    BigDecimal taxable = SettlementMath.taxableLeaveEncashment(line.getAmount(), encashExemptLeft);
                    encashExemptLeft = encashExemptLeft.subtract(line.getAmount().subtract(taxable)).max(BigDecimal.ZERO);
                    line.setTaxableAmount(taxable);
                    s.addLine(line);
                }
            } catch (RuntimeException ex) {
                log.warn("Leave encashment preview failed for employee {}: {}", employee.getId(), ex.getMessage());
                notes.add("Leave encashment could not be computed: " + ex.getMessage());
            }
        }

        // ---- gratuity
        if (g.eligible() && g.amount().signum() > 0) {
            FinalSettlementLine line = line(FinalSettlementLine.EARNING, "GRATUITY", "Gratuity (" + g.yearsCounted() + " years)", g.amount(), 60);
            line.setTaxableAmount(SettlementMath.taxableGratuity(line.getAmount()));
            s.addLine(line);
        }

        // ---- pending approved overtime / encashment / reimbursements
        YearMonth period = YearMonth.from(lwd.isAfter(LocalDate.now(IST)) ? lwd : LocalDate.now(IST));
        int providerOrder = 70;
        for (PayrollAdjustmentProvider provider : adjustmentProviders) {
            List<PayrollAdjustment> adjustments = provider.adjustmentsFor(companyId, employee.getId(), period);
            if (adjustments == null) continue;
            for (PayrollAdjustment a : adjustments) {
                if (a == null || a.amount() == null || a.amount().signum() <= 0) continue;
                FinalSettlementLine line = line(FinalSettlementLine.EARNING, a.code() == null ? "ADJUSTMENT" : a.code(),
                        a.label() == null ? "Adjustment" : a.label(), a.amount(), providerOrder++);
                line.setTaxableAmount(a.taxable() ? line.getAmount() : BigDecimal.ZERO);
                line.setSourceType(a.sourceType());
                line.setSourceId(a.sourceId());
                s.addLine(line);
            }
        }

        // ---- notice shortfall recovery
        SettlementMath.Notice notice = SettlementMath.notice(s.getResignationDate(), lwd, s.getNoticePeriodDays(), s.isNoticeWaived(), noticeOverride);
        s.setNoticeServedDays(notice.servedDays());
        s.setNoticeShortfallDays(notice.shortfallDays());
        if (full != null && notice.shortfallDays() > 0) {
            boolean gross = SettlementSettings.BASIS_GROSS.equals(s.getNoticeRecoveryBasis());
            BigDecimal monthly = gross ? full.grossSalary() : nz(full.getBasicSalary());
            addDeduction(s, "NOTICE_RECOVERY", "Notice shortfall recovery (" + notice.shortfallDays() + " d x "
                    + (gross ? "gross" : "basic") + " / 30)", SettlementMath.noticeRecovery(notice.shortfallDays(), monthly), 200);
        }

        // ---- TDS on the settlement
        SettlementMath.Totals before = SettlementMath.totals(s.getLines());
        BigDecimal tds = computeTds(companyId, employee, lwd, before.taxable(), settlementPt, settlementPf, ctx, notes);
        addDeduction(s, "TDS", "Income tax (TDS) on settlement", tds, 300);

        SettlementMath.Totals totals = SettlementMath.totals(s.getLines());
        s.setTotalEarnings(totals.earnings());
        s.setTotalDeductions(totals.deductions());
        s.setTaxableAmount(totals.taxable());
        s.setTdsAmount(tds.setScale(2, RoundingMode.HALF_UP));
        s.setNetPayable(totals.net());
        if (totals.net().signum() < 0) notes.add("Deductions exceed earnings: " + totals.net().negate().toPlainString() + " is recoverable from the employee.");
        String joined = String.join(" ", notes);
        s.setComputationNotes(joined.length() > 2000 ? joined.substring(0, 2000) : joined);
    }

    /** Month after the latest PROCESSED/PAID payroll line, or the joining month when never paid. */
    private YearMonth firstUnpaidMonth(Long companyId, Employee employee) {
        YearMonth lastPaid = null;
        for (PayrollItem item : payrollItemRepository.findByEmployee_Company_IdAndEmployeeIdAndDeletedFalseOrderByCreatedAtDesc(companyId, employee.getId())) {
            PayrollRun run = item.getPayrollRun();
            if (run == null || run.isDeleted() || PayrollRunStatus.CANCELLED.equals(run.getStatus())) continue;
            if (!PayrollItemStatus.PROCESSED.equals(item.getStatus()) && !PayrollItemStatus.PAID.equals(item.getStatus())) continue;
            YearMonth m = YearMonth.of(run.getPeriodYear(), run.getPeriodMonth());
            if (lastPaid == null || m.isAfter(lastPaid)) lastPaid = m;
        }
        if (lastPaid != null) return lastPaid.plusMonths(1);
        return employee.getDateOfJoining() != null ? YearMonth.from(employee.getDateOfJoining()) : YearMonth.now(IST);
    }

    /**
     * Annual tax with the settlement's taxable income added on top of this FY's finalized payroll,
     * minus TDS already deducted. Old-regime deductions from the declaration are carried over.
     */
    private BigDecimal computeTds(Long companyId, Employee employee, LocalDate lwd, BigDecimal settlementTaxable,
                                  BigDecimal settlementPt, BigDecimal settlementPf, StatutoryPayrollEngine.Context ctx, List<String> notes) {
        if (statutoryPayrollEngine == null || settlementTaxable.signum() <= 0) return BigDecimal.ZERO;
        StatutoryPayrollEngine.Context c = ctx != null ? ctx : statutoryPayrollEngine.contextFor(companyId);
        if (c != null && c.settings() != null && !c.settings().isTdsEnabled()) {
            notes.add("TDS is switched off in payroll settings.");
            return BigDecimal.ZERO;
        }
        try {
            int fy = StatutoryPayrollEngine.financialYearOf(YearMonth.from(lwd));
            EmployeeTaxDeclaration declaration = declarationRepository
                    .findByCompany_IdAndEmployee_IdAndFinancialYearAndDeletedFalse(companyId, employee.getId(), fy).orElse(null);
            StatutoryPayrollEngine.TaxProjection base = statutoryPayrollEngine.project(companyId, employee, null, fy,
                    YearMonth.from(lwd), declaration, null);
            IncomeTaxCalculator.TaxResult r = base.result();
            IncomeTaxCalculator.TaxInput with = new IncomeTaxCalculator.TaxInput(fy, r.regime(),
                    nz(r.grossSalary()).add(settlementTaxable), null, null, null, false,
                    nz(r.professionalTax()).add(settlementPt), nz(r.section80C()).add(settlementPf), r.section80D(),
                    r.homeLoanInterest(), r.nps80ccd1b(), nz(r.otherDeductions()).add(nz(r.hraExemption())));
            BigDecimal annual = IncomeTaxCalculator.compute(with).totalTax();
            BigDecimal due = annual.subtract(nz(base.tdsDeductedSoFar())).max(BigDecimal.ZERO).setScale(0, RoundingMode.HALF_UP);
            notes.add("TDS: FY " + fy + "-" + String.format("%02d", (fy + 1) % 100) + " " + r.regime() + " regime, annual tax "
                    + annual.toPlainString() + " less TDS already deducted " + nz(base.tdsDeductedSoFar()).toPlainString() + ".");
            return due.setScale(2, RoundingMode.UNNECESSARY);
        } catch (RuntimeException ex) {
            log.warn("TDS computation failed for employee {}: {}", employee.getId(), ex.getMessage());
            notes.add("TDS could not be computed automatically - add it as a manual deduction if needed.");
            return BigDecimal.ZERO;
        }
    }

    /** Marks provider-sourced lines paid (payroll item id = -settlementId). Fails when a source was paid elsewhere. */
    private void settleProviderLines(FinalSettlement s, Long companyId) {
        List<FinalSettlementLine> sourced = s.getLines().stream()
                .filter(l -> !l.isDeleted() && l.getSourceType() != null && l.getSourceId() != null).toList();
        if (sourced.isEmpty()) return;
        LocalDate lwd = s.getLastWorkingDate();
        YearMonth period = YearMonth.from(lwd.isAfter(LocalDate.now(IST)) ? lwd : LocalDate.now(IST));
        Long marker = -s.getId();
        for (PayrollAdjustmentProvider provider : adjustmentProviders) {
            Set<String> types = provider.sourceTypes() == null ? Set.of() : provider.sourceTypes();
            List<FinalSettlementLine> mine = sourced.stream().filter(l -> types.contains(l.getSourceType())).toList();
            if (mine.isEmpty()) continue;
            Set<String> stillPending = new HashSet<>();
            List<PayrollAdjustment> current = provider.adjustmentsFor(companyId, s.getEmployee().getId(), period);
            if (current != null) current.forEach(a -> stillPending.add(a.sourceType() + ":" + a.sourceId()));
            for (FinalSettlementLine l : mine) {
                if (!stillPending.contains(l.getSourceType() + ":" + l.getSourceId())) {
                    throw new BadRequestException("'" + l.getLabel() + "' is no longer pending (paid or withdrawn elsewhere). "
                            + "Send the settlement back to draft and save it again to recalculate.");
                }
            }
            provider.markSettled(mine.stream().map(l -> new PayrollAdjustment(l.getCode(), l.getLabel(), l.getAmount(),
                    l.getTaxableAmount().signum() > 0, l.getSourceType(), l.getSourceId())).toList(), marker);
        }
    }

    // ================================================================== helpers

    static boolean isExited(Employee e) {
        String status = e.getStatus();
        return EmploymentStatus.RESIGNED.equals(status) || EmploymentStatus.TERMINATED.equals(status);
    }

    private static FinalSettlementLine line(String kind, String code, String label, BigDecimal amount, int order) {
        FinalSettlementLine line = new FinalSettlementLine();
        line.setKind(kind);
        line.setCode(code);
        line.setLabel(label);
        line.setAmount(nz(amount).setScale(2, RoundingMode.HALF_UP));
        line.setSortOrder(order);
        return line;
    }

    private static void addDeduction(FinalSettlement s, String code, String label, BigDecimal amount, int order) {
        if (amount == null || amount.signum() <= 0) return;
        s.addLine(line(FinalSettlementLine.DEDUCTION, code, label, amount, order));
    }

    private void notifyEmployee(FinalSettlement s, String type, String title, String body) {
        if (notificationService == null || s.getEmployee().getUser() == null) return;
        try {
            notificationService.notifyUser(s.getCompany().getId(), s.getEmployee().getUser().getId(), type, title, body,
                    "FinalSettlement", s.getId(), "HIGH");
        } catch (RuntimeException ex) {
            log.warn("Could not notify employee about settlement {}: {}", s.getId(), ex.getMessage());
        }
    }

    private static void requireStatus(FinalSettlement s, String status, String message) {
        if (!status.equals(s.getStatus())) throw new BadRequestException(message);
    }

    private static String basis(String value) {
        return value != null && SettlementSettings.BASIS_GROSS.equalsIgnoreCase(value.trim())
                ? SettlementSettings.BASIS_GROSS : SettlementSettings.BASIS_BASIC;
    }

    private FinalSettlement find(Long id, Long companyId) {
        return settlementRepository.findByIdAndCompany_IdAndDeletedFalse(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Settlement not found"));
    }

    private Employee employee(Long id, Long companyId) {
        return employeeRepository.findByIdAndCompany_IdAndDeletedFalse(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
    }

    private Company company(Long companyId) {
        return companyRepository.findById(companyId).orElseThrow(() -> new ResourceNotFoundException("Company not found"));
    }

    private static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String name = auth == null ? "system" : auth.getName();
        return name.length() > 100 ? name.substring(0, 100) : name;
    }

    private static Long requiredTenant() {
        Long tenant = TenantContext.getCurrentTenant();
        if (tenant == null) throw new BadRequestException("Company context is required");
        return tenant;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
