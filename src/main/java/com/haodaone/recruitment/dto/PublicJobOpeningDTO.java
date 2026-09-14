package com.haodaone.recruitment.dto;

import com.haodaone.recruitment.entity.JobOpening;

import java.time.LocalDate;

public class PublicJobOpeningDTO {
    private Long id;
    private String title;
    private String departmentName;
    private String designationTitle;
    private String employmentType;
    private int openingsCount;
    private String description;
    private LocalDate postedDate;

    public static PublicJobOpeningDTO from(JobOpening j) {
        PublicJobOpeningDTO dto = new PublicJobOpeningDTO();
        dto.id = j.getId();
        dto.title = j.getTitle();
        dto.employmentType = j.getEmploymentType();
        dto.openingsCount = j.getOpeningsCount();
        dto.description = j.getDescription();
        dto.postedDate = j.getPostedDate();
        if (j.getDepartment() != null) {
            dto.departmentName = j.getDepartment().getName();
        }
        if (j.getDesignation() != null) {
            dto.designationTitle = j.getDesignation().getTitle();
        }
        return dto;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDepartmentName() {
        return departmentName;
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

    public String getDescription() {
        return description;
    }

    public LocalDate getPostedDate() {
        return postedDate;
    }
}
