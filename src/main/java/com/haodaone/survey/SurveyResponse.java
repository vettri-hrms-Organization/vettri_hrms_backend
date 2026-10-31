package com.haodaone.survey;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.*;

/** One person's answers to a survey (V67). respondentKey is an HMAC of survey+employee, so duplicates are blocked without storing identity on anonymous surveys. */
@Entity
@Table(name = "survey_response")
public class SurveyResponse extends BaseEntity {

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "survey_id", nullable = false)
    private Long surveyId;

    @Column(name = "employee_id")
    private Long employeeId;

    @Column(name = "respondent_key", nullable = false, length = 64)
    private String respondentKey;

    @Column(name = "answers_json", nullable = false, columnDefinition = "TEXT")
    private String answersJson;

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getSurveyId() { return surveyId; }
    public void setSurveyId(Long surveyId) { this.surveyId = surveyId; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
    public String getRespondentKey() { return respondentKey; }
    public void setRespondentKey(String respondentKey) { this.respondentKey = respondentKey; }
    public String getAnswersJson() { return answersJson; }
    public void setAnswersJson(String answersJson) { this.answersJson = answersJson; }
}
