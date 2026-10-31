package com.haodaone.performance.suite;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** A self, peer or manager review of a participant (V66). */
@Entity
@Table(name = "review_response")
public class ReviewResponse extends BaseEntity {

    public static final String SELF = "SELF";
    public static final String PEER = "PEER";
    public static final String MANAGER = "MANAGER";
    public static final String PENDING = "PENDING";
    public static final String SUBMITTED = "SUBMITTED";

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "participant_id", nullable = false)
    private Long participantId;

    @Column(name = "reviewer_employee_id", nullable = false)
    private Long reviewerEmployeeId;

    @Column(name = "kind", nullable = false, length = 10)
    private String kind;

    @Column(name = "status", nullable = false, length = 12)
    private String status;

    @Column(name = "rating", precision = 3, scale = 1)
    private BigDecimal rating;

    @Column(name = "strengths", columnDefinition = "TEXT")
    private String strengths;

    @Column(name = "improvements", columnDefinition = "TEXT")
    private String improvements;

    @Column(name = "comments", columnDefinition = "TEXT")
    private String comments;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getParticipantId() { return participantId; }
    public void setParticipantId(Long participantId) { this.participantId = participantId; }
    public Long getReviewerEmployeeId() { return reviewerEmployeeId; }
    public void setReviewerEmployeeId(Long reviewerEmployeeId) { this.reviewerEmployeeId = reviewerEmployeeId; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getRating() { return rating; }
    public void setRating(BigDecimal rating) { this.rating = rating; }
    public String getStrengths() { return strengths; }
    public void setStrengths(String strengths) { this.strengths = strengths; }
    public String getImprovements() { return improvements; }
    public void setImprovements(String improvements) { this.improvements = improvements; }
    public String getComments() { return comments; }
    public void setComments(String comments) { this.comments = comments; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
}
