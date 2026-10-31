package com.haodaone.salary.bonus;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.entity.EmploymentStatus;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.notifications.service.NotificationService;
import com.haodaone.salary.adjustment.PayrollAdjustment;
import com.haodaone.salary.adjustment.PayrollAdjustmentProvider;
import com.haodaone.salary.entity.PayrollItem;
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
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Bonus runs: one batch pays a bonus to many employees through payroll. Amounts come from the chosen method -
 * a fixed amount, a % of monthly basic or gross, or the statutory bonus (Payment of Bonus Act: rate% of the year's
 * calculation wages, each month's basic capped at the wage ceiling, for employees whose basic is at most
 * Rs 21,000) - and stay editable per employee while the batch is a draft. Approved batches are paid as a
 * taxable earning in the payout month's payroll (or the employee's full and final settlement).
 */
@Service
public class BonusService implements PayrollAdjustmentProvider {

    static final String SOURCE = "BONUS";
    public static final List<String> TYPES = List.of("ONE_TIME", "FESTIVAL", "PERFORMANCE", "STATUTORY");
    public static final List<String> METHODS = List.of("FIXED", "PERCENT_BASIC", "PERCENT_GROSS", "STATUTORY");
    static final BigDecimal STATUTORY_ELIGIBILITY = new BigDecimal("21000");
    static final BigDecimal DEFAULT_WAGE_CEILING = new BigDecimal("7000");

    private final BonusBatchRepository batches;
    private final BonusLineRepository lines;
    private final EmployeeRepository employees;
    private final SalaryStructureRepository structures;
    private final PayrollItemRepository payrollItems;
    private final AuthorizationService authorizationService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final Clock clock;

    public BonusService(BonusBatchRepository batches, BonusLineRepository lines, EmployeeRepository employees,
                        SalaryStructureRepository structures, PayrollItemRepository payrollItems,
                        AuthorizationService authorizationService, NotificationService notificationService,
                        AuditLogService auditLogService, Clock applicationClock) {
        this.batches = batches;
        this.lines = lines;
        this.employees = employees;
        this.structures = structures;
        this.payrollItems = payrollItems;
        this.authorizationService = authorizationService;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.clock = applicationClock;
    }

    public record CreateRequest(String name, String bonusType, String method, BigDecimal value, Integer financialYear,
                                BigDecimal wageCeiling, String payoutMonth, Boolean taxable, String note,
                                String scope, Long departmentId, List<Long> employeeIds) { }
    public record LineDTO(Long id, Long employeeId, String employeeName, String employeeCode, String departmentName,
                          BigDecimal basisAmount, BigDecimal amount, String note, boolean settled) { }
    public record BatchDTO(Long id, String name, String bonusType, String method, BigDecimal value, Integer financialYear,
                           BigDecimal wageCeiling, String payoutMonth, String status, boolean taxable, String note,
                           int employees, BigDecimal total, int paidCount, String createdByName, String approvedByName,
                           LocalDateTime approvedAt, LocalDateTime createdAt, List<LineDTO> lines, List<String> skipped) { }
    public record LineUpdate(BigDecimal amount, String note) { }

    // ------------------------------------------------------------------ batches

    @Transactional(readOnly = true)
    public List<BatchDTO> list() {
        return batches.findAllByCompanyIdAndDeletedFalseOrderByCreatedAtDesc(companyId()).stream().map(b -> toDto(b, false, List.of())).toList();
    }

    @Transactional(readOnly = true)
    public BatchDTO get(Long id) {
        return toDto(load(id), true, List.of());
    }

    @Transactional
    public BatchDTO create(CreateRequest r, String actor) {
        Long companyId = companyId();
        if (r == null) throw new BadRequestException("Fill in the bonus details");
        String name = text(r.name(), 120);
        if (name == null) throw new BadRequestException("Give the bonus a name");
        String type = pick(r.bonusType(), TYPES, "ONE_TIME", "bonus type");
        String method = pick(r.method(), METHODS, "FIXED", "method");
        if ("STATUTORY".equals(type)) method = "STATUTORY";
        BigDecimal value = r.value();
        if ("STATUTORY".equals(method)) {
            if (value == null) value = new BigDecimal("8.33");
            if (value.compareTo(new BigDecimal("8.33")) < 0 || value.compareTo(new BigDecimal("20")) > 0) {
                throw new BadRequestException("Statutory bonus is between 8.33% and 20%");
            }
            if (r.financialYear() == null) throw new BadRequestException("Choose the financial year the bonus is for");
        } else if (value == null || value.signum() <= 0) {
            throw new BadRequestException("Enter the bonus amount or percentage");
        } else if (!"FIXED".equals(method) && value.compareTo(new BigDecimal("500")) > 0) {
            throw new BadRequestException("That percentage looks too high");
        }
        YearMonth payout = r.payoutMonth() == null || r.payoutMonth().isBlank() ? YearMonth.now(clock) : parseMonth(r.payoutMonth());
        if (payout.isBefore(YearMonth.now(clock))) throw new BadRequestException("The payout month can't be in the past");

        BonusBatch b = new BonusBatch();
        b.setCompanyId(companyId);
        b.setName(name);
        b.setBonusType(type);
        b.setMethod(method);
        b.setMethodValue(value);
        b.setFinancialYear("STATUTORY".equals(method) ? r.financialYear() : null);
        b.setWageCeiling("STATUTORY".equals(method) ? (r.wageCeiling() == null ? DEFAULT_WAGE_CEILING : r.wageCeiling()) : null);
        b.setPayoutMonth(payout.toString());
        b.setTaxable(r.taxable() == null || r.taxable());
        b.setNote(text(r.note(), 1000));
        b.setCreatedByName(actor);
        b = batches.save(b);

        List<String> skipped = new ArrayList<>();
        for (Employee e : selectEmployees(companyId, r)) {
            SalaryStructure structure = structures.findByEmployeeIdAndActiveTrueAndDeletedFalse(e.getId()).orElse(null);
            BigDecimal[] calc = calculate(b, e, structure, skipped);
            if (calc == null) continue;
            BonusLine l = new BonusLine();
            l.setCompanyId(companyId);
            l.setBatchId(b.getId());
            l.setEmployeeId(e.getId());
            l.setBasisAmount(calc[0]);
            l.setAmount(calc[1]);
            lines.save(l);
        }
        auditLogService.log("BONUS_BATCH", b.getId(), "CREATED", actor + " created bonus '" + name + "'");
        return toDto(b, true, skipped);
    }

    @Transactional
    public BatchDTO updateLine(Long batchId, Long lineId, LineUpdate u) {
        BonusBatch b = load(batchId);
        if (!BonusBatch.DRAFT.equals(b.getStatus())) throw new BadRequestException("Amounts can only change while the bonus is a draft");
        BonusLine l = lines.findByIdAndBatchIdAndDeletedFalse(lineId, batchId).orElseThrow(() -> new ResourceNotFoundException("Line not found"));
        if (u == null || u.amount() == null || u.amount().signum() < 0) throw new BadRequestException("Enter an amount (0 to leave someone out)");
        l.setAmount(u.amount().setScale(2, RoundingMode.HALF_UP));
        l.setNote(text(u.note(), 300));
        lines.save(l);
        return toDto(b, true, List.of());
    }

    @Transactional
    public BatchDTO approve(Long id, String actor) {
        BonusBatch b = load(id);
        if (!BonusBatch.DRAFT.equals(b.getStatus())) throw new BadRequestException("Only a draft bonus can be approved");
        List<BonusLine> payable = lines.findAllByBatchIdAndDeletedFalseOrderByIdAsc(id).stream().filter(l -> l.getAmount().signum() > 0).toList();
        if (payable.isEmpty()) throw new BadRequestException("Nobody in this bonus has an amount to pay");
        b.setStatus(BonusBatch.APPROVED);
        b.setApprovedByName(actor);
        b.setApprovedAt(LocalDateTime.now(clock));
        batches.save(b);
        BigDecimal total = payable.stream().map(BonusLine::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        auditLogService.log("BONUS_BATCH", b.getId(), "APPROVED", actor + " approved '" + b.getName() + "' - "
                + payable.size() + " employee(s), total " + total.toPlainString());
        String month = YearMonth.parse(b.getPayoutMonth()).getMonth().getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH)
                + " " + YearMonth.parse(b.getPayoutMonth()).getYear();
        for (BonusLine l : payable) {
            employees.findById(l.getEmployeeId()).filter(e -> e.getUser() != null).ifPresent(e ->
                    notificationService.notifyUser(b.getCompanyId(), e.getUser().getId(), "BONUS", b.getName(),
                            "You'll receive a " + b.getName() + " of Rs " + l.getAmount().toPlainString() + " with your " + month + " salary.",
                            "BONUS_BATCH", b.getId(), "NORMAL"));
        }
        return toDto(b, true, List.of());
    }

    @Transactional
    public BatchDTO cancel(Long id, String actor) {
        BonusBatch b = load(id);
        boolean anyPaid = lines.findAllByBatchIdAndDeletedFalseOrderByIdAsc(id).stream().anyMatch(BonusLine::isSettled);
        if (BonusBatch.PAID.equals(b.getStatus()) || BonusBatch.CANCELLED.equals(b.getStatus()) || anyPaid) {
            throw new BadRequestException("This bonus has already been paid (at least in part) and can't be cancelled");
        }
        b.setStatus(BonusBatch.CANCELLED);
        batches.save(b);
        auditLogService.log("BONUS_BATCH", b.getId(), "CANCELLED", actor + " cancelled '" + b.getName() + "'");
        return toDto(b, true, List.of());
    }

    // ------------------------------------------------------------------ payroll hook

    @Override
    public Set<String> sourceTypes() {
        return Set.of(SOURCE);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PayrollAdjustment> adjustmentsFor(Long companyId, Long employeeId, YearMonth period) {
        List<PayrollAdjustment> out = new ArrayList<>();
        for (BonusLine l : lines.findAllByCompanyIdAndEmployeeIdAndSettledFalseAndDeletedFalse(companyId, employeeId)) {
            if (l.getAmount().signum() <= 0) continue;
            BonusBatch b = batches.findById(l.getBatchId()).orElse(null);
            if (b == null || !BonusBatch.APPROVED.equals(b.getStatus()) || b.getPayoutMonth().compareTo(period.toString()) > 0) continue;
            out.add(new PayrollAdjustment("BONUS", b.getName(), l.getAmount(), b.isTaxable(), SOURCE, l.getId()));
        }
        return out;
    }

    @Override
    @Transactional
    public void markSettled(List<PayrollAdjustment> adjustments, Long payrollItemId) {
        Set<Long> touched = new HashSet<>();
        for (PayrollAdjustment a : adjustments) {
            if (!SOURCE.equals(a.sourceType())) continue;
            lines.findById(a.sourceId()).ifPresent(l -> {
                l.setSettled(true);
                l.setPayrollItemId(payrollItemId);
                lines.save(l);
                touched.add(l.getBatchId());
            });
        }
        for (Long batchId : touched) {
            batches.findById(batchId).ifPresent(b -> {
                boolean done = lines.findAllByBatchIdAndDeletedFalseOrderByIdAsc(batchId).stream()
                        .allMatch(l -> l.isSettled() || l.getAmount().signum() == 0);
                if (done) {
                    b.setStatus(BonusBatch.PAID);
                    batches.save(b);
                }
            });
        }
    }

    // ------------------------------------------------------------------ calculation

    /** [basis, amount], or null (with a reason in skipped) when the employee doesn't qualify. */
    private BigDecimal[] calculate(BonusBatch b, Employee e, SalaryStructure structure, List<String> skipped) {
        BigDecimal value = b.getMethodValue();
        switch (b.getMethod()) {
            case "FIXED":
                return new BigDecimal[]{null, value.setScale(2, RoundingMode.HALF_UP)};
            case "PERCENT_BASIC":
            case "PERCENT_GROSS": {
                if (structure == null) {
                    skipped.add(e.getFullName() + " - no salary structure");
                    return null;
                }
                BigDecimal basis = "PERCENT_BASIC".equals(b.getMethod()) ? structure.getComponents().getBasicSalary() : structure.getGrossSalary();
                return new BigDecimal[]{basis, percent(basis, value)};
            }
            default: {
                BigDecimal basic = structure == null ? null : structure.getComponents().getBasicSalary();
                if (basic != null && basic.compareTo(STATUTORY_ELIGIBILITY) > 0) {
                    skipped.add(e.getFullName() + " - basic above Rs 21,000, not covered by the Bonus Act");
                    return null;
                }
                int fy = b.getFinancialYear();
                List<PayrollItem> year = payrollItems.findFinalizedForEmployeeInFinancialYear(b.getCompanyId(), e.getId(), fy, fy + 1);
                BigDecimal wages = BigDecimal.ZERO;
                int days = 0;
                for (PayrollItem i : year) {
                    BigDecimal monthBasic = i.getComponents().getBasicSalary() == null ? BigDecimal.ZERO : i.getComponents().getBasicSalary();
                    wages = wages.add(monthBasic.min(b.getWageCeiling()));
                    days += i.getPaidDays() == null ? 30 : i.getPaidDays().intValue();
                }
                if (days < 30) {
                    skipped.add(e.getFullName() + " - worked under 30 days in FY " + fy + "-" + String.valueOf(fy + 1).substring(2));
                    return null;
                }
                return new BigDecimal[]{wages.setScale(2, RoundingMode.HALF_UP), percent(wages, value)};
            }
        }
    }

    private static BigDecimal percent(BigDecimal basis, BigDecimal pct) {
        return basis.multiply(pct).divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP);
    }

    private List<Employee> selectEmployees(Long companyId, CreateRequest r) {
        String scope = r.scope() == null ? "ALL" : r.scope().trim().toUpperCase(Locale.ROOT);
        List<Employee> active = employees.findAllByCompany_IdAndDeletedFalseOrderByFirstNameAsc(companyId).stream()
                .filter(e -> {
                    String s = EmploymentStatus.normalize(e.getStatus());
                    return !EmploymentStatus.RESIGNED.equals(s) && !EmploymentStatus.TERMINATED.equals(s);
                }).toList();
        return switch (scope) {
            case "DEPARTMENT" -> {
                if (r.departmentId() == null) throw new BadRequestException("Choose a department");
                yield active.stream().filter(e -> e.getDepartment() != null && r.departmentId().equals(e.getDepartment().getId())).toList();
            }
            case "SELECTED" -> {
                if (r.employeeIds() == null || r.employeeIds().isEmpty()) throw new BadRequestException("Choose at least one employee");
                Set<Long> ids = new HashSet<>(r.employeeIds());
                yield active.stream().filter(e -> ids.contains(e.getId())).toList();
            }
            default -> active;
        };
    }

    // ------------------------------------------------------------------ helpers

    private BatchDTO toDto(BonusBatch b, boolean withLines, List<String> skipped) {
        List<BonusLine> rows = lines.findAllByBatchIdAndDeletedFalseOrderByIdAsc(b.getId());
        BigDecimal total = rows.stream().map(BonusLine::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        int paid = (int) rows.stream().filter(BonusLine::isSettled).count();
        List<LineDTO> lineDtos = List.of();
        if (withLines && !rows.isEmpty()) {
            Map<Long, Employee> people = employees.findAllByCompany_IdAndIdInAndDeletedFalseOrderByFirstNameAsc(b.getCompanyId(),
                    rows.stream().map(BonusLine::getEmployeeId).collect(Collectors.toSet())).stream().collect(Collectors.toMap(Employee::getId, e -> e));
            lineDtos = rows.stream().map(l -> {
                Employee e = people.get(l.getEmployeeId());
                return new LineDTO(l.getId(), l.getEmployeeId(), e == null ? "Former employee" : e.getFullName(), e == null ? "" : e.getEmployeeCode(),
                        e == null || e.getDepartment() == null ? null : e.getDepartment().getName(), l.getBasisAmount(), l.getAmount(), l.getNote(), l.isSettled());
            }).sorted(Comparator.comparing(LineDTO::employeeName)).toList();
        }
        return new BatchDTO(b.getId(), b.getName(), b.getBonusType(), b.getMethod(), b.getMethodValue(), b.getFinancialYear(), b.getWageCeiling(),
                b.getPayoutMonth(), b.getStatus(), b.isTaxable(), b.getNote(), (int) rows.stream().filter(l -> l.getAmount().signum() > 0).count(),
                total, paid, b.getCreatedByName(), b.getApprovedByName(), b.getApprovedAt(), b.getCreatedAt(), lineDtos, skipped);
    }

    private static String pick(String value, List<String> allowed, String fallback, String label) {
        String v = value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(v)) throw new BadRequestException("Unknown " + label);
        return v;
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

    private BonusBatch load(Long id) {
        return batches.findByIdAndCompanyIdAndDeletedFalse(id, companyId()).orElseThrow(() -> new ResourceNotFoundException("Bonus not found"));
    }

    private Long companyId() {
        if (!authorizationService.hasOrganizationScope("SALARY_MANAGE")) throw new AccessDeniedException("SALARY_MANAGE requires ORGANIZATION scope.");
        Long companyId = TenantContext.getCurrentTenant();
        if (companyId == null) throw new BadRequestException("Company context is required");
        return companyId;
    }
}
