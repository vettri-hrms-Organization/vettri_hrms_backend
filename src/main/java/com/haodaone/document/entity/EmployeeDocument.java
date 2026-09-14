package com.haodaone.document.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.employee.entity.Employee;
import com.haodaone.company.entity.Company;
import jakarta.persistence.*;

import java.time.LocalDate;

/**
 * Deliberately metadata-only - type, number, issue/expiry dates, notes -
 * not file storage. Attaching the actual scanned document would mean
 * wiring up S3 storage the way resumes/offer letters already are (see
 * ResumeS3StorageService), which is a real follow-up, not included here.
 * This covers the actual ask that was groundable without more input:
 * "remind HR before something expires," which only needs the date, not
 * the file.
 */
@Entity
@Table(name = "employee_document")
public class EmployeeDocument extends BaseEntity {

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

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
