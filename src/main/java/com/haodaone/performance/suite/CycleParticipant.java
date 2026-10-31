package com.haodaone.performance.suite;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** One employee in a review cycle (V66). */
@Entity
@Table(name = "cycle_participant")
public class CycleParticipant extends BaseEntity {

    public static final String SELF_PENDING = "SELF_PENDING";
    public static final String MANAGER_PENDING = "MANAGER_PENDING";
    public static final String CALIBRATION = "CALIBRATION";
    public static final String RELEASED = "RELEASED";

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "cycle_id", nullable = false)
    private Long cycleId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "manager_employee_id")
    private Long managerEmployeeId;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "manager_rating", precision = 3, scale = 1)
    private BigDecimal managerRating;

    @Column(name = "final_rating", precision = 3, scale = 1)
    private BigDecimal finalRating;

    @Column(name = "calibration_note", length = 1000)
    private String calibrationNote;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public Long getCycleId() { return cycleId; }
    public void setCycleId(Long cycleId) { this.cycleId = cycleId; }
    public Long getEmployeeId() { return employeeId; }
    public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
    public Long getManagerEmployeeId() { return managerEmployeeId; }
    public void setManagerEmployeeId(Long managerEmployeeId) { this.managerEmployeeId = managerEmployeeId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getManagerRating() { return managerRating; }
    public void setManagerRating(BigDecimal managerRating) { this.managerRating = managerRating; }
    public BigDecimal getFinalRating() { return finalRating; }
    public void setFinalRating(BigDecimal finalRating) { this.finalRating = finalRating; }
    public String getCalibrationNote() { return calibrationNote; }
    public void setCalibrationNote(String calibrationNote) { this.calibrationNote = calibrationNote; }
    public LocalDateTime getReleasedAt() { return releasedAt; }
    public void setReleasedAt(LocalDateTime releasedAt) { this.releasedAt = releasedAt; }
    public LocalDateTime getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(LocalDateTime acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }
}
