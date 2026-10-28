package com.haodaone.company.entity;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Tenant / Company record for Phase 1 multi-tenant support.
 * Additive and intentionally small for Phase 1: name + optional domain.
 */
@Entity
@Table(name = "company")
public class Company extends BaseEntity {

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "domain", length = 255)
    private String domain;

    @Column(name = "productive_threshold_percent")
    private Integer productiveThresholdPercent = 80;

    @Column(name = "attendance_method", nullable = false, length = 20)
    private String attendanceMethod = "HYBRID";

    @Column(name = "manual_regularization_enabled", nullable = false)
    private boolean manualRegularizationEnabled = true;

    @Column(name = "regularization_approval_required", nullable = false)
    private boolean regularizationApprovalRequired = true;

    @Column(name = "attendance_grace_minutes", nullable = false)
    private int attendanceGraceMinutes = 10;

    /** Comma list of weekly offs, e.g. "SATURDAY,SUNDAY" or "SUNDAY,SATURDAY#2,SATURDAY#4" - see WorkingWeek. */
    @Column(name = "weekly_off_days", nullable = false, length = 100)
    private String weeklyOffDays = "SATURDAY,SUNDAY";

    /** Attendance policy: web/mobile check-in must carry a selfie (V52). */
    @Column(name = "require_selfie_check_in", nullable = false)
    private boolean requireSelfieAtCheckIn = false;

    /** Days check-in selfies are kept before the nightly purge deletes them (V52). */
    @Column(name = "selfie_retention_days", nullable = false)
    private int selfieRetentionDays = 90;

    public boolean isRequireSelfieAtCheckIn() {
        return requireSelfieAtCheckIn;
    }

    public void setRequireSelfieAtCheckIn(boolean requireSelfieAtCheckIn) {
        this.requireSelfieAtCheckIn = requireSelfieAtCheckIn;
    }

    public int getSelfieRetentionDays() {
        return selfieRetentionDays;
    }

    public void setSelfieRetentionDays(int selfieRetentionDays) {
        this.selfieRetentionDays = selfieRetentionDays;
    }

    @Column(name = "neutral_threshold_percent")
    private Integer neutralThresholdPercent = 50;

    @Column(length = 100)
    private String industry;

    @Column(name = "company_size", length = 30)
    private String companySize;

    @Column(length = 100)
    private String country;

    @Column(name = "workspace_interests", columnDefinition = "TEXT")
    private String workspaceInterests;

    @Column(name = "legal_name", length = 255)
    private String legalName;

    @Column(name = "billing_address", length = 1000)
    private String billingAddress;

    @Column(name = "gstin", length = 30)
    private String gstin;

    /** GST billing profile (V49): buyer state for place of supply, optional PO number printed on invoices. */
    @Column(name = "billing_state", length = 100)
    private String billingState;

    @Column(name = "billing_state_code", length = 2)
    private String billingStateCode;

    @Column(name = "billing_po_number", length = 60)
    private String billingPoNumber;

    public String getBillingState() { return billingState; }
    public void setBillingState(String billingState) { this.billingState = billingState; }
    public String getBillingStateCode() { return billingStateCode; }
    public void setBillingStateCode(String billingStateCode) { this.billingStateCode = billingStateCode; }
    public String getBillingPoNumber() { return billingPoNumber; }
    public void setBillingPoNumber(String billingPoNumber) { this.billingPoNumber = billingPoNumber; }

    /** Sales demo workspace (see demo.service.DemoCompanyService): billing enforcement, emails and WhatsApp are skipped. */
    @Column(name = "is_demo", nullable = false)
    private boolean demo = false;

    public boolean isDemo() {
        return demo;
    }

    public void setDemo(boolean demo) {
        this.demo = demo;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public Integer getProductiveThresholdPercent() {
        return productiveThresholdPercent;
    }

    public void setProductiveThresholdPercent(Integer value) {
        this.productiveThresholdPercent = value;
    }

    public String getAttendanceMethod() {
        return attendanceMethod;
    }

    public void setAttendanceMethod(String attendanceMethod) {
        this.attendanceMethod = attendanceMethod;
    }

    public boolean isManualRegularizationEnabled() {
        return manualRegularizationEnabled;
    }

    public void setManualRegularizationEnabled(boolean manualRegularizationEnabled) {
        this.manualRegularizationEnabled = manualRegularizationEnabled;
    }

    public boolean isRegularizationApprovalRequired() {
        return regularizationApprovalRequired;
    }

    public void setRegularizationApprovalRequired(boolean regularizationApprovalRequired) {
        this.regularizationApprovalRequired = regularizationApprovalRequired;
    }

    public int getAttendanceGraceMinutes() {
        return attendanceGraceMinutes;
    }

    public void setAttendanceGraceMinutes(int attendanceGraceMinutes) {
        this.attendanceGraceMinutes = attendanceGraceMinutes;
    }

    public String getWeeklyOffDays() {
        return weeklyOffDays;
    }

    public void setWeeklyOffDays(String weeklyOffDays) {
        this.weeklyOffDays = weeklyOffDays;
    }

    public Integer getNeutralThresholdPercent() {
        return neutralThresholdPercent;
    }

    public void setNeutralThresholdPercent(Integer value) {
        this.neutralThresholdPercent = value;
    }

    public String getIndustry() {
        return industry;
    }

    public void setIndustry(String industry) {
        this.industry = industry;
    }

    public String getCompanySize() {
        return companySize;
    }

    public void setCompanySize(String companySize) {
        this.companySize = companySize;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getWorkspaceInterests() {
        return workspaceInterests;
    }

    public void setWorkspaceInterests(String workspaceInterests) {
        this.workspaceInterests = workspaceInterests;
    }

    public String getLegalName() {
        return legalName;
    }

    public void setLegalName(String legalName) {
        this.legalName = legalName;
    }

    public String getBillingAddress() {
        return billingAddress;
    }

    public void setBillingAddress(String billingAddress) {
        this.billingAddress = billingAddress;
    }

    public String getGstin() {
        return gstin;
    }

    public void setGstin(String gstin) {
        this.gstin = gstin;
    }
}
