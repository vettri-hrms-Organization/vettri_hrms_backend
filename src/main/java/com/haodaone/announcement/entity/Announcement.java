package com.haodaone.announcement.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.company.entity.Company;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Company announcement. Lifecycle: DRAFT -> (SCHEDULED ->) PUBLISHED.
 * "Expired" is derived from expiresAt rather than stored. notifiedAt is
 * set exactly once when the audience notification fan-out is claimed, which
 * keeps publishing idempotent across the API and the scheduler.
 */
@Entity
@Table(name = "announcement")
public class Announcement extends BaseEntity {

    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_SCHEDULED = "SCHEDULED";
    public static final String STATUS_PUBLISHED = "PUBLISHED";

    public static final String AUDIENCE_ALL = "ALL";
    public static final String AUDIENCE_DEPARTMENTS = "DEPARTMENTS";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false, length = 200)
    private String title;

    /** Plain text; line breaks preserved, links rendered client-side. Never HTML. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(nullable = false, length = 20)
    private String category = "GENERAL";

    @Column(name = "audience_type", nullable = false, length = 20)
    private String audienceType = AUDIENCE_ALL;

    /** Comma-separated department ids when audienceType = DEPARTMENTS. */
    @Column(name = "audience_department_ids", length = 1000)
    private String audienceDepartmentIds;

    @Column(nullable = false, length = 20)
    private String status = STATUS_DRAFT;

    @Column(name = "publish_at")
    private LocalDateTime publishAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean pinned;

    @Column(name = "requires_acknowledgement", nullable = false)
    private boolean requiresAcknowledgement;

    @Column(name = "acknowledgement_due_date")
    private LocalDate acknowledgementDueDate;

    @Column(name = "attachment_s3_key", length = 500)
    private String attachmentS3Key;

    @Column(name = "attachment_file_name", length = 255)
    private String attachmentFileName;

    @Column(name = "attachment_content_type", length = 120)
    private String attachmentContentType;

    @Column(name = "attachment_size_bytes")
    private Long attachmentSizeBytes;

    @Column(name = "author_name", length = 150)
    private String authorName;

    @Column(name = "notified_at")
    private LocalDateTime notifiedAt;

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getAudienceType() { return audienceType; }
    public void setAudienceType(String audienceType) { this.audienceType = audienceType; }
    public String getAudienceDepartmentIds() { return audienceDepartmentIds; }
    public void setAudienceDepartmentIds(String audienceDepartmentIds) { this.audienceDepartmentIds = audienceDepartmentIds; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getPublishAt() { return publishAt; }
    public void setPublishAt(LocalDateTime publishAt) { this.publishAt = publishAt; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public boolean isPinned() { return pinned; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }
    public boolean isRequiresAcknowledgement() { return requiresAcknowledgement; }
    public void setRequiresAcknowledgement(boolean requiresAcknowledgement) { this.requiresAcknowledgement = requiresAcknowledgement; }
    public LocalDate getAcknowledgementDueDate() { return acknowledgementDueDate; }
    public void setAcknowledgementDueDate(LocalDate acknowledgementDueDate) { this.acknowledgementDueDate = acknowledgementDueDate; }
    public String getAttachmentS3Key() { return attachmentS3Key; }
    public void setAttachmentS3Key(String attachmentS3Key) { this.attachmentS3Key = attachmentS3Key; }
    public String getAttachmentFileName() { return attachmentFileName; }
    public void setAttachmentFileName(String attachmentFileName) { this.attachmentFileName = attachmentFileName; }
    public String getAttachmentContentType() { return attachmentContentType; }
    public void setAttachmentContentType(String attachmentContentType) { this.attachmentContentType = attachmentContentType; }
    public Long getAttachmentSizeBytes() { return attachmentSizeBytes; }
    public void setAttachmentSizeBytes(Long attachmentSizeBytes) { this.attachmentSizeBytes = attachmentSizeBytes; }
    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }
    public LocalDateTime getNotifiedAt() { return notifiedAt; }
    public void setNotifiedAt(LocalDateTime notifiedAt) { this.notifiedAt = notifiedAt; }
}
