package com.haodaone.reports.builder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haodaone.attendance.shift.dto.DailyAttendanceDTO;
import com.haodaone.attendance.shift.service.DailyAttendanceService;
import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.expense.repository.ExpenseClaimRepository;
import com.haodaone.leave.repository.LeaveRequestRepository;
import com.haodaone.performance.suite.PerfContext;
import com.haodaone.security.AuthorizationService;
import com.haodaone.security.CustomUserPrincipal;
import com.haodaone.timesheet.TimesheetEntryRepository;
import com.haodaone.timesheet.TimesheetProject;
import com.haodaone.timesheet.TimesheetProjectRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Custom report builder. Each dataset is a fixed, whitelisted set of fields (no free-form SQL), visible only to people
 * with that dataset's permission, and rows are limited to the employees that permission's scope covers. Users pick
 * columns, filter by date range / department / status / text, optionally group with counts and sums, and export CSV.
 */
@Service
public class ReportBuilderService {

    static final int MAX_ROWS = 5000;

    public record Field(String key, String label, String type) { }
    public record Dataset(String key, String label, String permission, String dateField, int maxRangeDays, List<Field> fields) { }
    public record Filters(LocalDate from, LocalDate to, List<String> departments, List<String> statuses, String search) { }
    public record Definition(String dataset, List<String> columns, Filters filters, String groupBy, String sortBy, String sortDir) { }
    public record Result(List<Field> columns, List<Map<String, Object>> rows, int totalRows, boolean truncated, Map<String, Object> totals) { }
    public record SavedDTO(Long id, String name, String dataset, Definition definition, String createdByName, boolean shared, boolean mine) { }
    public record SaveRequest(String name, Definition definition, Boolean shared) { }

    static final Map<String, Dataset> DATASETS = new LinkedHashMap<>();
    static {
        DATASETS.put("EMPLOYEES", new Dataset("EMPLOYEES", "Employees", "EMPLOYEE_VIEW", "dateOfJoining", 0, List.of(
                f("employeeCode", "Employee code", "TEXT"), f("name", "Name", "TEXT"), f("email", "Work email", "TEXT"),
                f("department", "Department", "TEXT"), f("designation", "Designation", "TEXT"), f("team", "Team", "TEXT"),
                f("manager", "Reporting manager", "TEXT"), f("status", "Status", "TEXT"), f("employmentType", "Employment type", "TEXT"),
                f("gender", "Gender", "TEXT"), f("dateOfJoining", "Date of joining", "DATE"), f("lastWorkingDate", "Last working day", "DATE"),
                f("tenureMonths", "Tenure (months)", "NUMBER"))));
        DATASETS.put("LEAVE", new Dataset("LEAVE", "Leave requests", "LEAVE_VIEW", "startDate", 0, List.of(
                f("employeeCode", "Employee code", "TEXT"), f("name", "Name", "TEXT"), f("department", "Department", "TEXT"),
                f("leaveType", "Leave type", "TEXT"), f("startDate", "From", "DATE"), f("endDate", "To", "DATE"), f("days", "Days", "NUMBER"),
                f("status", "Status", "TEXT"), f("halfDay", "Half day", "TEXT"))));
        DATASETS.put("ATTENDANCE", new Dataset("ATTENDANCE", "Daily attendance", "ATTENDANCE_VIEW", "date", 62, List.of(
                f("employeeCode", "Employee code", "TEXT"), f("name", "Name", "TEXT"), f("department", "Department", "TEXT"),
                f("date", "Date", "DATE"), f("status", "Status", "TEXT"), f("shift", "Shift", "TEXT"), f("firstIn", "First in", "TEXT"),
                f("lastOut", "Last out", "TEXT"), f("workedHours", "Worked hours", "NUMBER"), f("lateMinutes", "Late (min)", "NUMBER"),
                f("earlyOutMinutes", "Early out (min)", "NUMBER"), f("overtimeMinutes", "Overtime (min)", "NUMBER"), f("leave", "Leave", "TEXT"))));
        DATASETS.put("EXPENSES", new Dataset("EXPENSES", "Expense claims", "EXPENSE_MANAGE", "submittedOn", 0, List.of(
                f("employeeCode", "Employee code", "TEXT"), f("name", "Name", "TEXT"), f("department", "Department", "TEXT"),
                f("title", "Claim", "TEXT"), f("status", "Status", "TEXT"), f("amount", "Amount", "NUMBER"),
                f("submittedOn", "Submitted", "DATE"), f("reimbursedOn", "Reimbursed", "DATE"))));
        DATASETS.put("TIMESHEETS", new Dataset("TIMESHEETS", "Timesheet hours", "TIMESHEET_MANAGE", "date", 366, List.of(
                f("employeeCode", "Employee code", "TEXT"), f("name", "Name", "TEXT"), f("department", "Department", "TEXT"),
                f("project", "Project", "TEXT"), f("client", "Client", "TEXT"), f("date", "Date", "DATE"),
                f("hours", "Hours", "NUMBER"), f("billable", "Billable", "TEXT"))));
    }

    private static Field f(String key, String label, String type) {
        return new Field(key, label, type);
    }

    private final EmployeeRepository employees;
    private final LeaveRequestRepository leaveRequests;
    private final ExpenseClaimRepository expenses;
    private final TimesheetEntryRepository timesheetEntries;
    private final TimesheetProjectRepository projects;
    private final DailyAttendanceService dailyAttendance;
    private final SavedReportRepository saved;
    private final AuthorizationService authorizationService;
    private final PerfContext ctx;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ReportBuilderService(EmployeeRepository employees, LeaveRequestRepository leaveRequests, ExpenseClaimRepository expenses,
                                TimesheetEntryRepository timesheetEntries, TimesheetProjectRepository projects, DailyAttendanceService dailyAttendance,
                                SavedReportRepository saved, AuthorizationService authorizationService, PerfContext ctx,
                                AuditLogService auditLogService, ObjectMapper objectMapper, Clock applicationClock) {
        this.employees = employees;
        this.leaveRequests = leaveRequests;
        this.expenses = expenses;
        this.timesheetEntries = timesheetEntries;
        this.projects = projects;
        this.dailyAttendance = dailyAttendance;
        this.saved = saved;
        this.authorizationService = authorizationService;
        this.ctx = ctx;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
        this.clock = applicationClock;
    }

    /** Datasets the caller may use. */
    public List<Dataset> datasets() {
        return DATASETS.values().stream().filter(d -> scope(d) != null).toList();
    }

    @Transactional(readOnly = true)
    public Result run(Definition def) {
        Dataset ds = dataset(def);
        Set<Long> allowed = scope(ds);
        if (allowed == null) throw new AccessDeniedException("You don't have access to " + ds.label().toLowerCase(Locale.ROOT));
        Filters filters = def.filters() == null ? new Filters(null, null, null, null, null) : def.filters();
        LocalDate from = filters.from(), to = filters.to();
        if (from != null && to != null && to.isBefore(from)) throw new BadRequestException("The end date is before the start date");
        if (ds.maxRangeDays() > 0) {
            if (from == null || to == null) throw new BadRequestException("Choose a date range for " + ds.label().toLowerCase(Locale.ROOT));
            if (ChronoUnit.DAYS.between(from, to) > ds.maxRangeDays()) throw new BadRequestException("Choose at most " + ds.maxRangeDays() + " days");
        }
        List<Employee> people = employees.findAllByCompany_IdAndDeletedFalseOrderByFirstNameAsc(ctx.companyId()).stream()
                .filter(e -> allowed.isEmpty() || allowed.contains(e.getId())).toList();
        Map<Long, Employee> byId = people.stream().collect(Collectors.toMap(Employee::getId, e -> e));
        List<Map<String, Object>> rows = switch (ds.key()) {
            case "EMPLOYEES" -> people.stream().map(this::employeeRow).collect(Collectors.toCollection(ArrayList::new));
            case "LEAVE" -> leaveRequests.findAllByCompany_IdOrderByStartDateDesc(ctx.companyId()).stream()
                    .filter(l -> byId.containsKey(l.getEmployee().getId()))
                    .map(l -> {
                        Map<String, Object> r = base(l.getEmployee());
                        r.put("leaveType", l.getLeaveType().getName());
                        r.put("startDate", l.getStartDate());
                        r.put("endDate", l.getEndDate());
                        r.put("days", BigDecimal.valueOf(l.getDays()));
                        r.put("status", l.getStatus());
                        r.put("halfDay", l.isHalfDay() ? "Yes" : "No");
                        return r;
                    }).collect(Collectors.toCollection(ArrayList::new));
            case "ATTENDANCE" -> {
                List<Map<String, Object>> out = new ArrayList<>();
                dailyAttendance.evaluate(ctx.companyId(), people, from, to).values().forEach(days -> days.values().forEach(d -> {
                    DailyAttendanceDTO a = DailyAttendanceService.toDto(d);
                    Map<String, Object> r = base(d.employee());
                    r.put("date", a.date());
                    r.put("status", a.status());
                    r.put("shift", a.shiftName());
                    r.put("firstIn", a.firstIn() == null ? null : a.firstIn().toLocalTime().withNano(0).toString());
                    r.put("lastOut", a.lastOut() == null ? null : a.lastOut().toLocalTime().withNano(0).toString());
                    r.put("workedHours", BigDecimal.valueOf(a.workedMinutes()).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP));
                    r.put("lateMinutes", BigDecimal.valueOf(a.lateInMinutes()));
                    r.put("earlyOutMinutes", BigDecimal.valueOf(a.earlyOutMinutes()));
                    r.put("overtimeMinutes", BigDecimal.valueOf(a.overtimeMinutes()));
                    r.put("leave", a.leave());
                    out.add(r);
                }));
                yield out;
            }
            case "EXPENSES" -> expenses.findAllByCompany_IdAndDeletedFalseOrderByIdDesc(ctx.companyId()).stream()
                    .filter(c -> c.getEmployee() != null && byId.containsKey(c.getEmployee().getId()))
                    .map(c -> {
                        Map<String, Object> r = base(c.getEmployee());
                        r.put("title", c.getTitle());
                        r.put("status", c.getStatus());
                        r.put("amount", c.getTotalAmount());
                        r.put("submittedOn", c.getSubmittedAt() == null ? null : c.getSubmittedAt().toLocalDate());
                        r.put("reimbursedOn", c.getReimbursedAt() == null ? null : c.getReimbursedAt().toLocalDate());
                        return r;
                    }).collect(Collectors.toCollection(ArrayList::new));
            case "TIMESHEETS" -> {
                Map<Long, TimesheetProject> projectsById = projects.findAll().stream().filter(p -> ctx.companyId().equals(p.getCompanyId()))
                        .collect(Collectors.toMap(TimesheetProject::getId, p -> p));
                LocalDate start = from == null ? LocalDate.now(clock).minusMonths(1) : from;
                LocalDate end = to == null ? LocalDate.now(clock) : to;
                yield timesheetEntries.findAllByCompanyIdAndWorkDateBetweenAndDeletedFalse(ctx.companyId(), start, end).stream()
                        .filter(t -> byId.containsKey(t.getEmployeeId()))
                        .map(t -> {
                            Map<String, Object> r = base(byId.get(t.getEmployeeId()));
                            TimesheetProject p = projectsById.get(t.getProjectId());
                            r.put("project", p == null ? null : p.getName());
                            r.put("client", p == null ? null : p.getClientName());
                            r.put("date", t.getWorkDate());
                            r.put("hours", BigDecimal.valueOf(t.getMinutes()).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP));
                            r.put("billable", t.isBillable() ? "Yes" : "No");
                            return r;
                        }).collect(Collectors.toCollection(ArrayList::new));
            }
            default -> throw new BadRequestException("Unknown dataset");
        };

        // Filters
        String search = filters.search() == null ? null : filters.search().trim().toLowerCase(Locale.ROOT);
        rows.removeIf(r -> {
            Object date = r.get(ds.dateField());
            if (from != null && (!(date instanceof LocalDate d) || d.isBefore(from))) return true;
            if (to != null && (!(date instanceof LocalDate d2) || d2.isAfter(to))) return true;
            if (filters.departments() != null && !filters.departments().isEmpty() && !filters.departments().contains(String.valueOf(r.get("department")))) return true;
            if (filters.statuses() != null && !filters.statuses().isEmpty() && !filters.statuses().contains(String.valueOf(r.get("status")))) return true;
            return search != null && !search.isEmpty() && r.values().stream().noneMatch(v -> v != null && v.toString().toLowerCase(Locale.ROOT).contains(search));
        });

        Map<String, Field> fields = ds.fields().stream().collect(Collectors.toMap(Field::key, x -> x, (a, b) -> a, LinkedHashMap::new));
        List<String> columns = def.columns() == null || def.columns().isEmpty() ? new ArrayList<>(fields.keySet())
                : def.columns().stream().filter(fields::containsKey).distinct().collect(Collectors.toCollection(ArrayList::new));
        if (columns.isEmpty()) throw new BadRequestException("Choose at least one column");

        // Grouping: one row per group with a count and the sum of every numeric column.
        if (def.groupBy() != null && !def.groupBy().isBlank()) {
            if (!fields.containsKey(def.groupBy())) throw new BadRequestException("Unknown group-by field");
            Map<Object, List<Map<String, Object>>> groups = rows.stream().collect(Collectors.groupingBy(r -> Objects.toString(r.get(def.groupBy()), "(blank)"), LinkedHashMap::new, Collectors.toList()));
            List<String> numeric = fields.values().stream().filter(x -> "NUMBER".equals(x.type()) && !x.key().equals(def.groupBy())).map(Field::key).toList();
            List<Map<String, Object>> grouped = new ArrayList<>();
            groups.forEach((key, list) -> {
                Map<String, Object> g = new LinkedHashMap<>();
                g.put(def.groupBy(), key);
                g.put("count", BigDecimal.valueOf(list.size()));
                numeric.forEach(n -> g.put(n, sum(list, n)));
                grouped.add(g);
            });
            rows = grouped;
            columns = new ArrayList<>(List.of(def.groupBy(), "count"));
            columns.addAll(numeric);
            fields.put("count", new Field("count", "Count", "NUMBER"));
        }

        String sortBy = def.sortBy() != null && columns.contains(def.sortBy()) ? def.sortBy() : columns.get(0);
        Comparator<Map<String, Object>> cmp = Comparator.comparing(r -> (Comparable) comparable(r.get(sortBy)), Comparator.nullsLast(Comparator.naturalOrder()));
        if ("DESC".equalsIgnoreCase(def.sortDir())) cmp = cmp.reversed();
        rows.sort(cmp);

        int total = rows.size();
        List<String> cols = columns;
        List<Map<String, Object>> out = rows.stream().limit(MAX_ROWS).map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            cols.forEach(c -> m.put(c, r.get(c)));
            return m;
        }).toList();
        Map<String, Object> totals = new LinkedHashMap<>();
        List<Map<String, Object>> all = rows;
        cols.stream().filter(c -> "NUMBER".equals(fields.get(c).type())).forEach(c -> totals.put(c, sum(all, c)));
        return new Result(cols.stream().map(fields::get).toList(), out, total, total > MAX_ROWS, totals);
    }

    public String csv(Definition def) {
        Result r = run(def);
        StringBuilder sb = new StringBuilder();
        sb.append(r.columns().stream().map(c -> cell(c.label())).collect(Collectors.joining(","))).append("\r\n");
        for (Map<String, Object> row : r.rows()) {
            sb.append(r.columns().stream().map(c -> cell(row.get(c.key()) == null ? "" : row.get(c.key()).toString())).collect(Collectors.joining(","))).append("\r\n");
        }
        auditLogService.log("REPORT_EXPORT", userId(), "EXPORT", "Exported a custom " + def.dataset() + " report (" + r.rows().size() + " rows)");
        return sb.toString();
    }

    // ------------------------------------------------------------------ saved reports

    @Transactional(readOnly = true)
    public List<SavedDTO> savedReports() {
        Long me = userId();
        Set<String> allowed = datasets().stream().map(Dataset::key).collect(Collectors.toSet());
        return saved.findAllByCompanyIdAndDeletedFalseOrderByNameAsc(ctx.companyId()).stream()
                .filter(s -> (s.isShared() || s.getOwnerUserId().equals(me)) && allowed.contains(s.getDataset()))
                .map(s -> toDto(s, me)).toList();
    }

    @Transactional
    public SavedDTO save(Long id, SaveRequest r) {
        Long me = userId();
        String name = PerfContext.text(r == null ? null : r.name(), 120);
        if (name == null) throw new BadRequestException("Name the report");
        Dataset ds = dataset(r.definition());
        if (scope(ds) == null) throw new AccessDeniedException("You don't have access to this dataset");
        SavedReport s = id == null ? new SavedReport() : saved.findByIdAndCompanyIdAndDeletedFalse(id, ctx.companyId()).orElseThrow(() -> new ResourceNotFoundException("Report not found"));
        if (id != null && !s.getOwnerUserId().equals(me)) throw new AccessDeniedException("Only the person who saved it can change it");
        s.setCompanyId(ctx.companyId());
        s.setName(name);
        s.setDataset(ds.key());
        s.setDefinitionJson(write(r.definition()));
        s.setShared(Boolean.TRUE.equals(r.shared()));
        s.setOwnerUserId(me);
        s.setCreatedByName(currentName());
        s = saved.save(s);
        return toDto(s, me);
    }

    @Transactional
    public void delete(Long id) {
        SavedReport s = saved.findByIdAndCompanyIdAndDeletedFalse(id, ctx.companyId()).orElseThrow(() -> new ResourceNotFoundException("Report not found"));
        if (!s.getOwnerUserId().equals(userId())) throw new AccessDeniedException("Only the person who saved it can delete it");
        s.setDeleted(true);
        saved.save(s);
    }

    // ------------------------------------------------------------------ helpers

    /** Employee ids the caller may see for this dataset: empty set + full scope = everyone; null = no access. */
    private Set<Long> scope(Dataset ds) {
        Optional<Set<Long>> ids = authorizationService.resolveEmployeeIds(ds.permission());
        if (ids.isEmpty()) return Set.of();
        return ids.get().isEmpty() ? null : ids.get();
    }

    private Dataset dataset(Definition def) {
        Dataset ds = def == null || def.dataset() == null ? null : DATASETS.get(def.dataset().toUpperCase(Locale.ROOT));
        if (ds == null) throw new BadRequestException("Choose what to report on");
        return ds;
    }

    private Map<String, Object> employeeRow(Employee e) {
        Map<String, Object> r = base(e);
        r.put("email", e.getEmail());
        r.put("designation", e.getDesignation() == null ? null : e.getDesignation().getTitle());
        r.put("team", e.getTeam() == null ? null : e.getTeam().getName());
        r.put("manager", e.getReportingManager() == null ? null : e.getReportingManager().getFullName());
        r.put("status", e.getStatus());
        r.put("employmentType", e.getEmploymentType());
        r.put("gender", e.getGender());
        r.put("dateOfJoining", e.getDateOfJoining());
        r.put("lastWorkingDate", e.getLastWorkingDate());
        r.put("tenureMonths", e.getDateOfJoining() == null ? null
                : BigDecimal.valueOf(ChronoUnit.MONTHS.between(e.getDateOfJoining(), e.getLastWorkingDate() != null ? e.getLastWorkingDate() : LocalDate.now(clock))));
        return r;
    }

    private static Map<String, Object> base(Employee e) {
        Map<String, Object> r = new HashMap<>();
        r.put("employeeCode", e.getEmployeeCode());
        r.put("name", e.getFullName());
        r.put("department", e.getDepartment() == null ? null : e.getDepartment().getName());
        return r;
    }

    private static BigDecimal sum(List<Map<String, Object>> rows, String key) {
        return rows.stream().map(r -> r.get(key)).filter(v -> v instanceof BigDecimal).map(v -> (BigDecimal) v).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static Object comparable(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal || v instanceof LocalDate) return v;
        return v.toString().toLowerCase(Locale.ROOT);
    }

    /** CSV cell with quoting and formula-injection protection (=, +, -, @ at the start). */
    static String cell(String v) {
        String s = v == null ? "" : v;
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0 && !s.matches("-?\\d+(\\.\\d+)?")) s = "'" + s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    private SavedDTO toDto(SavedReport s, Long me) {
        Definition d;
        try {
            d = objectMapper.readValue(s.getDefinitionJson(), Definition.class);
        } catch (Exception ex) {
            d = new Definition(s.getDataset(), List.of(), null, null, null, null);
        }
        return new SavedDTO(s.getId(), s.getName(), s.getDataset(), d, s.getCreatedByName(), s.isShared(), s.getOwnerUserId().equals(me));
    }

    private String write(Object v) {
        try {
            return objectMapper.writeValueAsString(v);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static Long userId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserPrincipal p)) throw new AccessDeniedException("Sign in again");
        return p.getId();
    }

    private static String currentName() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CustomUserPrincipal p && p.getUser() != null) return p.getUser().getFullName();
        return null;
    }
}
