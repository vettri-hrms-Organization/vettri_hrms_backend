package com.haodaone.billing.gst;

import java.time.LocalDate;

/** Indian financial year (1 April - 31 March), formatted "2026-27". */
public final class FinancialYear {

    private FinancialYear() {
    }

    public static String of(LocalDate date) {
        int startYear = date.getMonthValue() >= 4 ? date.getYear() : date.getYear() - 1;
        return startYear + "-" + String.format("%02d", (startYear + 1) % 100);
    }
}
