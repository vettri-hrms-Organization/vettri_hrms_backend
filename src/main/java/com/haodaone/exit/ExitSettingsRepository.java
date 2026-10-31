package com.haodaone.exit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ExitSettingsRepository extends JpaRepository<ExitSettings, Long> {
    Optional<ExitSettings> findByCompanyIdAndDeletedFalse(Long companyId);
}
