package com.haodaone.loan;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface EmployeeLoanInstallmentRepository extends JpaRepository<EmployeeLoanInstallment, Long> {
    List<EmployeeLoanInstallment> findAllByLoanIdAndDeletedFalseOrderBySequenceNoAsc(Long loanId);
    Optional<EmployeeLoanInstallment> findByIdAndCompanyIdAndDeletedFalse(Long id, Long companyId);
}
