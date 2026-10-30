package com.haodaone.timesheet;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.*;

/** Per-company timesheet policy (V58). A company without a row uses the defaults below. */
@Entity
@Table(name = "timesheet_settings")
public class TimesheetSettings extends BaseEntity {

    @Column(name = "company_id", nullable = false, unique = true)
    private Long companyId;

    @Column(nullable = false)
    private boolean enabled = true;

    /** Expected logged time per week; submitting less is allowed but flagged to the approver. */
    @Column(name = "weekly_target_minutes", nullable = false)
    private int weeklyTargetMinutes = 2400;

    @Column(name = "require_notes", nullable = false)
    private boolean requireNotes;

    @Column(name = "prompt_on_checkout", nullable = false)
    private boolean promptOnCheckout = true;

    @Column(name = "daily_reminder", nullable = false)
    private boolean dailyReminder = true;

    @Column(name = "weekly_reminder", nullable = false)
    private boolean weeklyReminder = true;

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getWeeklyTargetMinutes() { return weeklyTargetMinutes; }
    public void setWeeklyTargetMinutes(int weeklyTargetMinutes) { this.weeklyTargetMinutes = weeklyTargetMinutes; }
    public boolean isRequireNotes() { return requireNotes; }
    public void setRequireNotes(boolean requireNotes) { this.requireNotes = requireNotes; }
    public boolean isPromptOnCheckout() { return promptOnCheckout; }
    public void setPromptOnCheckout(boolean promptOnCheckout) { this.promptOnCheckout = promptOnCheckout; }
    public boolean isDailyReminder() { return dailyReminder; }
    public void setDailyReminder(boolean dailyReminder) { this.dailyReminder = dailyReminder; }
    public boolean isWeeklyReminder() { return weeklyReminder; }
    public void setWeeklyReminder(boolean weeklyReminder) { this.weeklyReminder = weeklyReminder; }
}
