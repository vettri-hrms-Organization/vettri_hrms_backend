package com.haodaone.settlement.dto;

import com.haodaone.settlement.entity.FinalSettlement;
import com.haodaone.settlement.entity.FinalSettlementLine;
import com.haodaone.settlement.entity.SettlementSettings;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Request/response shapes for Full & Final settlement and gratuity. */
public final class SettlementDTOs {

    private SettlementDTOs() {
    }

    public record LineDTO(Long id, String kind, String code, String label, BigDecimal amount, BigDecimal taxableAmount,
                          boolean manual, String sourceType, Long sourceId) {
        public static LineDTO from(FinalSettlementLine l) {
            return new LineDTO(l.getId(), l.getKind(), l.getCode(), l.getLabel(), l.getAmount(), l.getTaxableAmount(),
                    l.isManual(), l.getSourceType(), l.getSourceId());
        }
    }

    public record SettlementDTO(Long id, Long employeeId, String employeeName, String employeeCode, String employeeStatus,
                                LocalDate dateOfJoining, String status, LocalDate resignationDate, LocalDate lastWorkingDate,
                                int noticePeriodDays, int noticeServedDays, int noticeShortfallDays, boolean noticeWaived,
                                String noticeRecoveryBasis, boolean deathOrDisablement,
                                int serviceYears, int serviceMonths, int serviceDays, int gratuityYears,
                                boolean gratuityEligible, BigDecimal gratuityWage,
                                BigDecimal totalEarnings, BigDecimal totalDeductions, BigDecimal taxableAmount,
                                BigDecimal tdsAmount, BigDecimal netPayable, String notes, String computationNotes,
                                String reviewedBy, LocalDateTime reviewedAt, String approvedBy, LocalDateTime approvedAt,
                                String paymentReference, LocalDate paymentDate, LocalDateTime paidAt,
                                LocalDateTime createdAt, List<LineDTO> lines) {
        public static SettlementDTO from(FinalSettlement s) {
            var e = s.getEmployee();
            return new SettlementDTO(s.getId(), e.getId(), e.getFullName(), e.getEmployeeCode(), e.getStatus(),
                    e.getDateOfJoining(), s.getStatus(), s.getResignationDate(), s.getLastWorkingDate(),
                    s.getNoticePeriodDays(), s.getNoticeServedDays(), s.getNoticeShortfallDays(), s.isNoticeWaived(),
                    s.getNoticeRecoveryBasis(), s.isDeathOrDisablement(),
                    s.getServiceYears(), s.getServiceMonths(), s.getServiceDays(), s.getGratuityYears(),
                    s.isGratuityEligible(), s.getGratuityWage(),
                    s.getTotalEarnings(), s.getTotalDeductions(), s.getTaxableAmount(), s.getTdsAmount(), s.getNetPayable(),
                    s.getNotes(), s.getComputationNotes(), s.getReviewedBy(), s.getReviewedAt(), s.getApprovedBy(),
                    s.getApprovedAt(), s.getPaymentReference(), s.getPaymentDate(), s.getPaidAt(), s.getCreatedAt(),
                    s.getLines().stream().filter(l -> !l.isDeleted()).map(LineDTO::from).toList());
        }
    }

    /** A manual line HR adds: EARNING (BONUS, OTHER_EARNING) or DEDUCTION (ADVANCE, LOAN, ASSET_RECOVERY, OTHER_DEDUCTION). */
    public record ManualLine(String kind, String code, String label, BigDecimal amount, Boolean taxable) {
    }

    /**
     * Create / update a DRAFT. Null fields keep their current (or default) value on update.
     * {@code manualLines}, when not null, replaces every manual line.
     */
    public record SettlementRequest(Long employeeId, LocalDate resignationDate, LocalDate lastWorkingDate,
                                    Integer noticePeriodDays, Boolean noticeWaived, Integer noticeShortfallDaysOverride,
                                    String noticeRecoveryBasis, Boolean deathOrDisablement, String notes,
                                    List<ManualLine> manualLines) {
    }

    public record MarkPaidRequest(String paymentReference, LocalDate paymentDate) {
    }

    public record SettingsDTO(boolean gratuityApplicable, boolean gratuityCoveredUnderAct, BigDecimal gratuityCeiling,
                              int gratuityPartialYearDays, int defaultNoticePeriodDays, String noticeRecoveryBasis) {
        public static SettingsDTO from(SettlementSettings s) {
            return new SettingsDTO(s.isGratuityApplicable(), s.isGratuityCoveredUnderAct(), s.getGratuityCeiling(),
                    s.getGratuityPartialYearDays(), s.getDefaultNoticePeriodDays(), s.getNoticeRecoveryBasis());
        }
    }

    public record GratuityEstimateDTO(Long employeeId, String employeeName, LocalDate dateOfJoining, LocalDate asOf,
                                      boolean applicable, boolean coveredUnderAct, boolean eligible,
                                      int serviceYears, int serviceMonths, int serviceDays, int yearsCounted,
                                      BigDecimal monthlyWage, BigDecimal amount, boolean capped, boolean salaryFound,
                                      String explanation) {
    }

    public record EligibleEmployeeDTO(Long employeeId, String employeeName, String employeeCode, String status,
                                      LocalDate dateOfJoining, LocalDate lastWorkingDate) {
    }
}
