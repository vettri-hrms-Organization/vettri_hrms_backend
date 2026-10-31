package com.haodaone.esign;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** A click-to-sign request on an issued letter (V67). Keeps the SHA-256 of the document signed, signer name, time, IP and browser, and the stamped copy. */
@Entity
@Table(name = "signature_request")
public class SignatureRequest extends BaseEntity {

    public static final String PENDING = "PENDING";
    public static final String SIGNED = "SIGNED";
    public static final String DECLINED = "DECLINED";
    public static final String CANCELLED = "CANCELLED";

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "source_type", nullable = false, length = 20)
    private String sourceType;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "document_sha256", nullable = false, length = 64)
    private String documentSha256;

    @Column(name = "status", nullable = false, length = 12)
    private String status;

    @Column(name = "message", length = 1000)
    private String message;

    @Column(name = "requested_by_name", length = 150)
    private String requestedByName;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "viewed_at")
    private LocalDateTime viewedAt;

    @Column(name = "signed_name", length = 150)
    private String signedName;

    @Column(name = "signed_at")
    private LocalDateTime signedAt;

    @Column(name = "signer_ip", length = 64)
    private String signerIp;

    @Column(name = "signer_user_agent", length = 400)
    private String signerUserAgent;

    @Column(name = "signed_object_key", length = 500)
    private String signedObjectKey;

    @Column(name = "signed_sha256", length = 64)
    private String signedSha256;

    @Column(name = "decline_reason", length = 500)
    private String declineReason;

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public Long getSourceId() { return sourceId; }
    public void setSourceId(Long sourceId) { this.sourceId = sourceId; }
    public String getDocumentSha256() { return documentSha256; }
    public void setDocumentSha256(String documentSha256) { this.documentSha256 = documentSha256; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getRequestedByName() { return requestedByName; }
    public void setRequestedByName(String requestedByName) { this.requestedByName = requestedByName; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public LocalDateTime getViewedAt() { return viewedAt; }
    public void setViewedAt(LocalDateTime viewedAt) { this.viewedAt = viewedAt; }
    public String getSignedName() { return signedName; }
    public void setSignedName(String signedName) { this.signedName = signedName; }
    public LocalDateTime getSignedAt() { return signedAt; }
    public void setSignedAt(LocalDateTime signedAt) { this.signedAt = signedAt; }
    public String getSignerIp() { return signerIp; }
    public void setSignerIp(String signerIp) { this.signerIp = signerIp; }
    public String getSignerUserAgent() { return signerUserAgent; }
    public void setSignerUserAgent(String signerUserAgent) { this.signerUserAgent = signerUserAgent; }
    public String getSignedObjectKey() { return signedObjectKey; }
    public void setSignedObjectKey(String signedObjectKey) { this.signedObjectKey = signedObjectKey; }
    public String getSignedSha256() { return signedSha256; }
    public void setSignedSha256(String signedSha256) { this.signedSha256 = signedSha256; }
    public String getDeclineReason() { return declineReason; }
    public void setDeclineReason(String declineReason) { this.declineReason = declineReason; }
}
