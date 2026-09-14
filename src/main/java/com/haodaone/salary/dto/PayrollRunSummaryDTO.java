package com.haodaone.salary.dto;

import com.haodaone.salary.entity.PayrollRun;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Lightweight shape for the Payroll Processing run list - PayrollRunDTO carries the full item breakdown. */
public class PayrollRunSummaryDTO {

    private Long id;
    private int periodMonth;
    private int periodYear;
    private String periodLabel;
    private String status;
    private LocalDate payDate;
    private LocalDateTime processedAt;
    private int totalEmployees;
    private BigDecimal totalGross;
    private BigDecimal totalDeductions;
    private BigDecimal totalNet;

    private static final String[] MONTH_NAMES = {
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
    };

    public static PayrollRunSummaryDTO from(PayrollRun r) {
        PayrollRunSummaryDTO dto = new PayrollRunSummaryDTO();
        dto.id = r.getId();
        dto.periodMonth = r.getPeriodMonth();
        dto.periodYear = r.getPeriodYear();
        dto.periodLabel = MONTH_NAMES[r.getPeriodMonth() - 1] + " " + r.getPeriodYear();
        dto.status = r.getStatus();
        dto.payDate = r.getPayDate();
        dto.processedAt = r.getProcessedAt();
        dto.totalEmployees = r.getTotalEmployees();
        dto.totalGross = r.getTotalGross();
        dto.totalDeductions = r.getTotalDeductions();
        dto.totalNet = r.getTotalNet();
        return dto;
    }

    public Long getId() {
        return id;
    }

    public int getPeriodMonth() {
        return periodMonth;
    }

    public int getPeriodYear() {
        return periodYear;
    }

    public String getPeriodLabel() {
        return periodLabel;
    }

    public String getStatus() {
        return status;
    }

    public LocalDate getPayDate() {
        return payDate;
    }

    public LocalDateTime getProcessedAt() {
        return processedAt;
    }

    public int getTotalEmployees() {
        return totalEmployees;
    }

    public BigDecimal getTotalGross() {
        return totalGross;
    }

    public BigDecimal getTotalDeductions() {
        return totalDeductions;
    }

    public BigDecimal getTotalNet() {
        return totalNet;
    }
}
