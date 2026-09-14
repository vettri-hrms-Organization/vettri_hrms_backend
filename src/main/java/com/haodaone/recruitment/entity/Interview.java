package com.haodaone.recruitment.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.employee.entity.Employee;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "interview")
public class Interview extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_id", nullable = false)
    private Candidate candidate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interviewer_employee_id")
    private Employee interviewer;

    /** 1, 2, or 3 - see Candidate's class doc for the ROUND1/ROUND2/ROUND3 stage mapping. */
    @Column(name = "round_number", nullable = false)
    private int roundNumber = 1;

    /** HR_INTERVIEW (round 1), HIRING_MANAGER (round 2), FINAL (round 3, management). */
    @Column(name = "round_type", nullable = false, length = 30)
    private String roundType = "HR_INTERVIEW";

    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    /** ONSITE, VIDEO, PHONE */
    @Column(nullable = false, length = 20)
    private String mode = "VIDEO";

    /** SCHEDULED, COMPLETED, CANCELLED */
    @Column(nullable = false, length = 20)
    private String status = "SCHEDULED";

    /** 1-5. For round 1 (HR_INTERVIEW) this is the only rating recorded. For rounds 2/3, it doubles as "overall rating" alongside technicalRating/communicationRating below. */
    private Integer rating;

    @Column(length = 1000)
    private String feedback;

    /** Google Meet (or other) link for this round - set by HR when assigning the manager round; carried through unchanged for the final round unless re-scheduled. */
    @Column(name = "meeting_link", length = 500)
    private String meetingLink;

    /** 1-5, manager/final rounds only. */
    @Column(name = "technical_rating")
    private Integer technicalRating;

    /** 1-5, manager/final rounds only. */
    @Column(name = "communication_rating")
    private Integer communicationRating;

    /** Freeform note to the interviewer, e.g. "Focus on system design" - shown to them, not the candidate. */
    @Column(length = 1000)
    private String instructions;

    /** This round's outcome: REJECTED, SELECT_FOR_FINAL (round 2 only), APPROVED_FOR_OFFER (round 3 only). Null until the interviewer submits a decision. */
    @Column(length = 30)
    private String decision;

    public Candidate getCandidate() {
        return candidate;
    }

    public void setCandidate(Candidate candidate) {
        this.candidate = candidate;
    }

    public Employee getInterviewer() {
        return interviewer;
    }

    public void setInterviewer(Employee interviewer) {
        this.interviewer = interviewer;
    }

    public int getRoundNumber() {
        return roundNumber;
    }

    public void setRoundNumber(int roundNumber) {
        this.roundNumber = roundNumber;
    }

    public String getRoundType() {
        return roundType;
    }

    public void setRoundType(String roundType) {
        this.roundType = roundType;
    }

    public LocalDateTime getScheduledAt() {
        return scheduledAt;
    }

    public void setScheduledAt(LocalDateTime scheduledAt) {
        this.scheduledAt = scheduledAt;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

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

    public String getMeetingLink() {
        return meetingLink;
    }

    public void setMeetingLink(String meetingLink) {
        this.meetingLink = meetingLink;
    }

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

    public String getInstructions() {
        return instructions;
    }

    public void setInstructions(String instructions) {
        this.instructions = instructions;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }
}
