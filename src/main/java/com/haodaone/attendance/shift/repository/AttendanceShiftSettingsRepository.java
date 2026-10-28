package com.haodaone.attendance.shift.repository;

import com.haodaone.attendance.shift.entity.AttendanceShiftSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AttendanceShiftSettingsRepository extends JpaRepository<AttendanceShiftSettings, Long> {
    Optional<AttendanceShiftSettings> findByCompany_IdAndDeletedFalse(Long companyId);
}
