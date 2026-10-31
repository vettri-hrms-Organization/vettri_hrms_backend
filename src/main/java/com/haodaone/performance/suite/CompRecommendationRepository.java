package com.haodaone.performance.suite;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompRecommendationRepository extends JpaRepository<CompRecommendation, Long> {
    List<CompRecommendation> findAllByCompCycleIdAndDeletedFalseOrderByIdAsc(Long compCycleId);
    Optional<CompRecommendation> findByIdAndCompCycleIdAndDeletedFalse(Long id, Long compCycleId);
}
