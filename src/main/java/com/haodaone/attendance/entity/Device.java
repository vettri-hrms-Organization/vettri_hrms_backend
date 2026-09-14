package com.haodaone.attendance.entity;

import com.haodaone.common.entity.BaseEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A physical eSSL/ZKTeco-style biometric device talking to us over ADMS.
 * Self-registers the first time it calls /iclock/cdata with its serial
 * number - no pre-configuration needed. See attendance.controller.
 * AdmsController for the protocol details (this is the same pattern
 * proven in the standalone attendance POC, now backed by a real entity
 * with audit history instead of a flat table).
 */
@Entity
@Table(name = "biometric_device", uniqueConstraints = @UniqueConstraint(columnNames = "serial_number"))
public class Device extends BaseEntity {

    @Column(name = "serial_number", nullable = false, unique = true, length = 50)
    private String serialNumber;

    @Column(name = "device_name", nullable = false, length = 100)
    private String deviceName;

    @Column(name = "last_ip_address", length = 50)
    private String lastIpAddress;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    @Column(name = "push_version", length = 50)
    private String pushVersion;

    /** Tenant/company this biometric device belongs to (nullable for legacy rows until migration is applied). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private com.haodaone.company.entity.Company company;

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getLastIpAddress() {
        return lastIpAddress;
    }

    public void setLastIpAddress(String lastIpAddress) {
        this.lastIpAddress = lastIpAddress;
    }

    public LocalDateTime getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(LocalDateTime lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public String getPushVersion() {
        return pushVersion;
    }

    public void setPushVersion(String pushVersion) {
        this.pushVersion = pushVersion;
    }

    public com.haodaone.company.entity.Company getCompany() {
        return company;
    }

    public void setCompany(com.haodaone.company.entity.Company company) {
        this.company = company;
    }

    /** A device that hasn't checked in for 10+ minutes is shown as offline in the Device Dashboard. */
    public boolean isOnline() {
        return lastSeenAt != null && lastSeenAt.isAfter(LocalDateTime.now().minusMinutes(10));
    }
}
