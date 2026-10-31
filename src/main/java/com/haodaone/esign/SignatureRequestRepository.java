package com.haodaone.esign;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SignatureRequestRepository extends JpaRepository<SignatureRequest, Long> {
    List<SignatureRequest> findAllByCompanyIdAndDeletedFalseOrderByCreatedAtDesc(Long companyId);
    List<SignatureRequest> findAllByCompanyIdAndEmployeeIdAndDeletedFalseOrderByCreatedAtDesc(Long companyId, Long employeeId);
    Optional<SignatureRequest> findByIdAndCompanyIdAndDeletedFalse(Long id, Long companyId);
}
