package com.haodaone.monitoring.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class InventorySubmissionRequest {

    @NotBlank
    private String status;
    @Size(max = 80)
    private String errorCode;
    @Size(max = 300)
    private String errorMessage;
    @Valid
    private HardwareInventoryPayload inventory;

    @AssertTrue(message = "Completed scans require inventory and failed scans must not contain inventory")
    public boolean isSubmissionValid() {
        return "COMPLETED".equals(status) && inventory != null
                || "FAILED".equals(status) && inventory == null;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public HardwareInventoryPayload getInventory() { return inventory; }
    public void setInventory(HardwareInventoryPayload inventory) { this.inventory = inventory; }
}
