package com.haodaone.profile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProfileChangeRequestRepository extends JpaRepository<ProfileChangeRequest, Long> {
    List<ProfileChangeRequest> findAllByEmployeeIdAndDeletedFalseOrderByCreatedAtDesc(Long employeeId);
    List<ProfileChangeRequest> findAllByCompanyIdAndStatusAndDeletedFalseOrderByCreatedAtAsc(Long companyId, String status);
    Optional<ProfileChangeRequest> findByIdAndCompanyIdAndDeletedFalse(Long id, Long companyId);
    boolean existsByEmployeeIdAndSectionAndStatusAndDeletedFalse(Long employeeId, String section, String status);
}
