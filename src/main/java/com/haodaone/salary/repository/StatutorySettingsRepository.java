package com.haodaone.salary.repository;

import com.haodaone.salary.entity.StatutorySettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StatutorySettingsRepository extends JpaRepository<StatutorySettings, Long> {

    Optional<StatutorySettings> findByCompany_IdAndDeletedFalse(Long companyId);
}
