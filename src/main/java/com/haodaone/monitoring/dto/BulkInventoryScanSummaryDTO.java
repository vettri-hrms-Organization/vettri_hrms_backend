package com.haodaone.monitoring.dto;

public class BulkInventoryScanSummaryDTO {
    private int totalDevices;
    private int queuedCount;
    private int scanningCount;
    private int completedCount;
    private int failedCount;
    private int offlineCount;
    private int neverScannedCount;

    public BulkInventoryScanSummaryDTO() {
    }

    public BulkInventoryScanSummaryDTO(int totalDevices, int queuedCount, int scanningCount, int completedCount,
                                     int failedCount, int offlineCount, int neverScannedCount) {
        this.totalDevices = totalDevices;
        this.queuedCount = queuedCount;
        this.scanningCount = scanningCount;
        this.completedCount = completedCount;
        this.failedCount = failedCount;
        this.offlineCount = offlineCount;
        this.neverScannedCount = neverScannedCount;
    }

    public int getTotalDevices() {
        return totalDevices;
    }

    public void setTotalDevices(int totalDevices) {
        this.totalDevices = totalDevices;
    }

    public int getQueuedCount() {
        return queuedCount;
    }

    public void setQueuedCount(int queuedCount) {
        this.queuedCount = queuedCount;
    }

    public int getScanningCount() {
        return scanningCount;
    }

    public void setScanningCount(int scanningCount) {
        this.scanningCount = scanningCount;
    }

    public int getCompletedCount() {
        return completedCount;
    }

    public void setCompletedCount(int completedCount) {
        this.completedCount = completedCount;
    }

    public int getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(int failedCount) {
        this.failedCount = failedCount;
    }

    public int getOfflineCount() {
        return offlineCount;
    }

    public void setOfflineCount(int offlineCount) {
        this.offlineCount = offlineCount;
    }

    public int getNeverScannedCount() {
        return neverScannedCount;
    }

    public void setNeverScannedCount(int neverScannedCount) {
        this.neverScannedCount = neverScannedCount;
    }
}
