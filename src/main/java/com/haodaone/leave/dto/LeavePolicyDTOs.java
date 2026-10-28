package com.haodaone.leave.dto;

import com.haodaone.leave.entity.CompOffRequest;
import com.haodaone.leave.entity.LeaveEncashment;
import com.haodaone.leave.entity.LeaveType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Request/response shapes for leave policies, ledger, year-end, comp-off and encashment (V45). */
public final class LeavePolicyDTOs {

    private LeavePolicyDTOs() {
    }

    // ------------------------------------------------------------------ policy

    /** Full policy for one leave type (PUT /api/leave-types/{id}/policy). Nullable numbers mean "not set". */
    public static class PolicyRequest {
        private String accrualMode;
        private String accrualRounding;
        private boolean prorateOnJoining;
        private Integer joiningCutoffDay;
        private Double maxBalanceDays;
        private Integer probationDays;
        private Double negativeBalanceDays;
        private Boolean carryForward;
        private Double maxCarryForwardDays;
        private Integer carryForwardExpiryMonths;
        private boolean encashable;
        private Double maxEncashableDaysPerYear;
        private String encashmentBasis;
        private Integer creditExpiryDays;

        public String getAccrualMode() { return accrualMode; }
        public void setAccrualMode(String accrualMode) { this.accrualMode = accrualMode; }
        public String getAccrualRounding() { return accrualRounding; }
        public void setAccrualRounding(String accrualRounding) { this.accrualRounding = accrualRounding; }
        public boolean isProrateOnJoining() { return prorateOnJoining; }
        public void setProrateOnJoining(boolean prorateOnJoining) { this.prorateOnJoining = prorateOnJoining; }
        public Integer getJoiningCutoffDay() { return joiningCutoffDay; }
        public void setJoiningCutoffDay(Integer joiningCutoffDay) { this.joiningCutoffDay = joiningCutoffDay; }
        public Double getMaxBalanceDays() { return maxBalanceDays; }
        public void setMaxBalanceDays(Double maxBalanceDays) { this.maxBalanceDays = maxBalanceDays; }
        public Integer getProbationDays() { return probationDays; }
        public void setProbationDays(Integer probationDays) { this.probationDays = probationDays; }
        public Double getNegativeBalanceDays() { return negativeBalanceDays; }
        public void setNegativeBalanceDays(Double negativeBalanceDays) { this.negativeBalanceDays = negativeBalanceDays; }
        public Boolean getCarryForward() { return carryForward; }
        public void setCarryForward(Boolean carryForward) { this.carryForward = carryForward; }
        public Double getMaxCarryForwardDays() { return maxCarryForwardDays; }
        public void setMaxCarryForwardDays(Double maxCarryForwardDays) { this.maxCarryForwardDays = maxCarryForwardDays; }
        public Integer getCarryForwardExpiryMonths() { return carryForwardExpiryMonths; }
        public void setCarryForwardExpiryMonths(Integer carryForwardExpiryMonths) { this.carryForwardExpiryMonths = carryForwardExpiryMonths; }
        public boolean isEncashable() { return encashable; }
        public void setEncashable(boolean encashable) { this.encashable = encashable; }
        public Double getMaxEncashableDaysPerYear() { return maxEncashableDaysPerYear; }
        public void setMaxEncashableDaysPerYear(Double maxEncashableDaysPerYear) { this.maxEncashableDaysPerYear = maxEncashableDaysPerYear; }
        public String getEncashmentBasis() { return encashmentBasis; }
        public void setEncashmentBasis(String encashmentBasis) { this.encashmentBasis = encashmentBasis; }
        public Integer getCreditExpiryDays() { return creditExpiryDays; }
        public void setCreditExpiryDays(Integer creditExpiryDays) { this.creditExpiryDays = creditExpiryDays; }
    }

    public record PolicyView(String accrualMode, String accrualRounding, boolean prorateOnJoining, Integer joiningCutoffDay,
                             Double maxBalanceDays, Integer probationDays, Double negativeBalanceDays,
                             Double maxCarryForwardDays, Integer carryForwardExpiryMonths, boolean encashable,
                             Double maxEncashableDaysPerYear, String encashmentBasis, boolean systemManaged,
                             Integer creditExpiryDays) {
        public static PolicyView of(LeaveType t) {
            return new PolicyView(t.getAccrualMode(), t.getAccrualRounding(), t.isProrateOnJoining(), t.getJoiningCutoffDay(),
                    t.getMaxBalanceDays(), t.getProbationDays(), t.getNegativeBalanceDays(), t.getMaxCarryForwardDays(),
                    t.getCarryForwardExpiryMonths(), t.isEncashable(), t.getMaxEncashableDaysPerYear(), t.getEncashmentBasis(),
                    t.isSystemManaged(), t.getCreditExpiryDays());
        }
    }

    // ------------------------------------------------------------------ ledger / adjustment

    public record LedgerEntry(LocalDate date, Long leaveTypeId, String leaveTypeName, String kind, String direction,
                              double days, String description, Long referenceId, double balanceAfter) {
    }

    public static class AdjustmentRequest {
        private Long employeeId;
        private Long leaveTypeId;
        private Integer year;
        private Double days;
        private String reason;

        public Long getEmployeeId() { return employeeId; }
        public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
        public Long getLeaveTypeId() { return leaveTypeId; }
        public void setLeaveTypeId(Long leaveTypeId) { this.leaveTypeId = leaveTypeId; }
        public Integer getYear() { return year; }
        public void setYear(Integer year) { this.year = year; }
        public Double getDays() { return days; }
        public void setDays(Double days) { this.days = days; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public record AdjustmentResult(Long id, Long employeeId, Long leaveTypeId, String leaveTypeName, int year,
                                   double days, String reason, double remainingAfter) {
    }

    // ------------------------------------------------------------------ year end

    public record YearEndRow(Long employeeId, String employeeName, String employeeCode, Long leaveTypeId,
                             String leaveTypeName, int year, double closing, double carried, double lapsed,
                             double encashable, boolean processed) {
    }

    /** projected = the closing year is not over yet, so figures assume the full year's accrual and today's approvals. */
    public record YearEndPreview(int closingYear, int nextYear, int employees, double totalCarried, double totalLapsed,
                                 double totalEncashable, boolean projected, LocalDateTime lastProcessedAt,
                                 List<YearEndRow> rows) {
    }

    // ------------------------------------------------------------------ comp-off

    public static class CompOffApplyRequest {
        private Long employeeId;
        private LocalDate workDate;
        /** FULL_DAY, HALF_DAY or HOURS */
        private String duration;
        private Double hours;
        private String reason;

        public Long getEmployeeId() { return employeeId; }
        public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
        public LocalDate getWorkDate() { return workDate; }
        public void setWorkDate(LocalDate workDate) { this.workDate = workDate; }
        public String getDuration() { return duration; }
        public void setDuration(String duration) { this.duration = duration; }
        public Double getHours() { return hours; }
        public void setHours(Double hours) { this.hours = hours; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public record CompOffDTO(Long id, Long employeeId, String employeeName, String employeeCode, String departmentName,
                             LocalDate workDate, String duration, Double hours, double days, String dayType,
                             Boolean attendanceVerified, String reason, String status, LocalDate expiresOn,
                             String decidedByName, LocalDateTime decidedAt, String decisionNote, LocalDateTime createdAt) {
        public static CompOffDTO from(CompOffRequest r) {
            var e = r.getEmployee();
            return new CompOffDTO(r.getId(), e.getId(), e.getFullName(), e.getEmployeeCode(),
                    e.getDepartment() != null ? e.getDepartment().getName() : null, r.getWorkDate(), r.getDuration(),
                    r.getHours(), r.getDays(), r.getDayType(), r.getAttendanceVerified(), r.getReason(), r.getStatus(),
                    r.getExpiresOn(), r.getDecidedBy() != null ? r.getDecidedBy().getFullName() : null, r.getDecidedAt(),
                    r.getDecisionNote(), r.getCreatedAt());
        }
    }

    // ------------------------------------------------------------------ encashment

    public static class EncashmentApplyRequest {
        private Long employeeId;
        private Long leaveTypeId;
        private Double days;
        private String reason;

        public Long getEmployeeId() { return employeeId; }
        public void setEmployeeId(Long employeeId) { this.employeeId = employeeId; }
        public Long getLeaveTypeId() { return leaveTypeId; }
        public void setLeaveTypeId(Long leaveTypeId) { this.leaveTypeId = leaveTypeId; }
        public Double getDays() { return days; }
        public void setDays(Double days) { this.days = days; }
        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }
    }

    public record EncashmentDTO(Long id, Long employeeId, String employeeName, String employeeCode, String departmentName,
                                Long leaveTypeId, String leaveTypeName, int leaveYear, double days, String reason,
                                String status, String basis, BigDecimal perDayRate, BigDecimal amount,
                                String decidedByName, LocalDateTime decidedAt, String decisionNote, Long payrollItemId,
                                LocalDateTime paidAt, LocalDateTime createdAt) {
        public static EncashmentDTO from(LeaveEncashment r) {
            var e = r.getEmployee();
            return new EncashmentDTO(r.getId(), e.getId(), e.getFullName(), e.getEmployeeCode(),
                    e.getDepartment() != null ? e.getDepartment().getName() : null, r.getLeaveType().getId(),
                    r.getLeaveType().getName(), r.getLeaveYear(), r.getDays(), r.getReason(), r.getStatus(), r.getBasis(),
                    r.getPerDayRate(), r.getAmount(), r.getDecidedBy() != null ? r.getDecidedBy().getFullName() : null,
                    r.getDecidedAt(), r.getDecisionNote(), r.getPayrollItemId(), r.getPaidAt(), r.getCreatedAt());
        }
    }

    /** An approved, not-yet-paid encashment for payroll to add as an earning (see LeaveEncashmentService). */
    public record EncashmentPayable(Long id, Long employeeId, Long leaveTypeId, String leaveTypeCode, String leaveTypeName,
                                    int leaveYear, double days, BigDecimal perDayRate, BigDecimal amount, String basis,
                                    LocalDateTime approvedAt) {
    }

    public record FinalSettlementLine(Long leaveTypeId, String leaveTypeName, String basis, double remainingDays,
                                      double encashableDays, BigDecimal perDayRate, BigDecimal amount) {
    }

    public record FinalSettlementPreview(Long employeeId, String employeeName, LocalDate asOf, LocalDate lastWorkingDate,
                                         boolean salaryStructureFound, List<FinalSettlementLine> lines,
                                         BigDecimal totalAmount, String note) {
    }
}
