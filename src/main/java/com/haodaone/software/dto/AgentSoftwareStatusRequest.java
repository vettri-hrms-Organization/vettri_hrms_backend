package com.haodaone.software.dto;

public class AgentSoftwareStatusRequest {
    private String status;
    private String errorMessage;
    private String errorCode;
    private String installedVersion;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getInstalledVersion() { return installedVersion; }
    public void setInstalledVersion(String installedVersion) { this.installedVersion = installedVersion; }
}
