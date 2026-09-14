package com.haodaone.performance.repository;

import com.haodaone.performance.entity.PerformanceReview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PerformanceReviewRepository extends JpaRepository<PerformanceReview, Long> {
    List<PerformanceReview> findAllByCompany_IdAndEmployeeIdAndDeletedFalseOrderByCreatedAtDesc(Long companyId, Long employeeId);
    List<PerformanceReview> findAllByCompany_IdAndDeletedFalseOrderByCreatedAtDesc(Long companyId);
    java.util.Optional<PerformanceReview> findByIdAndCompany_IdAndDeletedFalse(Long id, Long companyId);
    List<PerformanceReview> findAllByEmployeeIdAndDeletedFalseOrderByCreatedAtDesc(Long employeeId);
    List<PerformanceReview> findAllByDeletedFalseOrderByCreatedAtDesc();
}
