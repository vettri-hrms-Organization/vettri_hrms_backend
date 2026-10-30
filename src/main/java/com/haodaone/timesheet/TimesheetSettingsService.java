package com.haodaone.timesheet;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reads and updates the per-company timesheet policy; companies without a row get the defaults. */
@Service
public class TimesheetSettingsService {

    private final TimesheetSettingsRepository repository;
    private final AuditLogService auditLogService;

    public TimesheetSettingsService(TimesheetSettingsRepository repository, AuditLogService auditLogService) {
        this.repository = repository;
        this.auditLogService = auditLogService;
    }

    public record SettingsDTO(boolean enabled, int weeklyTargetMinutes, boolean requireNotes, boolean promptOnCheckout,
                              boolean dailyReminder, boolean weeklyReminder) {
        static SettingsDTO of(TimesheetSettings s) {
            return new SettingsDTO(s.isEnabled(), s.getWeeklyTargetMinutes(), s.isRequireNotes(), s.isPromptOnCheckout(),
                    s.isDailyReminder(), s.isWeeklyReminder());
        }
    }

    @Transactional(readOnly = true)
    public TimesheetSettings forCompany(Long companyId) {
        return repository.findByCompanyIdAndDeletedFalse(companyId).orElseGet(() -> {
            TimesheetSettings defaults = new TimesheetSettings();
            defaults.setCompanyId(companyId);
            return defaults;
        });
    }

    @Transactional(readOnly = true)
    public SettingsDTO get(Long companyId) {
        return SettingsDTO.of(forCompany(companyId));
    }

    @Transactional
    public SettingsDTO update(Long companyId, SettingsDTO request) {
        if (request == null) throw new BadRequestException("Settings are required");
        if (request.weeklyTargetMinutes() < 0 || request.weeklyTargetMinutes() > 7 * 24 * 60) {
            throw new BadRequestException("Weekly target must be between 0 and 168 hours");
        }
        TimesheetSettings s = repository.findByCompanyIdAndDeletedFalse(companyId).orElseGet(() -> {
            TimesheetSettings created = new TimesheetSettings();
            created.setCompanyId(companyId);
            return created;
        });
        s.setEnabled(request.enabled());
        s.setWeeklyTargetMinutes(request.weeklyTargetMinutes());
        s.setRequireNotes(request.requireNotes());
        s.setPromptOnCheckout(request.promptOnCheckout());
        s.setDailyReminder(request.dailyReminder());
        s.setWeeklyReminder(request.weeklyReminder());
        TimesheetSettings saved = repository.save(s);
        auditLogService.log("TIMESHEET_SETTINGS", saved.getId(), "UPDATED", "Timesheet settings updated");
        return SettingsDTO.of(saved);
    }
}
