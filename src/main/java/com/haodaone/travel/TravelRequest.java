package com.haodaone.travel;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** A business trip request with an optional cash advance (V67). Advance: NONE -> REQUESTED -> PAID -> SETTLED, or RECOVERY (deducted in payroll) -> RECOVERED. */
@Entity
@Table(name = "travel_request")
public class TravelRequest extends BaseEntity {

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String CANCELLED = "CANCELLED";
    public static final String COMPLETED = "COMPLETED";

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "purpose", nullable = false, length = 300)
    private String purpose;

    @Column(name = "trip_type", nullable = false, length = 16)
    private String tripType;

    @Column(name = "from_city", nullable = false, length = 80)
    private String fromCity;

    @Column(name = "to_city", nullable = false, length = 80)
    private String toCity;

    @Column(name = "depart_date", nullable = false)
    private LocalDate departDate;

    @Column(name = "return_date", nullable = false)
    private LocalDate returnDate;

    @Column(name = "travel_mode", nullable = false, length = 12)
    private String travelMode;

    @Column(name = "needs_accommodation", nullable = false)
    private boolean needsAccommodation;

    @Column(name = "estimated_cost", precision = 14, scale = 2)
    private BigDecimal estimatedCost;

    @Column(name = "advance_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal advanceAmount = BigDecimal.ZERO;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "status", nullable = false, length = 12)
    private String status;

    @Column(name = "approver_employee_id")
    private Long approverEmployeeId;

    @Column(name = "decided_by_name", length = 150)
    private String decidedByName;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    @Column(name = "advance_status", nullable = false, length = 12)
    private String advanceStatus;

    @Column(name = "advance_paid_at")
    private LocalDateTime advancePaidAt;

    @Column(name = "advance_reference", length = 100)
    private String advanceReference;

    @Column(name = "spent_amount", precision = 14, scale = 2)
    private BigDecimal spentAmount;

    @Column(name = "recovery_amount", precision = 14, scale = 2)
    private BigDecimal recoveryAmount;

    @Column(name = "recovery_payroll_item_id")
    private Long recoveryPayrollItemId;

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
    public String getTripType() { return tripType; }
    public void setTripType(String tripType) { this.tripType = tripType; }
    public String getFromCity() { return fromCity; }
    public void setFromCity(String fromCity) { this.fromCity = fromCity; }
    public String getToCity() { return toCity; }
    public void setToCity(String toCity) { this.toCity = toCity; }
    public LocalDate getDepartDate() { return departDate; }
    public void setDepartDate(LocalDate departDate) { this.departDate = departDate; }
    public LocalDate getReturnDate() { return returnDate; }
    public void setReturnDate(LocalDate returnDate) { this.returnDate = returnDate; }
    public String getTravelMode() { return travelMode; }
    public void setTravelMode(String travelMode) { this.travelMode = travelMode; }
    public boolean isNeedsAccommodation() { return needsAccommodation; }
    public void setNeedsAccommodation(boolean needsAccommodation) { this.needsAccommodation = needsAccommodation; }
    public BigDecimal getEstimatedCost() { return estimatedCost; }
    public void setEstimatedCost(BigDecimal estimatedCost) { this.estimatedCost = estimatedCost; }
    public BigDecimal getAdvanceAmount() { return advanceAmount; }
    public void setAdvanceAmount(BigDecimal advanceAmount) { this.advanceAmount = advanceAmount; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getApproverEmployeeId() { return approverEmployeeId; }
    public void setApproverEmployeeId(Long approverEmployeeId) { this.approverEmployeeId = approverEmployeeId; }
    public String getDecidedByName() { return decidedByName; }
    public void setDecidedByName(String decidedByName) { this.decidedByName = decidedByName; }
    public LocalDateTime getDecidedAt() { return decidedAt; }
    public void setDecidedAt(LocalDateTime decidedAt) { this.decidedAt = decidedAt; }
    public String getDecisionNote() { return decisionNote; }
    public void setDecisionNote(String decisionNote) { this.decisionNote = decisionNote; }
    public String getAdvanceStatus() { return advanceStatus; }
    public void setAdvanceStatus(String advanceStatus) { this.advanceStatus = advanceStatus; }
    public LocalDateTime getAdvancePaidAt() { return advancePaidAt; }
    public void setAdvancePaidAt(LocalDateTime advancePaidAt) { this.advancePaidAt = advancePaidAt; }
    public String getAdvanceReference() { return advanceReference; }
    public void setAdvanceReference(String advanceReference) { this.advanceReference = advanceReference; }
    public BigDecimal getSpentAmount() { return spentAmount; }
    public void setSpentAmount(BigDecimal spentAmount) { this.spentAmount = spentAmount; }
    public BigDecimal getRecoveryAmount() { return recoveryAmount; }
    public void setRecoveryAmount(BigDecimal recoveryAmount) { this.recoveryAmount = recoveryAmount; }
    public Long getRecoveryPayrollItemId() { return recoveryPayrollItemId; }
    public void setRecoveryPayrollItemId(Long recoveryPayrollItemId) { this.recoveryPayrollItemId = recoveryPayrollItemId; }
}
