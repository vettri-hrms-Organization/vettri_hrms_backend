package com.haodaone.remotesupport.entity;

import com.haodaone.common.entity.BaseEntity;
import com.haodaone.company.entity.Company;
import com.haodaone.monitoring.entity.MonitoredDevice;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "remote_support_job")
public class RemoteSupportJob extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "company_id", nullable = false) private Company company;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "device_id", nullable = false) private MonitoredDevice device;
    @Column(name = "requested_by") private Long requestedBy;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private RemoteSupportOperation operation;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private RemoteSupportStatus status = RemoteSupportStatus.QUEUED;
    @Column(name = "ultra_viewer_version", length = 100) private String ultraViewerVersion;
    @Column(name = "ultra_viewer_id", length = 100) private String ultraViewerId;
    @Column(name = "executable_path", length = 500) private String executablePath;
    private Boolean running;
    @Column(name = "unattended_enabled") private Boolean unattendedEnabled;
    @Column(name = "error_code", length = 80) private String errorCode;
    @Column(name = "error_message", length = 2000) private String errorMessage;
    @Column(name = "correlation_id", nullable = false, unique = true, length = 80) private String correlationId;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    public Company getCompany(){return company;} public void setCompany(Company v){company=v;}
    public MonitoredDevice getDevice(){return device;} public void setDevice(MonitoredDevice v){device=v;}
    public Long getRequestedBy(){return requestedBy;} public void setRequestedBy(Long v){requestedBy=v;}
    public RemoteSupportOperation getOperation(){return operation;} public void setOperation(RemoteSupportOperation v){operation=v;}
    public RemoteSupportStatus getStatus(){return status;} public void setStatus(RemoteSupportStatus v){status=v;}
    public String getUltraViewerVersion(){return ultraViewerVersion;} public void setUltraViewerVersion(String v){ultraViewerVersion=v;}
    public String getUltraViewerId(){return ultraViewerId;} public void setUltraViewerId(String v){ultraViewerId=v;}
    public String getExecutablePath(){return executablePath;} public void setExecutablePath(String v){executablePath=v;}
    public Boolean getRunning(){return running;} public void setRunning(Boolean v){running=v;}
    public Boolean getUnattendedEnabled(){return unattendedEnabled;} public void setUnattendedEnabled(Boolean v){unattendedEnabled=v;}
    public String getErrorCode(){return errorCode;} public void setErrorCode(String v){errorCode=v;}
    public String getErrorMessage(){return errorMessage;} public void setErrorMessage(String v){errorMessage=v;}
    public String getCorrelationId(){return correlationId;} public void setCorrelationId(String v){correlationId=v;}
    public LocalDateTime getStartedAt(){return startedAt;} public void setStartedAt(LocalDateTime v){startedAt=v;}
    public LocalDateTime getCompletedAt(){return completedAt;} public void setCompletedAt(LocalDateTime v){completedAt=v;}
}