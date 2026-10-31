package com.haodaone.esign;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmployeeSignatureRepository extends JpaRepository<EmployeeSignature, Long> {
    Optional<EmployeeSignature> findFirstByCompanyIdAndEmployeeIdAndDeletedFalseOrderByIdDesc(Long companyId, Long employeeId);
}
