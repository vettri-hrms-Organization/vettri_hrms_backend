package com.haodaone.onboarding.entity;

import java.util.Set;

/** String vocabularies for the checklist engine (stored as plain strings). */
public final class ChecklistConstants {

    public static final String TYPE_ONBOARDING = "ONBOARDING";
    public static final String TYPE_EXIT = "EXIT";
    public static final Set<String> TYPES = Set.of(TYPE_ONBOARDING, TYPE_EXIT);

    public static final String OWNER_NEW_HIRE = "NEW_HIRE";
    public static final String OWNER_HR = "HR";
    public static final String OWNER_MANAGER = "MANAGER";
    public static final String OWNER_IT = "IT";
    public static final String OWNER_USER = "USER";
    public static final Set<String> OWNER_TYPES = Set.of(OWNER_NEW_HIRE, OWNER_HR, OWNER_MANAGER, OWNER_IT, OWNER_USER);

    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    public static final String TASK_PENDING = "PENDING";
    public static final String TASK_DONE = "DONE";
    public static final String TASK_SKIPPED = "SKIPPED";

    /** Role name whose users own IT tasks when the template names no specific user. */
    public static final String IT_ROLE = "IT_ADMINISTRATOR";

    private ChecklistConstants() {
    }
}
