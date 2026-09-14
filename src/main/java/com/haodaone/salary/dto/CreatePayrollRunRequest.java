package com.haodaone.salary.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** Body for POST /api/salary/payroll-runs. */
public class CreatePayrollRunRequest {

    @NotNull(message = "Period month is required")
    @Min(value = 1, message = "Period month must be between 1 and 12")
    @Max(value = 12, message = "Period month must be between 1 and 12")
    private Integer periodMonth;

    @NotNull(message = "Period year is required")
    @Min(value = 2000, message = "Period year looks invalid")
    private Integer periodYear;

    private LocalDate payDate;
    private String remarks;

    public Integer getPeriodMonth() {
        return periodMonth;
    }

    public void setPeriodMonth(Integer periodMonth) {
        this.periodMonth = periodMonth;
    }

    public Integer getPeriodYear() {
        return periodYear;
    }

    public void setPeriodYear(Integer periodYear) {
        this.periodYear = periodYear;
    }

    public LocalDate getPayDate() {
        return payDate;
    }

    public void setPayDate(LocalDate payDate) {
        this.payDate = payDate;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }
}
