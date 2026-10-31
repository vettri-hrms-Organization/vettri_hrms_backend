package com.haodaone.survey;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** An employee survey, quick poll or eNPS pulse (V67). Anonymous surveys never store who answered. */
@Entity
@Table(name = "survey")
public class Survey extends BaseEntity {

    public static final String DRAFT = "DRAFT";
    public static final String OPEN = "OPEN";
    public static final String CLOSED = "CLOSED";

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "kind", nullable = false, length = 10)
    private String kind;

    @Column(name = "anonymous", nullable = false)
    private boolean anonymous;

    @Column(name = "status", nullable = false, length = 10)
    private String status;

    @Column(name = "closes_on")
    private LocalDate closesOn;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "questions_json", nullable = false, columnDefinition = "TEXT")
    private String questionsJson;

    @Column(name = "created_by_name", length = 150)
    private String createdByName;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "results_visible", nullable = false)
    private boolean resultsVisible;

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public boolean isAnonymous() { return anonymous; }
    public void setAnonymous(boolean anonymous) { this.anonymous = anonymous; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getClosesOn() { return closesOn; }
    public void setClosesOn(LocalDate closesOn) { this.closesOn = closesOn; }
    public Long getDepartmentId() { return departmentId; }
    public void setDepartmentId(Long departmentId) { this.departmentId = departmentId; }
    public String getQuestionsJson() { return questionsJson; }
    public void setQuestionsJson(String questionsJson) { this.questionsJson = questionsJson; }
    public String getCreatedByName() { return createdByName; }
    public void setCreatedByName(String createdByName) { this.createdByName = createdByName; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }
    public boolean isResultsVisible() { return resultsVisible; }
    public void setResultsVisible(boolean resultsVisible) { this.resultsVisible = resultsVisible; }
}
