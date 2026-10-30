package com.haodaone.timesheet;

import com.haodaone.security.CustomUserPrincipal;
import com.haodaone.tenant.TenantContext;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Timesheets. /me endpoints act on the caller's own employee record; approvers (TIMESHEET_APPROVE) review
 * their team; TIMESHEET_MANAGE runs projects, settings and company-wide reports.
 */
@RestController
@RequestMapping("/api/timesheets")
@PreAuthorize("isAuthenticated()")
public class TimesheetController {

    private static final String APPROVER_OR_ADMIN = "hasAuthority('TIMESHEET_APPROVE') or hasAuthority('TIMESHEET_MANAGE')";

    private final TimesheetService service;
    private final TimesheetAdminService admin;
    private final TimesheetSettingsService settings;

    public TimesheetController(TimesheetService service, TimesheetAdminService admin, TimesheetSettingsService settings) {
        this.service = service;
        this.admin = admin;
        this.settings = settings;
    }

    // ---- my timesheet

    @GetMapping("/me")
    public TimesheetService.WeekView myWeek(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.myWeek(date);
    }

    @PutMapping("/me/entries")
    public TimesheetService.WeekView saveEntry(@RequestBody TimesheetService.EntryRequest request) {
        return service.saveEntry(request);
    }

    @DeleteMapping("/me/entries/{id}")
    public TimesheetService.WeekView deleteEntry(@PathVariable Long id) {
        return service.deleteEntry(id);
    }

    @PostMapping("/me/submit")
    public TimesheetService.WeekView submit(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.submit(date);
    }

    @PostMapping("/me/recall")
    public TimesheetService.WeekView recall(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.recall(date);
    }

    /** Readable by every employee: the check-out prompt and the timesheet page follow these switches. */
    @GetMapping("/settings")
    public TimesheetSettingsService.SettingsDTO settings() {
        return settings.get(TenantContext.getCurrentTenant());
    }

    // ---- approvers

    @GetMapping("/approvals")
    @PreAuthorize("hasAuthority('TIMESHEET_APPROVE')")
    public List<TimesheetService.ApprovalRow> approvals() {
        return service.pendingApprovals();
    }

    @GetMapping("/weeks/{id}")
    public TimesheetService.WeekView week(@PathVariable Long id) {
        return service.weekById(id);
    }

    @GetMapping("/employees/{employeeId}")
    public TimesheetService.WeekView employeeWeek(@PathVariable Long employeeId,
                                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.employeeWeek(employeeId, date);
    }

    public record ReviewBody(String note) { }

    @PostMapping("/weeks/{id}/approve")
    @PreAuthorize("hasAuthority('TIMESHEET_APPROVE')")
    public TimesheetService.WeekView approve(@PathVariable Long id, @RequestBody(required = false) ReviewBody body,
                                             @AuthenticationPrincipal CustomUserPrincipal principal) {
        return service.review(id, true, body == null ? null : body.note(), principal.getId(), name(principal));
    }

    @PostMapping("/weeks/{id}/reject")
    @PreAuthorize("hasAuthority('TIMESHEET_APPROVE')")
    public TimesheetService.WeekView reject(@PathVariable Long id, @RequestBody(required = false) ReviewBody body,
                                            @AuthenticationPrincipal CustomUserPrincipal principal) {
        return service.review(id, false, body == null ? null : body.note(), principal.getId(), name(principal));
    }

    @GetMapping("/team")
    @PreAuthorize(APPROVER_OR_ADMIN)
    public List<TimesheetAdminService.ComplianceRow> team(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return admin.teamWeek(date);
    }

    @GetMapping("/reports/projects")
    @PreAuthorize(APPROVER_OR_ADMIN)
    public TimesheetAdminService.Report projectReport(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                      @RequestParam(required = false) Long projectId) {
        return admin.projectReport(from, to, projectId);
    }

    // ---- administration

    @PutMapping("/settings")
    @PreAuthorize("hasAuthority('TIMESHEET_MANAGE')")
    public TimesheetSettingsService.SettingsDTO updateSettings(@RequestBody TimesheetSettingsService.SettingsDTO request) {
        return settings.update(TenantContext.getCurrentTenant(), request);
    }

    @GetMapping("/projects")
    @PreAuthorize("hasAuthority('TIMESHEET_MANAGE')")
    public List<TimesheetAdminService.ProjectDTO> projects() {
        return admin.listProjects();
    }

    @PostMapping("/projects")
    @PreAuthorize("hasAuthority('TIMESHEET_MANAGE')")
    public TimesheetAdminService.ProjectDTO createProject(@RequestBody TimesheetAdminService.ProjectRequest request) {
        return admin.saveProject(null, request);
    }

    @PutMapping("/projects/{id}")
    @PreAuthorize("hasAuthority('TIMESHEET_MANAGE')")
    public TimesheetAdminService.ProjectDTO updateProject(@PathVariable Long id, @RequestBody TimesheetAdminService.ProjectRequest request) {
        return admin.saveProject(id, request);
    }

    @PostMapping("/projects/{id}/archive")
    @PreAuthorize("hasAuthority('TIMESHEET_MANAGE')")
    public TimesheetAdminService.ProjectDTO archive(@PathVariable Long id) {
        return admin.setProjectStatus(id, true);
    }

    @PostMapping("/projects/{id}/restore")
    @PreAuthorize("hasAuthority('TIMESHEET_MANAGE')")
    public TimesheetAdminService.ProjectDTO restore(@PathVariable Long id) {
        return admin.setProjectStatus(id, false);
    }

    @PostMapping("/projects/{id}/tasks")
    @PreAuthorize("hasAuthority('TIMESHEET_MANAGE')")
    public TimesheetAdminService.ProjectDTO addTask(@PathVariable Long id, @RequestBody TimesheetAdminService.TaskRequest request) {
        return admin.saveTask(id, null, request);
    }

    @PutMapping("/projects/{id}/tasks/{taskId}")
    @PreAuthorize("hasAuthority('TIMESHEET_MANAGE')")
    public TimesheetAdminService.ProjectDTO updateTask(@PathVariable Long id, @PathVariable Long taskId,
                                                       @RequestBody TimesheetAdminService.TaskRequest request) {
        return admin.saveTask(id, taskId, request);
    }

    private static String name(CustomUserPrincipal principal) {
        if (principal == null || principal.getUser() == null) return "Approver";
        String full = principal.getUser().getFullName();
        return full == null || full.isBlank() ? principal.getUsername() : full;
    }
}
