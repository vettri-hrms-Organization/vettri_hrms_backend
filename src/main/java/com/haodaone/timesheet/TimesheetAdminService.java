package com.haodaone.timesheet;

import com.haodaone.attendance.shift.service.DailyAttendanceService;
import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.security.AuthorizationService;
import com.haodaone.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Timesheet administration (TIMESHEET_MANAGE): projects, members and tasks; plus reports and the
 * weekly team overview, which approvers see for their own team (TIMESHEET_APPROVE scope).
 */
@Service
public class TimesheetAdminService {

    static final int MAX_REPORT_DAYS = 366;

    private final TimesheetProjectRepository projects;
    private final TimesheetTaskRepository tasks;
    private final TimesheetEntryRepository entries;
    private final TimesheetWeekRepository weeks;
    private final TimesheetSettingsService settingsService;
    private final EmployeeRepository employees;
    private final AuthorizationService authorizationService;
    private final DailyAttendanceService dailyAttendanceService;
    private final AuditLogService auditLogService;
    private final Clock clock;
    private final TimesheetApprovalScope approvalScope;

    public TimesheetAdminService(TimesheetProjectRepository projects, TimesheetTaskRepository tasks, TimesheetEntryRepository entries,
                                 TimesheetWeekRepository weeks, TimesheetSettingsService settingsService, EmployeeRepository employees,
                                 AuthorizationService authorizationService, DailyAttendanceService dailyAttendanceService,
                                 AuditLogService auditLogService, Clock applicationClock,
                                 TimesheetApprovalScope approvalScope) {
        this.projects = projects;
        this.tasks = tasks;
        this.entries = entries;
        this.weeks = weeks;
        this.settingsService = settingsService;
        this.employees = employees;
        this.authorizationService = authorizationService;
        this.dailyAttendanceService = dailyAttendanceService;
        this.auditLogService = auditLogService;
        this.clock = applicationClock;
        this.approvalScope = approvalScope;
    }

    // ------------------------------------------------------------------ projects

    public record TaskDTO(Long id, String name, Boolean billable, boolean active) { }
    public record MemberDTO(Long employeeId, String name, String employeeCode) { }
    public record ProjectDTO(Long id, String name, String code, String clientName, String description, boolean billable,
                             String status, LocalDate startDate, LocalDate endDate, Integer budgetMinutes, int loggedMinutes,
                             boolean allEmployees, List<MemberDTO> members, List<TaskDTO> tasks) { }
    public record ProjectRequest(String name, String code, String clientName, String description, Boolean billable,
                                 LocalDate startDate, LocalDate endDate, Integer budgetMinutes, Boolean allEmployees,
                                 List<Long> memberEmployeeIds) { }
    public record TaskRequest(String name, Boolean billable, Boolean active) { }

    @Transactional(readOnly = true)
    public List<ProjectDTO> listProjects() {
        Long companyId = tenant();
        List<TimesheetProject> all = projects.findAllByCompanyIdAndDeletedFalseOrderByNameAsc(companyId);
        Map<Long, List<TimesheetTask>> taskMap = tasks.findAllByCompanyIdAndDeletedFalseOrderByNameAsc(companyId).stream()
                .collect(Collectors.groupingBy(TimesheetTask::getProjectId));
        Set<Long> memberIds = all.stream().flatMap(p -> p.getMemberEmployeeIds().stream()).collect(Collectors.toSet());
        Map<Long, Employee> people = memberIds.isEmpty() ? Map.of()
                : employees.findAllByCompany_IdAndIdInAndDeletedFalseOrderByFirstNameAsc(companyId, memberIds).stream()
                        .collect(Collectors.toMap(Employee::getId, e -> e));
        Map<Long, Integer> logged = new HashMap<>();
        for (TimesheetProject p : all) {
            logged.put(p.getId(), entries.findAllByCompanyIdAndProjectIdAndDeletedFalse(companyId, p.getId()).stream()
                    .mapToInt(TimesheetEntry::getMinutes).sum());
        }
        return all.stream().map(p -> toDto(p, taskMap.getOrDefault(p.getId(), List.of()), people, logged.getOrDefault(p.getId(), 0))).toList();
    }

    @Transactional
    public ProjectDTO saveProject(Long id, ProjectRequest r) {
        Long companyId = tenant();
        TimesheetProject p = id == null ? new TimesheetProject()
                : projects.findByIdAndCompanyIdAndDeletedFalse(id, companyId).orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        if (id == null) p.setCompanyId(companyId);
        p.setName(required(r.name(), 150, "Project name"));
        p.setCode(text(r.code(), 30, "Code"));
        p.setClientName(text(r.clientName(), 150, "Client"));
        p.setDescription(text(r.description(), 1000, "Description"));
        p.setBillable(Boolean.TRUE.equals(r.billable()));
        p.setStartDate(r.startDate());
        p.setEndDate(r.endDate());
        if (r.startDate() != null && r.endDate() != null && r.endDate().isBefore(r.startDate())) {
            throw new BadRequestException("End date can't be before the start date");
        }
        if (r.budgetMinutes() != null && (r.budgetMinutes() < 0 || r.budgetMinutes() > 1_000_000 * 60)) {
            throw new BadRequestException("Budget hours look too large");
        }
        p.setBudgetMinutes(r.budgetMinutes());
        p.setAllEmployees(Boolean.TRUE.equals(r.allEmployees()));
        if (r.memberEmployeeIds() != null) {
            Set<Long> wanted = new HashSet<>(r.memberEmployeeIds());
            Set<Long> valid = wanted.isEmpty() ? Set.of()
                    : employees.findAllByCompany_IdAndIdInAndDeletedFalseOrderByFirstNameAsc(companyId, wanted).stream()
                            .map(Employee::getId).collect(Collectors.toSet());
            if (valid.size() != wanted.size()) throw new BadRequestException("Some selected employees aren't in this company");
            p.getMemberEmployeeIds().clear();
            p.getMemberEmployeeIds().addAll(valid);
        }
        TimesheetProject saved = projects.save(p);
        auditLogService.log("TIMESHEET_PROJECT", saved.getId(), id == null ? "CREATED" : "UPDATED", saved.getName());
        return listProjects().stream().filter(x -> x.id().equals(saved.getId())).findFirst().orElseThrow();
    }

    @Transactional
    public ProjectDTO setProjectStatus(Long id, boolean archived) {
        TimesheetProject p = projects.findByIdAndCompanyIdAndDeletedFalse(id, tenant()).orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        p.setStatus(archived ? TimesheetProject.ARCHIVED : TimesheetProject.ACTIVE);
        projects.save(p);
        auditLogService.log("TIMESHEET_PROJECT", p.getId(), archived ? "ARCHIVED" : "RESTORED", p.getName());
        return listProjects().stream().filter(x -> x.id().equals(id)).findFirst().orElseThrow();
    }

    @Transactional
    public ProjectDTO saveTask(Long projectId, Long taskId, TaskRequest r) {
        Long companyId = tenant();
        TimesheetProject p = projects.findByIdAndCompanyIdAndDeletedFalse(projectId, companyId).orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        TimesheetTask t = taskId == null ? new TimesheetTask()
                : tasks.findByIdAndCompanyIdAndDeletedFalse(taskId, companyId).filter(x -> x.getProjectId().equals(projectId))
                        .orElseThrow(() -> new ResourceNotFoundException("Task not found"));
        if (taskId == null) {
            t.setCompanyId(companyId);
            t.setProjectId(p.getId());
        }
        t.setName(required(r.name(), 150, "Task name"));
        t.setBillable(r.billable());
        if (r.active() != null) t.setActive(r.active());
        tasks.save(t);
        auditLogService.log("TIMESHEET_PROJECT", p.getId(), taskId == null ? "TASK_ADDED" : "TASK_UPDATED", t.getName());
        return listProjects().stream().filter(x -> x.id().equals(projectId)).findFirst().orElseThrow();
    }

    // ------------------------------------------------------------------ reports

    public record ReportRow(Long projectId, String projectName, String clientName, Long employeeId, String employeeName,
                            int minutes, int billableMinutes) { }
    public record ComplianceRow(Long employeeId, String employeeName, String employeeCode, String departmentName,
                                int loggedMinutes, int billableMinutes, int attendanceMinutes, int targetMinutes,
                                String weekStatus) { }
    public record Report(LocalDate from, LocalDate to, int totalMinutes, int billableMinutes, List<ReportRow> rows) { }

    /** Hours by project and employee for a period; HR sees everyone, approvers their team. */
    @Transactional(readOnly = true)
    public Report projectReport(LocalDate from, LocalDate to, Long projectId) {
        Long companyId = tenant();
        checkRange(from, to);
        Optional<Set<Long>> scope = reportScope();
        if (scope.isPresent() && scope.get().isEmpty()) return new Report(from, to, 0, 0, List.of());
        List<TimesheetEntry> list = entries.findAllByCompanyIdAndWorkDateBetweenAndDeletedFalse(companyId, from, to).stream()
                .filter(e -> scope.isEmpty() || scope.get().contains(e.getEmployeeId()))
                .filter(e -> projectId == null || (projectId == 0 ? e.getProjectId() == null : projectId.equals(e.getProjectId())))
                .toList();
        Map<Long, TimesheetProject> projectById = projects.findAllByCompanyIdAndDeletedFalseOrderByNameAsc(companyId).stream()
                .collect(Collectors.toMap(TimesheetProject::getId, p -> p));
        Map<Long, Employee> people = people(companyId, list.stream().map(TimesheetEntry::getEmployeeId).collect(Collectors.toSet()));
        Map<String, int[]> totals = new LinkedHashMap<>();
        for (TimesheetEntry e : list) {
            int[] t = totals.computeIfAbsent((e.getProjectId() == null ? 0 : e.getProjectId()) + ":" + e.getEmployeeId(), k -> new int[2]);
            t[0] += e.getMinutes();
            if (e.isBillable()) t[1] += e.getMinutes();
        }
        List<ReportRow> rows = new ArrayList<>();
        totals.forEach((key, t) -> {
            String[] parts = key.split(":");
            Long pid = Long.valueOf(parts[0]);
            Long eid = Long.valueOf(parts[1]);
            TimesheetProject p = projectById.get(pid);
            Employee emp = people.get(eid);
            rows.add(new ReportRow(pid == 0 ? null : pid, pid == 0 ? "General work" : p == null ? "Removed project" : p.getName(),
                    p == null ? null : p.getClientName(), eid, emp == null ? "Former employee" : emp.getFullName(), t[0], t[1]));
        });
        rows.sort(Comparator.comparing(ReportRow::projectName).thenComparing(ReportRow::minutes, Comparator.reverseOrder()));
        return new Report(from, to, list.stream().mapToInt(TimesheetEntry::getMinutes).sum(),
                list.stream().filter(TimesheetEntry::isBillable).mapToInt(TimesheetEntry::getMinutes).sum(), rows);
    }

    /** Per employee for one week: logged vs attendance time and the timesheet status. */
    @Transactional(readOnly = true)
    public List<ComplianceRow> teamWeek(LocalDate anyDay) {
        Long companyId = tenant();
        LocalDate start = TimesheetService.weekStart(anyDay);
        LocalDate end = start.plusDays(6);
        Optional<Set<Long>> scope = reportScope();
        if (scope.isPresent() && scope.get().isEmpty()) return List.of();
        List<Employee> team = (scope.isEmpty()
                ? employees.findAllByCompany_IdAndDeletedFalseOrderByFirstNameAsc(companyId)
                : employees.findAllByCompany_IdAndIdInAndDeletedFalseOrderByFirstNameAsc(companyId, scope.get())).stream()
                .filter(e -> !Set.of(com.haodaone.employee.entity.EmploymentStatus.RESIGNED, com.haodaone.employee.entity.EmploymentStatus.TERMINATED)
                        .contains(com.haodaone.employee.entity.EmploymentStatus.normalize(e.getStatus())))
                .filter(e -> e.getDateOfJoining() == null || !e.getDateOfJoining().isAfter(end))
                .toList();
        if (team.isEmpty()) return List.of();
        Set<Long> ids = team.stream().map(Employee::getId).collect(Collectors.toSet());
        Map<Long, int[]> logged = new HashMap<>();
        entries.findAllByCompanyIdAndWorkDateBetweenAndDeletedFalse(companyId, start, end).stream()
                .filter(e -> ids.contains(e.getEmployeeId()))
                .forEach(e -> {
                    int[] t = logged.computeIfAbsent(e.getEmployeeId(), k -> new int[2]);
                    t[0] += e.getMinutes();
                    if (e.isBillable()) t[1] += e.getMinutes();
                });
        Map<Long, String> status = weeks.findAllByCompanyIdAndWeekStartAndDeletedFalse(companyId, start).stream()
                .collect(Collectors.toMap(TimesheetWeek::getEmployeeId, TimesheetWeek::getStatus, (a, b) -> a));
        LocalDate today = LocalDate.now(clock);
        Map<Long, Map<LocalDate, DailyAttendanceService.EmployeeDay>> attendance = Map.of();
        if (!start.isAfter(today)) {
            try {
                attendance = dailyAttendanceService.evaluate(companyId, team, start, end.isAfter(today) ? today : end);
            } catch (RuntimeException ignored) {
                // Report still shows logged time without attendance context.
            }
        }
        int target = settingsService.forCompany(companyId).getWeeklyTargetMinutes();
        List<ComplianceRow> rows = new ArrayList<>();
        for (Employee e : team) {
            int worked = attendance.getOrDefault(e.getId(), Map.of()).values().stream()
                    .mapToInt(d -> d.evaluation() == null ? 0 : d.evaluation().workedMinutes()).sum();
            int[] t = logged.getOrDefault(e.getId(), new int[2]);
            rows.add(new ComplianceRow(e.getId(), e.getFullName(), e.getEmployeeCode(),
                    e.getDepartment() == null ? null : e.getDepartment().getName(), t[0], t[1], worked, target,
                    status.getOrDefault(e.getId(), t[0] > 0 ? TimesheetWeek.DRAFT : "NOT_STARTED")));
        }
        return rows;
    }

    // ------------------------------------------------------------------ helpers

    /** Empty = whole company (TIMESHEET_MANAGE or org-wide approver); otherwise the approver's team. */
    private Optional<Set<Long>> reportScope() {
        if (authorizationService.hasOrganizationScope("TIMESHEET_MANAGE")) return Optional.empty();
        return approvalScope.employeeIds();
    }

    private Map<Long, Employee> people(Long companyId, Set<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return employees.findAllByCompany_IdAndIdInAndDeletedFalseOrderByFirstNameAsc(companyId, ids).stream()
                .collect(Collectors.toMap(Employee::getId, e -> e));
    }

    private static void checkRange(LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from) || from.plusDays(MAX_REPORT_DAYS).isBefore(to)) {
            throw new BadRequestException("Choose a date range of up to one year");
        }
    }

    private ProjectDTO toDto(TimesheetProject p, List<TimesheetTask> projectTasks, Map<Long, Employee> people, int logged) {
        List<MemberDTO> members = p.getMemberEmployeeIds().stream().map(people::get).filter(Objects::nonNull)
                .sorted(Comparator.comparing(Employee::getFullName))
                .map(e -> new MemberDTO(e.getId(), e.getFullName(), e.getEmployeeCode())).toList();
        return new ProjectDTO(p.getId(), p.getName(), p.getCode(), p.getClientName(), p.getDescription(), p.isBillable(),
                p.getStatus(), p.getStartDate(), p.getEndDate(), p.getBudgetMinutes(), logged, p.isAllEmployees(), members,
                projectTasks.stream().map(t -> new TaskDTO(t.getId(), t.getName(), t.getBillable(), t.isActive())).toList());
    }

    private static String required(String value, int max, String label) {
        String v = text(value, max, label);
        if (v == null) throw new BadRequestException(label + " is required");
        return v;
    }

    private static String text(String value, int max, String label) {
        if (value == null) return null;
        String v = value.trim();
        if (v.isEmpty()) return null;
        if (v.length() > max) throw new BadRequestException(label + " must be " + max + " characters or fewer");
        return v;
    }

    private static Long tenant() {
        Long companyId = TenantContext.getCurrentTenant();
        if (companyId == null) throw new BadRequestException("Company context is required");
        return companyId;
    }
}
