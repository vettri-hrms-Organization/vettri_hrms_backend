package com.haodaone.recruitment.dto;

import com.haodaone.recruitment.entity.Candidate;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class CandidateDTO {
    private Long id;
    private String firstName;
    private String lastName;
    private String fullName;
    private String email;
    private String phone;
    private Long jobOpeningId;
    private String jobOpeningTitle;
    private String source;
    private String resumeUrl;
    private boolean hasResume;
    private String resumeOriginalName;
    private Double experienceYears;
    private String skills;
    private String stage;
    private LocalDate appliedDate;
    private Integer rating;
    private String remarks;
    private String rejectionReason;
    private Double offerAmount;
    private LocalDate expectedJoiningDate;
    private LocalDateTime offerGeneratedAt;
    private boolean hasOfferLetter;
    private String offerLetterOriginalName;
    private LocalDateTime offerLetterUploadedAt;
    private String offerLetterUploadedBy;
    private LocalDateTime offerLetterSentAt;
    private String offerLetterEmailStatus;
    private LocalDateTime offerAcceptedAt;
    private Long createdEmployeeId;
    private String notes;

    public static CandidateDTO from(Candidate c) {
        CandidateDTO dto = new CandidateDTO();
        dto.id = c.getId();
        dto.firstName = c.getFirstName();
        dto.lastName = c.getLastName();
        dto.fullName = c.getFullName();
        dto.email = c.getEmail();
        dto.phone = c.getPhone();
        dto.jobOpeningId = c.getJobOpening().getId();
        dto.jobOpeningTitle = c.getJobOpening().getTitle();
        dto.source = c.getSource();
        dto.resumeUrl = c.getResumeUrl();
        dto.hasResume = c.getResumeFileKey() != null && !c.getResumeFileKey().isBlank();
        dto.resumeOriginalName = c.getResumeOriginalName();
        dto.experienceYears = c.getExperienceYears();
        dto.skills = c.getSkills();
        dto.stage = c.getStage();
        dto.appliedDate = c.getAppliedDate();
        dto.rating = c.getRating();
        dto.remarks = c.getRemarks();
        dto.rejectionReason = c.getRejectionReason();
        dto.offerAmount = c.getOfferAmount();
        dto.expectedJoiningDate = c.getExpectedJoiningDate();
        dto.offerGeneratedAt = c.getOfferGeneratedAt();
        dto.hasOfferLetter = c.getOfferLetterFileKey() != null && !c.getOfferLetterFileKey().isBlank();
        dto.offerLetterOriginalName = c.getOfferLetterOriginalName();
        dto.offerLetterUploadedAt = c.getOfferLetterUploadedAt();
        dto.offerLetterUploadedBy = c.getOfferLetterUploadedBy();
        dto.offerLetterSentAt = c.getOfferLetterSentAt();
        dto.offerLetterEmailStatus = c.getOfferLetterEmailStatus();
        dto.offerAcceptedAt = c.getOfferAcceptedAt();
        dto.createdEmployeeId = c.getCreatedEmployeeId();
        dto.notes = c.getNotes();
        return dto;
    }

    public Long getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public Long getJobOpeningId() {
        return jobOpeningId;
    }

    public String getJobOpeningTitle() {
        return jobOpeningTitle;
    }

    public String getSource() {
        return source;
    }

    public String getResumeUrl() {
        return resumeUrl;
    }

    public boolean isHasResume() {
        return hasResume;
    }

    public String getResumeOriginalName() {
        return resumeOriginalName;
    }

    public Double getExperienceYears() {
        return experienceYears;
    }

    public String getSkills() {
        return skills;
    }

    public String getStage() {
        return stage;
    }

    public LocalDate getAppliedDate() {
        return appliedDate;
    }

    public Integer getRating() {
        return rating;
    }

    public String getRemarks() {
        return remarks;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public Double getOfferAmount() {
        return offerAmount;
    }

    public LocalDate getExpectedJoiningDate() {
        return expectedJoiningDate;
    }

    public LocalDateTime getOfferGeneratedAt() {
        return offerGeneratedAt;
    }

    public boolean isHasOfferLetter() {
        return hasOfferLetter;
    }

    public String getOfferLetterOriginalName() {
        return offerLetterOriginalName;
    }

    public LocalDateTime getOfferLetterUploadedAt() {
        return offerLetterUploadedAt;
    }

    public String getOfferLetterUploadedBy() {
        return offerLetterUploadedBy;
    }

    public LocalDateTime getOfferLetterSentAt() {
        return offerLetterSentAt;
    }

    public String getOfferLetterEmailStatus() {
        return offerLetterEmailStatus;
    }

    public LocalDateTime getOfferAcceptedAt() {
        return offerAcceptedAt;
    }

    public Long getCreatedEmployeeId() {
        return createdEmployeeId;
    }

    public String getNotes() {
        return notes;
    }

    /** Manual add by HR (no resume file - use the public apply endpoint for that, or attach a resumeUrl link here). */
    public static class CreateRequest {
        @NotBlank(message = "First name is required")
        private String firstName;

        @NotBlank(message = "Last name is required")
        private String lastName;

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        private String email;

        private String phone;

        @NotNull(message = "Job opening is required")
        private Long jobOpeningId;

        private String source;
        private String resumeUrl;
        private Double experienceYears;
        private String skills;
        private String notes;

        public String getFirstName() {
            return firstName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public void setLastName(String lastName) {
            this.lastName = lastName;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public Long getJobOpeningId() {
            return jobOpeningId;
        }

        public void setJobOpeningId(Long jobOpeningId) {
            this.jobOpeningId = jobOpeningId;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getResumeUrl() {
            return resumeUrl;
        }

        public void setResumeUrl(String resumeUrl) {
            this.resumeUrl = resumeUrl;
        }

        public Double getExperienceYears() {
            return experienceYears;
        }

        public void setExperienceYears(Double experienceYears) {
            this.experienceYears = experienceYears;
        }

        public String getSkills() {
            return skills;
        }

        public void setSkills(String skills) {
            this.skills = skills;
        }

        public String getNotes() {
            return notes;
        }

        public void setNotes(String notes) {
            this.notes = notes;
        }
    }

    /** HR's initial screening decision on an APPLIED candidate. */
    public static class ReviewRequest {
        @NotBlank(message = "Decision is required")
        private String decision; // SHORTLISTED, HOLD, or REJECTED

        @Min(value = 1, message = "Rating must be between 1 and 5")
        @Max(value = 5, message = "Rating must be between 1 and 5")
        private Integer rating;

        private String remarks;

        /** Optional - only meaningful when decision = REJECTED. */
        private String rejectionReason;

        public String getDecision() {
            return decision;
        }

        public void setDecision(String decision) {
            this.decision = decision;
        }

        public Integer getRating() {
            return rating;
        }

        public void setRating(Integer rating) {
            this.rating = rating;
        }

        public String getRemarks() {
            return remarks;
        }

        public void setRemarks(String remarks) {
            this.remarks = remarks;
        }

        public String getRejectionReason() {
            return rejectionReason;
        }

        public void setRejectionReason(String rejectionReason) {
            this.rejectionReason = rejectionReason;
        }
    }

    /** General controlled pipeline transition (advance to next round, put on hold, or reject) once past the initial review. */
    public static class AdvanceStageRequest {
        @NotBlank(message = "Target stage is required")
        private String targetStage; // ROUND1, ROUND2, ROUND3, HOLD, REJECTED

        private String remarks;

        /** Optional - only meaningful when targetStage = REJECTED. */
        private String rejectionReason;

        public String getTargetStage() {
            return targetStage;
        }

        public void setTargetStage(String targetStage) {
            this.targetStage = targetStage;
        }

        public String getRemarks() {
            return remarks;
        }

        public void setRemarks(String remarks) {
            this.remarks = remarks;
        }

        public String getRejectionReason() {
            return rejectionReason;
        }

        public void setRejectionReason(String rejectionReason) {
            this.rejectionReason = rejectionReason;
        }
    }

    public static class OfferRequest {
        @NotNull(message = "Offer amount is required")
        @Positive(message = "Offer amount must be positive")
        private Double offerAmount;

        @NotNull(message = "Expected joining date is required")
        private LocalDate expectedJoiningDate;

        public Double getOfferAmount() {
            return offerAmount;
        }

        public void setOfferAmount(Double offerAmount) {
            this.offerAmount = offerAmount;
        }

        public LocalDate getExpectedJoiningDate() {
            return expectedJoiningDate;
        }

        public void setExpectedJoiningDate(LocalDate expectedJoiningDate) {
            this.expectedJoiningDate = expectedJoiningDate;
        }
    }

    /** Public, unauthenticated application submitted from the Careers page (resume file arrives as a separate multipart part - see PublicCareersController). */
    public static class PublicApplicationRequest {
        @NotBlank(message = "First name is required")
        private String firstName;

        @NotBlank(message = "Last name is required")
        private String lastName;

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        private String email;

        @NotBlank(message = "Mobile number is required")
        private String phone;

        @NotNull(message = "Job opening is required")
        private Long jobOpeningId;

        @DecimalMin(value = "0", message = "Experience can't be negative")
        private Double experienceYears;

        private String skills;
        private String notes;

        public String getFirstName() {
            return firstName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public void setLastName(String lastName) {
            this.lastName = lastName;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public Long getJobOpeningId() {
            return jobOpeningId;
        }

        public void setJobOpeningId(Long jobOpeningId) {
            this.jobOpeningId = jobOpeningId;
        }

        public Double getExperienceYears() {
            return experienceYears;
        }

        public void setExperienceYears(Double experienceYears) {
            this.experienceYears = experienceYears;
        }

        public String getSkills() {
            return skills;
        }

        public void setSkills(String skills) {
            this.skills = skills;
        }

        public String getNotes() {
            return notes;
        }

        public void setNotes(String notes) {
            this.notes = notes;
        }
    }

    /**
     * "Select for Manager Round" from the HR interview decision. Distinct
     * from the generic AdvanceStageRequest because this one carries the
     * scheduling details needed to create the round 2 Interview record and
     * fire the assignment emails in one atomic HR action, rather than HR
     * doing "advance stage" then "schedule interview" as two separate steps.
     */
    public static class AssignManagerRequest {
        @NotNull(message = "Hiring manager is required")
        private Long managerEmployeeId;

        @NotNull(message = "Interview date/time is required")
        private LocalDateTime scheduledAt;

        @NotBlank(message = "Google Meet link is required")
        private String meetingLink;

        private String instructions;

        public Long getManagerEmployeeId() {
            return managerEmployeeId;
        }

        public void setManagerEmployeeId(Long managerEmployeeId) {
            this.managerEmployeeId = managerEmployeeId;
        }

        public LocalDateTime getScheduledAt() {
            return scheduledAt;
        }

        public void setScheduledAt(LocalDateTime scheduledAt) {
            this.scheduledAt = scheduledAt;
        }

        public String getMeetingLink() {
            return meetingLink;
        }

        public void setMeetingLink(String meetingLink) {
            this.meetingLink = meetingLink;
        }

        public String getInstructions() {
            return instructions;
        }

        public void setInstructions(String instructions) {
            this.instructions = instructions;
        }
    }

    public static class UpdateNotesRequest {
        // Deliberately no @NotBlank - clearing the notes back to empty is
        // a valid edit, not something to reject.
        private String notes;

        public String getNotes() {
            return notes;
        }

        public void setNotes(String notes) {
            this.notes = notes;
        }
    }
}
