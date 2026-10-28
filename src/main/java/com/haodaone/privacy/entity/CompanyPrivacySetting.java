package com.haodaone.privacy.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.company.entity.Company;
import jakarta.persistence.*;

/** Per-company privacy configuration (DPDP Act 2023). No row means defaults. */
@Entity
@Table(name = "company_privacy_setting")
public class CompanyPrivacySetting extends BaseEntity {

    public static final int DEFAULT_PAYROLL_RETENTION_YEARS = 7;
    public static final int DEFAULT_OTHER_DATA_RETENTION_MONTHS = 24;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false, unique = true)
    private Company company;

    @Column(name = "grievance_officer_name", length = 150)
    private String grievanceOfficerName;

    @Column(name = "grievance_officer_email", length = 150)
    private String grievanceOfficerEmail;

    @Column(name = "grievance_officer_phone", length = 30)
    private String grievanceOfficerPhone;

    @Column(name = "breach_contact_name", length = 150)
    private String breachContactName;

    @Column(name = "breach_contact_email", length = 150)
    private String breachContactEmail;

    @Column(name = "breach_contact_phone", length = 30)
    private String breachContactPhone;

    /** Payroll/statutory records (payslips, PF/ESI/TDS) are kept at least this long after exit. */
    @Column(name = "payroll_retention_years", nullable = false)
    private int payrollRetentionYears = DEFAULT_PAYROLL_RETENTION_YEARS;

    /** Other personal data of exited employees becomes eligible for erasure after this many months. */
    @Column(name = "other_data_retention_months", nullable = false)
    private int otherDataRetentionMonths = DEFAULT_OTHER_DATA_RETENTION_MONTHS;

    /** When true a device cannot be enrolled for an employee until they accept the monitoring notice. */
    @Column(name = "monitoring_consent_required", nullable = false)
    private boolean monitoringConsentRequired = false;

    /** When true, withdrawing consent pauses (deactivates) the employee's monitored devices automatically. */
    @Column(name = "stop_monitoring_on_withdrawal", nullable = false)
    private boolean stopMonitoringOnWithdrawal = false;

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public String getGrievanceOfficerName() { return grievanceOfficerName; }
    public void setGrievanceOfficerName(String v) { this.grievanceOfficerName = v; }
    public String getGrievanceOfficerEmail() { return grievanceOfficerEmail; }
    public void setGrievanceOfficerEmail(String v) { this.grievanceOfficerEmail = v; }
    public String getGrievanceOfficerPhone() { return grievanceOfficerPhone; }
    public void setGrievanceOfficerPhone(String v) { this.grievanceOfficerPhone = v; }
    public String getBreachContactName() { return breachContactName; }
    public void setBreachContactName(String v) { this.breachContactName = v; }
    public String getBreachContactEmail() { return breachContactEmail; }
    public void setBreachContactEmail(String v) { this.breachContactEmail = v; }
    public String getBreachContactPhone() { return breachContactPhone; }
    public void setBreachContactPhone(String v) { this.breachContactPhone = v; }
    public int getPayrollRetentionYears() { return payrollRetentionYears; }
    public void setPayrollRetentionYears(int v) { this.payrollRetentionYears = v; }
    public int getOtherDataRetentionMonths() { return otherDataRetentionMonths; }
    public void setOtherDataRetentionMonths(int v) { this.otherDataRetentionMonths = v; }
    public boolean isMonitoringConsentRequired() { return monitoringConsentRequired; }
    public void setMonitoringConsentRequired(boolean v) { this.monitoringConsentRequired = v; }
    public boolean isStopMonitoringOnWithdrawal() { return stopMonitoringOnWithdrawal; }
    public void setStopMonitoringOnWithdrawal(boolean v) { this.stopMonitoringOnWithdrawal = v; }
}
