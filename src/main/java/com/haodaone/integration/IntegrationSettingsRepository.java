package com.haodaone.integration;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IntegrationSettingsRepository extends JpaRepository<IntegrationSettings, Long> {
    Optional<IntegrationSettings> findFirstByCompanyIdAndDeletedFalse(Long companyId);
}
