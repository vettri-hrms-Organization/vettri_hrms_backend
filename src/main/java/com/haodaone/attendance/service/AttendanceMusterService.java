package com.haodaone.attendance.service;

import com.haodaone.attendance.entity.AttendanceRecord;
import com.haodaone.attendance.entity.AttendanceSession;
import com.haodaone.attendance.repository.AttendanceRecordRepository;
import com.haodaone.attendance.repository.AttendanceSessionRepository;
import com.haodaone.attendance.shift.service.DailyAttendanceService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.company.service.WorkingWeek;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.leave.entity.LeaveRequest;
import com.haodaone.leave.repository.HolidayRepository;
import com.haodaone.leave.repository.LeaveRequestRepository;
import com.haodaone.security.AuthorizationService;
import com.haodaone.tenant.TenantContext;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Monthly attendance muster (.xlsx): one row per employee, one column per day with a status code,
 * plus totals. Presence = a RECEIVED biometric punch or a web/mobile/WFH AttendanceSession that day
 * (the same signals AttendanceController#exceptions uses).
 */
@Service
public class AttendanceMusterService {

    public static final String PRESENT = "P";
    public static final String ABSENT = "A";
    public static final String HALF_DAY = "HD";
    public static final String HOLIDAY = "H";
    public static final String WEEKLY_OFF = "WO";
    public static final String WFH = "WFH";
    public static final String LEAVE_FALLBACK = "L";

    private static final String[] TOTAL_HEADERS = {
            "Present", "WFH", "Absent", "Paid leave", "Unpaid leave (LOP)", "Holidays", "Week offs",
            "Late marks", "Overtime (h)"
    };

    private final EmployeeRepository employeeRepository;
    private final CompanyRepository companyRepository;
    private final HolidayRepository holidayRepository;
    private final AttendanceRecordRepository attendanceRecordRepository;
    private final AttendanceSessionRepository attendanceSessionRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final AuthorizationService authorizationService;
    private final Clock clock;
    private DailyAttendanceService dailyAttendanceService;

    private com.haodaone.leave.service.HolidayCalendarService holidayCalendarService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setHolidayCalendarService(com.haodaone.leave.service.HolidayCalendarService holidayCalendarService) {
        this.holidayCalendarService = holidayCalendarService;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setDailyAttendanceService(DailyAttendanceService dailyAttendanceService) {
        this.dailyAttendanceService = dailyAttendanceService;
    }

    public AttendanceMusterService(EmployeeRepository employeeRepository,
                                   CompanyRepository companyRepository,
                                   HolidayRepository holidayRepository,
                                   AttendanceRecordRepository attendanceRecordRepository,
                                   AttendanceSessionRepository attendanceSessionRepository,
                                   LeaveRequestRepository leaveRequestRepository,
                                   AuthorizationService authorizationService,
                                   Clock clock) {
        this.employeeRepository = employeeRepository;
        this.companyRepository = companyRepository;
        this.holidayRepository = holidayRepository;
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.attendanceSessionRepository = attendanceSessionRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.authorizationService = authorizationService;
        this.clock = clock;
    }

    /** One slice of approved leave on a day: a half-day slice counts 0.5. */
    public record LeaveSlice(String code, boolean halfDay, boolean paid) {}

    /** The computed cell for one employee/day plus its contribution to the totals. */
    public record DayStatus(String code, double present, double wfh, double absent,
                            double paidLeave, double unpaidLeave, boolean holiday, boolean weeklyOff) {
        static DayStatus blank() {
            return new DayStatus("", 0, 0, 0, 0, 0, false, false);
        }
    }

    /**
     * Status precedence: future / outside employment -> blank; holiday -> H; weekly off -> WO;
     * full-day leave (or both halves) -> leave type code; half-day leave -> HD (other half present
     * or absent); punched or checked in -> P (WFH when the session was WFH); otherwise A.
     */
    public static DayStatus computeDay(LocalDate date, LocalDate today, boolean employed,
                                       boolean holiday, boolean weeklyOff, List<LeaveSlice> leaves,
                                       boolean present, boolean wfh) {
        return computeDay(date, today, employed, holiday, weeklyOff, leaves, present, wfh, false);
    }

    /**
     * As above, plus shift-based half-day attendance: when {@code halfDayAttendance} is set (worked
     * at least the shift's half-day hours but less than its full-day hours, no leave that day) a
     * present day is HD = 0.5 present + 0.5 absent. Callers pass present=false when the worked time
     * fell below the half-day threshold.
     */
    public static DayStatus computeDay(LocalDate date, LocalDate today, boolean employed,
                                       boolean holiday, boolean weeklyOff, List<LeaveSlice> leaves,
                                       boolean present, boolean wfh, boolean halfDayAttendance) {
        if (!employed || date.isAfter(today)) {
            return DayStatus.blank();
        }
        if (holiday) {
            return new DayStatus(HOLIDAY, 0, 0, 0, 0, 0, true, false);
        }
        if (weeklyOff) {
            return new DayStatus(WEEKLY_OFF, 0, 0, 0, 0, 0, false, true);
        }
        List<LeaveSlice> slices = leaves == null ? List.of() : leaves;
        double paid = 0;
        double unpaid = 0;
        for (LeaveSlice slice : slices) {
            double amount = slice.halfDay() ? 0.5 : 1.0;
            if (slice.paid()) paid += amount; else unpaid += amount;
        }
        double leaveTotal = paid + unpaid;
        if (leaveTotal >= 1.0) {
            double scale = 1.0 / leaveTotal;
            LeaveSlice first = slices.stream().filter(s -> !s.halfDay()).findFirst().orElse(slices.get(0));
            String code = first.code() == null || first.code().isBlank() ? LEAVE_FALLBACK : first.code();
            return new DayStatus(code, 0, 0, 0, paid * scale, unpaid * scale, false, false);
        }
        if (leaveTotal > 0) {
            double worked = present ? 0.5 : 0;
            return new DayStatus(HALF_DAY, worked, present && wfh ? 0.5 : 0, present ? 0 : 0.5,
                    paid, unpaid, false, false);
        }
        if (present && halfDayAttendance) {
            return new DayStatus(HALF_DAY, 0.5, wfh ? 0.5 : 0, 0.5, 0, 0, false, false);
        }
        if (present) {
            return new DayStatus(wfh ? WFH : PRESENT, 1, wfh ? 1 : 0, 0, 0, 0, false, false);
        }
        return new DayStatus(ABSENT, 0, 0, 1, 0, 0, false, false);
    }

    @Transactional(readOnly = true)
    public byte[] exportMonthlyMuster(int year, int month) {
        if (month < 1 || month > 12 || year < 2000 || year > 2100) {
            throw new BadRequestException("Choose a valid year and month.");
        }
        Long companyId = TenantContext.getCurrentTenant();
        if (companyId == null) throw new BadRequestException("Company context is required.");
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate start = yearMonth.atDay(1);
        LocalDate end = yearMonth.atEndOfMonth();
        LocalDate today = LocalDate.now(clock);

        Optional<Set<Long>> scope = authorizationService.resolveEmployeeIds("ATTENDANCE_VIEW");
        List<Employee> employees;
        if (scope.isPresent() && scope.get().isEmpty()) {
            employees = List.of();
        } else if (scope.isPresent()) {
            employees = employeeRepository.findAllByCompany_IdAndIdInAndDeletedFalseOrderByFirstNameAsc(companyId, scope.get());
        } else {
            employees = employeeRepository.findAllByCompany_IdAndDeletedFalseOrderByFirstNameAsc(companyId);
        }
        employees = employees.stream()
                .filter(e -> e.getDateOfJoining() == null || !e.getDateOfJoining().isAfter(end))
                .filter(e -> e.getLastWorkingDate() == null || !e.getLastWorkingDate().isBefore(start))
                .toList();
        Set<Long> employeeIds = new HashSet<>();
        employees.forEach(e -> employeeIds.add(e.getId()));

        Company company = companyRepository.findById(companyId).orElse(null);
        WorkingWeek workingWeek = WorkingWeek.of(company);
        Set<LocalDate> holidays = new HashSet<>();
        holidayRepository.findAllByCompany_IdAndDateBetweenAndDeletedFalse(companyId, start, end)
                .forEach(h -> holidays.add(h.getDate()));
        // Per-employee holidays (work location + picked optional holidays) when the calendar service is available.
        Map<Long, Set<LocalDate>> holidaysByEmployee = holidayCalendarService != null
                ? holidayCalendarService.holidayDatesByEmployee(companyId, employees, start, end)
                : null;

        Map<Long, Set<LocalDate>> present = new HashMap<>();
        Map<Long, Set<LocalDate>> wfhDays = new HashMap<>();
        if (!employeeIds.isEmpty()) {
            List<AttendanceRecord> punches = attendanceRecordRepository.findScopedByCompanyAndEmployees(
                    companyId, employeeIds, start.atStartOfDay(), end.plusDays(1).atStartOfDay().minusNanos(1));
            for (AttendanceRecord punch : punches) {
                if (punch.getEmployee() == null || punch.getPunchTime() == null
                        || !"RECEIVED".equalsIgnoreCase(punch.getStatus()) || punch.isDeleted()) continue;
                present.computeIfAbsent(punch.getEmployee().getId(), k -> new HashSet<>())
                        .add(punch.getPunchTime().toLocalDate());
            }
        }
        Set<String> biometricPresence = new HashSet<>();
        present.forEach((id, dates) -> dates.forEach(d -> biometricPresence.add(id + "|" + d)));
        for (AttendanceSession session : attendanceSessionRepository
                .findAllByCompany_IdAndDeletedFalseAndAttendanceDateBetween(companyId, start, end)) {
            if (session.getEmployee() == null || session.getAttendanceDate() == null) continue;
            Long id = session.getEmployee().getId();
            if (!employeeIds.contains(id)) continue;
            present.computeIfAbsent(id, k -> new HashSet<>()).add(session.getAttendanceDate());
            if (session.isWfh() && !biometricPresence.contains(id + "|" + session.getAttendanceDate())) {
                wfhDays.computeIfAbsent(id, k -> new HashSet<>()).add(session.getAttendanceDate());
            }
        }

        Map<Long, Map<LocalDate, List<LeaveSlice>>> leaves = new HashMap<>();
        for (LeaveRequest leave : leaveRequestRepository.findApprovedOverlappingForCompany(companyId, start, end)) {
            Long id = leave.getEmployee().getId();
            if (!employeeIds.contains(id)) continue;
            LeaveSlice slice = new LeaveSlice(leave.getLeaveType().getCode(), leave.isHalfDay(), leave.getLeaveType().isPaid());
            LocalDate from = leave.getStartDate().isBefore(start) ? start : leave.getStartDate();
            LocalDate to = leave.getEndDate().isAfter(end) ? end : leave.getEndDate();
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                leaves.computeIfAbsent(id, k -> new HashMap<>()).computeIfAbsent(d, k -> new ArrayList<>()).add(slice);
            }
        }

        // Shift-aware evaluation (overnight windows, half-day threshold, roster week offs, late/OT).
        Map<Long, Map<LocalDate, DailyAttendanceService.EmployeeDay>> evaluated = dailyAttendanceService == null
                ? Map.of() : dailyAttendanceService.evaluate(companyId, employees, start, end);

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Muster " + yearMonth);
            CellStyle headerStyle = headerStyle(workbook);
            CellStyle centered = workbook.createCellStyle();
            centered.setAlignment(HorizontalAlignment.CENTER);

            String monthLabel = yearMonth.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + year;
            sheet.createRow(0).createCell(0).setCellValue("Attendance muster - " + monthLabel
                    + (company != null && company.getName() != null ? " - " + company.getName() : ""));

            Row header = sheet.createRow(2);
            int col = 0;
            col = headerCell(header, col, "Employee Code", headerStyle);
            col = headerCell(header, col, "Employee Name", headerStyle);
            col = headerCell(header, col, "Department", headerStyle);
            for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                col = headerCell(header, col, String.format("%02d %s", d.getDayOfMonth(),
                        d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH)), headerStyle);
            }
            for (String total : TOTAL_HEADERS) {
                col = headerCell(header, col, total, headerStyle);
            }
            int lastColumn = col;

            int rowIndex = 3;
            for (Employee employee : employees) {
                Row row = sheet.createRow(rowIndex++);
                int c = 0;
                row.createCell(c++).setCellValue(nvl(employee.getEmployeeCode()));
                row.createCell(c++).setCellValue(nvl(employee.getFullName()));
                row.createCell(c++).setCellValue(employee.getDepartment() != null ? nvl(employee.getDepartment().getName()) : "");
                double[] totals = new double[TOTAL_HEADERS.length];
                Set<LocalDate> presentDates = present.getOrDefault(employee.getId(), Set.of());
                Set<LocalDate> wfhDates = wfhDays.getOrDefault(employee.getId(), Set.of());
                Map<LocalDate, List<LeaveSlice>> employeeLeaves = leaves.getOrDefault(employee.getId(), Map.of());
                Map<LocalDate, DailyAttendanceService.EmployeeDay> employeeDays = evaluated.getOrDefault(employee.getId(), Map.of());
                Set<LocalDate> employeeHolidays = holidaysByEmployee != null
                        ? holidaysByEmployee.getOrDefault(employee.getId(), Set.of()) : holidays;
                for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
                    boolean employed = (employee.getDateOfJoining() == null || !d.isBefore(employee.getDateOfJoining()))
                            && (employee.getLastWorkingDate() == null || !d.isAfter(employee.getLastWorkingDate()));
                    DailyAttendanceService.EmployeeDay day = employeeDays.get(d);
                    boolean weeklyOff = day != null ? day.weekOff() : workingWeek.isWeeklyOff(d);
                    boolean presentToday = day != null ? day.countsPresent() : presentDates.contains(d);
                    boolean halfDayAttendance = day != null && day.halfDayAttendance();
                    DayStatus status = computeDay(d, today, employed, employeeHolidays.contains(d), weeklyOff,
                            employeeLeaves.get(d), presentToday, wfhDates.contains(d), halfDayAttendance);
                    if (day != null) {
                        totals[7] += day.evaluation().late() ? 1 : 0;
                        totals[8] += day.evaluation().overtimeMinutes() / 60.0;
                    }
                    Cell cell = row.createCell(c++);
                    cell.setCellValue(status.code());
                    cell.setCellStyle(centered);
                    totals[0] += status.present();
                    totals[1] += status.wfh();
                    totals[2] += status.absent();
                    totals[3] += status.paidLeave();
                    totals[4] += status.unpaidLeave();
                    totals[5] += status.holiday() ? 1 : 0;
                    totals[6] += status.weeklyOff() ? 1 : 0;
                }
                for (double total : totals) {
                    row.createCell(c++).setCellValue(total);
                }
            }

            rowIndex++;
            sheet.createRow(rowIndex++).createCell(0).setCellValue(
                    "Legend: P = Present, WFH = Work from home, A = Absent, "
                            + "HD = Half day (half-day leave, or worked between the shift's half-day and full-day hours), "
                            + "H = Holiday, WO = Weekly off, other codes = leave type code. Future days are blank.");

            sheet.createFreezePane(3, 3);
            for (int i = 0; i < 3; i++) sheet.autoSizeColumn(i);
            for (int i = 3; i < lastColumn; i++) sheet.setColumnWidth(i, i < lastColumn - TOTAL_HEADERS.length ? 7 * 256 : 14 * 256);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to build attendance muster export", e);
        }
    }

    private int headerCell(Row header, int col, String value, CellStyle style) {
        Cell cell = header.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
        return col + 1;
    }

    private CellStyle headerStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private static String nvl(String value) {
        return value == null ? "" : value;
    }
}
