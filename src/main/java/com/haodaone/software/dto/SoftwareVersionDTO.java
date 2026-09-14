package com.haodaone.software.dto;

import com.haodaone.software.entity.SoftwareInstallerType;
import com.haodaone.software.entity.SoftwareVersion;

public class SoftwareVersionDTO {
    private Long id;
    private Long softwarePackageId;
    private String version;
    private String architecture;
    private SoftwareInstallerType installerType;
    private String installerUrl;
    private String packageStorageKey;
    private String checksumSha256;
    private Long fileSizeBytes;
    private String silentInstallArguments;
    private String detectionRule;
    private boolean active;

    public static class CreateRequest {
        private String version;
        private String architecture;
        private SoftwareInstallerType installerType;
        private String installerUrl;
        private String packageStorageKey;
        private String checksumSha256;
        private Long fileSizeBytes;
        private String silentInstallArguments;
        private String detectionRule;
        private boolean active = true;

        public String getVersion() { return version; }
        public void setVersion(String version) { this.version = version; }
        public String getArchitecture() { return architecture; }
        public void setArchitecture(String architecture) { this.architecture = architecture; }
        public SoftwareInstallerType getInstallerType() { return installerType; }
        public void setInstallerType(SoftwareInstallerType installerType) { this.installerType = installerType; }
        public String getInstallerUrl() { return installerUrl; }
        public void setInstallerUrl(String installerUrl) { this.installerUrl = installerUrl; }
        public String getPackageStorageKey() { return packageStorageKey; }
        public void setPackageStorageKey(String packageStorageKey) { this.packageStorageKey = packageStorageKey; }
        public String getChecksumSha256() { return checksumSha256; }
        public void setChecksumSha256(String checksumSha256) { this.checksumSha256 = checksumSha256; }
        public Long getFileSizeBytes() { return fileSizeBytes; }
        public void setFileSizeBytes(Long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }
        public String getSilentInstallArguments() { return silentInstallArguments; }
        public void setSilentInstallArguments(String silentInstallArguments) { this.silentInstallArguments = silentInstallArguments; }
        public String getDetectionRule() { return detectionRule; }
        public void setDetectionRule(String detectionRule) { this.detectionRule = detectionRule; }
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
    }

    public static SoftwareVersionDTO from(SoftwareVersion entity) {
        if (entity == null) return null;
        SoftwareVersionDTO dto = new SoftwareVersionDTO();
        dto.setId(entity.getId());
        dto.setSoftwarePackageId(entity.getSoftwarePackage() != null ? entity.getSoftwarePackage().getId() : null);
        dto.setVersion(entity.getPackageVersion());
        dto.setArchitecture(entity.getArchitecture());
        dto.setInstallerType(entity.getInstallerType());
        dto.setInstallerUrl(entity.getInstallerUrl());
        dto.setPackageStorageKey(entity.getPackageStorageKey());
        dto.setChecksumSha256(entity.getChecksumSha256());
        dto.setFileSizeBytes(entity.getFileSizeBytes());
        dto.setSilentInstallArguments(entity.getSilentInstallArguments());
        dto.setDetectionRule(entity.getDetectionRule());
        dto.setActive(entity.isActive());
        return dto;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSoftwarePackageId() { return softwarePackageId; }
    public void setSoftwarePackageId(Long softwarePackageId) { this.softwarePackageId = softwarePackageId; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getArchitecture() { return architecture; }
    public void setArchitecture(String architecture) { this.architecture = architecture; }
    public SoftwareInstallerType getInstallerType() { return installerType; }
    public void setInstallerType(SoftwareInstallerType installerType) { this.installerType = installerType; }
    public String getInstallerUrl() { return installerUrl; }
    public void setInstallerUrl(String installerUrl) { this.installerUrl = installerUrl; }
    public String getPackageStorageKey() { return packageStorageKey; }
    public void setPackageStorageKey(String packageStorageKey) { this.packageStorageKey = packageStorageKey; }
    public String getChecksumSha256() { return checksumSha256; }
    public void setChecksumSha256(String checksumSha256) { this.checksumSha256 = checksumSha256; }
    public Long getFileSizeBytes() { return fileSizeBytes; }
    public void setFileSizeBytes(Long fileSizeBytes) { this.fileSizeBytes = fileSizeBytes; }
    public String getSilentInstallArguments() { return silentInstallArguments; }
    public void setSilentInstallArguments(String silentInstallArguments) { this.silentInstallArguments = silentInstallArguments; }
    public String getDetectionRule() { return detectionRule; }
    public void setDetectionRule(String detectionRule) { this.detectionRule = detectionRule; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
