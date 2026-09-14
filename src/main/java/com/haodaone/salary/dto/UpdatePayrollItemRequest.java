package com.haodaone.salary.dto;

/** Body for PATCH /api/salary/payroll-runs/{runId}/items/{itemId}/hold. */
public class UpdatePayrollItemRequest {

    private boolean onHold;
    private String remarks;

    public boolean isOnHold() {
        return onHold;
    }

    public void setOnHold(boolean onHold) {
        this.onHold = onHold;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }
}
