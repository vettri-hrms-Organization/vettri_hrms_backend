package com.haodaone.auth.entity;

/**
 * OFF - 2FA disabled for the company (no enrolment, no login challenge).
 * OPTIONAL - users may enrol; enrolled users are challenged at login (default).
 * REQUIRED_ADMINS - users holding any *_MANAGE permission or an admin role must use 2FA.
 * REQUIRED_ALL - everyone must use 2FA.
 */
public enum TwoFactorPolicy {
    OFF, OPTIONAL, REQUIRED_ADMINS, REQUIRED_ALL
}
