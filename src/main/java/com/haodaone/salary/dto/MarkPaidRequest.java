package com.haodaone.salary.dto;

import java.time.LocalDate;

/** Body for POST /api/salary/payroll-runs/{id}/mark-paid. */
public class MarkPaidRequest {

    private LocalDate paymentDate;

    public LocalDate getPaymentDate() {
        return paymentDate;
    }

    public void setPaymentDate(LocalDate paymentDate) {
        this.paymentDate = paymentDate;
    }
}
