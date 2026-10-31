package com.haodaone.performance.suite;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OkrObjectiveRepository extends JpaRepository<OkrObjective, Long> {
    List<OkrObjective> findAllByCompanyIdAndPeriodAndDeletedFalseOrderByLevelAscIdAsc(Long companyId, String period);
    Optional<OkrObjective> findByIdAndCompanyIdAndDeletedFalse(Long id, Long companyId);
}
