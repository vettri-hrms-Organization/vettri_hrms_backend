package com.haodaone.performance.suite;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OneOnOneRepository extends JpaRepository<OneOnOne, Long> {
    List<OneOnOne> findAllByCompanyIdAndManagerEmployeeIdAndDeletedFalseOrderByScheduledAtDesc(Long companyId, Long managerEmployeeId);
    List<OneOnOne> findAllByCompanyIdAndEmployeeIdAndDeletedFalseOrderByScheduledAtDesc(Long companyId, Long employeeId);
    Optional<OneOnOne> findByIdAndCompanyIdAndDeletedFalse(Long id, Long companyId);
}
