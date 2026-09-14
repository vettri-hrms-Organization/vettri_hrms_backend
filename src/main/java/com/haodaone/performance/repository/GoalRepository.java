package com.haodaone.performance.repository;

import com.haodaone.performance.entity.Goal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GoalRepository extends JpaRepository<Goal, Long> {
    List<Goal> findAllByCompany_IdAndEmployeeIdAndDeletedFalseOrderByTargetDateAsc(Long companyId, Long employeeId);
    java.util.Optional<Goal> findByIdAndCompany_IdAndDeletedFalse(Long id, Long companyId);
    List<Goal> findAllByEmployeeIdAndDeletedFalseOrderByTargetDateAsc(Long employeeId);
}
