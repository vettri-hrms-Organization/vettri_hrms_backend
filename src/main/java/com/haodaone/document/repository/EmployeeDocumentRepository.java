package com.haodaone.document.repository;

import com.haodaone.document.entity.EmployeeDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface EmployeeDocumentRepository extends JpaRepository<EmployeeDocument, Long> {
    List<EmployeeDocument> findAllByCompany_IdAndEmployeeIdAndDeletedFalseOrderByExpiryDateAsc(Long companyId, Long employeeId);

    List<EmployeeDocument> findAllByEmployeeIdAndDeletedFalseOrderByExpiryDateAsc(Long employeeId);

    /** Powers the Dashboard "expiring soon" widget and the Settings-wide expiry list - deliberately unpaginated like Goal/PerformanceReview's per-employee queries, since org-wide expiries within a lookahead window is a naturally bounded list. */
    List<EmployeeDocument> findAllByDeletedFalseAndExpiryDateBetweenOrderByExpiryDateAsc(LocalDate start, LocalDate end);
    List<EmployeeDocument> findAllByCompany_IdAndDeletedFalseAndExpiryDateBetweenOrderByExpiryDateAsc(Long companyId, LocalDate start, LocalDate end);
    java.util.Optional<EmployeeDocument> findByIdAndCompany_IdAndDeletedFalse(Long id, Long companyId);
}
