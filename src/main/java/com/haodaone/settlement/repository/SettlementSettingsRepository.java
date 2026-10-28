package com.haodaone.settlement.repository;

import com.haodaone.settlement.entity.SettlementSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SettlementSettingsRepository extends JpaRepository<SettlementSettings, Long> {
    Optional<SettlementSettings> findByCompany_IdAndDeletedFalse(Long companyId);
}
