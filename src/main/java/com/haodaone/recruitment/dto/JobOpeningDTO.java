package com.haodaone.recruitment.dto;

import com.haodaone.recruitment.entity.JobOpening;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class JobOpeningDTO {
    private Long id;
    private String title;
    private Long departmentId;
    private String departmentName;
    private Long designationId;
    private String designationTitle;
    private String employmentType;
    private int openingsCount;
    private String status;
    private String description;
    private LocalDate postedDate;
    private long candidateCount;
    private long hiredCount;
    private Long recruiterId;
    private String recruiterName;
    private String closedReason;
    private String closedComments;
    private String closedBy;
    private LocalDateTime closedAt;

    public static JobOpeningDTO from(JobOpening j) {
        JobOpeningDTO dto = new JobOpeningDTO();
        dto.id = j.getId();
        dto.title = j.getTitle();
        dto.employmentType = j.getEmploymentType();
        dto.openingsCount = j.getOpeningsCount();
        dto.status = j.getStatus();
        dto.description = j.getDescription();
        dto.postedDate = j.getPostedDate();
        dto.closedReason = j.getClosedReason();
        dto.closedComments = j.getClosedComments();
        dto.closedBy = j.getClosedBy();
        dto.closedAt = j.getClosedAt();
        if (j.getDepartment() != null) {
            dto.departmentId = j.getDepartment().getId();
            dto.departmentName = j.getDepartment().getName();
        }
        if (j.getDesignation() != null) {
            dto.designationId = j.getDesignation().getId();
            dto.designationTitle = j.getDesignation().getTitle();
        }
        if (j.getRecruiter() != null) {
            dto.recruiterId = j.getRecruiter().getId();
            dto.recruiterName = j.getRecruiter().getFullName();
        }
        return dto;
    }

    public void setCandidateCount(long candidateCount) {
        this.candidateCount = candidateCount;
    }

    public void setHiredCount(long hiredCount) {
        this.hiredCount = hiredCount;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public Long getDesignationId() {
        return designationId;
    }

    public String getDesignationTitle() {
        return designationTitle;
    }

    public String getEmploymentType() {
        return employmentType;
    }

    public int getOpeningsCount() {
        return openingsCount;
    }

    public String getStatus() {
        return status;
    }

    public String getDescription() {
        return description;
    }

    public LocalDate getPostedDate() {
        return postedDate;
    }

    public long getCandidateCount() {
        return candidateCount;
    }

    public long getHiredCount() {
        return hiredCount;
    }

    public Long getRecruiterId() {
        return recruiterId;
    }

    public String getRecruiterName() {
        return recruiterName;
    }

    public String getClosedReason() { return closedReason; }
    public String getClosedComments() { return closedComments; }
    public String getClosedBy() { return closedBy; }
    public LocalDateTime getClosedAt() { return closedAt; }

    public static class CreateRequest {
        @NotBlank(message = "Title is required")
        private String title;
        private Long departmentId;
        private Long designationId;
        private String employmentType = "FULL_TIME";

        @Positive(message = "Openings count must be positive")
        private int openingsCount = 1;

        private String description;
        private Long recruiterId;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public Long getDepartmentId() {
            return departmentId;
        }

        public void setDepartmentId(Long departmentId) {
            this.departmentId = departmentId;
        }

        public Long getDesignationId() {
            return designationId;
        }

        public void setDesignationId(Long designationId) {
            this.designationId = designationId;
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

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Long getRecruiterId() {
            return recruiterId;
        }

        public void setRecruiterId(Long recruiterId) {
            this.recruiterId = recruiterId;
        }
    }
}
