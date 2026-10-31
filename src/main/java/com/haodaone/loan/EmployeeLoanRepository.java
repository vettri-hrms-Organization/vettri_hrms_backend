package com.haodaone.loan;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EmployeeLoanRepository extends JpaRepository<EmployeeLoan, Long> {
    List<EmployeeLoan> findAllByEmployeeIdAndDeletedFalseOrderByCreatedAtDesc(Long employeeId);
    List<EmployeeLoan> findAllByCompanyIdAndDeletedFalseOrderByCreatedAtDesc(Long companyId);
    List<EmployeeLoan> findAllByCompanyIdAndEmployeeIdAndStatusInAndDeletedFalse(Long companyId, Long employeeId, Collection<String> statuses);
    Optional<EmployeeLoan> findByIdAndCompanyIdAndDeletedFalse(Long id, Long companyId);
}
