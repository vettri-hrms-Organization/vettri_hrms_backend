package com.haodaone.recruitment.repository;

import com.haodaone.recruitment.entity.Candidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CandidateRepository extends JpaRepository<Candidate, Long> {
    List<Candidate> findAllByJobOpening_Company_IdAndJobOpeningIdAndDeletedFalseOrderByAppliedDateDesc(Long companyId, Long jobOpeningId);
    List<Candidate> findAllByJobOpening_Company_IdAndDeletedFalseOrderByAppliedDateDesc(Long companyId);
    java.util.Optional<Candidate> findByIdAndJobOpening_Company_IdAndDeletedFalse(Long id, Long companyId);
    long countByJobOpeningIdAndDeletedFalse(Long jobOpeningId);
    long countByJobOpeningIdAndStageAndDeletedFalse(Long jobOpeningId, String stage);
    long countByJobOpening_Company_IdAndDeletedFalse(Long companyId);
    long countByJobOpening_Company_IdAndStageAndDeletedFalse(Long companyId, String stage);

    /** Candidates awaiting first review, scoped to a specific recruiter's own requisitions - see JobOpening.recruiter (V8 migration). */
    List<Candidate> findAllByJobOpening_Recruiter_IdAndStageAndDeletedFalseOrderByAppliedDateAsc(Long recruiterId, String stage);

    @Query("select c from Candidate c where c.stage = 'HIRED' and c.deleted = false and year(c.updatedAt) = :year and c.jobOpening.company.id = :companyId")
    List<Candidate> findHiredInYearForCompany(@Param("companyId") Long companyId, @Param("year") int year);
}
