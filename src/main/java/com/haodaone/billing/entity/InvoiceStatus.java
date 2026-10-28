package com.haodaone.billing.entity;

public enum InvoiceStatus {
    PAID,
    PENDING,
    FAILED,
    REFUNDED,
    /** Credit note issued (negative document referencing the original invoice). */
    ISSUED,
    /** Pending renewal invoice that was voided (e.g. subscription cancelled). */
    CANCELLED
}
