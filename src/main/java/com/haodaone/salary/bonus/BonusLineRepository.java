package com.haodaone.salary.bonus;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BonusLineRepository extends JpaRepository<BonusLine, Long> {
    List<BonusLine> findAllByBatchIdAndDeletedFalseOrderByIdAsc(Long batchId);
    List<BonusLine> findAllByCompanyIdAndEmployeeIdAndSettledFalseAndDeletedFalse(Long companyId, Long employeeId);
    Optional<BonusLine> findByIdAndBatchIdAndDeletedFalse(Long id, Long batchId);
}
