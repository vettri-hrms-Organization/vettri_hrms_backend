package com.haodaone.leave.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.entity.EmploymentStatus;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.leave.dto.LeaveBalanceDTO;
import com.haodaone.leave.dto.LeavePolicyDTOs.AdjustmentRequest;
import com.haodaone.leave.dto.LeavePolicyDTOs.AdjustmentResult;
import com.haodaone.leave.dto.LeavePolicyDTOs.LedgerEntry;
import com.haodaone.leave.dto.LeavePolicyDTOs.YearEndPreview;
import com.haodaone.leave.dto.LeavePolicyDTOs.YearEndRow;
import com.haodaone.leave.entity.CompOffRequest;
import com.haodaone.leave.entity.LeaveBalance;
import com.haodaone.leave.entity.LeaveBalanceAdjustment;
import com.haodaone.leave.entity.LeaveEncashment;
import com.haodaone.leave.entity.LeaveRequest;
import com.haodaone.leave.entity.LeaveType;
import com.haodaone.leave.repository.CompOffRequestRepository;
import com.haodaone.leave.repository.LeaveBalanceAdjustmentRepository;
import com.haodaone.leave.repository.LeaveBalanceRepository;
import com.haodaone.leave.repository.LeaveEncashmentRepository;
import com.haodaone.leave.repository.LeaveRequestRepository;
import com.haodaone.leave.repository.LeaveTypeRepository;
import com.haodaone.notifications.service.NotificationService;
import com.haodaone.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Policy-aware leave balances. Everything is computed on read from the leave type's policy, the employee's
 * joining date and the "as of" date (see {@link LeaveAccrualCalculator}); LeaveBalance rows only hold the
 * year-end carry-forward plus materialised figures for audit/visibility.
 *
 * <p>remaining = accrued (capped) + carried + adjustments - approved leave - approved/paid encashment - lapsed.
 * Comp-off (system type CO) ignores the leave year: credits are approved CompOffRequests, consumed FIFO by
 * earliest expiry, and the unused part of an expired credit lapses.
 */
@Service
public class LeaveBalanceService {

    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    public static final String COMP_OFF_CODE = "CO";

    private final LeaveTypeRepository leaveTypeRepository;
    private final LeaveBalanceRepository leaveBalanceRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final LeaveBalanceAdjustmentRepository adjustmentRepository;
    private final LeaveEncashmentRepository encashmentRepository;
    private final CompOffRequestRepository compOffRequestRepository;
    private final EmployeeRepository employeeRepository;
    private final AuditLogService auditLogService;
    private NotificationService notificationService;

    public LeaveBalanceService(LeaveTypeRepository leaveTypeRepository, LeaveBalanceRepository leaveBalanceRepository,
                               LeaveRequestRepository leaveRequestRepository, LeaveBalanceAdjustmentRepository adjustmentRepository,
                               LeaveEncashmentRepository encashmentRepository, CompOffRequestRepository compOffRequestRepository,
                               EmployeeRepository employeeRepository, AuditLogService auditLogService) {
        this.leaveTypeRepository = leaveTypeRepository;
        this.leaveBalanceRepository = leaveBalanceRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.encashmentRepository = encashmentRepository;
        this.compOffRequestRepository = compOffRequestRepository;
        this.employeeRepository = employeeRepository;
        this.auditLogService = auditLogService;
    }

    @Autowired
    public void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public static boolean isCompOff(LeaveType type) {
        return type != null && type.isSystemManaged() && COMP_OFF_CODE.equalsIgnoreCase(type.getCode());
    }

    public static LocalDate today() {
        return LocalDate.now(IST);
    }

    /** Current year = today; past years = Dec 31 (closing); future years = Jan 1 (what is credited up front). */
    public static LocalDate asOfFor(int year) {
        LocalDate today = today();
        if (year == today.getYear()) return today;
        return year < today.getYear() ? LocalDate.of(year, 12, 31) : LocalDate.of(year, 1, 1);
    }

    /** Full computation for one employee/type/year. */
    public record BalanceDetail(LeaveType type, int year, LocalDate asOf, double accrued, double accruedUncapped,
                                double annualEntitlement, double carried, double adjustments, double used,
                                double encashed, double lapsed, double remaining, LocalDate carryExpiry,
                                List<LeaveAccrualCalculator.Credit> schedule, LeaveAccrualCalculator.CompOffState compOff) {
    }

    // ------------------------------------------------------------------ balances

    @Transactional(readOnly = true)
    public List<LeaveBalanceDTO> balancesFor(Long companyId, Long employeeId, int year) {
        Employee employee = employeeRepository.findByIdAndCompany_IdAndDeletedFalse(employeeId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
        LocalDate asOf = asOfFor(year);
        return activeTypes(companyId).stream()
                .map(type -> toDto(compute(companyId, employee, type, year, asOf)))
                .toList();
    }

    public LeaveBalanceDTO toDto(BalanceDetail d) {
        LeaveType type = d.type();
        LeaveBalanceDTO dto = new LeaveBalanceDTO(type.getId(), type.getName(), type.getCode(), d.year(), d.accrued(),
                d.carried(), d.adjustments(), d.used(), d.encashed(), d.lapsed());
        double negative = type.getNegativeBalanceDays() == null ? 0 : Math.max(0, type.getNegativeBalanceDays());
        return dto.withPolicy(isCompOff(type) ? "COMP_OFF" : LeaveAccrualCalculator.normalizeMode(type.getAccrualMode()),
                d.asOf(), d.annualEntitlement(), negative, type.getMaxBalanceDays(), type.isEncashable(), type.isSystemManaged());
    }

    @Transactional(readOnly = true)
    public BalanceDetail compute(Long companyId, Employee employee, LeaveType type, int year, LocalDate asOf) {
        Long employeeId = employee.getId();
        if (isCompOff(type)) {
            List<LeaveAccrualCalculator.CompOffCredit> credits = compOffRequestRepository
                    .findAllByCompany_IdAndEmployee_IdAndStatusAndDeletedFalseOrderByExpiresOnAsc(companyId, employeeId, "APPROVED").stream()
                    .map(c -> new LeaveAccrualCalculator.CompOffCredit(c.getId(), c.getWorkDate(), c.getExpiresOn(), c.getDays()))
                    .toList();
            List<LeaveAccrualCalculator.Debit> debits = leaveRequestRepository
                    .findApprovedForEmployeeAndType(companyId, employeeId, type.getId()).stream()
                    .map(r -> new LeaveAccrualCalculator.Debit(r.getStartDate(), r.getDays()))
                    .toList();
            LeaveAccrualCalculator.CompOffState state = LeaveAccrualCalculator.compOffFifo(credits, debits, asOf);
            double adjustments = adjustmentRepository.sumDaysAllYears(companyId, employeeId, type.getId());
            double remaining = state.credited() + adjustments - state.consumed() - state.lapsed();
            return new BalanceDetail(type, year, asOf, state.credited(), state.credited(), 0, 0, adjustments,
                    state.consumed(), 0, state.lapsed(), remaining, null, List.of(), state);
        }

        LeaveAccrualCalculator.Policy policy = LeaveAccrualCalculator.Policy.of(type);
        LocalDate joining = employee.getDateOfJoining();
        List<LeaveAccrualCalculator.Credit> schedule = LeaveAccrualCalculator.schedule(policy, joining, year, asOf);
        double accruedRaw = 0;
        for (LeaveAccrualCalculator.Credit credit : schedule) accruedRaw += credit.days();
        double annual = LeaveAccrualCalculator.allocatedAsOf(policy, joining, year, LocalDate.of(year, 12, 31));

        LeaveBalance row = leaveBalanceRepository
                .findByEmployee_Company_IdAndEmployeeIdAndLeaveTypeIdAndYear(companyId, employeeId, type.getId(), year)
                .orElse(null);
        double carried = row != null ? row.getCarriedForwardDays() : 0;
        double adjustments = adjustmentRepository.sumDays(companyId, employeeId, type.getId(), year);
        double used = leaveRequestRepository.sumApprovedDays(companyId, employeeId, type.getId(), year);
        double encashed = encashmentRepository.sumConsumedDays(companyId, employeeId, type.getId(), year);

        double carryLapsed = 0;
        LocalDate carryExpiry = null;
        Integer expiryMonths = type.getCarryForwardExpiryMonths();
        if (carried > 0 && expiryMonths != null && expiryMonths > 0) {
            LocalDate yearStart = LocalDate.of(year, 1, 1);
            carryExpiry = yearStart.plusMonths(expiryMonths);
            boolean expired = !asOf.isBefore(carryExpiry);
            double usedBefore = expired
                    ? leaveRequestRepository.sumApprovedDaysBetween(companyId, employeeId, type.getId(), yearStart, carryExpiry.minusDays(1))
                    : 0;
            carryLapsed = LeaveAccrualCalculator.carryForwardLapsed(carried, usedBefore, expired);
        }
        double accrued = LeaveAccrualCalculator.capAllocation(accruedRaw, carried + adjustments,
                used + encashed + carryLapsed, type.getMaxBalanceDays());
        double remaining = accrued + carried + adjustments - used - encashed - carryLapsed;
        return new BalanceDetail(type, year, asOf, round2(accrued), round2(accruedRaw), annual, carried, adjustments,
                used, encashed, carryLapsed, round2(remaining), carryExpiry, schedule, null);
    }

    // ------------------------------------------------------------------ ledger

    @Transactional(readOnly = true)
    public List<LedgerEntry> ledger(Long employeeId, int year) {
        Long companyId = requiredTenant();
        Employee employee = employeeRepository.findByIdAndCompany_IdAndDeletedFalse(employeeId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
        LocalDate asOf = asOfFor(year);
        List<LeaveBalanceAdjustment> adjustments = adjustmentRepository
                .findAllByCompany_IdAndEmployee_IdAndDeletedFalseOrderByEffectiveDateAscIdAsc(companyId, employeeId);
        List<LeaveEncashment> encashments = encashmentRepository
                .findAllByCompany_IdAndEmployee_IdAndDeletedFalseOrderByCreatedAtDesc(companyId, employeeId);

        List<LedgerEntry> all = new ArrayList<>();
        for (LeaveType type : activeTypes(companyId)) {
            BalanceDetail d = compute(companyId, employee, type, year, asOf);
            List<RawEntry> raw = new ArrayList<>();
            boolean compOff = isCompOff(type);
            if (compOff) {
                for (CompOffRequest credit : compOffRequestRepository
                        .findAllByCompany_IdAndEmployee_IdAndStatusAndDeletedFalseOrderByExpiresOnAsc(companyId, employeeId, "APPROVED")) {
                    raw.add(new RawEntry(credit.getWorkDate(), "COMP_OFF", credit.getDays(),
                            "Comp-off for working on " + credit.getWorkDate() + (credit.getExpiresOn() != null ? " (expires " + credit.getExpiresOn() + ")" : ""),
                            credit.getId()));
                }
                if (d.compOff() != null) {
                    for (LeaveAccrualCalculator.CompOffLapse lapse : d.compOff().lapses()) {
                        raw.add(new RawEntry(lapse.expiredOn().plusDays(1), "LAPSE", -lapse.days(), "Unused comp-off expired", lapse.creditId()));
                    }
                }
            } else {
                if (d.carried() > 0) {
                    raw.add(new RawEntry(LocalDate.of(year, 1, 1), "CARRY_FORWARD", d.carried(), "Carried forward from " + (year - 1), null));
                }
                String label = switch (LeaveAccrualCalculator.normalizeMode(type.getAccrualMode())) {
                    case LeaveAccrualCalculator.MONTHLY -> "Monthly accrual";
                    case LeaveAccrualCalculator.QUARTERLY -> "Quarterly accrual";
                    default -> "Annual allocation";
                };
                for (LeaveAccrualCalculator.Credit credit : d.schedule()) {
                    if (credit.days() != 0) raw.add(new RawEntry(credit.date(), "ACCRUAL", credit.days(), label, null));
                }
                double trimmed = round2(d.accruedUncapped() - d.accrued());
                if (trimmed > 0) {
                    raw.add(new RawEntry(asOf, "CAP", -trimmed, "Accrual above the " + fmt(type.getMaxBalanceDays()) + "-day balance cap", null));
                }
                if (d.lapsed() > 0 && d.carryExpiry() != null) {
                    raw.add(new RawEntry(d.carryExpiry(), "LAPSE", -d.lapsed(), "Unused carried-forward days expired", null));
                }
            }
            for (LeaveBalanceAdjustment adjustment : adjustments) {
                if (!adjustment.getLeaveType().getId().equals(type.getId())) continue;
                if (!compOff && adjustment.getLeaveYear() != year) continue;
                raw.add(new RawEntry(adjustment.getEffectiveDate(), "ADJUSTMENT", adjustment.getDays(),
                        "Manual adjustment: " + adjustment.getReason()
                                + (adjustment.getAdjustedBy() != null ? " (by " + adjustment.getAdjustedBy().getFullName() + ")" : ""),
                        adjustment.getId()));
            }
            for (LeaveRequest request : leaveRequestRepository.findApprovedForEmployeeAndType(companyId, employeeId, type.getId())) {
                if (!compOff && request.getStartDate().getYear() != year) continue;
                raw.add(new RawEntry(request.getStartDate(), "LEAVE", -request.getDays(),
                        "Leave " + request.getStartDate() + (request.getEndDate().equals(request.getStartDate()) ? "" : " to " + request.getEndDate()),
                        request.getId()));
            }
            for (LeaveEncashment encashment : encashments) {
                if (!encashment.getLeaveType().getId().equals(type.getId()) || encashment.getLeaveYear() != year) continue;
                if (!"APPROVED".equals(encashment.getStatus()) && !"PAID".equals(encashment.getStatus())) continue;
                LocalDate date = encashment.getDecidedAt() != null ? encashment.getDecidedAt().toLocalDate() : asOf;
                raw.add(new RawEntry(date, "ENCASHMENT", -encashment.getDays(), "Leave encashment", encashment.getId()));
            }
            raw.sort(Comparator.comparing(RawEntry::date).thenComparing(e -> e.days() < 0 ? 1 : 0));
            double running = 0;
            for (RawEntry entry : raw) {
                running = round2(running + entry.days());
                all.add(new LedgerEntry(entry.date(), type.getId(), type.getName(), entry.kind(),
                        entry.days() >= 0 ? "CREDIT" : "DEBIT", Math.abs(entry.days()), entry.description(),
                        entry.referenceId(), running));
            }
        }
        return all;
    }

    private record RawEntry(LocalDate date, String kind, double days, String description, Long referenceId) {
    }

    // ------------------------------------------------------------------ manual adjustment

    @Transactional
    public AdjustmentResult adjust(AdjustmentRequest request) {
        Long companyId = requiredTenant();
        if (request == null || request.getEmployeeId() == null || request.getLeaveTypeId() == null) {
            throw new BadRequestException("Employee and leave type are required");
        }
        double days = request.getDays() == null ? 0 : round2(request.getDays());
        if (days == 0 || Math.abs(days) > 366) {
            throw new BadRequestException("Adjustment must be a non-zero number of days (at most 366)");
        }
        String reason = request.getReason() == null ? "" : request.getReason().trim();
        if (reason.isEmpty()) {
            throw new BadRequestException("A reason is required for a balance adjustment");
        }
        Employee employee = employeeRepository.findByIdAndCompany_IdAndDeletedFalse(request.getEmployeeId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
        LeaveType type = leaveTypeRepository.findByIdAndCompany_IdAndDeletedFalse(request.getLeaveTypeId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave type not found"));
        int year = request.getYear() != null ? request.getYear() : today().getYear();
        employeeRepository.lockById(employee.getId());

        BalanceDetail before = compute(companyId, employee, type, year, asOfFor(year));
        double negative = type.getNegativeBalanceDays() == null ? 0 : Math.max(0, type.getNegativeBalanceDays());
        if (days < 0 && before.remaining() + days < -negative - 1e-9) {
            throw new BadRequestException(String.format(Locale.ROOT,
                    "Deducting %s day(s) would take %s below the allowed balance (%s day(s) available)",
                    fmt(-days), type.getName(), fmt(before.remaining())));
        }

        LeaveBalanceAdjustment adjustment = new LeaveBalanceAdjustment();
        adjustment.setCompany(employee.getCompany());
        adjustment.setEmployee(employee);
        adjustment.setLeaveType(type);
        adjustment.setLeaveYear(year);
        adjustment.setDays(days);
        adjustment.setEffectiveDate(asOfFor(year));
        adjustment.setReason(reason.length() > 500 ? reason.substring(0, 500) : reason);
        currentEmployee().ifPresent(adjustment::setAdjustedBy);
        LeaveBalanceAdjustment saved = adjustmentRepository.save(adjustment);

        String summary = String.format(Locale.ROOT, "%s%s day(s) of %s for %s (%d): %s",
                days > 0 ? "+" : "", fmt(days), type.getName(), employee.getFullName(), year, reason);
        auditLogService.log("LeaveBalanceAdjustment", saved.getId(), "ADJUST", summary);
        if (notificationService != null && employee.getUser() != null) {
            notificationService.notifyUser(companyId, employee.getUser().getId(), "LEAVE_BALANCE_ADJUSTED",
                    "Leave balance adjusted",
                    String.format(Locale.ROOT, "HR %s %s day(s) %s your %s balance. Reason: %s",
                            days > 0 ? "added" : "deducted", fmt(Math.abs(days)), days > 0 ? "to" : "from", type.getName(), reason),
                    "LeaveBalanceAdjustment", saved.getId(), "NORMAL");
        }
        return new AdjustmentResult(saved.getId(), employee.getId(), type.getId(), type.getName(), year, days,
                saved.getReason(), round2(before.remaining() + days));
    }

    // ------------------------------------------------------------------ accrual materialisation

    /** Writes each employee's accrued-to-date allocation into this year's LeaveBalance row. Idempotent (overwrites with the computed value). */
    @Transactional
    public int materialiseAccruals(Long companyId, LocalDate today) {
        int year = today.getYear();
        int written = 0;
        List<LeaveType> types = activeTypes(companyId).stream().filter(t -> !isCompOff(t)).toList();
        if (types.isEmpty()) return 0;
        for (Employee employee : employeeRepository.findAllByCompany_IdAndDeletedFalseOrderByFirstNameAsc(companyId)) {
            if (!isEmployedOn(employee, today)) continue;
            for (LeaveType type : types) {
                BalanceDetail d = compute(companyId, employee, type, year, today);
                LeaveBalance row = findOrNewRow(companyId, employee, type, year);
                row.setAllocatedDays(d.accrued());
                row.setAccruedThrough(today);
                leaveBalanceRepository.save(row);
                written++;
            }
        }
        return written;
    }

    // ------------------------------------------------------------------ year end

    @Transactional(readOnly = true)
    public YearEndPreview previewYearEnd(Long companyId, int closingYear) {
        validateClosingYear(closingYear);
        LocalDate closingDate = LocalDate.of(closingYear, 12, 31);
        List<LeaveType> types = activeTypes(companyId).stream().filter(t -> !isCompOff(t)).toList();
        List<YearEndRow> rows = new ArrayList<>();
        LocalDateTime lastProcessed = null;
        java.util.Set<Long> employeeIds = new java.util.HashSet<>();
        for (Employee employee : employeeRepository.findAllByCompany_IdAndDeletedFalseOrderByFirstNameAsc(companyId)) {
            if (!isEmployedOn(employee, closingDate)) continue;
            if (employee.getDateOfJoining() != null && employee.getDateOfJoining().isAfter(closingDate)) continue;
            for (LeaveType type : types) {
                BalanceDetail d = compute(companyId, employee, type, closingYear, closingDate);
                LeaveAccrualCalculator.YearEndResult r = LeaveAccrualCalculator.yearEnd(d.remaining(), type.isCarryForward(),
                        type.getMaxCarryForwardDays(), type.getMaxBalanceDays(), type.isEncashable(),
                        type.getMaxEncashableDaysPerYear(), d.encashed());
                Optional<LeaveBalance> closingRow = leaveBalanceRepository
                        .findByEmployee_Company_IdAndEmployeeIdAndLeaveTypeIdAndYear(companyId, employee.getId(), type.getId(), closingYear);
                LocalDateTime processedAt = closingRow.map(LeaveBalance::getYearEndProcessedAt).orElse(null);
                if (processedAt != null && (lastProcessed == null || processedAt.isAfter(lastProcessed))) lastProcessed = processedAt;
                rows.add(new YearEndRow(employee.getId(), employee.getFullName(), employee.getEmployeeCode(), type.getId(),
                        type.getName(), closingYear, round2(r.closing()), round2(r.carried()), round2(r.lapsed()),
                        round2(r.encashable()), processedAt != null));
                employeeIds.add(employee.getId());
            }
        }
        double carried = rows.stream().mapToDouble(YearEndRow::carried).sum();
        double lapsed = rows.stream().mapToDouble(YearEndRow::lapsed).sum();
        double encashable = rows.stream().mapToDouble(YearEndRow::encashable).sum();
        boolean projected = closingYear >= today().getYear();
        return new YearEndPreview(closingYear, closingYear + 1, employeeIds.size(), round2(carried), round2(lapsed),
                round2(encashable), projected, lastProcessed, rows);
    }

    /**
     * Writes next year's carriedForwardDays (set, not added - re-running gives the same result) and stamps the closing
     * year's row with closing/lapsed figures. Safe to re-run, e.g. after a late approval in the closing year.
     */
    @Transactional
    public YearEndPreview runYearEnd(Long companyId, int closingYear) {
        YearEndPreview preview = previewYearEnd(companyId, closingYear);
        LocalDateTime now = LocalDateTime.now(IST);
        Map<Long, Employee> employees = new HashMap<>();
        Map<Long, LeaveType> types = new HashMap<>();
        for (LeaveType type : activeTypes(companyId)) types.put(type.getId(), type);
        for (YearEndRow row : preview.rows()) {
            Employee employee = employees.computeIfAbsent(row.employeeId(),
                    id -> employeeRepository.findByIdAndCompany_IdAndDeletedFalse(id, companyId).orElse(null));
            LeaveType type = types.get(row.leaveTypeId());
            if (employee == null || type == null) continue;

            LeaveBalance closing = findOrNewRow(companyId, employee, type, closingYear);
            closing.setClosingBalanceDays(row.closing());
            closing.setLapsedDays(row.lapsed());
            closing.setYearEndProcessedAt(now);
            closing.setAllocatedDays(LeaveAccrualCalculator.allocatedAsOf(LeaveAccrualCalculator.Policy.of(type),
                    employee.getDateOfJoining(), closingYear, LocalDate.of(closingYear, 12, 31)));
            leaveBalanceRepository.save(closing);

            LeaveBalance next = findOrNewRow(companyId, employee, type, closingYear + 1);
            next.setCarriedForwardDays(row.carried());
            if (next.getAccruedThrough() == null) {
                next.setAllocatedDays(LeaveAccrualCalculator.allocatedAsOf(LeaveAccrualCalculator.Policy.of(type),
                        employee.getDateOfJoining(), closingYear + 1, LocalDate.of(closingYear + 1, 1, 1)));
            }
            leaveBalanceRepository.save(next);
        }
        Long previousTenant = TenantContext.getCurrentTenant();
        try {
            TenantContext.setCurrentTenant(companyId);
            auditLogService.log("LeaveYearEnd", (long) closingYear, "RUN", String.format(Locale.ROOT,
                    "Year-end %d processed for %d employee(s): %s day(s) carried to %d, %s lapsed, %s encashable",
                    closingYear, preview.employees(), fmt(preview.totalCarried()), closingYear + 1,
                    fmt(preview.totalLapsed()), fmt(preview.totalEncashable())));
        } finally {
            if (previousTenant != null) TenantContext.setCurrentTenant(previousTenant); else TenantContext.clear();
        }
        return previewYearEnd(companyId, closingYear);
    }

    private void validateClosingYear(int closingYear) {
        int current = today().getYear();
        if (closingYear < 2000 || closingYear > current) {
            throw new BadRequestException("Year-end can be processed for " + current + " or an earlier year");
        }
    }

    // ------------------------------------------------------------------ helpers

    public List<LeaveType> activeTypes(Long companyId) {
        return leaveTypeRepository.findAllByCompany_IdAndDeletedFalseOrderByNameAsc(companyId).stream()
                .filter(LeaveType::isActive)
                .toList();
    }

    private LeaveBalance findOrNewRow(Long companyId, Employee employee, LeaveType type, int year) {
        return leaveBalanceRepository
                .findByEmployee_Company_IdAndEmployeeIdAndLeaveTypeIdAndYear(companyId, employee.getId(), type.getId(), year)
                .orElseGet(() -> {
                    LeaveBalance row = new LeaveBalance();
                    row.setCompany(employee.getCompany());
                    row.setEmployee(employee);
                    row.setLeaveType(type);
                    row.setYear(year);
                    row.setAllocatedDays(0);
                    row.setCarriedForwardDays(0);
                    return row;
                });
    }

    /** Resigned/Terminated employees count until their last working date. */
    public static boolean isEmployedOn(Employee employee, LocalDate date) {
        String status = employee.getStatus();
        boolean exited = EmploymentStatus.RESIGNED.equals(status) || EmploymentStatus.TERMINATED.equals(status);
        if (!exited) return true;
        return employee.getLastWorkingDate() != null && !employee.getLastWorkingDate().isBefore(date);
    }

    Optional<Employee> currentEmployee() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) return Optional.empty();
        return employeeRepository.findByUser_UsernameAndDeletedFalse(authentication.getName());
    }

    static Long requiredTenant() {
        Long tenant = TenantContext.getCurrentTenant();
        if (tenant == null) throw new BadRequestException("Company context is required");
        return tenant;
    }

    static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    static String fmt(Double value) {
        if (value == null) return "0";
        double v = round2(value);
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
