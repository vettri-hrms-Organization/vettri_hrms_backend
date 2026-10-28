package com.haodaone.settlement.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.company.entity.Company;
import jakarta.persistence.*;

import java.math.BigDecimal;

/**
 * One row per company: gratuity and Full & Final settlement rules. A company without a row
 * uses the defaults below (covered under the Payment of Gratuity Act 1972, Rs 20 lakh ceiling).
 */
@Entity
@Table(name = "settlement_settings")
public class SettlementSettings extends BaseEntity {

    public static final String BASIS_BASIC = "BASIC";
    public static final String BASIS_GROSS = "GROSS";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, unique = true)
    private Company company;

    /** Whether the company pays gratuity at all. */
    @Column(name = "gratuity_applicable", nullable = false)
    private boolean gratuityApplicable = true;

    /** Covered under the Act: 15/26 formula with last-year rounding. Not covered: 15/30 on completed years. */
    @Column(name = "gratuity_covered_under_act", nullable = false)
    private boolean gratuityCoveredUnderAct = true;

    @Column(name = "gratuity_ceiling", nullable = false, precision = 14, scale = 2)
    private BigDecimal gratuityCeiling = new BigDecimal("2000000.00");

    /**
     * 0 = off (strict 5 completed years). 240 (6-day-week establishment) or 190 (5-day week, s.2A(2)):
     * an employee with 4 completed years who worked at least this many days in the 5th year is treated
     * as having 5 years of continuous service (court-recognised reading of s.2A).
     */
    @Column(name = "gratuity_partial_year_days", nullable = false)
    private int gratuityPartialYearDays = 0;

    @Column(name = "default_notice_period_days", nullable = false)
    private int defaultNoticePeriodDays = 30;

    /** BASIC or GROSS: monthly amount / 30 = one day of notice shortfall recovery. */
    @Column(name = "notice_recovery_basis", nullable = false, length = 10)
    private String noticeRecoveryBasis = BASIS_BASIC;

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public boolean isGratuityApplicable() { return gratuityApplicable; }
    public void setGratuityApplicable(boolean gratuityApplicable) { this.gratuityApplicable = gratuityApplicable; }
    public boolean isGratuityCoveredUnderAct() { return gratuityCoveredUnderAct; }
    public void setGratuityCoveredUnderAct(boolean gratuityCoveredUnderAct) { this.gratuityCoveredUnderAct = gratuityCoveredUnderAct; }
    public BigDecimal getGratuityCeiling() { return gratuityCeiling; }
    public void setGratuityCeiling(BigDecimal gratuityCeiling) { this.gratuityCeiling = gratuityCeiling; }
    public int getGratuityPartialYearDays() { return gratuityPartialYearDays; }
    public void setGratuityPartialYearDays(int gratuityPartialYearDays) { this.gratuityPartialYearDays = gratuityPartialYearDays; }
    public int getDefaultNoticePeriodDays() { return defaultNoticePeriodDays; }
    public void setDefaultNoticePeriodDays(int defaultNoticePeriodDays) { this.defaultNoticePeriodDays = defaultNoticePeriodDays; }
    public String getNoticeRecoveryBasis() { return noticeRecoveryBasis; }
    public void setNoticeRecoveryBasis(String noticeRecoveryBasis) { this.noticeRecoveryBasis = noticeRecoveryBasis; }
}
