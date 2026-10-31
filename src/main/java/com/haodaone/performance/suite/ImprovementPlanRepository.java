package com.haodaone.performance.suite;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ImprovementPlanRepository extends JpaRepository<ImprovementPlan, Long> {
    List<ImprovementPlan> findAllByCompanyIdAndDeletedFalseOrderByStartDateDesc(Long companyId);
    List<ImprovementPlan> findAllByCompanyIdAndEmployeeIdAndDeletedFalseOrderByStartDateDesc(Long companyId, Long employeeId);
    Optional<ImprovementPlan> findByIdAndCompanyIdAndDeletedFalse(Long id, Long companyId);
}
