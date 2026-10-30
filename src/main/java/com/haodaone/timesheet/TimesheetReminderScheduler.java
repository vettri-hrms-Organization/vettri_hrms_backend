package com.haodaone.timesheet;

import com.haodaone.attendance.shift.service.DailyAttendanceService;
import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.entity.EmploymentStatus;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.notifications.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Timesheet nudges, sent as in-app notifications (which also reach the Vettri Agent tray):
 * 19:00 IST - people who worked today (attendance shows time) but logged nothing;
 * Monday 10:00 IST - people whose previous week has time but isn't submitted yet.
 * Each company can switch either reminder off in timesheet settings.
 */
@Component
public class TimesheetReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(TimesheetReminderScheduler.class);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final Set<String> LEFT = Set.of(EmploymentStatus.RESIGNED, EmploymentStatus.TERMINATED);

    private final CompanyRepository companies;
    private final EmployeeRepository employees;
    private final TimesheetSettingsService settingsService;
    private final TimesheetEntryRepository entries;
    private final TimesheetWeekRepository weeks;
    private final DailyAttendanceService dailyAttendanceService;
    private final NotificationService notificationService;
    private final TransactionTemplate transactions;

    public TimesheetReminderScheduler(CompanyRepository companies, EmployeeRepository employees, TimesheetSettingsService settingsService,
                                      TimesheetEntryRepository entries, TimesheetWeekRepository weeks,
                                      DailyAttendanceService dailyAttendanceService, NotificationService notificationService,
                                      TransactionTemplate transactions) {
        this.companies = companies;
        this.employees = employees;
        this.settingsService = settingsService;
        this.entries = entries;
        this.weeks = weeks;
        this.dailyAttendanceService = dailyAttendanceService;
        this.notificationService = notificationService;
        this.transactions = transactions;
    }

    @Scheduled(cron = "0 0 19 * * MON-SAT", zone = "Asia/Kolkata")
    public void dailyReminders() {
        LocalDate today = LocalDate.now(IST);
        for (Company company : companies.findAll()) {
            if (company.isDeleted()) continue;
            try {
                Integer sent = transactions.execute(status -> remindToday(company.getId(), today));
                if (sent != null && sent > 0) log.info("Sent {} timesheet reminder(s) for company {}", sent, company.getId());
            } catch (RuntimeException ex) {
                log.error("Daily timesheet reminders failed for company {}", company.getId(), ex);
            }
        }
    }

    @Scheduled(cron = "0 0 10 * * MON", zone = "Asia/Kolkata")
    public void weeklyReminders() {
        LocalDate lastWeek = TimesheetService.weekStart(LocalDate.now(IST)).minusWeeks(1);
        for (Company company : companies.findAll()) {
            if (company.isDeleted()) continue;
            try {
                Integer sent = transactions.execute(status -> remindUnsubmitted(company.getId(), lastWeek));
                if (sent != null && sent > 0) log.info("Sent {} weekly timesheet reminder(s) for company {}", sent, company.getId());
            } catch (RuntimeException ex) {
                log.error("Weekly timesheet reminders failed for company {}", company.getId(), ex);
            }
        }
    }

    int remindToday(Long companyId, LocalDate today) {
        TimesheetSettings settings = settingsService.forCompany(companyId);
        if (!settings.isEnabled() || !settings.isDailyReminder()) return 0;
        List<Employee> staff = activeWithLogin(companyId);
        if (staff.isEmpty()) return 0;
        Map<Long, Map<LocalDate, DailyAttendanceService.EmployeeDay>> attendance = dailyAttendanceService.evaluate(companyId, staff, today, today);
        int sent = 0;
        for (Employee e : staff) {
            DailyAttendanceService.EmployeeDay day = attendance.getOrDefault(e.getId(), Map.of()).get(today);
            boolean worked = day != null && day.evaluation() != null && day.evaluation().punched();
            if (!worked || entries.existsByEmployeeIdAndWorkDateAndDeletedFalse(e.getId(), today)) continue;
            boolean locked = weeks.findByEmployeeIdAndWeekStartAndDeletedFalse(e.getId(), TimesheetService.weekStart(today))
                    .map(w -> !w.isEditable()).orElse(false);
            if (locked) continue;
            notificationService.notifyUser(companyId, e.getUser().getId(), "TIMESHEET_REMINDER", "Log today's work",
                    "Take a minute to add what you worked on today to your timesheet.", "TIMESHEET_WEEK", null, "NORMAL");
            sent++;
        }
        return sent;
    }

    int remindUnsubmitted(Long companyId, LocalDate weekStart) {
        TimesheetSettings settings = settingsService.forCompany(companyId);
        if (!settings.isEnabled() || !settings.isWeeklyReminder()) return 0;
        int sent = 0;
        for (Employee e : activeWithLogin(companyId)) {
            String status = weeks.findByEmployeeIdAndWeekStartAndDeletedFalse(e.getId(), weekStart).map(TimesheetWeek::getStatus).orElse(null);
            if (TimesheetWeek.SUBMITTED.equals(status) || TimesheetWeek.APPROVED.equals(status)) continue;
            boolean hasTime = !entries.findAllByEmployeeIdAndWorkDateBetweenAndDeletedFalseOrderByWorkDateAscIdAsc(e.getId(), weekStart, weekStart.plusDays(6)).isEmpty();
            if (!hasTime) continue;
            notificationService.notifyUser(companyId, e.getUser().getId(), "TIMESHEET_REMINDER",
                    TimesheetWeek.REJECTED.equals(status) ? "Timesheet needs your changes" : "Submit last week's timesheet",
                    "Your timesheet for the week of " + weekStart + " hasn't been submitted yet.", "TIMESHEET_WEEK", null, "NORMAL");
            sent++;
        }
        return sent;
    }

    private List<Employee> activeWithLogin(Long companyId) {
        return employees.findAllByCompany_IdAndDeletedFalseOrderByFirstNameAsc(companyId).stream()
                .filter(e -> e.getUser() != null && e.getUser().isActive())
                .filter(e -> !LEFT.contains(EmploymentStatus.normalize(e.getStatus())))
                .toList();
    }
}
