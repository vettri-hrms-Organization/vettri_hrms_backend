package com.haodaone.monitoring.report.dto;

/** One row of the "Application Usage Breakdown" - total time spent in a given application within the report's scope. */
public class AppUsageDTO {

    private String applicationName;
    private String windowTitle;
    private long seconds;
    private boolean idle;

    public AppUsageDTO() {
    }

    public AppUsageDTO(String applicationName, String windowTitle, long seconds, boolean idle) {
        this.applicationName = applicationName;
        this.windowTitle = windowTitle;
        this.seconds = seconds;
        this.idle = idle;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public String getWindowTitle() {
        return windowTitle;
    }

    public void setWindowTitle(String windowTitle) {
        this.windowTitle = windowTitle;
    }

    public long getSeconds() {
        return seconds;
    }

    public void setSeconds(long seconds) {
        this.seconds = seconds;
    }

    public boolean isIdle() {
        return idle;
    }

    public void setIdle(boolean idle) {
        this.idle = idle;
    }
}
