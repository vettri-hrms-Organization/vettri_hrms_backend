package com.haodaone.document.dto;

import com.haodaone.document.entity.EmployeeDocument;
import com.haodaone.employee.entity.Employee;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class EmployeeDocumentDTO {
    private Long id;
    private Long employeeId;
    private String employeeName;
    private String documentType;
    private String documentNumber;
    private LocalDate issueDate;
    private LocalDate expiryDate;
    private String status;
    private String s3ObjectKey;
    private String originalFileName;
    private String contentType;
    private Long fileSizeBytes;
    private LocalDateTime uploadedAt;
    private LocalDateTime reviewedAt;
    private String rejectionReason;
    private String notes;

    public static EmployeeDocumentDTO from(EmployeeDocument d) {
        return from(d, d.getEmployee());
    }

    public static EmployeeDocumentDTO from(EmployeeDocument d, Employee employee) {
        EmployeeDocumentDTO dto = new EmployeeDocumentDTO();
        dto.id = d.getId();
        dto.employeeId = employee.getId();
        dto.employeeName = employee.getFullName();
        dto.documentType = d.getDocumentType();
        dto.documentNumber = d.getDocumentNumber();
        dto.issueDate = d.getIssueDate();
        dto.expiryDate = d.getExpiryDate();
        dto.status = d.getStatus();
        dto.s3ObjectKey = d.getS3ObjectKey();
        dto.originalFileName = d.getOriginalFileName();
        dto.contentType = d.getContentType();
        dto.fileSizeBytes = d.getFileSizeBytes();
        dto.uploadedAt = d.getUploadedAt();
        dto.reviewedAt = d.getReviewedAt();
        dto.rejectionReason = d.getRejectionReason();
        dto.notes = d.getNotes();
        return dto;
    }

    public Long getId() {
        return id;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public String getDocumentType() {
        return documentType;
    }

    public String getDocumentNumber() {
        return documentNumber;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public String getStatus() {
        return status;
    }

    public String getS3ObjectKey() {
        return s3ObjectKey;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public String getContentType() {
        return contentType;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public String getNotes() {
        return notes;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public void setS3ObjectKey(String s3ObjectKey) {
        this.s3ObjectKey = s3ObjectKey;
    }

    public void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public void setFileSizeBytes(Long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public void setUploadedAt(LocalDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public static class CreateRequest {
        @NotNull(message = "Employee is required")
        private Long employeeId;

        @NotBlank(message = "Document type is required")
        private String documentType;

        private String documentNumber;
        private LocalDate issueDate;

        private LocalDate expiryDate;

        private String notes;

        public Long getEmployeeId() {
            return employeeId;
        }

        public void setEmployeeId(Long employeeId) {
            this.employeeId = employeeId;
        }

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

        public String getNotes() {
            return notes;
        }

        public void setNotes(String notes) {
            this.notes = notes;
        }
    }

    public static class ReviewRequest {
        @NotNull(message = "Approval decision is required")
        private Boolean approved;

        private String rejectionReason;

        public Boolean getApproved() {
            return approved;
        }

        public void setApproved(Boolean approved) {
            this.approved = approved;
        }

        public String getRejectionReason() {
            return rejectionReason;
        }

        public void setRejectionReason(String rejectionReason) {
            this.rejectionReason = rejectionReason;
        }
    }

    public static class UploadRequest {
        @NotNull(message = "Employee is required")
        private Long employeeId;

        @NotBlank(message = "Document type is required")
        private String documentType;

        private String documentNumber;
        private LocalDate issueDate;

        private LocalDate expiryDate;

        private String notes;
        private MultipartFile file;

        public Long getEmployeeId() {
            return employeeId;
        }

        public void setEmployeeId(Long employeeId) {
            this.employeeId = employeeId;
        }

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

        public String getNotes() {
            return notes;
        }

        public void setNotes(String notes) {
            this.notes = notes;
        }

        public MultipartFile getFile() {
            return file;
        }

        public void setFile(MultipartFile file) {
            this.file = file;
        }
    }
}
