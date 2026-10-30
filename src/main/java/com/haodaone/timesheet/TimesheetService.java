package com.haodaone.timesheet;

import com.haodaone.attendance.shift.service.DailyAttendanceService;
import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.notifications.service.NotificationService;
import com.haodaone.security.AuthorizationService;
import com.haodaone.tenant.TenantContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Keka-style weekly timesheets. Employees log time per day against projects/tasks (or general work),
 * submit the week, and their approver (TIMESHEET_APPROVE: managers for their team, HR for everyone)
 * approves or sends it back. Approved weeks are locked; nobody approves their own timesheet.
 */
@Service
public class TimesheetService {

    static final int MAX_DAY_MINUTES = 24 * 60;

    private final TimesheetEntryRepository entries;
    private final TimesheetWeekRepository weeks;
    private final TimesheetProjectRepository projects;
    private final TimesheetTaskRepository tasks;
    private final TimesheetSettingsService settingsService;
    private final EmployeeRepository employees;
    private final AuthorizationService authorizationService;
    private final DailyAttendanceService dailyAttendanceService;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final Clock clock;
    private final TimesheetApprovalScope approvalScope;

    public TimesheetService(TimesheetEntryRepository entries, TimesheetWeekRepository weeks, TimesheetProjectRepository projects,
                            TimesheetTaskRepository tasks, TimesheetSettingsService settingsService, EmployeeRepository employees,
                            AuthorizationService authorizationService, DailyAttendanceService dailyAttendanceService,
                            NotificationService notificationService, AuditLogService auditLogService, Clock applicationClock,
                            TimesheetApprovalScope approvalScope) {
        this.entries = entries;
        this.weeks = weeks;
        this.projects = projects;
        this.tasks = tasks;
        this.settingsService = settingsService;
        this.employees = employees;
        this.authorizationService = authorizationService;
        this.dailyAttendanceService = dailyAttendanceService;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
        this.clock = applicationClock;
        this.approvalScope = approvalScope;
    }

    // ------------------------------------------------------------------ DTOs

    public record TaskOption(Long id, String name, boolean billable) { }
    public record ProjectOption(Long id, String name, String code, String clientName, boolean billable, List<TaskOption> tasks) { }
    public record EntryDTO(Long id, LocalDate workDate, Long projectId, String projectName, Long taskId, String taskName,
                           int minutes, String notes, boolean billable) { }
    public record DayInfo(LocalDate date, int attendanceMinutes, boolean holiday, boolean weekOff, boolean onLeave, boolean future) { }
    public record WeekView(Long weekId, Long employeeId, String employeeName, LocalDate weekStart, LocalDate weekEnd, String status,
                           boolean editable, int totalMinutes, int billableMinutes, int targetMinutes, int attendanceMinutes,
                           LocalDateTime submittedAt, String reviewedByName, String reviewNote, LocalDateTime reviewedAt,
                           boolean requireNotes, boolean canReview, List<DayInfo> days, List<EntryDTO> entries,
                           List<ProjectOption> projects) { }
    public record EntryRequest(Long id, LocalDate workDate, Long projectId, Long taskId, Integer minutes, String notes) { }
    public record ApprovalRow(Long weekId, Long employeeId, String employeeName, String employeeCode, String departmentName,
                              LocalDate weekStart, int totalMinutes, int billableMinutes, int targetMinutes,
                              LocalDateTime submittedAt, String projectSummary) { }

    // ------------------------------------------------------------------ employee

    @Transactional(readOnly = true)
    public WeekView myWeek(LocalDate anyDay) {
        return view(currentEmployee(), weekStart(anyDay == null ? today() : anyDay));
    }

    /** Creates, updates or (minutes 0) removes the entry for one day + project + task. */
    @Transactional
    public WeekView saveEntry(EntryRequest request) {
        Employee me = currentEmployee();
        if (request == null || request.workDate() == null) throw new BadRequestException("Choose a date");
        LocalDate date = request.workDate();
        if (date.isAfter(today())) throw new BadRequestException("You can't log time for a future date");
        if (me.getDateOfJoining() != null && date.isBefore(me.getDateOfJoining())) throw new BadRequestException("That date is before your joining date");
        TimesheetWeek week = editableWeek(me, weekStart(date));
        int minutes = request.minutes() == null ? 0 : request.minutes();
        if (minutes < 0 || minutes > MAX_DAY_MINUTES) throw new BadRequestException("Hours must be between 0 and 24");

        List<TimesheetEntry> sameDay = entries.findAllByEmployeeIdAndWorkDateBetweenAndDeletedFalseOrderByWorkDateAscIdAsc(me.getId(), date, date);
        TimesheetEntry entry = request.id() != null
                ? sameDay.stream().filter(e -> e.getId().equals(request.id())).findFirst()
                        .or(() -> entries.findByIdAndEmployeeIdAndDeletedFalse(request.id(), me.getId()))
                        .orElseThrow(() -> new ResourceNotFoundException("Entry not found"))
                : sameDay.stream().filter(e -> Objects.equals(e.getProjectId(), request.projectId()) && Objects.equals(e.getTaskId(), request.taskId()))
                        .findFirst().orElse(null);
        if (entry != null && !entry.getWorkDate().equals(date)) {
            throw new BadRequestException("An entry can't be moved to another day; delete it and add a new one");
        }

        if (minutes == 0) {
            if (entry != null) softDelete(entry);
        } else {
            TimesheetSettings settings = settingsService.forCompany(me.getCompany().getId());
            String notes = trim(request.notes(), 1000);
            if (settings.isRequireNotes() && notes == null) throw new BadRequestException("Add a short note about what you worked on");
            boolean billable = validateTarget(me, date, request.projectId(), request.taskId());
            int otherMinutes = sameDay.stream().filter(e -> entry == null || !e.getId().equals(entry.getId()))
                    .mapToInt(TimesheetEntry::getMinutes).sum();
            if (otherMinutes + minutes > MAX_DAY_MINUTES) throw new BadRequestException("A day can't have more than 24 hours logged");
            TimesheetEntry target = entry != null ? entry : new TimesheetEntry();
            if (entry == null) {
                target.setCompanyId(me.getCompany().getId());
                target.setEmployeeId(me.getId());
                target.setWorkDate(date);
            }
            target.setProjectId(request.projectId());
            target.setTaskId(request.projectId() == null ? null : request.taskId());
            target.setMinutes(minutes);
            target.setNotes(notes);
            target.setBillable(billable);
            entries.save(target);
        }
        recalculate(week);
        return view(me, week.getWeekStart());
    }

    @Transactional
    public WeekView deleteEntry(Long id) {
        Employee me = currentEmployee();
        TimesheetEntry entry = entries.findByIdAndEmployeeIdAndDeletedFalse(id, me.getId()).orElseThrow(() -> new ResourceNotFoundException("Entry not found"));
        TimesheetWeek week = editableWeek(me, weekStart(entry.getWorkDate()));
        softDelete(entry);
        recalculate(week);
        return view(me, week.getWeekStart());
    }

    @Transactional
    public WeekView submit(LocalDate anyDay) {
        Employee me = currentEmployee();
        TimesheetWeek week = editableWeek(me, weekStart(anyDay));
        recalculate(week);
        if (week.getTotalMinutes() <= 0) throw new BadRequestException("Log some time before submitting this week");
        week.setStatus(TimesheetWeek.SUBMITTED);
        week.setSubmittedAt(LocalDateTime.now(clock));
        week.setReviewNote(null);
        week.setReviewedAt(null);
        week.setReviewedByName(null);
        week.setReviewedByUserId(null);
        weeks.save(week);
        auditLogService.log("TIMESHEET_WEEK", week.getId(), "SUBMITTED", me.getFullName() + " submitted " + hours(week.getTotalMinutes()) + " for the week of " + week.getWeekStart());
        notificationService.notifyPermissionRecipients(me.getCompany().getId(), me.getId(), "TIMESHEET_APPROVE",
                "TIMESHEET_SUBMITTED", "Timesheet to review",
                me.getFullName() + " submitted " + hours(week.getTotalMinutes()) + " for the week of " + week.getWeekStart() + ".",
                "TIMESHEET_WEEK", week.getId());
        // The reporting manager approves too (see TimesheetApprovalScope); the call above only reaches them via a shared team.
        Employee manager = me.getReportingManager();
        boolean sameTeam = manager != null && manager.getTeam() != null && me.getTeam() != null
                && manager.getTeam().getId().equals(me.getTeam().getId());
        if (manager != null && manager.getUser() != null && !sameTeam) {
            notificationService.notifyUser(me.getCompany().getId(), manager.getUser().getId(), "TIMESHEET_SUBMITTED",
                    "Timesheet to review", me.getFullName() + " submitted " + hours(week.getTotalMinutes())
                            + " for the week of " + week.getWeekStart() + ".", "TIMESHEET_WEEK", week.getId(), "NORMAL");
        }
        return view(me, week.getWeekStart());
    }

    /** Takes a submitted (not yet reviewed) week back to draft so the employee can correct it. */
    @Transactional
    public WeekView recall(LocalDate anyDay) {
        Employee me = currentEmployee();
        TimesheetWeek week = weeks.findByEmployeeIdAndWeekStartAndDeletedFalse(me.getId(), weekStart(anyDay))
                .orElseThrow(() -> new BadRequestException("This week hasn't been submitted"));
        if (!TimesheetWeek.SUBMITTED.equals(week.getStatus())) throw new BadRequestException("Only a submitted week that isn't reviewed yet can be recalled");
        week.setStatus(TimesheetWeek.DRAFT);
        week.setSubmittedAt(null);
        weeks.save(week);
        auditLogService.log("TIMESHEET_WEEK", week.getId(), "RECALLED", me.getFullName() + " recalled their timesheet");
        return view(me, week.getWeekStart());
    }

    // ------------------------------------------------------------------ approver

    @Transactional(readOnly = true)
    public List<ApprovalRow> pendingApprovals() {
        Long companyId = tenant();
        Optional<Set<Long>> scope = approvalScope.employeeIds();
        if (scope.isPresent() && scope.get().isEmpty()) return List.of();
        Employee me = authorizationService.resolveCurrentEmployee();
        List<TimesheetWeek> pending = weeks.findAllByCompanyIdAndStatusAndDeletedFalseOrderByWeekStartAscIdAsc(companyId, TimesheetWeek.SUBMITTED).stream()
                .filter(w -> scope.isEmpty() || scope.get().contains(w.getEmployeeId()))
                .filter(w -> me == null || !me.getId().equals(w.getEmployeeId()))
                .toList();
        if (pending.isEmpty()) return List.of();
        Map<Long, Employee> people = employees.findAllByCompany_IdAndIdInAndDeletedFalseOrderByFirstNameAsc(companyId,
                pending.stream().map(TimesheetWeek::getEmployeeId).collect(Collectors.toSet())).stream()
                .collect(Collectors.toMap(Employee::getId, e -> e));
        int target = settingsService.forCompany(companyId).getWeeklyTargetMinutes();
        List<ApprovalRow> rows = new ArrayList<>();
        for (TimesheetWeek w : pending) {
            Employee e = people.get(w.getEmployeeId());
            if (e == null) continue;
            List<TimesheetEntry> list = entries.findAllByEmployeeIdAndWorkDateBetweenAndDeletedFalseOrderByWorkDateAscIdAsc(e.getId(), w.getWeekStart(), w.getWeekStart().plusDays(6));
            rows.add(new ApprovalRow(w.getId(), e.getId(), e.getFullName(), e.getEmployeeCode(),
                    e.getDepartment() == null ? null : e.getDepartment().getName(), w.getWeekStart(), w.getTotalMinutes(),
                    list.stream().filter(TimesheetEntry::isBillable).mapToInt(TimesheetEntry::getMinutes).sum(), target,
                    w.getSubmittedAt(), projectSummary(companyId, list)));
        }
        return rows;
    }

    /** Any employee's week, for themselves, their approver or a TIMESHEET_MANAGE holder. */
    @Transactional(readOnly = true)
    public WeekView employeeWeek(Long employeeId, LocalDate anyDay) {
        Employee e = employees.findByIdAndCompany_IdAndDeletedFalse(employeeId, tenant()).orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
        Employee me = authorizationService.resolveCurrentEmployee();
        boolean self = me != null && me.getId().equals(employeeId);
        if (!self && !approvalScope.canApprove(employeeId)
                && !authorizationService.hasOrganizationScope("TIMESHEET_MANAGE")) {
            throw new AccessDeniedException("You can't view this timesheet");
        }
        return view(e, weekStart(anyDay));
    }

    @Transactional(readOnly = true)
    public WeekView weekById(Long weekId) {
        TimesheetWeek w = weeks.findByIdAndCompanyIdAndDeletedFalse(weekId, tenant()).orElseThrow(() -> new ResourceNotFoundException("Timesheet not found"));
        return employeeWeek(w.getEmployeeId(), w.getWeekStart());
    }

    @Transactional
    public WeekView review(Long weekId, boolean approve, String note, Long reviewerUserId, String reviewerName) {
        Long companyId = tenant();
        TimesheetWeek w = weeks.findByIdAndCompanyIdAndDeletedFalse(weekId, companyId).orElseThrow(() -> new ResourceNotFoundException("Timesheet not found"));
        if (!TimesheetWeek.SUBMITTED.equals(w.getStatus())) throw new BadRequestException("This timesheet is " + w.getStatus().toLowerCase(Locale.ROOT) + ", not waiting for review");
        Employee me = authorizationService.resolveCurrentEmployee();
        if (me != null && me.getId().equals(w.getEmployeeId())) throw new BadRequestException("You can't approve your own timesheet");
        if (!approvalScope.canApprove(w.getEmployeeId())) throw new AccessDeniedException("This timesheet isn't in your team");
        String trimmed = trim(note, 500);
        if (!approve && trimmed == null) throw new BadRequestException("Tell the employee what to fix");
        w.setStatus(approve ? TimesheetWeek.APPROVED : TimesheetWeek.REJECTED);
        w.setReviewedAt(LocalDateTime.now(clock));
        w.setReviewedByUserId(reviewerUserId);
        w.setReviewedByName(reviewerName);
        w.setReviewNote(trimmed);
        weeks.save(w);
        Employee e = employees.findByIdAndCompany_IdAndDeletedFalse(w.getEmployeeId(), companyId).orElseThrow();
        auditLogService.log("TIMESHEET_WEEK", w.getId(), approve ? "APPROVED" : "REJECTED",
                reviewerName + (approve ? " approved " : " returned ") + e.getFullName() + "'s timesheet for the week of " + w.getWeekStart());
        if (e.getUser() != null) {
            notificationService.notifyUser(companyId, e.getUser().getId(), approve ? "TIMESHEET_APPROVED" : "TIMESHEET_REJECTED",
                    approve ? "Timesheet approved" : "Timesheet sent back",
                    approve ? "Your timesheet for the week of " + w.getWeekStart() + " was approved."
                            : "Your timesheet for the week of " + w.getWeekStart() + " needs changes: " + trimmed,
                    "TIMESHEET_WEEK", w.getId(), approve ? "NORMAL" : "HIGH");
        }
        return view(e, w.getWeekStart());
    }

    // ------------------------------------------------------------------ view building

    WeekView view(Employee e, LocalDate start) {
        Long companyId = e.getCompany().getId();
        LocalDate end = start.plusDays(6);
        TimesheetSettings settings = settingsService.forCompany(companyId);
        Optional<TimesheetWeek> week = weeks.findByEmployeeIdAndWeekStartAndDeletedFalse(e.getId(), start);
        List<TimesheetEntry> list = entries.findAllByEmployeeIdAndWorkDateBetweenAndDeletedFalseOrderByWorkDateAscIdAsc(e.getId(), start, end);
        Map<Long, TimesheetProject> projectById = projectsById(companyId, list.stream().map(TimesheetEntry::getProjectId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, TimesheetTask> taskById = tasksById(companyId, list.stream().map(TimesheetEntry::getTaskId).filter(Objects::nonNull).collect(Collectors.toSet()));
        List<EntryDTO> entryDtos = list.stream().map(x -> new EntryDTO(x.getId(), x.getWorkDate(), x.getProjectId(),
                x.getProjectId() == null ? "General work" : Optional.ofNullable(projectById.get(x.getProjectId())).map(TimesheetProject::getName).orElse("Removed project"),
                x.getTaskId(), x.getTaskId() == null ? null : Optional.ofNullable(taskById.get(x.getTaskId())).map(TimesheetTask::getName).orElse(null),
                x.getMinutes(), x.getNotes(), x.isBillable())).toList();

        LocalDate today = today();
        List<DayInfo> days = new ArrayList<>();
        Map<LocalDate, DailyAttendanceService.EmployeeDay> attendance = Map.of();
        try {
            attendance = dailyAttendanceService.evaluate(companyId, List.of(e), start, end.isAfter(today) ? today : end).getOrDefault(e.getId(), Map.of());
        } catch (RuntimeException ignored) {
            // Attendance context is a convenience; the timesheet still works without it.
        }
        int attendanceTotal = 0;
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            DailyAttendanceService.EmployeeDay day = attendance.get(d);
            int worked = day == null || day.evaluation() == null ? 0 : day.evaluation().workedMinutes();
            attendanceTotal += worked;
            days.add(new DayInfo(d, worked, day != null && day.holiday(), day != null && day.weekOff(),
                    day != null && day.leave() != null && day.leave() != com.haodaone.attendance.shift.service.ShiftEvaluator.LeaveKind.NONE,
                    d.isAfter(today)));
        }

        Employee viewer = authorizationService.resolveCurrentEmployee();
        boolean canReview = week.isPresent() && TimesheetWeek.SUBMITTED.equals(week.get().getStatus())
                && (viewer == null || !viewer.getId().equals(e.getId()))
                && approvalScope.canApprove(e.getId());
        int total = list.stream().mapToInt(TimesheetEntry::getMinutes).sum();
        return new WeekView(week.map(TimesheetWeek::getId).orElse(null), e.getId(), e.getFullName(), start, end,
                week.map(TimesheetWeek::getStatus).orElse(TimesheetWeek.DRAFT), week.map(TimesheetWeek::isEditable).orElse(true),
                total, list.stream().filter(TimesheetEntry::isBillable).mapToInt(TimesheetEntry::getMinutes).sum(),
                settings.getWeeklyTargetMinutes(), attendanceTotal,
                week.map(TimesheetWeek::getSubmittedAt).orElse(null), week.map(TimesheetWeek::getReviewedByName).orElse(null),
                week.map(TimesheetWeek::getReviewNote).orElse(null), week.map(TimesheetWeek::getReviewedAt).orElse(null),
                settings.isRequireNotes(), canReview, days, entryDtos, projectOptions(e, start, end));
    }

    /** Projects this employee may log against during the week (active on at least one day of it). */
    List<ProjectOption> projectOptions(Employee e, LocalDate start, LocalDate end) {
        Long companyId = e.getCompany().getId();
        List<TimesheetProject> open = projects.findAllByCompanyIdAndDeletedFalseOrderByNameAsc(companyId).stream()
                .filter(p -> p.isOpenTo(e.getId()) && TimesheetProject.ACTIVE.equals(p.getStatus())
                        && (p.getStartDate() == null || !p.getStartDate().isAfter(end))
                        && (p.getEndDate() == null || !p.getEndDate().isBefore(start)))
                .toList();
        if (open.isEmpty()) return List.of();
        Map<Long, List<TimesheetTask>> taskMap = tasks.findAllByCompanyIdAndDeletedFalseOrderByNameAsc(companyId).stream()
                .filter(TimesheetTask::isActive).collect(Collectors.groupingBy(TimesheetTask::getProjectId));
        return open.stream().map(p -> new ProjectOption(p.getId(), p.getName(), p.getCode(), p.getClientName(), p.isBillable(),
                taskMap.getOrDefault(p.getId(), List.of()).stream()
                        .map(t -> new TaskOption(t.getId(), t.getName(), t.getBillable() == null ? p.isBillable() : t.getBillable())).toList()))
                .toList();
    }

    // ------------------------------------------------------------------ helpers

    private boolean validateTarget(Employee me, LocalDate date, Long projectId, Long taskId) {
        if (projectId == null) {
            if (taskId != null) throw new BadRequestException("Choose a project for this task");
            return false;
        }
        TimesheetProject project = projects.findByIdAndCompanyIdAndDeletedFalse(projectId, me.getCompany().getId())
                .orElseThrow(() -> new BadRequestException("Project not found"));
        if (!project.isOpenTo(me.getId())) throw new BadRequestException("You're not on the " + project.getName() + " project");
        if (!project.isActiveOn(date)) throw new BadRequestException(project.getName() + " isn't open for time on " + date);
        if (taskId == null) return project.isBillable();
        TimesheetTask task = tasks.findByIdAndCompanyIdAndDeletedFalse(taskId, me.getCompany().getId())
                .filter(t -> t.getProjectId().equals(projectId) && t.isActive())
                .orElseThrow(() -> new BadRequestException("That task isn't part of " + project.getName()));
        return task.getBillable() == null ? project.isBillable() : task.getBillable();
    }

    private TimesheetWeek editableWeek(Employee me, LocalDate start) {
        TimesheetWeek week = weeks.findByEmployeeIdAndWeekStartAndDeletedFalse(me.getId(), start).orElseGet(() -> {
            TimesheetWeek created = new TimesheetWeek();
            created.setCompanyId(me.getCompany().getId());
            created.setEmployeeId(me.getId());
            created.setWeekStart(start);
            return weeks.save(created);
        });
        if (!week.isEditable()) {
            throw new BadRequestException(TimesheetWeek.APPROVED.equals(week.getStatus())
                    ? "This week is approved and locked"
                    : "This week is waiting for approval. Recall it to make changes.");
        }
        return week;
    }

    private void recalculate(TimesheetWeek week) {
        int total = entries.findAllByEmployeeIdAndWorkDateBetweenAndDeletedFalseOrderByWorkDateAscIdAsc(
                week.getEmployeeId(), week.getWeekStart(), week.getWeekStart().plusDays(6)).stream().mapToInt(TimesheetEntry::getMinutes).sum();
        week.setTotalMinutes(total);
        weeks.save(week);
    }

    private String projectSummary(Long companyId, List<TimesheetEntry> list) {
        Map<Long, Integer> byProject = new LinkedHashMap<>();
        list.forEach(x -> byProject.merge(x.getProjectId() == null ? -1L : x.getProjectId(), x.getMinutes(), Integer::sum));
        Map<Long, TimesheetProject> names = projectsById(companyId, byProject.keySet().stream().filter(id -> id > 0).collect(Collectors.toSet()));
        return byProject.entrySet().stream().sorted(Map.Entry.<Long, Integer>comparingByValue().reversed())
                .map(x -> (x.getKey() < 0 ? "General" : Optional.ofNullable(names.get(x.getKey())).map(TimesheetProject::getName).orElse("Project")) + " " + hours(x.getValue()))
                .collect(Collectors.joining(" · "));
    }

    private Map<Long, TimesheetProject> projectsById(Long companyId, Set<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return projects.findAllByCompanyIdAndIdIn(companyId, ids).stream().collect(Collectors.toMap(TimesheetProject::getId, p -> p));
    }

    private Map<Long, TimesheetTask> tasksById(Long companyId, Set<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return tasks.findAllByCompanyIdAndIdIn(companyId, ids).stream().collect(Collectors.toMap(TimesheetTask::getId, t -> t));
    }

    private void softDelete(TimesheetEntry entry) {
        entry.setDeleted(true);
        entry.setDeletedAt(LocalDateTime.now(clock));
        entries.save(entry);
    }

    static LocalDate weekStart(LocalDate anyDay) {
        if (anyDay == null) throw new BadRequestException("Choose a week");
        return anyDay.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    static String hours(int minutes) {
        int h = minutes / 60;
        int m = minutes % 60;
        return m == 0 ? h + "h" : h + "h " + m + "m";
    }

    private static String trim(String value, int max) {
        if (value == null) return null;
        String v = value.trim();
        if (v.isEmpty()) return null;
        if (v.length() > max) throw new BadRequestException("Keep it under " + max + " characters");
        return v;
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private Employee currentEmployee() {
        Employee me = authorizationService.resolveCurrentEmployee();
        if (me == null || me.getCompany() == null) throw new BadRequestException("Your login isn't linked to an employee record");
        if (!settingsService.forCompany(me.getCompany().getId()).isEnabled()) throw new BadRequestException("Timesheets are turned off for your company");
        return me;
    }

    private static Long tenant() {
        Long companyId = TenantContext.getCurrentTenant();
        if (companyId == null) throw new BadRequestException("Company context is required");
        return companyId;
    }
}
