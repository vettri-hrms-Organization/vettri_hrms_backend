package com.haodaone.salary.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.entity.EmploymentStatus;
import com.haodaone.salary.dto.*;
import com.haodaone.salary.entity.*;
import com.haodaone.salary.repository.PayrollItemRepository;
import com.haodaone.salary.repository.PayrollRunRepository;
import com.haodaone.salary.repository.SalaryStructureRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.haodaone.tenant.TenantContext;
import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.notifications.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Payroll Processing state machine: DRAFT (items generated from every
 * actively-paid employee, individually hold-able) -> PROCESSED (totals
 * locked in, items snapshot their structure permanently) -> PAID (payment
 * date recorded). Mirrors CandidateService's "explicit VALID transitions,
 * checked before every write" style rather than leaving state changes
 * implicit in controller code.
 */
@Service
public class PayrollService {

    private final PayrollRunRepository payrollRunRepository;
    private final PayrollItemRepository payrollItemRepository;
    private final SalaryStructureRepository salaryStructureRepository;
    private final AuditLogService auditLogService;
    private final CompanyRepository companyRepository;
    private final com.haodaone.security.AuthorizationService authorizationService;
    private NotificationService notificationService;

    private com.haodaone.integration.IntegrationEventService integrationEvents;

    /** Slack/Teams posts and outbound webhooks (optional so unit tests can omit it). */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setIntegrationEvents(com.haodaone.integration.IntegrationEventService integrationEvents) {
        this.integrationEvents = integrationEvents;
    }

    public PayrollService(PayrollRunRepository payrollRunRepository, PayrollItemRepository payrollItemRepository,
                           SalaryStructureRepository salaryStructureRepository, AuditLogService auditLogService,
                           CompanyRepository companyRepository,
                           com.haodaone.security.AuthorizationService authorizationService) {
        this.payrollRunRepository = payrollRunRepository;
        this.payrollItemRepository = payrollItemRepository;
        this.salaryStructureRepository = salaryStructureRepository;
        this.auditLogService = auditLogService;
        this.companyRepository = companyRepository;
        this.authorizationService = authorizationService;
    }

    @Autowired
    public void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    private StatutoryPayrollEngine statutoryPayrollEngine;
    private List<com.haodaone.salary.adjustment.PayrollAdjustmentProvider> adjustmentProviders = List.of();

    @Autowired
    public void setStatutoryPayrollEngine(StatutoryPayrollEngine statutoryPayrollEngine) {
        this.statutoryPayrollEngine = statutoryPayrollEngine;
    }

    private com.haodaone.attendance.shift.service.DailyAttendanceService dailyAttendanceService;

    /** Source of the "N late marks = X days LOP" penalty (attendance/shift); optional so payroll works without it. */
    @Autowired(required = false)
    public void setDailyAttendanceService(com.haodaone.attendance.shift.service.DailyAttendanceService dailyAttendanceService) {
        this.dailyAttendanceService = dailyAttendanceService;
    }

    /** Adds late-mark penalty days to loss of pay, never paying fewer than zero days. */
    private PayrollProration.Result withLatePenalty(PayrollProration.Result days, Long companyId, Long employeeId, YearMonth period) {
        if (dailyAttendanceService == null) return days;
        java.math.BigDecimal penalty = dailyAttendanceService.latePenaltyDays(companyId, employeeId, period);
        if (penalty == null || penalty.signum() <= 0) return days;
        java.math.BigDecimal applied = penalty.min(days.paidDays());
        return new PayrollProration.Result(days.daysInPeriod(), days.lopDays().add(applied), days.paidDays().subtract(applied));
    }

    /** Every PayrollAdjustmentProvider bean (overtime, leave encashment, reimbursements...); empty when none exist. */
    @Autowired(required = false)
    public void setAdjustmentProviders(List<com.haodaone.salary.adjustment.PayrollAdjustmentProvider> adjustmentProviders) {
        this.adjustmentProviders = adjustmentProviders != null ? adjustmentProviders : List.of();
    }

    private List<PayrollExclusionProvider> exclusionProviders = List.of();

    private List<com.haodaone.salary.adjustment.PayrollDeductionProvider> deductionProviders = List.of();

    /** Every PayrollDeductionProvider bean (loan EMIs, advance recovery); empty when none exist. */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setDeductionProviders(List<com.haodaone.salary.adjustment.PayrollDeductionProvider> deductionProviders) {
        this.deductionProviders = deductionProviders != null ? deductionProviders : List.of();
    }

    /**
     * Attaches post-tax recoveries after statutory deductions are final. A line goes on only if net pay still
     * covers it in full; anything left off is offered again by the next run (never a negative payslip).
     */
    private void attachDeductions(PayrollItem item, Company company, Long employeeId, YearMonth period) {
        for (var provider : deductionProviders) {
            var lines = provider.deductionsFor(company.getId(), employeeId, period);
            if (lines == null) continue;
            for (var line : lines) {
                if (line == null || line.amount() == null || line.amount().signum() <= 0) continue;
                java.math.BigDecimal amount = line.amount().setScale(2, java.math.RoundingMode.HALF_UP);
                if (item.getNetSalary() == null || item.getNetSalary().compareTo(amount) < 0) continue;
                com.haodaone.salary.entity.PayrollItemDeduction row = new com.haodaone.salary.entity.PayrollItemDeduction();
                row.setCompany(company);
                row.setCode(line.code() != null ? line.code() : "DEDUCTION");
                row.setLabel(line.label() != null ? line.label() : row.getCode());
                row.setAmount(amount);
                row.setSourceType(line.sourceType());
                row.setSourceId(line.sourceId());
                item.addDeductionLine(row);
                item.recalculate();
            }
        }
    }

    private void settleDeductions(PayrollItem item) {
        if (item.getDeductionLines().isEmpty() || deductionProviders.isEmpty()) return;
        for (var provider : deductionProviders) {
            java.util.Set<String> types = provider.sourceTypes() != null ? provider.sourceTypes() : java.util.Set.of();
            var rows = item.getDeductionLines().stream()
                    .filter(d -> !d.isDeleted() && !d.isSettled() && d.getSourceType() != null && types.contains(d.getSourceType()))
                    .toList();
            if (rows.isEmpty()) continue;
            provider.markSettled(rows.stream()
                    .map(d -> new com.haodaone.salary.adjustment.PayrollDeductionProvider.PayrollDeduction(
                            d.getCode(), d.getLabel(), d.getAmount(), d.getSourceType(), d.getSourceId()))
                    .toList(), item.getId());
            rows.forEach(d -> d.setSettled(true));
        }
    }

    /** Every PayrollExclusionProvider bean (e.g. Full & Final settlement); empty when none exist. */
    @Autowired(required = false)
    public void setExclusionProviders(List<PayrollExclusionProvider> exclusionProviders) {
        this.exclusionProviders = exclusionProviders != null ? exclusionProviders : List.of();
    }

    /** Asks every provider for this employee's extra earnings in the period and attaches them to the item. */
    private void attachAdjustments(PayrollItem item, Company company, Long employeeId, YearMonth period) {
        for (var provider : adjustmentProviders) {
            List<com.haodaone.salary.adjustment.PayrollAdjustment> lines = provider.adjustmentsFor(company.getId(), employeeId, period);
            if (lines == null) continue;
            for (var line : lines) {
                if (line == null || line.amount() == null || line.amount().signum() <= 0) continue;
                PayrollItemAdjustment row = new PayrollItemAdjustment();
                row.setCompany(company);
                row.setCode(line.code() != null ? line.code() : "ADJUSTMENT");
                row.setLabel(line.label() != null ? line.label() : row.getCode());
                row.setAmount(line.amount().setScale(2, java.math.RoundingMode.HALF_UP));
                row.setTaxable(line.taxable());
                row.setSourceType(line.sourceType());
                row.setSourceId(line.sourceId());
                item.addAdjustment(row);
            }
        }
    }

    /** Routes a processed item's adjustment lines back to the provider(s) that own their sourceType. */
    private void settleAdjustments(PayrollItem item) {
        if (item.getAdjustments().isEmpty() || adjustmentProviders.isEmpty()) return;
        for (var provider : adjustmentProviders) {
            java.util.Set<String> types = provider.sourceTypes() != null ? provider.sourceTypes() : java.util.Set.of();
            List<PayrollItemAdjustment> rows = item.getAdjustments().stream()
                    .filter(a -> !a.isDeleted() && !a.isSettled() && a.getSourceType() != null && types.contains(a.getSourceType()))
                    .toList();
            if (rows.isEmpty()) continue;
            provider.markSettled(rows.stream()
                    .map(a -> new com.haodaone.salary.adjustment.PayrollAdjustment(a.getCode(), a.getLabel(), a.getAmount(),
                            a.isTaxable(), a.getSourceType(), a.getSourceId()))
                    .toList(), item.getId());
            rows.forEach(a -> a.setSettled(true));
        }
    }

    public List<PayrollRunSummaryDTO> listRuns() {
        requireOrganizationPermission("SALARY_VIEW");
        Long companyId = requiredTenant();
        var scope = authorizationService.resolveEmployeeIds("SALARY_VIEW");
        if (scope.isPresent() && scope.get().isEmpty()) return List.of();
        var runs = scope.isEmpty() ? payrollRunRepository.findAllByCompany_IdAndDeletedFalseOrderByPeriodYearDescPeriodMonthDesc(companyId)
            : payrollRunRepository.findAllScoped(companyId, scope.get());
        return runs
                .stream().map(PayrollRunSummaryDTO::from).toList();
    }

    @Transactional(readOnly = true)
    public PayrollRunDTO getRun(Long runId) {
        requireOrganizationPermission("SALARY_VIEW");
        return getRunData(runId);
    }

    private PayrollRunDTO getRunData(Long runId) {
        PayrollRun run = findRunOrThrow(runId);
        List<PayrollItem> items = payrollItemRepository.findByPayrollRun_Company_IdAndPayrollRunIdAndDeletedFalseOrderByEmployee_FirstNameAsc(
                requiredTenant(), runId);
        return PayrollRunDTO.of(run, items);
    }

    /**
     * Opens a new DRAFT run for the given month and snapshots every
     * actively employed, actively-paid employee's current salary structure
     * into a PENDING PayrollItem. Only one run may exist per period.
     */
    @Transactional
    public PayrollRunDTO createRun(CreatePayrollRunRequest request) {
        requireOrganizationPermission("SALARY_MANAGE");
        Long companyId = requiredTenant();
        payrollRunRepository.findByPeriodYearAndPeriodMonthAndCompany_IdAndDeletedFalse(request.getPeriodYear(), request.getPeriodMonth(), companyId)
                .ifPresent(existing -> {
                    throw new BadRequestException("A payroll run already exists for " + request.getPeriodMonth() + "/" + request.getPeriodYear());
                });

        PayrollRun run = new PayrollRun();
        run.setPeriodMonth(request.getPeriodMonth());
        run.setPeriodYear(request.getPeriodYear());
        run.setPayDate(request.getPayDate());
        run.setRemarks(request.getRemarks());
        run.setStatus(PayrollRunStatus.DRAFT);
        Company company = companyRepository.findById(companyId).orElseThrow(() -> new ResourceNotFoundException("Company not found"));
        run.setCompany(company);
        PayrollRun savedRun = payrollRunRepository.save(run);

        YearMonth period = YearMonth.of(request.getPeriodYear(), request.getPeriodMonth());
        LocalDate periodStart = period.atDay(1);
        LocalDate periodEnd = period.atEndOfMonth();
        // Approved unpaid leave overlapping the month, grouped per employee - one query for the whole run.
        Map<Long, List<PayrollProration.LeaveSpan>> unpaidLeaveByEmployee = payrollItemRepository
                .findApprovedUnpaidLeavesOverlapping(companyId, periodStart, periodEnd).stream()
                .collect(Collectors.groupingBy(lr -> lr.getEmployee().getId(),
                        Collectors.mapping(lr -> new PayrollProration.LeaveSpan(lr.getStartDate(), lr.getEndDate(), lr.getDays()),
                                Collectors.toList())));

        List<SalaryStructure> activeStructures = salaryStructureRepository.findAllActiveByCompany(companyId);
        StatutoryPayrollEngine.Context statutory = statutoryPayrollEngine != null
                && activeStructures.stream().anyMatch(SalaryStructure::isAutoStatutory)
                ? statutoryPayrollEngine.contextFor(companyId) : null;
        int included = 0;
        // Employees paid outside payroll (e.g. approved/paid Full & Final settlement).
        java.util.Set<Long> excludedEmployeeIds = new java.util.HashSet<>();
        for (PayrollExclusionProvider exclusionProvider : exclusionProviders) {
            java.util.Set<Long> ids = exclusionProvider.excludedEmployeeIds(companyId);
            if (ids != null) excludedEmployeeIds.addAll(ids);
        }
        for (SalaryStructure structure : activeStructures) {
            Employee employee = structure.getEmployee();
            if (employee.isDeleted() || EmploymentStatus.RESIGNED.equals(employee.getStatus())
                    || EmploymentStatus.TERMINATED.equals(employee.getStatus())
                    || excludedEmployeeIds.contains(employee.getId())) {
                continue;
            }
            // Not yet employed / structure not yet in force for this period.
            if ((employee.getDateOfJoining() != null && employee.getDateOfJoining().isAfter(periodEnd))
                    || (structure.getEffectiveFrom() != null && structure.getEffectiveFrom().isAfter(periodEnd))) {
                continue;
            }
            PayrollProration.Result days = withLatePenalty(PayrollProration.compute(period, employee.getDateOfJoining(),
                    unpaidLeaveByEmployee.getOrDefault(employee.getId(), List.of())), companyId, employee.getId(), period);
            PayrollItem item = new PayrollItem();
            item.setPayrollRun(savedRun);
            item.setCompany(company);
            item.setEmployee(employee);
            item.setSalaryStructure(structure);
            item.setComponents(days.isFullPeriod()
                    ? copyComponents(structure)
                    : PayrollProration.prorate(structure.getComponents(), days.paidDays(), days.daysInPeriod()));
            item.setLopDays(days.lopDays());
            item.setPaidDays(days.paidDays());
            attachAdjustments(item, company, employee.getId(), period);
            item.recalculate();
            if (statutory != null && structure.isAutoStatutory()) {
                // Overwrites PF/ESI/PT/TDS, fills employer contributions, caps deductions at gross, recalculates.
                statutoryPayrollEngine.apply(item, structure, period, statutory);
            }
            attachDeductions(item, company, employee.getId(), period);
            item.setStatus(PayrollItemStatus.PENDING);
            payrollItemRepository.save(item);
            included++;
        }

        savedRun.setTotalEmployees(included);
        payrollRunRepository.save(savedRun);

        auditLogService.log("PayrollRun", savedRun.getId(), "CREATE",
                "Opened payroll run for " + request.getPeriodMonth() + "/" + request.getPeriodYear() + " with " + included + " employee(s)");
        return getRunData(savedRun.getId());
    }

    /** Toggles a single employee's line between PENDING and ON_HOLD while the run is still DRAFT. */
    @Transactional
    public PayrollItemDTO setItemHold(Long runId, Long itemId, UpdatePayrollItemRequest request) {
        requireOrganizationPermission("SALARY_MANAGE");
        PayrollRun run = findRunOrThrow(runId);
        if (!PayrollRunStatus.DRAFT.equals(run.getStatus())) {
            throw new BadRequestException("Employees can only be held or released while the run is still in DRAFT");
        }
        PayrollItem item = payrollItemRepository.findByIdAndDeletedFalse(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Payroll item not found: " + itemId));
        if (!item.getPayrollRun().getId().equals(runId)) {
            throw new BadRequestException("That payroll item does not belong to this run");
        }

        item.setStatus(request.isOnHold() ? PayrollItemStatus.ON_HOLD : PayrollItemStatus.PENDING);
        item.setRemarks(request.getRemarks());
        PayrollItem saved = payrollItemRepository.save(item);

        auditLogService.log("PayrollItem", saved.getId(), request.isOnHold() ? "HOLD" : "RELEASE",
                (request.isOnHold() ? "Held" : "Released") + " '" + saved.getEmployee().getFullName() + "' in payroll run #" + runId);
        return PayrollItemDTO.from(saved);
    }

    /** DRAFT -> PROCESSED: locks in totals across every non-held item and stamps processedAt. */
    @Transactional
    public PayrollRunDTO process(Long runId) {
        requireOrganizationPermission("SALARY_MANAGE");
        PayrollRun run = findRunOrThrow(runId);
        if (!PayrollRunStatus.DRAFT.equals(run.getStatus())) {
            throw new BadRequestException("Only a DRAFT run can be processed");
        }

        List<PayrollItem> items = payrollItemRepository.findByPayrollRunIdAndDeletedFalseOrderByEmployee_FirstNameAsc(runId);
        if (items.isEmpty()) {
            throw new BadRequestException("This run has no employees to process");
        }

        for (PayrollItem item : items) {
            if (PayrollItemStatus.PENDING.equals(item.getStatus())) {
                item.setStatus(PayrollItemStatus.PROCESSED);
                settleAdjustments(item);
                settleDeductions(item);
                payrollItemRepository.save(item);
            }
        }

        run.setTotalGross(payrollItemRepository.sumGrossSalaryForRun(runId));
        run.setTotalDeductions(payrollItemRepository.sumDeductionsForRun(runId));
        run.setTotalNet(payrollItemRepository.sumNetSalaryForRun(runId));
        run.setStatus(PayrollRunStatus.PROCESSED);
        run.setProcessedAt(LocalDateTime.now());
        PayrollRun saved = payrollRunRepository.save(run);

        auditLogService.log("PayrollRun", saved.getId(), "PROCESS",
                "Processed payroll for " + saved.getPeriodMonth() + "/" + saved.getPeriodYear()
                        + " - net payout " + saved.getTotalNet());
        if (integrationEvents != null && com.haodaone.tenant.TenantContext.getCurrentTenant() != null) {
            integrationEvents.publish(com.haodaone.tenant.TenantContext.getCurrentTenant(), com.haodaone.integration.IntegrationEventService.PAYROLL_PROCESSED,
                    "Payroll for " + java.time.Month.of(saved.getPeriodMonth()).getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH) + " " + saved.getPeriodYear() + " has been processed.",
                    java.util.Map.of("payrollRunId", saved.getId(), "periodMonth", saved.getPeriodMonth(), "periodYear", saved.getPeriodYear()));
        }
        return getRunData(saved.getId());
    }

    /** PROCESSED -> PAID: stamps every processed item (and the run) with a payment date. */
    @Transactional
    public PayrollRunDTO markPaid(Long runId, MarkPaidRequest request) {
        requireOrganizationPermission("SALARY_MANAGE");
        PayrollRun run = findRunOrThrow(runId);
        if (!PayrollRunStatus.PROCESSED.equals(run.getStatus())) {
            throw new BadRequestException("Only a PROCESSED run can be marked as paid");
        }

        LocalDate paymentDate = request.getPaymentDate() != null ? request.getPaymentDate() : LocalDate.now();
        List<PayrollItem> items = payrollItemRepository.findByPayrollRunIdAndDeletedFalseOrderByEmployee_FirstNameAsc(runId);
        for (PayrollItem item : items) {
            if (PayrollItemStatus.PROCESSED.equals(item.getStatus())) {
                item.setStatus(PayrollItemStatus.PAID);
                item.setPaymentDate(paymentDate);
                payrollItemRepository.save(item);
                if (notificationService != null && item.getEmployee().getUser() != null) {
                    notificationService.notifyUser(run.getCompany().getId(), item.getEmployee().getUser().getId(),
                            "PAYROLL_PAID", "Payroll payment recorded",
                            "Your payroll payment for " + run.getPeriodMonth() + "/" + run.getPeriodYear()
                                    + " was recorded as paid on " + paymentDate + ".",
                            "PayrollRun", run.getId(), "NORMAL");
                }
            }
        }

        run.setStatus(PayrollRunStatus.PAID);
        run.setPayDate(paymentDate);
        PayrollRun saved = payrollRunRepository.save(run);

        auditLogService.log("PayrollRun", saved.getId(), "MARK_PAID",
                "Marked payroll for " + saved.getPeriodMonth() + "/" + saved.getPeriodYear() + " as paid on " + paymentDate);
        return getRunData(saved.getId());
    }

    /** DRAFT -> CANCELLED, for a run opened against the wrong period. Never allowed once processed. */
    @Transactional
    public void cancel(Long runId) {
        requireOrganizationPermission("SALARY_MANAGE");
        PayrollRun run = findRunOrThrow(runId);
        if (!PayrollRunStatus.DRAFT.equals(run.getStatus())) {
            throw new BadRequestException("Only a DRAFT run can be cancelled");
        }
        run.setStatus(PayrollRunStatus.CANCELLED);
        run.setDeleted(true);
        payrollRunRepository.save(run);

        List<PayrollItem> items = payrollItemRepository.findByPayrollRunIdAndDeletedFalseOrderByEmployee_FirstNameAsc(runId);
        items.forEach(item -> item.setDeleted(true));
        payrollItemRepository.saveAll(items);

        auditLogService.log("PayrollRun", run.getId(), "CANCEL",
                "Cancelled draft payroll run for " + run.getPeriodMonth() + "/" + run.getPeriodYear());
    }

    private SalaryComponents copyComponents(SalaryStructure structure) {
        return SalaryComponentsDTO.from(structure.getComponents()).toEntity();
    }

    private PayrollRun findRunOrThrow(Long runId) {
        return payrollRunRepository.findByIdAndCompany_IdAndDeletedFalse(runId, requiredTenant())
                .orElseThrow(() -> new ResourceNotFoundException("Payroll run not found: " + runId));
    }

    private Long requiredTenant() {
        Long tenant = TenantContext.getCurrentTenant();
        if (tenant == null) throw new BadRequestException("Company context is required");
        return tenant;
    }

    private void requireOrganizationPermission(String permissionCode) {
        if (!authorizationService.hasOrganizationScope(permissionCode)) {
            throw new org.springframework.security.access.AccessDeniedException(
                    permissionCode + " requires ORGANIZATION scope.");
        }
    }
}
