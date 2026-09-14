package com.haodaone.recruitment.repository;

import com.haodaone.recruitment.entity.JobOpening;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobOpeningRepository extends JpaRepository<JobOpening, Long> {
    /** Tenant-scoped: only company job openings. */
    List<JobOpening> findAllByCompany_IdAndDeletedFalseOrderByPostedDateDesc(Long companyId);
    /** For public job board (intentionally global for careers page). Keep for public access. */
    List<JobOpening> findAllByStatusAndDeletedFalseOrderByPostedDateDesc(String status);
    long countByCompany_IdAndStatusAndDeletedFalse(Long companyId, String status);

    /** A recruiter's own open requisitions - powers the Recruiter persona dashboard. */
    List<JobOpening> findAllByRecruiter_IdAndDeletedFalseAndStatusOrderByPostedDateDesc(Long recruiterId, String status);
}
