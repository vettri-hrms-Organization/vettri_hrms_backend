package com.haodaone.salary.repository;

import com.haodaone.salary.entity.EmployeeTaxDeclaration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeeTaxDeclarationRepository extends JpaRepository<EmployeeTaxDeclaration, Long> {

    Optional<EmployeeTaxDeclaration> findByCompany_IdAndEmployee_IdAndFinancialYearAndDeletedFalse(
            Long companyId, Long employeeId, int financialYear);

    List<EmployeeTaxDeclaration> findAllByCompany_IdAndFinancialYearAndDeletedFalse(Long companyId, int financialYear);
}
