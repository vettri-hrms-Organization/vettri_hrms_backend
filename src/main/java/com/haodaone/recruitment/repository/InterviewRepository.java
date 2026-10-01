package com.haodaone.recruitment.repository;

import com.haodaone.recruitment.entity.Interview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InterviewRepository extends JpaRepository<Interview, Long> {
    List<Interview> findAllByCandidateIdAndDeletedFalseOrderByScheduledAtDesc(Long candidateId);
    List<Interview> findAllByCandidate_IdAndCandidate_JobOpening_Company_IdAndDeletedFalseOrderByScheduledAtDesc(Long candidateId, Long companyId);
    List<Interview> findAllByStatusOrderByScheduledAtAsc(String status);
    List<Interview> findAllByStatusAndCandidate_JobOpening_Company_IdAndDeletedFalseOrderByScheduledAtAsc(String status, Long companyId);
    java.util.Optional<Interview> findByIdAndCandidate_JobOpening_Company_IdAndDeletedFalse(Long id, Long companyId);

    /** "My Interviews" for the currently logged-in manager - resolved via their linked Employee record. */
    List<Interview> findAllByInterviewer_IdAndDeletedFalseOrderByScheduledAtDesc(Long interviewerEmployeeId);
    List<Interview> findAllByInterviewer_IdAndCandidate_JobOpening_Company_IdAndDeletedFalseOrderByScheduledAtDesc(Long interviewerEmployeeId, Long companyId);

    /** Scheduled interviews across a specific recruiter's own requisitions - see JobOpening.recruiter (V8 migration). */
    List<Interview> findAllByCandidate_JobOpening_Recruiter_IdAndStatusOrderByScheduledAtAsc(Long recruiterId, String status);
}
