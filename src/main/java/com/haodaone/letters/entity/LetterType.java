package com.haodaone.letters.entity;

import java.util.List;

/** Letter kinds. Stored as plain strings on LetterTemplate / IssuedLetter. */
public final class LetterType {

    public static final String OFFER = "OFFER";
    public static final String APPOINTMENT = "APPOINTMENT";
    public static final String CONFIRMATION = "CONFIRMATION";
    public static final String SALARY_REVISION = "SALARY_REVISION";
    public static final String EXPERIENCE = "EXPERIENCE";
    public static final String RELIEVING = "RELIEVING";
    public static final String EMPLOYMENT_VERIFICATION = "EMPLOYMENT_VERIFICATION";
    public static final String CUSTOM = "CUSTOM";

    public static final List<String> ALL = List.of(OFFER, APPOINTMENT, CONFIRMATION, SALARY_REVISION,
            EXPERIENCE, RELIEVING, EMPLOYMENT_VERIFICATION, CUSTOM);

    private LetterType() {
    }
}
