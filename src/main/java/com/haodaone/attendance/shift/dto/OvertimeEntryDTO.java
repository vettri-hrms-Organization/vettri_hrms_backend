package com.haodaone.attendance.shift.dto;

import com.haodaone.attendance.shift.entity.OvertimeEntry;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record OvertimeEntryDTO(Long id, Long employeeId, String employeeCode, String employeeName,
                               String departmentName, LocalDate workDate, String shiftName,
                               int computedMinutes, Integer approvedMinutes, String status, String source,
                               String employeeNote, String reviewedByName, LocalDateTime reviewedAt,
                               String reviewNote, BigDecimal hourlyRate, BigDecimal rateMultiplier,
                               BigDecimal amount, Long payrollItemId, LocalDateTime paidAt) {

    public static OvertimeEntryDTO from(OvertimeEntry e) {
        return new OvertimeEntryDTO(e.getId(), e.getEmployee().getId(), e.getEmployee().getEmployeeCode(),
                e.getEmployee().getFullName(),
                e.getEmployee().getDepartment() == null ? null : e.getEmployee().getDepartment().getName(),
                e.getWorkDate(), e.getShiftName(), e.getComputedMinutes(), e.getApprovedMinutes(), e.getStatus(),
                e.getSource(), e.getEmployeeNote(),
                e.getReviewedByEmployee() == null ? null : e.getReviewedByEmployee().getFullName(),
                e.getReviewedAt(), e.getReviewNote(), e.getHourlyRate(), e.getRateMultiplier(), e.getAmount(),
                e.getPayrollItemId(), e.getPaidAt());
    }
}
