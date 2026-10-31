package com.haodaone.salary.fbp;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FbpDeclarationRepository extends JpaRepository<FbpDeclaration, Long> {
    List<FbpDeclaration> findAllByEmployeeIdAndDeletedFalseOrderByFinancialYearDescIdDesc(Long employeeId);
    List<FbpDeclaration> findAllByCompanyIdAndDeletedFalseOrderByCreatedAtDesc(Long companyId);
    List<FbpDeclaration> findAllByCompanyIdAndEmployeeIdAndStatusAndDeletedFalse(Long companyId, Long employeeId, String status);
    Optional<FbpDeclaration> findByIdAndCompanyIdAndDeletedFalse(Long id, Long companyId);
}
