package com.haodaone.monitoring.report.repository;

import java.time.LocalDate;

public interface ApplicationUsageProjection {
    Long getEmployeeId();
    Long getDeviceId();
    LocalDate getUsageDate();
    String getApplicationName();
    String getWindowTitle();
    Long getSeconds();
    Boolean getIdle();
}