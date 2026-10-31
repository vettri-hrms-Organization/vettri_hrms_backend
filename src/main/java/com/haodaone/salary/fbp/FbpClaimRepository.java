package com.haodaone.salary.fbp;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FbpClaimRepository extends JpaRepository<FbpClaim, Long> {
    List<FbpClaim> findAllByDeclarationIdAndDeletedFalseOrderByBillDateDesc(Long declarationId);
    List<FbpClaim> findAllByCompanyIdAndStatusAndDeletedFalseOrderByCreatedAtAsc(Long companyId, String status);
    List<FbpClaim> findAllByCompanyIdAndEmployeeIdAndStatusAndSettledFalseAndDeletedFalse(Long companyId, Long employeeId, String status);
    Optional<FbpClaim> findByIdAndCompanyIdAndDeletedFalse(Long id, Long companyId);
}
