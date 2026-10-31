package com.haodaone.probation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EmployeeProbationRepository extends JpaRepository<EmployeeProbation, Long> {
    List<EmployeeProbation> findAllByCompanyIdAndDeletedFalseOrderByProbationEndAsc(Long companyId);
    Optional<EmployeeProbation> findFirstByEmployeeIdAndDeletedFalse(Long employeeId);
    Optional<EmployeeProbation> findByIdAndCompanyIdAndDeletedFalse(Long id, Long companyId);
    List<EmployeeProbation> findAllByStatusAndProbationEndLessThanEqualAndDeletedFalse(String status, LocalDate date);
}
