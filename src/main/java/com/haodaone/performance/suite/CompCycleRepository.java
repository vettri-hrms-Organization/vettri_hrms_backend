package com.haodaone.performance.suite;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompCycleRepository extends JpaRepository<CompCycle, Long> {
    List<CompCycle> findAllByCompanyIdAndDeletedFalseOrderByEffectiveFromDesc(Long companyId);
    Optional<CompCycle> findByIdAndCompanyIdAndDeletedFalse(Long id, Long companyId);
}
