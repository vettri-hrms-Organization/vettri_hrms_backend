package com.haodaone.software.dto;

public class AgentSoftwareJobDTO {
    private Long targetId;
    private Long deploymentId;
    private String packageName;
    private String version;
    private String installerType;
    private String installerUrl;
    private String checksumSha256;
    private String silentInstallArguments;
    private String detectionRule;

    public static AgentSoftwareJobDTO from(com.haodaone.software.entity.SoftwareDeploymentTarget target) {
        var version = target.getDeployment().getSoftwareVersion();
        var packageEntity = version.getSoftwarePackage();
        AgentSoftwareJobDTO dto = new AgentSoftwareJobDTO();
        dto.setTargetId(target.getId());
        dto.setDeploymentId(target.getDeployment().getId());
        dto.setPackageName(packageEntity.getName());
        dto.setVersion(version.getPackageVersion());
        dto.setInstallerType(version.getInstallerType().name());
        dto.setInstallerUrl(version.getInstallerUrl());
        dto.setChecksumSha256(version.getChecksumSha256());
        dto.setSilentInstallArguments(version.getSilentInstallArguments());
        dto.setDetectionRule(version.getDetectionRule());
        return dto;
    }

    public Long getTargetId() { return targetId; }
    public void setTargetId(Long targetId) { this.targetId = targetId; }
    public Long getDeploymentId() { return deploymentId; }
    public void setDeploymentId(Long deploymentId) { this.deploymentId = deploymentId; }
    public String getPackageName() { return packageName; }
    public void setPackageName(String packageName) { this.packageName = packageName; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getInstallerType() { return installerType; }
    public void setInstallerType(String installerType) { this.installerType = installerType; }
    public String getInstallerUrl() { return installerUrl; }
    public void setInstallerUrl(String installerUrl) { this.installerUrl = installerUrl; }
    public String getChecksumSha256() { return checksumSha256; }
    public void setChecksumSha256(String checksumSha256) { this.checksumSha256 = checksumSha256; }
    public String getSilentInstallArguments() { return silentInstallArguments; }
    public void setSilentInstallArguments(String silentInstallArguments) { this.silentInstallArguments = silentInstallArguments; }
    public String getDetectionRule() { return detectionRule; }
    public void setDetectionRule(String detectionRule) { this.detectionRule = detectionRule; }
}
