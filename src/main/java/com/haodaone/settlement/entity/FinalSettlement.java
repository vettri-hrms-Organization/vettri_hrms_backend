package com.haodaone.settlement.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.company.entity.Company;
import com.haodaone.employee.entity.Employee;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Full & Final settlement of an exiting employee: DRAFT -> REVIEWED -> APPROVED -> PAID
 * (DRAFT/REVIEWED may be CANCELLED). Amounts are frozen on the lines; totals are derived.
 */
@Entity
@Table(name = "final_settlement", indexes = {
        @Index(name = "idx_final_settlement_company_status", columnList = "company_id, status"),
        @Index(name = "idx_final_settlement_employee", columnList = "employee_id")
})
public class FinalSettlement extends BaseEntity {

    public static final String DRAFT = "DRAFT";
    public static final String REVIEWED = "REVIEWED";
    public static final String APPROVED = "APPROVED";
    public static final String PAID = "PAID";
    public static final String CANCELLED = "CANCELLED";
    /** Statuses that block a second settlement for the same employee. */
    public static final Set<String> ACTIVE = Set.of(DRAFT, REVIEWED, APPROVED, PAID);

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(nullable = false, length = 20)
    private String status = DRAFT;

    @Column(name = "resignation_date")
    private LocalDate resignationDate;

    @Column(name = "last_working_date", nullable = false)
    private LocalDate lastWorkingDate;

    @Column(name = "notice_period_days", nullable = false)
    private int noticePeriodDays;

    @Column(name = "notice_served_days", nullable = false)
    private int noticeServedDays;

    @Column(name = "notice_shortfall_days", nullable = false)
    private int noticeShortfallDays;

    @Column(name = "notice_waived", nullable = false)
    private boolean noticeWaived;

    @Column(name = "notice_recovery_basis", nullable = false, length = 10)
    private String noticeRecoveryBasis = SettlementSettings.BASIS_BASIC;

    /** Gratuity is payable without 5 years of service on death or disablement (s.4(1) proviso). */
    @Column(name = "death_or_disablement", nullable = false)
    private boolean deathOrDisablement;

    @Column(name = "service_years", nullable = false)
    private int serviceYears;

    @Column(name = "service_months", nullable = false)
    private int serviceMonths;

    @Column(name = "service_days", nullable = false)
    private int serviceDays;

    @Column(name = "gratuity_years", nullable = false)
    private int gratuityYears;

    @Column(name = "gratuity_eligible", nullable = false)
    private boolean gratuityEligible;

    /** Last drawn monthly basic + DA used for gratuity. */
    @Column(name = "gratuity_wage", nullable = false, precision = 14, scale = 2)
    private BigDecimal gratuityWage = BigDecimal.ZERO;

    @Column(name = "total_earnings", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalEarnings = BigDecimal.ZERO;

    @Column(name = "total_deductions", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalDeductions = BigDecimal.ZERO;

    @Column(name = "taxable_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxableAmount = BigDecimal.ZERO;

    @Column(name = "tds_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal tdsAmount = BigDecimal.ZERO;

    @Column(name = "net_payable", nullable = false, precision = 14, scale = 2)
    private BigDecimal netPayable = BigDecimal.ZERO;

    @Column(length = 1000)
    private String notes;

    @Column(name = "computation_notes", length = 2000)
    private String computationNotes;

    @Column(name = "reviewed_by", length = 100)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "approved_by", length = 100)
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "payment_reference", length = 120)
    private String paymentReference;

    @Column(name = "payment_date")
    private LocalDate paymentDate;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @OneToMany(mappedBy = "settlement", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<FinalSettlementLine> lines = new ArrayList<>();

    public void addLine(FinalSettlementLine line) {
        line.setSettlement(this);
        line.setCompany(company);
        lines.add(line);
    }

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }
    public Employee getEmployee() { return employee; }
    public void setEmployee(Employee employee) { this.employee = employee; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getResignationDate() { return resignationDate; }
    public void setResignationDate(LocalDate resignationDate) { this.resignationDate = resignationDate; }
    public LocalDate getLastWorkingDate() { return lastWorkingDate; }
    public void setLastWorkingDate(LocalDate lastWorkingDate) { this.lastWorkingDate = lastWorkingDate; }
    public int getNoticePeriodDays() { return noticePeriodDays; }
    public void setNoticePeriodDays(int noticePeriodDays) { this.noticePeriodDays = noticePeriodDays; }
    public int getNoticeServedDays() { return noticeServedDays; }
    public void setNoticeServedDays(int noticeServedDays) { this.noticeServedDays = noticeServedDays; }
    public int getNoticeShortfallDays() { return noticeShortfallDays; }
    public void setNoticeShortfallDays(int noticeShortfallDays) { this.noticeShortfallDays = noticeShortfallDays; }
    public boolean isNoticeWaived() { return noticeWaived; }
    public void setNoticeWaived(boolean noticeWaived) { this.noticeWaived = noticeWaived; }
    public String getNoticeRecoveryBasis() { return noticeRecoveryBasis; }
    public void setNoticeRecoveryBasis(String noticeRecoveryBasis) { this.noticeRecoveryBasis = noticeRecoveryBasis; }
    public boolean isDeathOrDisablement() { return deathOrDisablement; }
    public void setDeathOrDisablement(boolean deathOrDisablement) { this.deathOrDisablement = deathOrDisablement; }
    public int getServiceYears() { return serviceYears; }
    public void setServiceYears(int serviceYears) { this.serviceYears = serviceYears; }
    public int getServiceMonths() { return serviceMonths; }
    public void setServiceMonths(int serviceMonths) { this.serviceMonths = serviceMonths; }
    public int getServiceDays() { return serviceDays; }
    public void setServiceDays(int serviceDays) { this.serviceDays = serviceDays; }
    public int getGratuityYears() { return gratuityYears; }
    public void setGratuityYears(int gratuityYears) { this.gratuityYears = gratuityYears; }
    public boolean isGratuityEligible() { return gratuityEligible; }
    public void setGratuityEligible(boolean gratuityEligible) { this.gratuityEligible = gratuityEligible; }
    public BigDecimal getGratuityWage() { return gratuityWage; }
    public void setGratuityWage(BigDecimal gratuityWage) { this.gratuityWage = gratuityWage; }
    public BigDecimal getTotalEarnings() { return totalEarnings; }
    public void setTotalEarnings(BigDecimal totalEarnings) { this.totalEarnings = totalEarnings; }
    public BigDecimal getTotalDeductions() { return totalDeductions; }
    public void setTotalDeductions(BigDecimal totalDeductions) { this.totalDeductions = totalDeductions; }
    public BigDecimal getTaxableAmount() { return taxableAmount; }
    public void setTaxableAmount(BigDecimal taxableAmount) { this.taxableAmount = taxableAmount; }
    public BigDecimal getTdsAmount() { return tdsAmount; }
    public void setTdsAmount(BigDecimal tdsAmount) { this.tdsAmount = tdsAmount; }
    public BigDecimal getNetPayable() { return netPayable; }
    public void setNetPayable(BigDecimal netPayable) { this.netPayable = netPayable; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getComputationNotes() { return computationNotes; }
    public void setComputationNotes(String computationNotes) { this.computationNotes = computationNotes; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public String getApprovedBy() { return approvedBy; }
    public void setApprovedBy(String approvedBy) { this.approvedBy = approvedBy; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void setApprovedAt(LocalDateTime approvedAt) { this.approvedAt = approvedAt; }
    public String getPaymentReference() { return paymentReference; }
    public void setPaymentReference(String paymentReference) { this.paymentReference = paymentReference; }
    public LocalDate getPaymentDate() { return paymentDate; }
    public void setPaymentDate(LocalDate paymentDate) { this.paymentDate = paymentDate; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }
    public List<FinalSettlementLine> getLines() { return lines; }
}
