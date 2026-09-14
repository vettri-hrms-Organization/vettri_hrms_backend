package com.haodaone.dashboard.dto;

import com.haodaone.recruitment.dto.CandidateDTO;
import com.haodaone.recruitment.dto.InterviewDTO;
import com.haodaone.recruitment.dto.JobOpeningDTO;

import java.util.List;

/**
 * Backs the Recruiter persona's dashboard - their own open requisitions
 * (JobOpening.recruiter, see V8 migration) plus, scoped to just those
 * requisitions: candidates still awaiting a first review (stage=APPLIED)
 * and interviews still scheduled. See DashboardController#myRecruitment
 * for how the scoping is resolved.
 */
public class RecruiterDashboardDTO {
    private final List<JobOpeningDTO> myOpenRequisitions;
    private final List<CandidateDTO> candidatesAwaitingReview;
    private final List<InterviewDTO> upcomingInterviews;

    public RecruiterDashboardDTO(List<JobOpeningDTO> myOpenRequisitions, List<CandidateDTO> candidatesAwaitingReview,
                                  List<InterviewDTO> upcomingInterviews) {
        this.myOpenRequisitions = myOpenRequisitions;
        this.candidatesAwaitingReview = candidatesAwaitingReview;
        this.upcomingInterviews = upcomingInterviews;
    }

    public List<JobOpeningDTO> getMyOpenRequisitions() {
        return myOpenRequisitions;
    }

    public List<CandidateDTO> getCandidatesAwaitingReview() {
        return candidatesAwaitingReview;
    }

    public List<InterviewDTO> getUpcomingInterviews() {
        return upcomingInterviews;
    }
}
