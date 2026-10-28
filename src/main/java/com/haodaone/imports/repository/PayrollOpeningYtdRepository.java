package com.haodaone.imports.repository;

import com.haodaone.imports.entity.PayrollOpeningYtd;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PayrollOpeningYtdRepository extends JpaRepository<PayrollOpeningYtd, Long> {

    Optional<PayrollOpeningYtd> findFirstByCompany_IdAndEmployee_IdAndFinancialYearAndDeletedFalse(
            Long companyId, Long employeeId, int financialYear);

    @EntityGraph(attributePaths = {"employee"})
    List<PayrollOpeningYtd> findAllByCompany_IdAndFinancialYearAndDeletedFalseOrderByIdAsc(Long companyId, int financialYear);
}
