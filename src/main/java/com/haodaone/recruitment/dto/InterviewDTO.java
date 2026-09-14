package com.haodaone.recruitment.dto;

import com.haodaone.recruitment.entity.Interview;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public class InterviewDTO {
    private Long id;
    private Long candidateId;
    private String candidateName;
    private Long interviewerId;
    private String interviewerName;
    private LocalDateTime scheduledAt;
    private int roundNumber;
    private String roundType;
    private String mode;
    private String status;
    private Integer rating;
    private String feedback;
    private String meetingLink;
    private Integer technicalRating;
    private Integer communicationRating;
    private String instructions;
    private String decision;
    // Candidate-context fields, populated only for "my interviews" (manager portal) responses,
    // where the caller isn't separately fetching the candidate record - keeps that screen to one call.
    private String candidateEmail;
    private String candidateSkills;
    private Double candidateExperienceYears;
    private boolean candidateHasResume;
    private String jobOpeningTitle;

    public static InterviewDTO from(Interview i) {
        InterviewDTO dto = new InterviewDTO();
        dto.id = i.getId();
        dto.candidateId = i.getCandidate().getId();
        dto.candidateName = i.getCandidate().getFullName();
        dto.scheduledAt = i.getScheduledAt();
        dto.roundNumber = i.getRoundNumber();
        dto.roundType = i.getRoundType();
        dto.mode = i.getMode();
        dto.status = i.getStatus();
        dto.rating = i.getRating();
        dto.feedback = i.getFeedback();
        dto.meetingLink = i.getMeetingLink();
        dto.technicalRating = i.getTechnicalRating();
        dto.communicationRating = i.getCommunicationRating();
        dto.instructions = i.getInstructions();
        dto.decision = i.getDecision();
        if (i.getInterviewer() != null) {
            dto.interviewerId = i.getInterviewer().getId();
            dto.interviewerName = i.getInterviewer().getFullName();
        }
        return dto;
    }

    /** Same as from(), plus the candidate-context fields the Manager Portal's "My Interviews" screen needs without a second fetch. */
    public static InterviewDTO fromWithCandidateContext(Interview i) {
        InterviewDTO dto = from(i);
        dto.candidateEmail = i.getCandidate().getEmail();
        dto.candidateSkills = i.getCandidate().getSkills();
        dto.candidateExperienceYears = i.getCandidate().getExperienceYears();
        dto.candidateHasResume = i.getCandidate().getResumeFileKey() != null || i.getCandidate().getResumeUrl() != null;
        dto.jobOpeningTitle = i.getCandidate().getJobOpening().getTitle();
        return dto;
    }

    public Long getId() {
        return id;
    }

    public Long getCandidateId() {
        return candidateId;
    }

    public String getCandidateName() {
        return candidateName;
    }

    public Long getInterviewerId() {
        return interviewerId;
    }

    public String getInterviewerName() {
        return interviewerName;
    }

    public LocalDateTime getScheduledAt() {
        return scheduledAt;
    }

    public int getRoundNumber() {
        return roundNumber;
    }

    public String getRoundType() {
        return roundType;
    }

    public String getMode() {
        return mode;
    }

    public String getStatus() {
        return status;
    }

    public Integer getRating() {
        return rating;
    }

    public String getFeedback() {
        return feedback;
    }

    public String getMeetingLink() {
        return meetingLink;
    }

    public Integer getTechnicalRating() {
        return technicalRating;
    }

    public Integer getCommunicationRating() {
        return communicationRating;
    }

    public String getInstructions() {
        return instructions;
    }

    public String getDecision() {
        return decision;
    }

    public String getCandidateEmail() {
        return candidateEmail;
    }

    public String getCandidateSkills() {
        return candidateSkills;
    }

    public Double getCandidateExperienceYears() {
        return candidateExperienceYears;
    }

    public boolean isCandidateHasResume() {
        return candidateHasResume;
    }

    public String getJobOpeningTitle() {
        return jobOpeningTitle;
    }

    public static class CreateRequest {
        @NotNull(message = "Candidate is required")
        private Long candidateId;
        private Long interviewerId;

        @NotNull(message = "Scheduled time is required")
        private LocalDateTime scheduledAt;

        @Min(value = 1, message = "Round must be 1, 2, or 3")
        @Max(value = 3, message = "Round must be 1, 2, or 3")
        private int roundNumber = 1;

        private String mode = "VIDEO";

        public Long getCandidateId() {
            return candidateId;
        }

        public void setCandidateId(Long candidateId) {
            this.candidateId = candidateId;
        }

        public Long getInterviewerId() {
            return interviewerId;
        }

        public void setInterviewerId(Long interviewerId) {
            this.interviewerId = interviewerId;
        }

        public LocalDateTime getScheduledAt() {
            return scheduledAt;
        }

        public void setScheduledAt(LocalDateTime scheduledAt) {
            this.scheduledAt = scheduledAt;
        }

        public int getRoundNumber() {
            return roundNumber;
        }

        public void setRoundNumber(int roundNumber) {
            this.roundNumber = roundNumber;
        }

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }
    }

    public static class FeedbackRequest {
        @Min(1)
        @Max(5)
        private Integer rating;
        private String feedback;

        public Integer getRating() {
            return rating;
        }

        public void setRating(Integer rating) {
            this.rating = rating;
        }

        public String getFeedback() {
            return feedback;
        }

        public void setFeedback(String feedback) {
            this.feedback = feedback;
        }
    }

    /**
     * Submitted by the interviewer (manager for round 2, final interviewer
     * for round 3) after conducting their round. Which decision values are
     * valid depends on the round - see InterviewService.submitDecision.
     */
    public static class DecisionRequest {
        @Min(1)
        @Max(5)
        private Integer technicalRating;

        @Min(1)
        @Max(5)
        private Integer communicationRating;

        @NotNull(message = "Overall rating is required")
        @Min(1)
        @Max(5)
        private Integer overallRating;

        private String remarks;

        @NotNull(message = "Decision is required")
        private String decision;

        public Integer getTechnicalRating() {
            return technicalRating;
        }

        public void setTechnicalRating(Integer technicalRating) {
            this.technicalRating = technicalRating;
        }

        public Integer getCommunicationRating() {
            return communicationRating;
        }

        public void setCommunicationRating(Integer communicationRating) {
            this.communicationRating = communicationRating;
        }

        public Integer getOverallRating() {
            return overallRating;
        }

        public void setOverallRating(Integer overallRating) {
            this.overallRating = overallRating;
        }

        public String getRemarks() {
            return remarks;
        }

        public void setRemarks(String remarks) {
            this.remarks = remarks;
        }

        public String getDecision() {
            return decision;
        }

        public void setDecision(String decision) {
            this.decision = decision;
        }
    }

    /** Both default true - the frontend's single "Resend Invite" button posts an empty body to resend to both. */
    public static class ResendInviteRequest {
        private boolean toManager = true;
        private boolean toCandidate = true;

        public boolean isToManager() {
            return toManager;
        }

        public void setToManager(boolean toManager) {
            this.toManager = toManager;
        }

        public boolean isToCandidate() {
            return toCandidate;
        }

        public void setToCandidate(boolean toCandidate) {
            this.toCandidate = toCandidate;
        }
    }
}
