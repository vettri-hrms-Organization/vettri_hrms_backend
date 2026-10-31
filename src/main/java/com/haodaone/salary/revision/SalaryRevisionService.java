package com.haodaone.salary.revision;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.entity.EmploymentStatus;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.notifications.service.NotificationService;
import com.haodaone.salary.adjustment.PayrollAdjustment;
import com.haodaone.salary.dto.SalaryComponentsDTO;
import com.haodaone.salary.entity.PayrollItem;
import com.haodaone.salary.entity.PayrollItemStatus;
import com.haodaone.salary.entity.PayrollRunStatus;
import com.haodaone.salary.entity.SalaryComponents;
import com.haodaone.salary.entity.SalaryStructure;
import com.haodaone.salary.repository.PayrollItemRepository;
import com.haodaone.salary.repository.SalaryStructureRepository;
import com.haodaone.security.AuthorizationService;
import com.haodaone.tenant.TenantContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Salary revisions (Keka-style "revise salary" with arrears). A revision with a past effective date becomes the
 * active structure now and pays arrears - the gap between the new salary and what was paid for every payroll
 * month already processed since that date - as one taxable line in a chosen payroll month. A future-dated revision
 * is SCHEDULED and applied by {@link SalaryRevisionScheduler} on its effective date. Pay cuts apply going forward
 * only (nothing is recovered from months already paid).
 */
@Service
public class SalaryRevisionService {

    public static final List<String> TYPES = List.of("ANNUAL_APPRAISAL", "PROMOTION", "MARKET_CORRECTION", "ROLE_CHANGE", "OTHER");
    static final String SOURCE_ARREARS = "SALARY_ARREARS";
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

    private final SalaryRevisionRepository revisions;
    private final SalaryRevisionArrearRepository arrears;
    private final SalaryStructureRepository structures;
    private final PayrollItemRepository payrollItems;
    private final EmployeeRepository employees;
    private final AuthorizationService authorizationService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public SalaryRevisionService(SalaryRevisionRepository revisions, SalaryRevisionArrearRepository arrears,
                                 SalaryStructureRepository structures, PayrollItemRepository payrollItems, EmployeeRepository employees,
                                 AuthorizationService authorizationService, NotificationService notificationService,
                                 AuditLogService auditLogService, ObjectMapper objectMapper, Clock applicationClock) {
        this.revisions = revisions;
        this.arrears = arrears;
        this.structures = structures;
        this.payrollItems = payrollItems;
        this.employees = employees;
        this.authorizationService = authorizationService;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
        this.clock = applicationClock;
    }

    public record RevisionRequest(Long employeeId, String revisionType, LocalDate effectiveFrom, SalaryComponentsDTO components,
                                  Boolean autoStatutory, String reason, String arrearsPayoutMonth) { }
    public record ArrearLine(String period, BigDecimal paidGross, BigDecimal revisedGross, BigDecimal amount) { }
    public record Preview(Long employeeId, String employeeName, BigDecimal oldMonthlyGross, BigDecimal newMonthlyGross,
                          BigDecimal increment, BigDecimal incrementPercent, boolean scheduled, List<ArrearLine> arrears,
                          BigDecimal arrearsTotal, String arrearsPayoutMonth, boolean draftRunAffected, List<String> notes) { }
    public record RevisionDTO(Long id, Long employeeId, String employeeName, String employeeCode, String revisionType, String status,
                              LocalDate effectiveFrom, BigDecimal oldMonthlyGross, BigDecimal newMonthlyGross,
                              BigDecimal incrementPercent, String reason, BigDecimal arrearsTotal, String arrearsPayoutMonth,
                              String arrearsStatus, List<ArrearLine> arrears, LocalDateTime createdAt, String createdByName,
                              LocalDateTime appliedAt) { }

    // ------------------------------------------------------------------ preview / create

    @Transactional(readOnly = true)
    public Preview preview(RevisionRequest r) {
        Plan plan = plan(r);
        return plan.preview();
    }

    @Transactional
    public RevisionDTO create(RevisionRequest r, String actor) {
        Plan plan = plan(r);
        Employee e = plan.employee();
        SalaryRevision x = new SalaryRevision();
        x.setCompanyId(e.getCompany().getId());
        x.setEmployeeId(e.getId());
        x.setRevisionType(plan.type());
        x.setEffectiveFrom(r.effectiveFrom());
        x.setOldStructureId(plan.current().getId());
        x.setOldMonthlyGross(plan.current().getGrossSalary());
        x.setNewMonthlyGross(plan.components().grossSalary());
        x.setNewComponentsJson(writeJson(SalaryComponentsDTO.from(plan.components())));
        x.setAutoStatutory(plan.autoStatutory());
        x.setReason(text(r.reason(), 1000));
        x.setCreatedByName(actor);
        x.setStatus(plan.scheduled() ? SalaryRevision.SCHEDULED : SalaryRevision.APPLIED);
        x = revisions.save(x);
        if (!plan.scheduled()) {
            applyStructure(x, plan.current(), plan.components());
            saveArrears(x, plan.arrears(), plan.payoutMonth());
        }
        revisions.save(x);
        auditLogService.log("SALARY_REVISION", x.getId(), plan.scheduled() ? "SCHEDULED" : "APPLIED",
                actor + " revised " + e.getFullName() + "'s salary " + x.getOldMonthlyGross() + " -> " + x.getNewMonthlyGross()
                        + " effective " + x.getEffectiveFrom() + (x.getArrearsTotal().signum() > 0 ? "; arrears " + x.getArrearsTotal() : ""));
        if (e.getUser() != null) {
            notificationService.notifyUser(e.getCompany().getId(), e.getUser().getId(), "SALARY_REVISION", "Your salary has been revised",
                    "Your salary is revised effective " + x.getEffectiveFrom().format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH))
                            + (x.getArrearsTotal().signum() > 0 ? ". Arrears of Rs " + x.getArrearsTotal().toPlainString() + " will be paid with your "
                            + YearMonth.parse(x.getArrearsPayoutMonth()).format(MONTH) + " salary." : "."),
                    "SALARY_REVISION", x.getId(), "NORMAL");
        }
        return toDto(x, e);
    }

    @Transactional
    public RevisionDTO cancel(Long id, String actor) {
        SalaryRevision x = load(id);
        if (!SalaryRevision.SCHEDULED.equals(x.getStatus())) {
            throw new BadRequestException("Only a scheduled revision can be cancelled. To undo an applied one, add a new revision.");
        }
        x.setStatus(SalaryRevision.CANCELLED);
        revisions.save(x);
        auditLogService.log("SALARY_REVISION", x.getId(), "CANCELLED", actor + " cancelled the scheduled revision");
        return toDto(x, employee(x.getEmployeeId()));
    }

    @Transactional(readOnly = true)
    public List<RevisionDTO> list(Long employeeId) {
        Long companyId = tenant();
        List<SalaryRevision> rows = employeeId == null
                ? revisions.findAllByCompanyIdAndDeletedFalseOrderByCreatedAtDesc(companyId)
                : revisions.findAllByEmployeeIdAndDeletedFalseOrderByEffectiveFromDesc(employeeId).stream()
                        .filter(x -> companyId.equals(x.getCompanyId())).toList();
        Set<Long> ids = rows.stream().map(SalaryRevision::getEmployeeId).collect(Collectors.toSet());
        Map<Long, Employee> people = ids.isEmpty() ? Map.of()
                : employees.findAllByCompany_IdAndIdInAndDeletedFalseOrderByFirstNameAsc(companyId, ids).stream()
                        .collect(Collectors.toMap(Employee::getId, e -> e));
        return rows.stream().filter(x -> people.containsKey(x.getEmployeeId()))
                .filter(x -> authorizationService.isAllowed("SALARY_VIEW", "EMPLOYEE", x.getEmployeeId())
                        || authorizationService.isAllowed("SALARY_MANAGE", "EMPLOYEE", x.getEmployeeId()))
                .map(x -> toDto(x, people.get(x.getEmployeeId()))).toList();
    }

    // ------------------------------------------------------------------ scheduled + payroll

    /** Applies SCHEDULED revisions whose date has come (SalaryRevisionScheduler; no user context). */
    @Transactional
    public int applyDue(LocalDate today) {
        int applied = 0;
        for (SalaryRevision x : revisions.findAllByStatusAndEffectiveFromLessThanEqualAndDeletedFalse(SalaryRevision.SCHEDULED, today)) {
            SalaryStructure current = structures.findByEmployeeIdAndActiveTrueAndDeletedFalse(x.getEmployeeId()).orElse(null);
            if (current == null) continue;
            Long previousTenant = TenantContext.getCurrentTenant();
            TenantContext.setCurrentTenant(x.getCompanyId());
            try {
            SalaryComponents components = readJson(x.getNewComponentsJson()).toEntity();
            applyStructure(x, current, components);
            List<ArrearLine> lines = computeArrears(x.getEmployeeId(), x.getCompanyId(), x.getEffectiveFrom(), components.grossSalary());
            saveArrears(x, lines, YearMonth.now(clock).toString());
            revisions.save(x);
            auditLogService.log("SALARY_REVISION", x.getId(), "APPLIED", "Scheduled revision applied on " + today);
            applied++;
            } finally {
                if (previousTenant == null) TenantContext.clear(); else TenantContext.setCurrentTenant(previousTenant);
            }
        }
        return applied;
    }

    /** Pending arrears due in this payroll month (PayrollAdjustmentProvider via SalaryArrearsProvider). */
    @Transactional(readOnly = true)
    public List<PayrollAdjustment> arrearsDue(Long companyId, Long employeeId, YearMonth period) {
        return revisions.findAllByCompanyIdAndEmployeeIdAndArrearsStatusAndDeletedFalse(companyId, employeeId, SalaryRevision.ARREARS_PENDING).stream()
                .filter(x -> x.getArrearsPayoutMonth() == null || x.getArrearsPayoutMonth().compareTo(period.toString()) <= 0)
                .filter(x -> x.getArrearsTotal().signum() > 0)
                .map(x -> new PayrollAdjustment("ARREARS", "Salary arrears (" + arrearsRange(x) + ")", x.getArrearsTotal(), true,
                        SOURCE_ARREARS, x.getId()))
                .toList();
    }

    @Transactional
    public void arrearsPaid(List<PayrollAdjustment> lines, Long payrollItemId) {
        for (PayrollAdjustment a : lines) {
            if (!SOURCE_ARREARS.equals(a.sourceType())) continue;
            revisions.findById(a.sourceId()).ifPresent(x -> {
                x.setArrearsStatus(SalaryRevision.ARREARS_PAID);
                x.setArrearsPayrollItemId(payrollItemId);
                revisions.save(x);
            });
        }
    }

    // ------------------------------------------------------------------ planning

    private record Plan(Employee employee, SalaryStructure current, SalaryComponents components, String type, boolean autoStatutory,
                        boolean scheduled, List<ArrearLine> arrears, String payoutMonth, boolean draftRunAffected, List<String> notes) {
        Preview preview() {
            BigDecimal oldGross = current.getGrossSalary();
            BigDecimal newGross = components.grossSalary();
            BigDecimal inc = newGross.subtract(oldGross);
            BigDecimal pct = oldGross.signum() == 0 ? BigDecimal.ZERO : inc.multiply(BigDecimal.valueOf(100)).divide(oldGross, 2, RoundingMode.HALF_UP);
            BigDecimal total = arrears.stream().map(ArrearLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
            return new Preview(employee.getId(), employee.getFullName(), oldGross, newGross, inc, pct, scheduled, arrears, total,
                    payoutMonth, draftRunAffected, notes);
        }
    }

    private Plan plan(RevisionRequest r) {
        if (r == null || r.employeeId() == null) throw new BadRequestException("Choose an employee");
        Employee e = employee(r.employeeId());
        if (!authorizationService.isAllowed("SALARY_MANAGE", "EMPLOYEE", e.getId())) throw new AccessDeniedException("SALARY_MANAGE is not authorized for this employee");
        Employee me = authorizationService.resolveCurrentEmployee();
        if (me != null && me.getId().equals(e.getId())) throw new BadRequestException("Ask a colleague to revise your own salary");
        String status = EmploymentStatus.normalize(e.getStatus());
        if (EmploymentStatus.RESIGNED.equals(status) || EmploymentStatus.TERMINATED.equals(status)) {
            throw new BadRequestException(e.getFullName() + " has left; settle any difference in their full & final instead");
        }
        String type = r.revisionType() == null ? "OTHER" : r.revisionType().trim().toUpperCase(Locale.ROOT);
        if (!TYPES.contains(type)) throw new BadRequestException("Unknown revision type");
        if (r.effectiveFrom() == null) throw new BadRequestException("Choose the effective date");
        LocalDate today = LocalDate.now(clock);
        if (r.effectiveFrom().isBefore(today.minusMonths(12))) throw new BadRequestException("A revision can be back-dated by up to 12 months");
        if (e.getDateOfJoining() != null && r.effectiveFrom().isBefore(e.getDateOfJoining())) throw new BadRequestException("The effective date is before joining");
        SalaryStructure current = structures.findByEmployeeIdAndActiveTrueAndDeletedFalse(e.getId())
                .orElseThrow(() -> new BadRequestException(e.getFullName() + " has no salary structure yet - set one up first"));
        if (r.components() == null) throw new BadRequestException("Enter the new salary");
        SalaryComponents components = r.components().toEntity();
        if (components.getBasicSalary() == null || components.getBasicSalary().signum() <= 0) throw new BadRequestException("Basic salary must be greater than zero");
        boolean open = revisions.findAllByEmployeeIdAndDeletedFalseOrderByEffectiveFromDesc(e.getId()).stream()
                .anyMatch(x -> SalaryRevision.SCHEDULED.equals(x.getStatus()) || SalaryRevision.ARREARS_PENDING.equals(x.getArrearsStatus()));
        if (open) throw new BadRequestException(e.getFullName() + " already has a scheduled revision or unpaid arrears. Let that finish (or cancel it) first.");

        boolean scheduled = r.effectiveFrom().isAfter(today);
        List<String> notes = new ArrayList<>();
        List<ArrearLine> lines = scheduled ? List.of() : computeArrears(e.getId(), e.getCompany().getId(), r.effectiveFrom(), components.grossSalary());
        if (!scheduled && components.grossSalary().compareTo(current.getGrossSalary()) < 0) {
            notes.add("This is a pay cut. It applies from now on; nothing is recovered from months already paid.");
        }
        if (r.effectiveFrom().getDayOfMonth() != 1) {
            notes.add("Mid-month dates are pro-rated in arrears, but a month not yet processed is paid at the new rate for the whole month.");
        }
        YearMonth payout = r.arrearsPayoutMonth() == null || r.arrearsPayoutMonth().isBlank() ? YearMonth.now(clock) : parseMonth(r.arrearsPayoutMonth());
        if (payout.isBefore(YearMonth.now(clock))) throw new BadRequestException("Arrears can't be paid in a past month");
        boolean draftAffected = payrollItems.findByEmployee_Company_IdAndEmployeeIdAndDeletedFalseOrderByCreatedAtDesc(e.getCompany().getId(), e.getId()).stream()
                .anyMatch(i -> PayrollRunStatus.DRAFT.equals(i.getPayrollRun().getStatus())
                        && !YearMonth.of(i.getPayrollRun().getPeriodYear(), i.getPayrollRun().getPeriodMonth()).isBefore(YearMonth.from(r.effectiveFrom())));
        if (draftAffected && !scheduled) {
            notes.add("A draft payroll run already includes " + e.getFullName() + " at the old salary - cancel and regenerate it to use the new one.");
        }
        return new Plan(e, current, components, type, r.autoStatutory() == null ? current.isAutoStatutory() : r.autoStatutory(),
                scheduled, lines, payout.toString(), draftAffected, notes);
    }

    /**
     * For every processed/paid payroll month on or after the effective month: what the new salary would have paid
     * (pro-rated by that month's paid days, and by the part of the month after a mid-month effective date) minus what
     * the salary structure actually paid. Additional earnings (overtime etc.) are not involved. Increases only.
     */
    List<ArrearLine> computeArrears(Long employeeId, Long companyId, LocalDate effectiveFrom, BigDecimal newMonthlyGross) {
        YearMonth start = YearMonth.from(effectiveFrom);
        Map<YearMonth, PayrollItem> paid = new TreeMap<>();
        for (PayrollItem i : payrollItems.findByEmployee_Company_IdAndEmployeeIdAndDeletedFalseOrderByCreatedAtDesc(companyId, employeeId)) {
            String runStatus = i.getPayrollRun().getStatus();
            if (!PayrollRunStatus.PROCESSED.equals(runStatus) && !PayrollRunStatus.PAID.equals(runStatus)) continue;
            if (!PayrollItemStatus.PROCESSED.equals(i.getStatus()) && !PayrollItemStatus.PAID.equals(i.getStatus())) continue;
            YearMonth period = YearMonth.of(i.getPayrollRun().getPeriodYear(), i.getPayrollRun().getPeriodMonth());
            if (period.isBefore(start)) continue;
            paid.putIfAbsent(period, i);
        }
        List<ArrearLine> lines = new ArrayList<>();
        for (Map.Entry<YearMonth, PayrollItem> entry : paid.entrySet()) {
            YearMonth period = entry.getKey();
            PayrollItem item = entry.getValue();
            BigDecimal days = BigDecimal.valueOf(period.lengthOfMonth());
            BigDecimal paidDays = item.getPaidDays() == null ? days : item.getPaidDays();
            BigDecimal paidFraction = paidDays.divide(days, 10, RoundingMode.HALF_UP);
            BigDecimal afterFraction = period.equals(start)
                    ? BigDecimal.valueOf(period.lengthOfMonth() - effectiveFrom.getDayOfMonth() + 1L).divide(days, 10, RoundingMode.HALF_UP)
                    : BigDecimal.ONE;
            BigDecimal paidGross = item.getComponents().grossSalary();
            BigDecimal revised = newMonthlyGross.multiply(paidFraction);
            BigDecimal amount = revised.subtract(paidGross).multiply(afterFraction).setScale(2, RoundingMode.HALF_UP);
            if (amount.signum() <= 0) continue;
            lines.add(new ArrearLine(period.toString(), paidGross.setScale(2, RoundingMode.HALF_UP),
                    paidGross.add(amount).setScale(2, RoundingMode.HALF_UP), amount));
        }
        return lines;
    }

    private void applyStructure(SalaryRevision x, SalaryStructure current, SalaryComponents components) {
        current.setActive(false);
        structures.save(current);
        structures.flush();
        SalaryStructure next = new SalaryStructure();
        next.setCompany(current.getCompany());
        next.setEmployee(current.getEmployee());
        next.setEffectiveFrom(x.getEffectiveFrom());
        next.setActive(true);
        next.setComponents(components);
        next.setAutoStatutory(x.isAutoStatutory());
        next.setNotes("Salary revision #" + x.getId() + " (" + x.getRevisionType().replace('_', ' ').toLowerCase(Locale.ROOT) + ")");
        next.recalculate();
        next = structures.save(next);
        x.setNewStructureId(next.getId());
        x.setStatus(SalaryRevision.APPLIED);
        x.setAppliedAt(LocalDateTime.now(clock));
    }

    private void saveArrears(SalaryRevision x, List<ArrearLine> lines, String payoutMonth) {
        BigDecimal total = BigDecimal.ZERO;
        for (ArrearLine l : lines) {
            SalaryRevisionArrear a = new SalaryRevisionArrear();
            a.setCompanyId(x.getCompanyId());
            a.setRevisionId(x.getId());
            a.setPeriod(l.period());
            a.setPaidGross(l.paidGross());
            a.setRevisedGross(l.revisedGross());
            a.setAmount(l.amount());
            arrears.save(a);
            total = total.add(l.amount());
        }
        x.setArrearsTotal(total);
        x.setArrearsPayoutMonth(total.signum() > 0 ? payoutMonth : null);
        x.setArrearsStatus(total.signum() > 0 ? SalaryRevision.ARREARS_PENDING : SalaryRevision.ARREARS_NONE);
    }

    // ------------------------------------------------------------------ helpers

    private RevisionDTO toDto(SalaryRevision x, Employee e) {
        List<ArrearLine> lines = x.getId() == null ? List.of() : arrears.findAllByRevisionIdAndDeletedFalseOrderByPeriodAsc(x.getId()).stream()
                .map(a -> new ArrearLine(a.getPeriod(), a.getPaidGross(), a.getRevisedGross(), a.getAmount())).toList();
        BigDecimal pct = x.getOldMonthlyGross().signum() == 0 ? BigDecimal.ZERO
                : x.getNewMonthlyGross().subtract(x.getOldMonthlyGross()).multiply(BigDecimal.valueOf(100)).divide(x.getOldMonthlyGross(), 2, RoundingMode.HALF_UP);
        return new RevisionDTO(x.getId(), e.getId(), e.getFullName(), e.getEmployeeCode(), x.getRevisionType(), x.getStatus(),
                x.getEffectiveFrom(), x.getOldMonthlyGross(), x.getNewMonthlyGross(), pct, x.getReason(), x.getArrearsTotal(),
                x.getArrearsPayoutMonth(), x.getArrearsStatus(), lines, x.getCreatedAt(), x.getCreatedByName(), x.getAppliedAt());
    }

    private String arrearsRange(SalaryRevision x) {
        List<SalaryRevisionArrear> lines = arrears.findAllByRevisionIdAndDeletedFalseOrderByPeriodAsc(x.getId());
        if (lines.isEmpty()) return "from " + YearMonth.from(x.getEffectiveFrom()).format(MONTH);
        String first = YearMonth.parse(lines.get(0).getPeriod()).format(MONTH);
        String last = YearMonth.parse(lines.get(lines.size() - 1).getPeriod()).format(MONTH);
        return first.equals(last) ? first : first + " - " + last;
    }

    private String writeJson(SalaryComponentsDTO dto) {
        try {
            return objectMapper.writeValueAsString(dto);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Could not store the new salary", ex);
        }
    }

    private SalaryComponentsDTO readJson(String json) {
        try {
            return objectMapper.readValue(json, SalaryComponentsDTO.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Stored revision is unreadable", ex);
        }
    }

    private static YearMonth parseMonth(String value) {
        try {
            return YearMonth.parse(value.trim());
        } catch (java.time.format.DateTimeParseException ex) {
            throw new BadRequestException("Month must look like 2026-11");
        }
    }

    private static String text(String value, int max) {
        if (value == null) return null;
        String v = value.trim();
        if (v.isEmpty()) return null;
        if (v.length() > max) throw new BadRequestException("Keep it under " + max + " characters");
        return v;
    }

    private SalaryRevision load(Long id) {
        return revisions.findByIdAndCompanyIdAndDeletedFalse(id, tenant()).orElseThrow(() -> new ResourceNotFoundException("Revision not found"));
    }

    private Employee employee(Long id) {
        return employees.findByIdAndCompany_IdAndDeletedFalse(id, tenant()).orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
    }

    private static Long tenant() {
        Long companyId = TenantContext.getCurrentTenant();
        if (companyId == null) throw new BadRequestException("Company context is required");
        return companyId;
    }
}
