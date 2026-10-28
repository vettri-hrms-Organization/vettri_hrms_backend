package com.haodaone.leave.repository;

import com.haodaone.leave.entity.HolidaySettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HolidaySettingsRepository extends JpaRepository<HolidaySettings, Long> {
    Optional<HolidaySettings> findByCompany_IdAndDeletedFalse(Long companyId);
}
