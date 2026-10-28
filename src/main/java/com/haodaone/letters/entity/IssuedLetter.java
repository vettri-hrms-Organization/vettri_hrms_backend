package com.haodaone.letters.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.company.entity.Company;
import com.haodaone.employee.entity.Employee;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A generated letter PDF, issued either to an employee or (offer letters)
 * to a recruitment candidate. The PDF lives in the employee-document S3
 * bucket; for employees an EmployeeDocument of type LETTER points at the
 * same object so it also shows up in the employee's documents.
 */
@Entity
@Table(name = "issued_letter")
public class IssuedLetter extends BaseEntity {

    public static final String EMAIL_SENT = "SENT";
    public static final String EMAIL_FAILED = "FAILED";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "template_id")
    private LetterTemplate template;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    @Column(name = "candidate_id")
    private Long candidateId;

    @Column(name = "letter_type", nullable = false, length = 40)
    private String letterType;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "rendered_body", columnDefinition = "TEXT")
    private String renderedBody;

    @Column(name = "s3_object_key", length = 500)
    private String s3ObjectKey;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "employee_document_id")
    private Long employeeDocumentId;

    @Column(name = "issued_at", nullable = false)
    private LocalDateTime issuedAt;

    @Column(name = "issued_by", length = 150)
    private String issuedBy;

    @Column(name = "emailed_at")
    private LocalDateTime emailedAt;

    @Column(name = "email_status", length = 20)
    private String emailStatus;

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public LetterTemplate getTemplate() { return template; }
    public void setTemplate(LetterTemplate template) { this.template = template; }
    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }
    public Long getCandidateId() { return candidateId; }
    public void setCandidateId(Long candidateId) { this.candidateId = candidateId; }
    public String getLetterType() { return letterType; }
    public void setLetterType(String letterType) { this.letterType = letterType; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getRenderedBody() { return renderedBody; }
    public void setRenderedBody(String renderedBody) { this.renderedBody = renderedBody; }
    public String getS3ObjectKey() { return s3ObjectKey; }
    public void setS3ObjectKey(String s3ObjectKey) { this.s3ObjectKey = s3ObjectKey; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public Long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(Long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }
    public Long getEmployeeDocumentId() { return employeeDocumentId; }
    public void setEmployeeDocumentId(Long employeeDocumentId) { this.employeeDocumentId = employeeDocumentId; }
    public LocalDateTime getIssuedAt() { return issuedAt; }
    public void setIssuedAt(LocalDateTime issuedAt) { this.issuedAt = issuedAt; }
    public String getIssuedBy() { return issuedBy; }
    public void setIssuedBy(String issuedBy) { this.issuedBy = issuedBy; }
    public LocalDateTime getEmailedAt() { return emailedAt; }
    public void setEmailedAt(LocalDateTime emailedAt) { this.emailedAt = emailedAt; }
    public String getEmailStatus() { return emailStatus; }
    public void setEmailStatus(String emailStatus) { this.emailStatus = emailStatus; }
}
