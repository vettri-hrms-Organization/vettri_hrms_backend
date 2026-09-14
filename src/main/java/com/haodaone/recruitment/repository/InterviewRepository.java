package com.haodaone.recruitment.repository;

import com.haodaone.recruitment.entity.Interview;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InterviewRepository extends JpaRepository<Interview, Long> {
    List<Interview> findAllByCandidateIdAndDeletedFalseOrderByScheduledAtDesc(Long candidateId);
    List<Interview> findAllByStatusOrderByScheduledAtAsc(String status);

    /** "My Interviews" for the currently logged-in manager - resolved via their linked Employee record. */
    List<Interview> findAllByInterviewer_IdAndDeletedFalseOrderByScheduledAtDesc(Long interviewerEmployeeId);

    /** Scheduled interviews across a specific recruiter's own requisitions - see JobOpening.recruiter (V8 migration). */
    List<Interview> findAllByCandidate_JobOpening_Recruiter_IdAndStatusOrderByScheduledAtAsc(Long recruiterId, String status);
}
