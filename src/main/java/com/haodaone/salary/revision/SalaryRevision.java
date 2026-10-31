package com.haodaone.salary.revision;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A salary revision (V61). SCHEDULED (effective in the future) -> APPLIED (new structure active); or CANCELLED.
 * Arrears for months already paid at the old rate: NONE, PENDING (paid with the next payroll for
 * {@code arrearsPayoutMonth} or later) or PAID.
 */
@Entity
@Table(name = "salary_revision")
public class SalaryRevision extends BaseEntity {

    public static final String SCHEDULED = "SCHEDULED";
    public static final String APPLIED = "APPLIED";
    public static final String CANCELLED = "CANCELLED";

    public static final String ARREARS_NONE = "NONE";
    public static final String ARREARS_PENDING = "PENDING";
    public static final String ARREARS_PAID = "PAID";

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "revision_type", nullable = false, length = 30)
    private String revisionType;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "old_structure_id")
    private Long oldStructureId;

    @Column(name = "new_structure_id")
    private Long newStructureId;

    @Column(name = "old_monthly_gross", nullable = false, precision = 14, scale = 2)
    private BigDecimal oldMonthlyGross;

    @Column(name = "new_monthly_gross", nullable = false, precision = 14, scale = 2)
    private BigDecimal newMonthlyGross;

    @Column(name = "new_components_json", nullable = false, columnDefinition = "TEXT")
    private String newComponentsJson;

    @Column(name = "auto_statutory", nullable = false)
    private boolean autoStatutory = true;

    @Column(length = 1000)
    private String reason;

    @Column(name = "arrears_total", nullable = false, precision = 14, scale = 2)
    private BigDecimal arrearsTotal = BigDecimal.ZERO;

    @Column(name = "arrears_payout_month", length = 7)
    private String arrearsPayoutMonth;

    @Column(name = "arrears_status", nullable = false, length = 20)
    private String arrearsStatus = ARREARS_NONE;

    @Column(name = "arrears_payroll_item_id")
    private Long arrearsPayrollItemId;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    @Column(name = "created_by_name", length = 150)
    private String createdByName;

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
    public String getRevisionType() { return revisionType; }
    public void setRevisionType(String revisionType) { this.revisionType = revisionType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public void setEffectiveFrom(LocalDate effectiveFrom) { this.effectiveFrom = effectiveFrom; }
    public Long getOldStructureId() { return oldStructureId; }
    public void setOldStructureId(Long oldStructureId) { this.oldStructureId = oldStructureId; }
    public Long getNewStructureId() { return newStructureId; }
    public void setNewStructureId(Long newStructureId) { this.newStructureId = newStructureId; }
    public BigDecimal getOldMonthlyGross() { return oldMonthlyGross; }
    public void setOldMonthlyGross(BigDecimal oldMonthlyGross) { this.oldMonthlyGross = oldMonthlyGross; }
    public BigDecimal getNewMonthlyGross() { return newMonthlyGross; }
    public void setNewMonthlyGross(BigDecimal newMonthlyGross) { this.newMonthlyGross = newMonthlyGross; }
    public String getNewComponentsJson() { return newComponentsJson; }
    public void setNewComponentsJson(String newComponentsJson) { this.newComponentsJson = newComponentsJson; }
    public boolean isAutoStatutory() { return autoStatutory; }
    public void setAutoStatutory(boolean autoStatutory) { this.autoStatutory = autoStatutory; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public BigDecimal getArrearsTotal() { return arrearsTotal; }
    public void setArrearsTotal(BigDecimal arrearsTotal) { this.arrearsTotal = arrearsTotal; }
    public String getArrearsPayoutMonth() { return arrearsPayoutMonth; }
    public void setArrearsPayoutMonth(String arrearsPayoutMonth) { this.arrearsPayoutMonth = arrearsPayoutMonth; }
    public String getArrearsStatus() { return arrearsStatus; }
    public void setArrearsStatus(String arrearsStatus) { this.arrearsStatus = arrearsStatus; }
    public Long getArrearsPayrollItemId() { return arrearsPayrollItemId; }
    public void setArrearsPayrollItemId(Long arrearsPayrollItemId) { this.arrearsPayrollItemId = arrearsPayrollItemId; }
    public LocalDateTime getAppliedAt() { return appliedAt; }
    public void setAppliedAt(LocalDateTime appliedAt) { this.appliedAt = appliedAt; }
    public String getCreatedByName() { return createdByName; }
    public void setCreatedByName(String createdByName) { this.createdByName = createdByName; }
}
