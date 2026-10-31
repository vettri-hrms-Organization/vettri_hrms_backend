package com.haodaone.salary.bonus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BonusBatchRepository extends JpaRepository<BonusBatch, Long> {
    List<BonusBatch> findAllByCompanyIdAndDeletedFalseOrderByCreatedAtDesc(Long companyId);
    Optional<BonusBatch> findByIdAndCompanyIdAndDeletedFalse(Long id, Long companyId);
}
