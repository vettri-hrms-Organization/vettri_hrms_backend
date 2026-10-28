package com.haodaone.attendance.shift.service;

import com.haodaone.attendance.shift.dto.OvertimeEntryDTO;
import com.haodaone.attendance.shift.dto.OvertimePayable;
import com.haodaone.attendance.shift.entity.AttendanceShiftSettings;
import com.haodaone.attendance.shift.entity.OvertimeEntry;
import com.haodaone.attendance.shift.repository.OvertimeEntryRepository;
import com.haodaone.attendance.shift.service.DailyAttendanceService.EmployeeDay;
import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.company.service.WorkingWeek;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.leave.repository.HolidayRepository;
import com.haodaone.notifications.service.NotificationService;
import com.haodaone.salary.repository.SalaryStructureRepository;
import com.haodaone.security.AuthorizationService;
import com.haodaone.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Overtime entries: generated per day from the shift evaluation (nightly job or employee claim),
 * approved by managers (OVERTIME_APPROVE, TEAM scope) or HR (ORGANIZATION scope), priced with the
 * company's OT rate, and handed to payroll via {@link #approvedUnpaidOvertime} / {@link #markPaid}.
 */
@Service("overtimeService")
public class OvertimeService {

    private static final int MAX_LIST_DAYS = 366;

    private final OvertimeEntryRepository overtimeRepository;
    private final DailyAttendanceService dailyAttendanceService;
    private final ShiftService shiftService;
    private final EmployeeRepository employeeRepository;
    private final CompanyRepository companyRepository;
    private final HolidayRepository holidayRepository;
    private final SalaryStructureRepository salaryStructureRepository;
    private final AuthorizationService authorizationService;
    private final AuditLogService auditLogService;
    private NotificationService notificationService;

    public OvertimeService(OvertimeEntryRepository overtimeRepository,
                           DailyAttendanceService dailyAttendanceService,
                           ShiftService shiftService,
                           EmployeeRepository employeeRepository,
                           CompanyRepository companyRepository,
                           HolidayRepository holidayRepository,
                           SalaryStructureRepository salaryStructureRepository,
                           AuthorizationService authorizationService,
                           AuditLogService auditLogService) {
        this.overtimeRepository = overtimeRepository;
        this.dailyAttendanceService = dailyAttendanceService;
        this.shiftService = shiftService;
        this.employeeRepository = employeeRepository;
        this.companyRepository = companyRepository;
        this.holidayRepository = holidayRepository;
        this.salaryStructureRepository = salaryStructureRepository;
        this.authorizationService = authorizationService;
        this.auditLogService = auditLogService;
    }

    @Autowired(required = false)
    public void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    // ---------------------------------------------------------------- generation

    /**
     * System generation for one company and day: creates a PENDING entry for every employee whose
     * evaluation shows overtime, refreshes the minutes of still-pending entries, and never touches
     * reviewed or paid entries. Idempotent; works without a tenant context. Returns entries created.
     */
    @Transactional
    public int generateForDate(Long companyId, LocalDate date) {
        Company company = companyRepository.findById(companyId).orElse(null);
        if (company == null || date == null || date.isAfter(dailyAttendanceService.today())) return 0;
        List<Employee> employees = employeeRepository.findAllByCompany_IdAndDeletedFalseOrderByFirstNameAsc(companyId);
        Map<Long, Map<LocalDate, EmployeeDay>> evaluated = dailyAttendanceService.evaluate(companyId, employees, date, date);
        int created = 0;
        for (Employee employee : employees) {
            EmployeeDay day = evaluated.getOrDefault(employee.getId(), Map.of()).get(date);
            if (day == null || day.evaluation().overtimeMinutes() <= 0) continue;
            Optional<OvertimeEntry> existing = overtimeRepository.findByEmployee_IdAndWorkDate(employee.getId(), date);
            if (existing.isPresent() && !existing.get().isDeleted()) {
                OvertimeEntry entry = existing.get();
                if (OvertimeEntry.PENDING.equals(entry.getStatus())
                        && entry.getComputedMinutes() != day.evaluation().overtimeMinutes()) {
                    entry.setComputedMinutes(day.evaluation().overtimeMinutes());
                    overtimeRepository.save(entry);
                }
                continue;
            }
            OvertimeEntry entry = existing.orElseGet(OvertimeEntry::new);
            fillNew(entry, company, employee, day, "SYSTEM", null);
            overtimeRepository.save(entry);
            created++;
        }
        return created;
    }

    /** Employee self-claim for a past day (or today): uses the evaluated overtime minutes. */
    @Transactional
    public OvertimeEntryDTO claim(LocalDate date, String note) {
        Long companyId = requiredTenant();
        Employee me = currentEmployee();
        if (date.isAfter(dailyAttendanceService.today())) {
            throw new BadRequestException("Overtime cannot be claimed for a future date.");
        }
        EmployeeDay day = dailyAttendanceService.evaluate(companyId, List.of(me), date, date)
                .getOrDefault(me.getId(), Map.of()).get(date);
        if (day == null || day.evaluation().overtimeMinutes() <= 0) {
            throw new BadRequestException("No overtime was recorded for " + date
                    + ". Overtime counts only when your shift allows it, you punched out after the shift end, "
                    + "and the extra time meets the shift's minimum.");
        }
        Optional<OvertimeEntry> existing = overtimeRepository.findByEmployee_IdAndWorkDate(me.getId(), date);
        OvertimeEntry entry;
        if (existing.isPresent() && !existing.get().isDeleted()) {
            entry = existing.get();
            if (!OvertimeEntry.PENDING.equals(entry.getStatus())) {
                throw new BadRequestException("Overtime for " + date + " has already been " + entry.getStatus().toLowerCase() + ".");
            }
            entry.setComputedMinutes(day.evaluation().overtimeMinutes());
            entry.setSource("EMPLOYEE");
            entry.setEmployeeNote(trim(note));
        } else {
            entry = existing.orElseGet(OvertimeEntry::new);
            fillNew(entry, me.getCompany(), me, day, "EMPLOYEE", trim(note));
        }
        OvertimeEntry saved = overtimeRepository.save(entry);
        if (notificationService != null) {
            notificationService.notifyPermissionRecipients(companyId, me.getId(), "OVERTIME_APPROVE",
                    "OVERTIME_REQUEST", "Overtime needs approval",
                    me.getFullName() + " claimed " + formatMinutes(saved.getComputedMinutes()) + " overtime for " + date + ".",
                    "OvertimeEntry", saved.getId());
        }
        auditLogService.log("OvertimeEntry", saved.getId(), "CLAIM",
                "Overtime claimed for employee " + me.getId() + " on " + date + " (" + saved.getComputedMinutes() + " min)");
        return OvertimeEntryDTO.from(saved);
    }

    private void fillNew(OvertimeEntry entry, Company company, Employee employee, EmployeeDay day, String source, String note) {
        entry.setCompany(company);
        entry.setEmployee(employee);
        entry.setWorkDate(day.date());
        entry.setShiftId(day.resolved().shift().shiftId());
        entry.setShiftName(day.resolved().shift().name());
        entry.setComputedMinutes(day.evaluation().overtimeMinutes());
        entry.setApprovedMinutes(null);
        entry.setStatus(OvertimeEntry.PENDING);
        entry.setSource(source);
        entry.setEmployeeNote(note);
        entry.setReviewedByEmployee(null);
        entry.setReviewedAt(null);
        entry.setReviewNote(null);
        entry.setHourlyRate(null);
        entry.setRateMultiplier(null);
        entry.setAmount(null);
        entry.setPayrollItemId(null);
        entry.setPaidAt(null);
        entry.setDeleted(false);
        entry.setDeletedAt(null);
    }

    // ---------------------------------------------------------------- queries

    @Transactional(readOnly = true)
    public List<OvertimeEntryDTO> mine() {
        Long companyId = requiredTenant();
        Employee me = currentEmployee();
        return overtimeRepository.findAllByCompany_IdAndEmployee_IdAndDeletedFalseOrderByWorkDateDesc(companyId, me.getId())
                .stream().map(OvertimeEntryDTO::from).toList();
    }

    @Transactional(readOnly = true)
    public List<OvertimeEntryDTO> list(String status, LocalDate from, LocalDate to) {
        Long companyId = requiredTenant();
        if (from == null || to == null || to.isBefore(from) || from.plusDays(MAX_LIST_DAYS).isBefore(to)) {
            throw new BadRequestException("Choose a valid date range of at most one year.");
        }
        Optional<Set<Long>> scope = authorizationService.resolveEmployeeIds("OVERTIME_APPROVE");
        if (scope.isPresent() && scope.get().isEmpty()) return List.of();
        List<OvertimeEntry> entries = scope.isEmpty()
                ? overtimeRepository.findAllByCompany_IdAndDeletedFalseAndWorkDateBetweenOrderByWorkDateDesc(companyId, from, to)
                : overtimeRepository.findAllByCompany_IdAndEmployee_IdInAndDeletedFalseAndWorkDateBetweenOrderByWorkDateDesc(
                        companyId, scope.get(), from, to);
        String wanted = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        return entries.stream()
                .filter(e -> wanted == null || wanted.equals(e.getStatus()))
                .map(OvertimeEntryDTO::from).toList();
    }

    // ---------------------------------------------------------------- review

    @Transactional(readOnly = true)
    public boolean canReview(Long id) {
        Long companyId = TenantContext.getCurrentTenant();
        if (companyId == null || id == null) return false;
        OvertimeEntry entry = overtimeRepository.findByIdAndCompany_IdAndDeletedFalse(id, companyId).orElse(null);
        if (entry == null || !OvertimeEntry.PENDING.equals(entry.getStatus())) return false;
        Employee me = authorizationService.resolveCurrentEmployee();
        if (me != null && me.getId().equals(entry.getEmployee().getId())) return false; // no self-approval
        return authorizationService.isAllowed("OVERTIME_APPROVE", "EMPLOYEE", entry.getEmployee().getId());
    }

    @Transactional
    public OvertimeEntryDTO review(Long id, boolean approved, Integer approvedMinutes, String note) {
        Long companyId = requiredTenant();
        OvertimeEntry entry = overtimeRepository.findByIdAndCompany_IdAndDeletedFalse(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Overtime entry not found."));
        if (!canReview(id)) {
            throw new AccessDeniedException("You are not allowed to review this overtime entry.");
        }
        Employee reviewer = currentEmployee();
        entry.setReviewedByEmployee(reviewer);
        entry.setReviewedAt(LocalDateTime.now());
        entry.setReviewNote(trim(note));
        if (approved) {
            int minutes = approvedMinutes == null ? entry.getComputedMinutes() : approvedMinutes;
            if (minutes <= 0 || minutes > entry.getComputedMinutes()) {
                throw new BadRequestException("Approved minutes must be between 1 and " + entry.getComputedMinutes() + ".");
            }
            entry.setApprovedMinutes(minutes);
            entry.setStatus(OvertimeEntry.APPROVED);
            price(entry);
        } else {
            entry.setStatus(OvertimeEntry.REJECTED);
        }
        OvertimeEntry saved = overtimeRepository.save(entry);
        if (notificationService != null && saved.getEmployee().getUser() != null) {
            notificationService.notifyUser(companyId, saved.getEmployee().getUser().getId(), "OVERTIME_DECISION",
                    approved ? "Overtime approved" : "Overtime declined",
                    "Your overtime for " + saved.getWorkDate() + (approved
                            ? " was approved (" + formatMinutes(saved.getApprovedMinutes()) + ")."
                            : " was declined."),
                    "OvertimeEntry", saved.getId(), "NORMAL");
        }
        auditLogService.log("OvertimeEntry", saved.getId(), approved ? "APPROVE" : "REJECT",
                (approved ? "Approved " + saved.getApprovedMinutes() + " min" : "Rejected") + " overtime of employee "
                        + saved.getEmployee().getId() + " on " + saved.getWorkDate()
                        + (saved.getAmount() == null ? "" : " amount " + saved.getAmount()));
        return OvertimeEntryDTO.from(saved);
    }

    // ---------------------------------------------------------------- payroll hooks

    /**
     * Approved overtime not yet paid with a work date on or before the end of {@code period}
     * (so approvals that missed an earlier run are picked up). Entries priced as null at approval
     * (e.g. no salary structure yet) are re-priced now; the amount is never null (ZERO if unpriceable).
     * Works without a tenant context.
     */
    @Transactional
    public List<OvertimePayable> approvedUnpaidOvertime(Long companyId, Long employeeId, YearMonth period) {
        List<OvertimePayable> result = new ArrayList<>();
        for (OvertimeEntry entry : overtimeRepository
                .findAllByCompany_IdAndEmployee_IdAndStatusAndPayrollItemIdIsNullAndDeletedFalseAndWorkDateLessThanEqualOrderByWorkDateAsc(
                        companyId, employeeId, OvertimeEntry.APPROVED, period.atEndOfMonth())) {
            if (entry.getAmount() == null) {
                price(entry);
                overtimeRepository.save(entry);
            }
            int minutes = entry.getApprovedMinutes() == null ? entry.getComputedMinutes() : entry.getApprovedMinutes();
            result.add(new OvertimePayable(entry.getId(), entry.getWorkDate(), minutes,
                    entry.getAmount() == null ? BigDecimal.ZERO : entry.getAmount()));
        }
        return result;
    }

    /** Marks approved entries as paid by a payroll line. Unknown / non-approved / already-paid ids are ignored. */
    @Transactional
    public void markPaid(List<Long> ids, Long payrollItemId) {
        if (ids == null || ids.isEmpty() || payrollItemId == null) return;
        for (OvertimeEntry entry : overtimeRepository.findAllById(new HashSet<>(ids))) {
            if (entry.isDeleted() || !OvertimeEntry.APPROVED.equals(entry.getStatus()) || entry.getPayrollItemId() != null) continue;
            entry.setStatus(OvertimeEntry.PAID);
            entry.setPayrollItemId(payrollItemId);
            entry.setPaidAt(LocalDateTime.now());
            overtimeRepository.save(entry);
        }
    }

    /** Reverses {@link #markPaid} for a payroll line that was cancelled/regenerated. */
    @Transactional
    public void releasePayrollItem(Long payrollItemId) {
        if (payrollItemId == null) return;
        for (OvertimeEntry entry : overtimeRepository.findAllByPayrollItemIdAndDeletedFalse(payrollItemId)) {
            entry.setStatus(OvertimeEntry.APPROVED);
            entry.setPayrollItemId(null);
            entry.setPaidAt(null);
            overtimeRepository.save(entry);
        }
    }

    // ---------------------------------------------------------------- pricing

    private void price(OvertimeEntry entry) {
        Long companyId = entry.getCompany().getId();
        AttendanceShiftSettings settings = shiftService.settingsFor(companyId);
        int minutes = entry.getApprovedMinutes() == null ? entry.getComputedMinutes() : entry.getApprovedMinutes();
        if (AttendanceShiftSettings.RATE_FLAT.equals(settings.getOvertimeRateType())) {
            entry.setHourlyRate(settings.getOvertimeFlatHourlyRate());
            entry.setRateMultiplier(BigDecimal.ONE);
            entry.setAmount(amount(settings.getOvertimeRateType(), BigDecimal.ONE, settings.getOvertimeFlatHourlyRate(), null, minutes));
            return;
        }
        BigDecimal gross = salaryStructureRepository
                .findByEmployee_Company_IdAndEmployeeIdAndActiveTrueAndDeletedFalse(companyId, entry.getEmployee().getId())
                .map(s -> s.getGrossSalary()).orElse(null);
        if (gross == null || gross.signum() <= 0) {
            entry.setHourlyRate(null);
            entry.setRateMultiplier(settings.getOvertimeMultiplier());
            entry.setAmount(null); // priced again when payroll asks (approvedUnpaidOvertime)
            return;
        }
        YearMonth month = YearMonth.from(entry.getWorkDate());
        int workingDays = workingDays(companyId, month, entry.getEmployee());
        ShiftCalendar calendar = shiftService.loadCalendar(companyId, entry.getWorkDate(), entry.getWorkDate());
        Employee employee = entry.getEmployee();
        ShiftRule rule = calendar.resolve(employee.getId(),
                employee.getDepartment() == null ? null : employee.getDepartment().getId(), entry.getWorkDate()).shift();
        BigDecimal hourly = hourlyRate(gross, workingDays, rule.scheduledMinutes());
        entry.setHourlyRate(hourly);
        entry.setRateMultiplier(settings.getOvertimeMultiplier());
        entry.setAmount(amount(settings.getOvertimeRateType(), settings.getOvertimeMultiplier(), null, hourly, minutes));
    }

    private com.haodaone.leave.service.HolidayCalendarService holidayCalendarService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setHolidayCalendarService(com.haodaone.leave.service.HolidayCalendarService holidayCalendarService) {
        this.holidayCalendarService = holidayCalendarService;
    }

    private int workingDays(Long companyId, YearMonth month, Employee employee) {
        Company company = companyRepository.findById(companyId).orElse(null);
        WorkingWeek week = WorkingWeek.of(company);
        Set<LocalDate> holidays = new HashSet<>();
        if (holidayCalendarService != null) {
            // Only the holidays that apply to this employee's location (plus optional ones they picked).
            holidays.addAll(holidayCalendarService.holidayDatesFor(companyId, employee, month.atDay(1), month.atEndOfMonth()));
        } else {
            holidayRepository.findAllByCompany_IdAndDateBetweenAndDeletedFalse(companyId, month.atDay(1), month.atEndOfMonth())
                    .forEach(h -> holidays.add(h.getDate()));
        }
        int days = 0;
        for (LocalDate d = month.atDay(1); !d.isAfter(month.atEndOfMonth()); d = d.plusDays(1)) {
            if (!week.isWeeklyOff(d) && !holidays.contains(d)) days++;
        }
        return Math.max(1, days);
    }

    /** Monthly gross / (working days x shift hours), 2 decimals. */
    public static BigDecimal hourlyRate(BigDecimal monthlyGross, int workingDays, int shiftMinutes) {
        if (monthlyGross == null || workingDays <= 0 || shiftMinutes <= 0) return BigDecimal.ZERO;
        BigDecimal hours = BigDecimal.valueOf(workingDays).multiply(BigDecimal.valueOf(shiftMinutes))
                .divide(BigDecimal.valueOf(60), 6, RoundingMode.HALF_UP);
        return monthlyGross.divide(hours, 2, RoundingMode.HALF_UP);
    }

    /** MULTIPLIER: hourly x multiplier x hours; FLAT: flat x hours. Rounded to paise. */
    public static BigDecimal amount(String rateType, BigDecimal multiplier, BigDecimal flatHourly,
                                    BigDecimal hourlyRate, int minutes) {
        if (minutes <= 0) return BigDecimal.ZERO.setScale(2);
        BigDecimal hours = BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 6, RoundingMode.HALF_UP);
        BigDecimal perHour;
        if (AttendanceShiftSettings.RATE_FLAT.equals(rateType)) {
            perHour = flatHourly == null ? BigDecimal.ZERO : flatHourly;
        } else {
            perHour = (hourlyRate == null ? BigDecimal.ZERO : hourlyRate)
                    .multiply(multiplier == null ? BigDecimal.ONE : multiplier);
        }
        return perHour.multiply(hours).setScale(2, RoundingMode.HALF_UP);
    }

    // ---------------------------------------------------------------- helpers

    private Employee currentEmployee() {
        Employee me = authorizationService.resolveCurrentEmployee();
        if (me == null) throw new BadRequestException("Current login is not linked to an employee.");
        return me;
    }

    private static String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String formatMinutes(Integer minutes) {
        int m = minutes == null ? 0 : minutes;
        return (m / 60) + "h " + (m % 60) + "m";
    }

    private Long requiredTenant() {
        Long companyId = TenantContext.getCurrentTenant();
        if (companyId == null) throw new BadRequestException("Company context is required.");
        return companyId;
    }
}
