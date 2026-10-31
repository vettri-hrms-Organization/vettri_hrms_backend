package com.haodaone.salary.revision;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SalaryRevisionRepository extends JpaRepository<SalaryRevision, Long> {
    List<SalaryRevision> findAllByCompanyIdAndDeletedFalseOrderByCreatedAtDesc(Long companyId);
    List<SalaryRevision> findAllByEmployeeIdAndDeletedFalseOrderByEffectiveFromDesc(Long employeeId);
    List<SalaryRevision> findAllByStatusAndEffectiveFromLessThanEqualAndDeletedFalse(String status, LocalDate date);
    List<SalaryRevision> findAllByCompanyIdAndEmployeeIdAndArrearsStatusAndDeletedFalse(Long companyId, Long employeeId, String arrearsStatus);
    Optional<SalaryRevision> findByIdAndCompanyIdAndDeletedFalse(Long id, Long companyId);
}
