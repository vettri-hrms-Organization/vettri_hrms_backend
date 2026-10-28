package com.haodaone.attendance.shift.service;

import com.haodaone.attendance.entity.AttendanceRecord;
import com.haodaone.attendance.entity.AttendanceSession;
import com.haodaone.attendance.repository.AttendanceRecordRepository;
import com.haodaone.attendance.repository.AttendanceSessionRepository;
import com.haodaone.attendance.shift.dto.DailyAttendanceDTO;
import com.haodaone.attendance.shift.dto.LateEarlyReportDTO;
import com.haodaone.attendance.shift.dto.ShiftTimes;
import com.haodaone.attendance.shift.entity.AttendanceShiftSettings;
import com.haodaone.attendance.shift.service.ShiftEvaluator.DayEvaluation;
import com.haodaone.attendance.shift.service.ShiftEvaluator.LeaveKind;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.leave.entity.LeaveRequest;
import com.haodaone.leave.repository.HolidayRepository;
import com.haodaone.leave.repository.LeaveRequestRepository;
import com.haodaone.security.AuthorizationService;
import com.haodaone.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Daily attendance evaluation against the resolved shift: first-in / last-out from biometric
 * punches + web/WFH sessions, worked minutes, status, late-in, early-out and overtime.
 * The rules live in {@link ShiftEvaluator} (pure); this service only gathers the inputs in bulk.
 * Methods taking a companyId work without a tenant context (payroll hooks, scheduled jobs).
 */
@Service
public class DailyAttendanceService {

    /** One evaluated employee-day. */
    public record EmployeeDay(Employee employee, LocalDate date, ShiftCalendar.Resolved resolved,
                              boolean holiday, LeaveKind leave, DayEvaluation evaluation) {

        public boolean weekOff() {
            return resolved.weekOff();
        }

        /** Attendance (not leave) counted as present for the muster: worked >= half-day or a single punch. */
        public boolean countsPresent() {
            String status = evaluation.status();
            if (ShiftEvaluator.ABSENT.equals(status)) return false;
            return evaluation.punched();
        }

        /** Worked at least the half-day threshold but less than the full-day threshold (no leave involved). */
        public boolean halfDayAttendance() {
            return leave == LeaveKind.NONE && ShiftEvaluator.HALF_DAY.equals(evaluation.status());
        }
    }

    private final ShiftService shiftService;
    private final EmployeeRepository employeeRepository;
    private final HolidayRepository holidayRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AttendanceSessionRepository attendanceSessionRepository;
    private final AuthorizationService authorizationService;
    private final Clock clock;

    public DailyAttendanceService(ShiftService shiftService,
                                  EmployeeRepository employeeRepository,
                                  HolidayRepository holidayRepository,
                                  LeaveRequestRepository leaveRequestRepository,
                                  AttendanceRecordRepository attendanceRecordRepository,
                                  AttendanceSessionRepository attendanceSessionRepository,
                                  AuthorizationService authorizationService,
                                  Clock clock) {
        this.shiftService = shiftService;
        this.employeeRepository = employeeRepository;
        this.holidayRepository = holidayRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.attendanceSessionRepository = attendanceSessionRepository;
        this.authorizationService = authorizationService;
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    /**
     * Evaluates every employee for every day in [from, to] (days after today and outside
     * employment are skipped). Result: employeeId -> date -> day.
     */
    private com.haodaone.leave.service.HolidayCalendarService holidayCalendarService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setHolidayCalendarService(com.haodaone.leave.service.HolidayCalendarService holidayCalendarService) {
        this.holidayCalendarService = holidayCalendarService;
    }

    @Transactional(readOnly = true)
    public Map<Long, Map<LocalDate, EmployeeDay>> evaluate(Long companyId, Collection<Employee> employees,
                                                           LocalDate from, LocalDate to) {
        Map<Long, Map<LocalDate, EmployeeDay>> result = new HashMap<>();
        if (employees == null || employees.isEmpty() || to.isBefore(from)) return result;
        LocalDate today = today();
        Set<Long> employeeIds = new HashSet<>();
        employees.forEach(e -> employeeIds.add(e.getId()));

        ShiftCalendar calendar = shiftService.loadCalendar(companyId, from, to);
        Set<LocalDate> holidays = new HashSet<>();
        holidayRepository.findAllByCompany_IdAndDateBetweenAndDeletedFalse(companyId, from, to)
                .forEach(h -> holidays.add(h.getDate()));
        // Location-specific and picked optional holidays differ per employee; fall back to all dates without the calendar.
        Map<Long, Set<LocalDate>> holidaysByEmployee = holidayCalendarService != null
                ? holidayCalendarService.holidayDatesByEmployee(companyId, employees, from, to) : null;

        Map<Long, Map<LocalDate, double[]>> leaveDays = new HashMap<>();
        for (LeaveRequest leave : leaveRequestRepository.findApprovedOverlappingForCompany(companyId, from, to)) {
            if (leave.getEmployee() == null || !employeeIds.contains(leave.getEmployee().getId())) continue;
            LocalDate start = leave.getStartDate().isBefore(from) ? from : leave.getStartDate();
            LocalDate end = leave.getEndDate().isAfter(to) ? to : leave.getEndDate();
            for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                leaveDays.computeIfAbsent(leave.getEmployee().getId(), k -> new HashMap<>())
                        .computeIfAbsent(d, k -> new double[1])[0] += leave.isHalfDay() ? 0.5 : 1.0;
            }
        }

        // Punch times: one day either side so overnight / early windows see their punches.
        Map<Long, List<LocalDateTime>> times = new HashMap<>();
        LocalDateTime rangeStart = from.minusDays(1).atStartOfDay();
        LocalDateTime rangeEnd = to.plusDays(2).atStartOfDay().minusNanos(1);
        for (AttendanceRecord punch : attendanceRecordRepository.findScopedByCompanyAndEmployees(
                companyId, employeeIds, rangeStart, rangeEnd)) {
            if (punch.getEmployee() == null || punch.getPunchTime() == null || punch.isDeleted()
                    || !"RECEIVED".equalsIgnoreCase(punch.getStatus())) continue;
            times.computeIfAbsent(punch.getEmployee().getId(), k -> new ArrayList<>()).add(punch.getPunchTime());
        }
        for (AttendanceSession session : attendanceSessionRepository
                .findAllByCompany_IdAndDeletedFalseAndAttendanceDateBetween(companyId, from.minusDays(1), to.plusDays(1))) {
            if (session.getEmployee() == null || !employeeIds.contains(session.getEmployee().getId())) continue;
            List<LocalDateTime> list = times.computeIfAbsent(session.getEmployee().getId(), k -> new ArrayList<>());
            if (session.getCheckInTime() != null) list.add(session.getCheckInTime());
            if (session.getCheckOutTime() != null) list.add(session.getCheckOutTime());
        }

        for (Employee employee : employees) {
            Long deptId = employee.getDepartment() == null ? null : employee.getDepartment().getId();
            List<LocalDateTime> employeeTimes = times.getOrDefault(employee.getId(), List.of());
            Map<LocalDate, double[]> employeeLeave = leaveDays.getOrDefault(employee.getId(), Map.of());
            Map<LocalDate, EmployeeDay> days = new LinkedHashMap<>();
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                if (d.isAfter(today)) break;
                if (employee.getDateOfJoining() != null && d.isBefore(employee.getDateOfJoining())) continue;
                if (employee.getLastWorkingDate() != null && d.isAfter(employee.getLastWorkingDate())) continue;
                ShiftCalendar.Resolved resolved = calendar.resolve(employee.getId(), deptId, d);
                double leaveAmount = employeeLeave.containsKey(d) ? employeeLeave.get(d)[0] : 0;
                LeaveKind leave = leaveAmount >= 1.0 ? LeaveKind.FULL : leaveAmount > 0 ? LeaveKind.HALF : LeaveKind.NONE;
                boolean holiday = holidaysByEmployee != null
                        ? holidaysByEmployee.getOrDefault(employee.getId(), Set.of()).contains(d)
                        : holidays.contains(d);
                DayEvaluation evaluation = ShiftEvaluator.evaluate(resolved.shift(), d, employeeTimes, holiday,
                        resolved.weekOff(), leave);
                days.put(d, new EmployeeDay(employee, d, resolved, holiday, leave, evaluation));
            }
            result.put(employee.getId(), days);
        }
        return result;
    }

    // ---------------------------------------------------------------- endpoints

    @Transactional(readOnly = true)
    public List<DailyAttendanceDTO> forEmployee(Long employeeId, LocalDate from, LocalDate to) {
        Long companyId = requiredTenant();
        ShiftService.validateRange(from, to);
        Employee employee = employeeRepository.findByIdAndCompany_IdAndDeletedFalse(employeeId, companyId)
                .orElseThrow(() -> new BadRequestException("Employee not found in this company."));
        Map<LocalDate, EmployeeDay> days = evaluate(companyId, List.of(employee), from, to)
                .getOrDefault(employeeId, Map.of());
        List<DailyAttendanceDTO> rows = new ArrayList<>();
        days.values().forEach(day -> rows.add(toDto(day)));
        rows.sort(Comparator.comparing(DailyAttendanceDTO::date).reversed());
        return rows;
    }

    @Transactional(readOnly = true)
    public List<DailyAttendanceDTO> mine(LocalDate from, LocalDate to) {
        Employee me = authorizationService.resolveCurrentEmployee();
        if (me == null) throw new BadRequestException("Current login is not linked to an employee.");
        return forEmployee(me.getId(), from, to);
    }

    @Transactional(readOnly = true)
    public LateEarlyReportDTO lateEarly(LocalDate from, LocalDate to) {
        Long companyId = requiredTenant();
        ShiftService.validateRange(from, to);
        List<Employee> employees = shiftService.scopedEmployees(companyId, from, to);
        Map<Long, Map<LocalDate, EmployeeDay>> evaluated = evaluate(companyId, employees, from, to);
        AttendanceShiftSettings settings = shiftService.settingsFor(companyId);
        boolean singleMonth = YearMonth.from(from).equals(YearMonth.from(to));

        List<DailyAttendanceDTO> rows = new ArrayList<>();
        List<LateEarlyReportDTO.Summary> summary = new ArrayList<>();
        for (Employee employee : employees) {
            int lateCount = 0, lateMinutes = 0, earlyCount = 0, earlyMinutes = 0;
            for (EmployeeDay day : evaluated.getOrDefault(employee.getId(), Map.of()).values()) {
                DayEvaluation e = day.evaluation();
                if (!e.late() && !e.earlyOut()) continue;
                rows.add(toDto(day));
                if (e.late()) { lateCount++; lateMinutes += e.lateInMinutes(); }
                if (e.earlyOut()) { earlyCount++; earlyMinutes += e.earlyOutMinutes(); }
            }
            if (lateCount == 0 && earlyCount == 0) continue;
            BigDecimal penalty = singleMonth && settings.isLatePenaltyEnabled()
                    ? penaltyDays(lateCount, settings.getLateMarksPerPenalty(), settings.getLatePenaltyDays()) : null;
            summary.add(new LateEarlyReportDTO.Summary(employee.getId(), employee.getEmployeeCode(), employee.getFullName(),
                    employee.getDepartment() == null ? null : employee.getDepartment().getName(),
                    lateCount, lateMinutes, earlyCount, earlyMinutes, penalty));
        }
        rows.sort(Comparator.comparing(DailyAttendanceDTO::date).reversed()
                .thenComparing(DailyAttendanceDTO::employeeName, Comparator.nullsLast(Comparator.naturalOrder())));
        summary.sort(Comparator.comparingInt(LateEarlyReportDTO.Summary::lateCount).reversed());
        return new LateEarlyReportDTO(from, to, settings.isLatePenaltyEnabled(), settings.getLateMarksPerPenalty(),
                settings.getLatePenaltyDays(), rows, summary);
    }

    // ---------------------------------------------------------------- payroll hook

    /**
     * Loss-of-pay days caused by late marks in {@code month} under the company's
     * "every N late marks = X day(s) LOP" policy; ZERO when the policy is off.
     * Works without a tenant context - intended for the payroll LOP calculation.
     */
    @Transactional(readOnly = true)
    public BigDecimal latePenaltyDays(Long companyId, Long employeeId, YearMonth month) {
        AttendanceShiftSettings settings = shiftService.settingsFor(companyId);
        if (!settings.isLatePenaltyEnabled()) return BigDecimal.ZERO;
        Employee employee = employeeRepository.findByIdAndCompany_IdAndDeletedFalse(employeeId, companyId).orElse(null);
        if (employee == null) return BigDecimal.ZERO;
        int lateCount = 0;
        for (EmployeeDay day : evaluate(companyId, List.of(employee), month.atDay(1), month.atEndOfMonth())
                .getOrDefault(employeeId, Map.of()).values()) {
            if (day.evaluation().late()) lateCount++;
        }
        return penaltyDays(lateCount, settings.getLateMarksPerPenalty(), settings.getLatePenaltyDays());
    }

    /** floor(lateCount / marksPerPenalty) x daysPerPenalty. */
    public static BigDecimal penaltyDays(int lateCount, int marksPerPenalty, BigDecimal daysPerPenalty) {
        if (lateCount <= 0 || marksPerPenalty <= 0 || daysPerPenalty == null || daysPerPenalty.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return daysPerPenalty.multiply(BigDecimal.valueOf(lateCount / marksPerPenalty)).setScale(2, RoundingMode.HALF_UP);
    }

    // ---------------------------------------------------------------- mapping

    public static DailyAttendanceDTO toDto(EmployeeDay day) {
        Employee employee = day.employee();
        ShiftRule rule = day.resolved().shift();
        DayEvaluation e = day.evaluation();
        return new DailyAttendanceDTO(employee.getId(), employee.getEmployeeCode(), employee.getFullName(),
                employee.getDepartment() == null ? null : employee.getDepartment().getName(),
                day.date(), rule.shiftId(), rule.name(), rule.code(), rule.color(),
                ShiftTimes.format(rule.start()), ShiftTimes.format(rule.end()), day.resolved().source(),
                e.status(), ist(e.firstIn()), ist(e.lastOut()), e.workedMinutes(), e.lateInMinutes(), e.earlyOutMinutes(),
                e.overtimeMinutes(), e.late(), e.earlyOut(), e.missingOut(), day.holiday(), day.weekOff(),
                day.leave().name());
    }

    /** Punch / session times are stored as Asia/Kolkata local times; send them with the +05:30 offset. */
    private static java.time.OffsetDateTime ist(LocalDateTime value) {
        return value == null ? null : value.atZone(java.time.ZoneId.of("Asia/Kolkata")).toOffsetDateTime();
    }

    private Long requiredTenant() {
        Long companyId = TenantContext.getCurrentTenant();
        if (companyId == null) throw new BadRequestException("Company context is required.");
        return companyId;
    }
}
