package com.haodaone.performance.suite;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OkrKeyResultRepository extends JpaRepository<OkrKeyResult, Long> {
    List<OkrKeyResult> findAllByObjectiveIdAndDeletedFalseOrderByIdAsc(Long objectiveId);
    Optional<OkrKeyResult> findByIdAndCompanyIdAndDeletedFalse(Long id, Long companyId);
}
