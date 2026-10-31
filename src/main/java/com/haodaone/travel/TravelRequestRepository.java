package com.haodaone.travel;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TravelRequestRepository extends JpaRepository<TravelRequest, Long> {
    List<TravelRequest> findAllByCompanyIdAndDeletedFalseOrderByDepartDateDesc(Long companyId);
    List<TravelRequest> findAllByCompanyIdAndEmployeeIdAndDeletedFalseOrderByDepartDateDesc(Long companyId, Long employeeId);
    Optional<TravelRequest> findByIdAndCompanyIdAndDeletedFalse(Long id, Long companyId);
    List<TravelRequest> findAllByCompanyIdAndEmployeeIdAndAdvanceStatusAndDeletedFalse(Long companyId, Long employeeId, String advanceStatus);
}
