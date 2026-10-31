package com.haodaone.reports.builder;

/** Test access to package-private helpers of {@link ReportBuilderService}. */
public final class ReportBuilderServiceAccess {
    private ReportBuilderServiceAccess() {
    }

    public static String cell(String value) {
        return ReportBuilderService.cell(value);
    }
}
