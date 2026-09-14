package com.haodaone.employee.dto;

import jakarta.validation.constraints.NotBlank;

public class AccountStatusRequest {
    @NotBlank(message = "Account status is required")
    private String status;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}