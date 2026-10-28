package com.haodaone.employee.dto;

import jakarta.validation.constraints.NotBlank;

public class UpdateStatusRequest {

    @NotBlank(message = "Status is required")
    private String status;

    private String reason;

    /** Optional - only used for Resigned / Terminated. Defaults to today (IST). */
    private java.time.LocalDate lastWorkingDate;

    public java.time.LocalDate getLastWorkingDate() {
        return lastWorkingDate;
    }

    public void setLastWorkingDate(java.time.LocalDate lastWorkingDate) {
        this.lastWorkingDate = lastWorkingDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
