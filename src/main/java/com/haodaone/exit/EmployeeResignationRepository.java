package com.haodaone.exit;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeeResignationRepository extends JpaRepository<EmployeeResignation, Long> {
    List<EmployeeResignation> findAllByEmployeeIdAndDeletedFalseOrderByCreatedAtDesc(Long employeeId);
    List<EmployeeResignation> findAllByCompanyIdAndDeletedFalseOrderByCreatedAtDesc(Long companyId);
    List<EmployeeResignation> findAllByCompanyIdAndStatusAndDeletedFalseOrderByCreatedAtAsc(Long companyId, String status);
    Optional<EmployeeResignation> findByIdAndCompanyIdAndDeletedFalse(Long id, Long companyId);
}
