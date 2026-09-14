package com.haodaone.software.entity;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "software_version", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"software_package_id", "package_version"})
})
public class SoftwareVersion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "software_package_id", nullable = false)
    private SoftwarePackage softwarePackage;

    @Column(name = "package_version", nullable = false, length = 50)
    private String packageVersion;

    @Column(name = "architecture", length = 20)
    private String architecture = "x64";

    @Enumerated(EnumType.STRING)
    @Column(name = "installer_type", nullable = false, length = 20)
    private SoftwareInstallerType installerType = SoftwareInstallerType.EXE;

    @Column(name = "installer_url", length = 2048)
    private String installerUrl;

    @Column(name = "package_storage_key", length = 500)
    private String packageStorageKey;

    @Column(name = "checksum_sha256", length = 128)
    private String checksumSha256;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "silent_install_arguments", length = 1000)
    private String silentInstallArguments;

    @Column(name = "detection_rule", length = 1000)
    private String detectionRule;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public SoftwarePackage getSoftwarePackage() {
        return softwarePackage;
    }

    public void setSoftwarePackage(SoftwarePackage softwarePackage) {
        this.softwarePackage = softwarePackage;
    }

    public String getPackageVersion() {
        return packageVersion;
    }

    public void setPackageVersion(String packageVersion) {
        this.packageVersion = packageVersion;
    }

    public String getArchitecture() {
        return architecture;
    }

    public void setArchitecture(String architecture) {
        this.architecture = architecture;
    }

    public SoftwareInstallerType getInstallerType() {
        return installerType;
    }

    public void setInstallerType(SoftwareInstallerType installerType) {
        this.installerType = installerType;
    }

    public String getInstallerUrl() {
        return installerUrl;
    }

    public void setInstallerUrl(String installerUrl) {
        this.installerUrl = installerUrl;
    }

    public String getPackageStorageKey() {
        return packageStorageKey;
    }

    public void setPackageStorageKey(String packageStorageKey) {
        this.packageStorageKey = packageStorageKey;
    }

    public String getChecksumSha256() {
        return checksumSha256;
    }

    public void setChecksumSha256(String checksumSha256) {
        this.checksumSha256 = checksumSha256;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(Long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public String getSilentInstallArguments() {
        return silentInstallArguments;
    }

    public void setSilentInstallArguments(String silentInstallArguments) {
        this.silentInstallArguments = silentInstallArguments;
    }

    public String getDetectionRule() {
        return detectionRule;
    }

    public void setDetectionRule(String detectionRule) {
        this.detectionRule = detectionRule;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
