package com.haodaone.company.entity;

public enum SubscriptionStatus {
    TRIALING,
    TRIAL,
    PENDING_PAYMENT,
    ACTIVE,
    PAST_DUE,
    SUSPENDED,
    /** Trial ended without payment: read-only until the first invoice is paid. */
    EXPIRED,
    CANCELLED
}
