package com.haodaone.billing.entity;

/** Why a Razorpay order/payment was created. Null on legacy rows = signup verification. */
public enum PaymentPurpose {
    SIGNUP_VERIFICATION,
    RENEWAL,
    AUTO_DEBIT
}
