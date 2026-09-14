package com.haodaone.document.dto;

import com.haodaone.document.entity.EmployeeDocument;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public class EmployeeDocumentDTO {
    private Long id;
    private Long employeeId;
    private String employeeName;
    private String documentType;
    private String documentNumber;
    private LocalDate issueDate;
    private LocalDate expiryDate;
    private String notes;

    public static EmployeeDocumentDTO from(EmployeeDocument d) {
        EmployeeDocumentDTO dto = new EmployeeDocumentDTO();
        dto.id = d.getId();
        dto.employeeId = d.getEmployee().getId();
        dto.employeeName = d.getEmployee().getFullName();
        dto.documentType = d.getDocumentType();
        dto.documentNumber = d.getDocumentNumber();
        dto.issueDate = d.getIssueDate();
        dto.expiryDate = d.getExpiryDate();
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

    public String getNotes() {
        return notes;
    }

    public static class CreateRequest {
        @NotNull(message = "Employee is required")
        private Long employeeId;

        @NotBlank(message = "Document type is required")
        private String documentType;

        private String documentNumber;
        private LocalDate issueDate;

        @NotNull(message = "Expiry date is required")
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
}
