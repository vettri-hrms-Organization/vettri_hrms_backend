package com.haodaone.reports.builder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SavedReportRepository extends JpaRepository<SavedReport, Long> {
    List<SavedReport> findAllByCompanyIdAndDeletedFalseOrderByNameAsc(Long companyId);
    Optional<SavedReport> findByIdAndCompanyIdAndDeletedFalse(Long id, Long companyId);
}
