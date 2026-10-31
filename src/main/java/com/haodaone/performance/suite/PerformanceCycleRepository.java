package com.haodaone.performance.suite;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PerformanceCycleRepository extends JpaRepository<PerformanceCycle, Long> {
    List<PerformanceCycle> findAllByCompanyIdAndDeletedFalseOrderByPeriodEndDesc(Long companyId);
    Optional<PerformanceCycle> findByIdAndCompanyIdAndDeletedFalse(Long id, Long companyId);
}
