package com.haodaone.timesheet;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TimesheetSettingsRepository extends JpaRepository<TimesheetSettings, Long> {
    Optional<TimesheetSettings> findByCompanyIdAndDeletedFalse(Long companyId);
}
