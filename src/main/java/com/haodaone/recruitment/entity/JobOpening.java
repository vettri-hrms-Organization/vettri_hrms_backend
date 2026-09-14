package com.haodaone.recruitment.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.employee.entity.Employee;
import com.haodaone.company.entity.Company;
import com.haodaone.org.entity.Department;
import com.haodaone.org.entity.Designation;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "job_opening")
public class JobOpening extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @Column(nullable = false, length = 150)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "designation_id")
    private Designation designation;

    @Column(name = "employment_type", nullable = false, length = 20)
    private String employmentType = "FULL_TIME";

    @Column(name = "openings_count", nullable = false)
    private int openingsCount = 1;

    /** OPEN, ON_HOLD, CLOSED */
    @Column(nullable = false, length = 20)
    private String status = "OPEN";

    @Column(length = 2000)
    private String description;

    @Column(name = "posted_date")
    private LocalDate postedDate;

    @Column(name = "closed_date")
    private LocalDate closedDate;

    @Column(name = "closed_reason", length = 80)
    private String closedReason;

    @Column(name = "closed_comments", length = 1000)
    private String closedComments;

    @Column(name = "closed_by", length = 100)
    private String closedBy;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    /** Who's running this requisition - optional, see V8 migration. Powers the Recruiter persona's "my open reqs" scoping. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recruiter_id")
    private Employee recruiter;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Department getDepartment() {
        return department;
    }

    public void setDepartment(Department department) {
        this.department = department;
    }

    public Designation getDesignation() {
        return designation;
    }

    public void setDesignation(Designation designation) {
        this.designation = designation;
    }

    public String getEmploymentType() {
        return employmentType;
    }

    public void setEmploymentType(String employmentType) {
        this.employmentType = employmentType;
    }

    public int getOpeningsCount() {
        return openingsCount;
    }

    public void setOpeningsCount(int openingsCount) {
        this.openingsCount = openingsCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDate getPostedDate() {
        return postedDate;
    }

    public void setPostedDate(LocalDate postedDate) {
        this.postedDate = postedDate;
    }

    public LocalDate getClosedDate() {
        return closedDate;
    }

    public void setClosedDate(LocalDate closedDate) {
        this.closedDate = closedDate;
    }

    public String getClosedReason() { return closedReason; }
    public void setClosedReason(String closedReason) { this.closedReason = closedReason; }
    public String getClosedComments() { return closedComments; }
    public void setClosedComments(String closedComments) { this.closedComments = closedComments; }
    public String getClosedBy() { return closedBy; }
    public void setClosedBy(String closedBy) { this.closedBy = closedBy; }
    public LocalDateTime getClosedAt() { return closedAt; }
    public void setClosedAt(LocalDateTime closedAt) { this.closedAt = closedAt; }

    public Employee getRecruiter() {
        return recruiter;
    }

    public void setRecruiter(Employee recruiter) {
        this.recruiter = recruiter;
    }

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
}
