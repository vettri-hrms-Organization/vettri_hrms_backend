package com.haodaone.document.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.employee.entity.Employee;
import com.haodaone.company.entity.Company;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "employee_document")
public class EmployeeDocument extends BaseEntity {

    public static final String STATUS_PENDING_REVIEW = "PENDING_REVIEW";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    /** ID_PROOF, PASSPORT, WORK_VISA, PROFESSIONAL_CERTIFICATION, EMPLOYMENT_CONTRACT, OTHER - see DocumentType on the frontend for the full list shown in the picker. */
    @Column(name = "document_type", nullable = false, length = 40)
    private String documentType;

    @Column(name = "document_number", length = 100)
    private String documentNumber;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "expiry_date", nullable = false)
    private LocalDate expiryDate;

    @Column(nullable = false, length = 30)
    private String status = STATUS_PENDING_REVIEW;

    @Column(name = "s3_object_key", length = 500)
    private String s3ObjectKey;

    @Column(name = "original_file_name", length = 255)
    private String originalFileName;

    @Column(name = "content_type", length = 120)
    private String contentType;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_employee_id")
    private Employee reviewedByEmployee;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    @Column(length = 500)
    private String notes;

    public Employee getEmployee() {
        return employee;
    }

    public void setEmployee(Employee employee) {
        this.employee = employee;
    }

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }

    public String getDocumentType() {
        return documentType;
    }

    public void setDocumentType(String documentType) {
        this.documentType = documentType;
    }

    public String getDocumentNumber() {
        return documentNumber;
    }

    public void setDocumentNumber(String documentNumber) {
        this.documentNumber = documentNumber;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public void setIssueDate(LocalDate issueDate) {
        this.issueDate = issueDate;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getS3ObjectKey() {
        return s3ObjectKey;
    }

    public void setS3ObjectKey(String s3ObjectKey) {
        this.s3ObjectKey = s3ObjectKey;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(Long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public void setUploadedAt(LocalDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public Employee getReviewedByEmployee() {
        return reviewedByEmployee;
    }

    public void setReviewedByEmployee(Employee reviewedByEmployee) {
        this.reviewedByEmployee = reviewedByEmployee;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
