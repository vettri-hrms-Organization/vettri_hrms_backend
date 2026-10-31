package com.haodaone.probation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProbationSettingsRepository extends JpaRepository<ProbationSettings, Long> {
    Optional<ProbationSettings> findFirstByCompanyIdAndDeletedFalse(Long companyId);
    List<ProbationSettings> findAllByEnabledTrueAndDeletedFalse();
}
