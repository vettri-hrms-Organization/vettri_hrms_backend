package com.haodaone.performance.suite;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReviewResponseRepository extends JpaRepository<ReviewResponse, Long> {
    List<ReviewResponse> findAllByParticipantIdAndDeletedFalseOrderByIdAsc(Long participantId);
    List<ReviewResponse> findAllByCompanyIdAndReviewerEmployeeIdAndDeletedFalseOrderByIdDesc(Long companyId, Long reviewerEmployeeId);
    Optional<ReviewResponse> findByIdAndCompanyIdAndDeletedFalse(Long id, Long companyId);
}
