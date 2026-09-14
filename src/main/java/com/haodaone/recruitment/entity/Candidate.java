package com.haodaone.recruitment.entity;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Offer management is folded into this entity (offerAmount/
 * expectedJoiningDate/offerGeneratedAt/offerAcceptedAt) rather than a
 * separate Offer entity - an offer is terminal state on the candidate's
 * pipeline, not a multi-revision negotiation record. Split it out if/when
 * offer letters, approvals, or revision history are needed.
 *
 * Pipeline: APPLIED -> SHORTLISTED (or REJECTED/HOLD) -> ROUND1 -> ROUND2
 * -> ROUND3 -> OFFERED -> OFFER_LETTER_SENT -> OFFER_ACCEPTED -> HIRED,
 * with REJECTED reachable from any non-terminal stage. See
 * CandidateService.VALID_STAGES / ALLOWED_TRANSITIONS for the enforced
 * graph, and the Interview entity (roundNumber/roundType) for the
 * per-round interviewer/rating/feedback history that goes with
 * ROUND1/ROUND2/ROUND3.
 *
 * OFFERED is reached the moment HR generates the offer (offerAmount/
 * expectedJoiningDate/offerGeneratedAt) - no offer letter document exists
 * yet at that point. HR then uploads the signed offer letter
 * (offerLetterFileKey et al, see CandidateService.uploadOfferLetter) and
 * explicitly sends it (see CandidateService.sendOfferLetter), which is
 * what moves the stage on to OFFER_LETTER_SENT and stamps
 * offerLetterSentAt/offerLetterEmailStatus. Nothing here auto-emails the
 * candidate just because an offer was generated.
 */
@Entity
@Table(name = "candidate")
public class Candidate extends BaseEntity {

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(length = 30)
    private String phone;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_opening_id", nullable = false)
    private JobOpening jobOpening;

    /** e.g. "Referral", "LinkedIn", "Job Board", "Careers Page" - free text by design, not a fixed list. */
    @Column(length = 100)
    private String source;

    /**
     * External resume link, for candidates added manually by HR (e.g. a
     * Drive/LinkedIn link) rather than through the public application
     * form. Mutually optional alongside resumeFileKey below - a given
     * candidate typically has one or the other, not both.
     */
    @Column(name = "resume_url", length = 500)
    private String resumeUrl;

    /** S3 object key for an uploaded resume file, e.g. "resumes/uuid.pdf" (see ResumeS3StorageService) - set when the candidate applied via the public Careers page. */
    @Column(name = "resume_file_key", length = 300)
    private String resumeFileKey;

    /** Original uploaded filename, kept only for display (e.g. "Download resume.pdf") - never used to build the storage path. */
    @Column(name = "resume_original_name", length = 255)
    private String resumeOriginalName;

    /** Years of experience as stated on the application. */
    @Column(name = "experience_years")
    private Double experienceYears;

    /** Free-text skills as stated on the application (comma-separated by convention, not enforced). */
    @Column(length = 500)
    private String skills;

    /** APPLIED, SHORTLISTED, HOLD, ROUND1, ROUND2, ROUND3, OFFERED, OFFER_ACCEPTED, HIRED, REJECTED */
    @Column(nullable = false, length = 20)
    private String stage = "APPLIED";

    @Column(name = "applied_date", nullable = false)
    private LocalDate appliedDate;

    /** 1-5, set during the HR screening review (separate from any individual interview round's rating). */
    private Integer rating;

    /** HR's screening remarks - separate from interview feedback, which lives on each Interview row. */
    @Column(length = 1000)
    private String remarks;

    /** Optional - only meaningful when stage = REJECTED. */
    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(name = "offer_amount")
    private Double offerAmount;

    @Column(name = "expected_joining_date")
    private LocalDate expectedJoiningDate;

    @Column(name = "offer_generated_at")
    private LocalDateTime offerGeneratedAt;

    /** S3 object key for the HR-uploaded offer letter document (see OfferLetterS3StorageService) - never a filesystem path, never a full URL. */
    @Column(name = "offer_letter_file_key", length = 300)
    private String offerLetterFileKey;

    /** Original uploaded filename, kept only for display - never used to build the storage path. */
    @Column(name = "offer_letter_original_name", length = 255)
    private String offerLetterOriginalName;

    @Column(name = "offer_letter_uploaded_at")
    private LocalDateTime offerLetterUploadedAt;

    /** Display name (or username, if no linked Employee) of whoever uploaded the current offer letter file. */
    @Column(name = "offer_letter_uploaded_by", length = 150)
    private String offerLetterUploadedBy;

    /** Set every time "Send Offer Letter" is clicked - most recent send/resend, not just the first. */
    @Column(name = "offer_letter_sent_at")
    private LocalDateTime offerLetterSentAt;

    /** SENT or FAILED - the outcome of the most recent send/resend attempt's email delivery. */
    @Column(name = "offer_letter_email_status", length = 20)
    private String offerLetterEmailStatus;

    @Column(name = "offer_accepted_at")
    private LocalDateTime offerAcceptedAt;

    /** Set once OFFER_ACCEPTED triggers auto-onboarding - the employee.id this candidate became. */
    @Column(name = "created_employee_id")
    private Long createdEmployeeId;

    @Column(length = 1000)
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

    public String getFullName() {
        return firstName + " " + lastName;
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

    public JobOpening getJobOpening() {
        return jobOpening;
    }

    public void setJobOpening(JobOpening jobOpening) {
        this.jobOpening = jobOpening;
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

    public String getResumeFileKey() {
        return resumeFileKey;
    }

    public void setResumeFileKey(String resumeFileKey) {
        this.resumeFileKey = resumeFileKey;
    }

    public String getResumeOriginalName() {
        return resumeOriginalName;
    }

    public void setResumeOriginalName(String resumeOriginalName) {
        this.resumeOriginalName = resumeOriginalName;
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

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public LocalDate getAppliedDate() {
        return appliedDate;
    }

    public void setAppliedDate(LocalDate appliedDate) {
        this.appliedDate = appliedDate;
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

    public LocalDateTime getOfferGeneratedAt() {
        return offerGeneratedAt;
    }

    public void setOfferGeneratedAt(LocalDateTime offerGeneratedAt) {
        this.offerGeneratedAt = offerGeneratedAt;
    }

    public String getOfferLetterFileKey() {
        return offerLetterFileKey;
    }

    public void setOfferLetterFileKey(String offerLetterFileKey) {
        this.offerLetterFileKey = offerLetterFileKey;
    }

    public String getOfferLetterOriginalName() {
        return offerLetterOriginalName;
    }

    public void setOfferLetterOriginalName(String offerLetterOriginalName) {
        this.offerLetterOriginalName = offerLetterOriginalName;
    }

    public LocalDateTime getOfferLetterUploadedAt() {
        return offerLetterUploadedAt;
    }

    public void setOfferLetterUploadedAt(LocalDateTime offerLetterUploadedAt) {
        this.offerLetterUploadedAt = offerLetterUploadedAt;
    }

    public String getOfferLetterUploadedBy() {
        return offerLetterUploadedBy;
    }

    public void setOfferLetterUploadedBy(String offerLetterUploadedBy) {
        this.offerLetterUploadedBy = offerLetterUploadedBy;
    }

    public LocalDateTime getOfferLetterSentAt() {
        return offerLetterSentAt;
    }

    public void setOfferLetterSentAt(LocalDateTime offerLetterSentAt) {
        this.offerLetterSentAt = offerLetterSentAt;
    }

    public String getOfferLetterEmailStatus() {
        return offerLetterEmailStatus;
    }

    public void setOfferLetterEmailStatus(String offerLetterEmailStatus) {
        this.offerLetterEmailStatus = offerLetterEmailStatus;
    }

    public LocalDateTime getOfferAcceptedAt() {
        return offerAcceptedAt;
    }

    public void setOfferAcceptedAt(LocalDateTime offerAcceptedAt) {
        this.offerAcceptedAt = offerAcceptedAt;
    }

    public Long getCreatedEmployeeId() {
        return createdEmployeeId;
    }

    public void setCreatedEmployeeId(Long createdEmployeeId) {
        this.createdEmployeeId = createdEmployeeId;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
