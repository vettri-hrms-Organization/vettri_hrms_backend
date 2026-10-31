package com.haodaone.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haodaone.attendance.shift.dto.DailyAttendanceDTO;
import com.haodaone.attendance.shift.service.DailyAttendanceService;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.leave.repository.HolidayRepository;
import com.haodaone.leave.repository.LeaveRequestRepository;
import com.haodaone.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Read-only public API for customers' own systems (BI, ERP, access control). Authenticated with an API key in the
 * {@code X-API-Key} header (not a login), limited to the key's scopes and company, and rate-limited per key.
 * Security config permits /api/public/v1/** without a JWT; every handler authenticates the key itself.
 */
@RestController
@RequestMapping("/api/public/v1")
public class PublicApiController {

    static final int RATE_PER_MINUTE = 120;
    static final int MAX_RANGE_DAYS = 93;

    private final ApiKeyRepository apiKeys;
    private final EmployeeRepository employees;
    private final LeaveRequestRepository leaveRequests;
    private final HolidayRepository holidays;
    private final DailyAttendanceService dailyAttendance;
    private final TransactionTemplate tx;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Map<Long, long[]> usage = new ConcurrentHashMap<>();

    public PublicApiController(ApiKeyRepository apiKeys, EmployeeRepository employees, LeaveRequestRepository leaveRequests, HolidayRepository holidays,
                               DailyAttendanceService dailyAttendance, TransactionTemplate tx, ObjectMapper objectMapper, Clock applicationClock) {
        this.apiKeys = apiKeys;
        this.employees = employees;
        this.leaveRequests = leaveRequests;
        this.holidays = holidays;
        this.dailyAttendance = dailyAttendance;
        this.tx = tx;
        this.objectMapper = objectMapper;
        this.clock = applicationClock;
    }

    public record EmployeeRow(Long id, String employeeCode, String firstName, String lastName, String email, String department, String designation,
                              String status, LocalDate dateOfJoining, Long reportingManagerId) { }
    public record LeaveRow(Long id, Long employeeId, String employeeCode, String leaveType, LocalDate startDate, LocalDate endDate, double days, boolean halfDay) { }
    public record HolidayRow(LocalDate date, String name) { }
    public record Page<T>(List<T> data, int count) { }

    @GetMapping("/employees")
    public ResponseEntity<?> employees(@RequestHeader(value = "X-API-Key", required = false) String key) {
        return run(key, "employees:read", companyId -> new Page<>(employees.findAllByCompany_IdAndDeletedFalseOrderByFirstNameAsc(companyId).stream()
                .map(e -> new EmployeeRow(e.getId(), e.getEmployeeCode(), e.getFirstName(), e.getLastName(), e.getEmail(),
                        e.getDepartment() == null ? null : e.getDepartment().getName(), e.getDesignation() == null ? null : e.getDesignation().getTitle(),
                        e.getStatus(), e.getDateOfJoining(), e.getReportingManager() == null ? null : e.getReportingManager().getId())).toList(), 0));
    }

    /** Approved leave overlapping [from, to] (max 93 days). */
    @GetMapping("/leave")
    public ResponseEntity<?> leave(@RequestHeader(value = "X-API-Key", required = false) String key, @RequestParam LocalDate from, @RequestParam LocalDate to) {
        ResponseEntity<?> bad = range(from, to);
        if (bad != null) return bad;
        return run(key, "leave:read", companyId -> new Page<>(leaveRequests.findAllByCompany_IdAndStatusOrderByStartDateAsc(companyId, "APPROVED").stream()
                .filter(l -> !l.getEndDate().isBefore(from) && !l.getStartDate().isAfter(to))
                .map(l -> new LeaveRow(l.getId(), l.getEmployee().getId(), l.getEmployee().getEmployeeCode(), l.getLeaveType().getName(),
                        l.getStartDate(), l.getEndDate(), l.getDays(), l.isHalfDay())).toList(), 0));
    }

    /** Evaluated daily attendance (first in, last out, worked minutes, status) for [from, to] (max 31 days). */
    @GetMapping("/attendance")
    public ResponseEntity<?> attendance(@RequestHeader(value = "X-API-Key", required = false) String key, @RequestParam LocalDate from, @RequestParam LocalDate to,
                                        @RequestParam(required = false) String employeeCode) {
        ResponseEntity<?> bad = range(from, to);
        if (bad != null) return bad;
        if (ChronoUnit.DAYS.between(from, to) > 31) return error(HttpStatus.BAD_REQUEST, "Attendance is limited to 31 days per call");
        return run(key, "attendance:read", companyId -> {
            List<Employee> people = employees.findAllByCompany_IdAndDeletedFalseOrderByFirstNameAsc(companyId).stream()
                    .filter(e -> employeeCode == null || employeeCode.equalsIgnoreCase(e.getEmployeeCode())).toList();
            List<DailyAttendanceDTO> rows = new ArrayList<>();
            dailyAttendance.evaluate(companyId, people, from, to).values().forEach(days -> days.values().forEach(d -> rows.add(DailyAttendanceService.toDto(d))));
            rows.sort(Comparator.comparing(DailyAttendanceDTO::date).thenComparing(r -> Objects.toString(r.employeeCode(), "")));
            return new Page<>(rows, 0);
        });
    }

    @GetMapping("/holidays")
    public ResponseEntity<?> holidays(@RequestHeader(value = "X-API-Key", required = false) String key, @RequestParam int year) {
        return run(key, "holidays:read", companyId -> new Page<>(holidays.findAllByCompany_IdAndDateBetweenAndDeletedFalse(companyId, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
                .stream().sorted(Comparator.comparing(h -> h.getDate())).map(h -> new HolidayRow(h.getDate(), h.getName())).toList(), 0));
    }

    // ------------------------------------------------------------------ helpers

    private <T> ResponseEntity<?> run(String rawKey, String scope, Function<Long, Page<T>> query) {
        if (rawKey == null || rawKey.isBlank()) return error(HttpStatus.UNAUTHORIZED, "Send your API key in the X-API-Key header");
        ApiKey k = apiKeys.findByKeyHashAndDeletedFalse(IntegrationService.sha256(rawKey.trim())).orElse(null);
        if (k == null || k.getRevokedAt() != null || (k.getExpiresOn() != null && k.getExpiresOn().isBefore(LocalDate.now(clock))))
            return error(HttpStatus.UNAUTHORIZED, "Invalid or revoked API key");
        if (!scopes(k).contains(scope)) return error(HttpStatus.FORBIDDEN, "This key can't read " + scope.replace(":read", ""));
        if (!allow(k.getId())) return error(HttpStatus.TOO_MANY_REQUESTS, "Rate limit is " + RATE_PER_MINUTE + " requests a minute");
        Long previous = TenantContext.getCurrentTenant();
        TenantContext.setCurrentTenant(k.getCompanyId());
        try {
            Page<T> page = tx.execute(status -> {
                k.setLastUsedAt(LocalDateTime.now(clock));
                apiKeys.save(k);
                return query.apply(k.getCompanyId());
            });
            return ResponseEntity.ok(new Page<>(page.data(), page.data().size()));
        } finally {
            if (previous != null) TenantContext.setCurrentTenant(previous); else TenantContext.clear();
        }
    }

    private boolean allow(Long keyId) {
        long minute = System.currentTimeMillis() / 60000;
        long[] slot = usage.compute(keyId, (id, s) -> s == null || s[0] != minute ? new long[]{minute, 0} : s);
        synchronized (slot) {
            return ++slot[1] <= RATE_PER_MINUTE;
        }
    }

    private List<String> scopes(ApiKey k) {
        try {
            return objectMapper.readValue(k.getScopesJson(), new TypeReference<List<String>>() { });
        } catch (Exception ex) {
            return List.of();
        }
    }

    private static ResponseEntity<?> range(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) return error(HttpStatus.BAD_REQUEST, "'to' is before 'from'");
        if (ChronoUnit.DAYS.between(from, to) > MAX_RANGE_DAYS) return error(HttpStatus.BAD_REQUEST, "Ask for at most " + MAX_RANGE_DAYS + " days at a time");
        return null;
    }

    private static ResponseEntity<?> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", status.getReasonPhrase(), "message", message));
    }
}
