package com.haodaone.timesheet;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectInvoiceRepository extends JpaRepository<ProjectInvoice, Long> {
    List<ProjectInvoice> findAllByCompanyIdAndDeletedFalseOrderByCreatedAtDesc(Long companyId);
    Optional<ProjectInvoice> findByIdAndCompanyIdAndDeletedFalse(Long id, Long companyId);
    long countByCompanyId(Long companyId);
}
