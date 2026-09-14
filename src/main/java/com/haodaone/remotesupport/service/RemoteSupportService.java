package com.haodaone.remotesupport.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.monitoring.entity.MonitoredDevice;
import com.haodaone.monitoring.repository.MonitoredDeviceRepository;
import com.haodaone.remotesupport.dto.RemoteSupportDTO;
import com.haodaone.remotesupport.entity.*;
import com.haodaone.remotesupport.repository.RemoteSupportJobRepository;
import com.haodaone.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class RemoteSupportService {
    private final RemoteSupportJobRepository jobs;
    private final MonitoredDeviceRepository devices;
    private final RemoteSupportCredentialService credentials;
    private final AuditLogService audit;
    private final RustDeskConfiguration rustDesk;
    public RemoteSupportService(RemoteSupportJobRepository jobs, MonitoredDeviceRepository devices, RemoteSupportCredentialService credentials, AuditLogService audit, RustDeskConfiguration rustDesk) { this.jobs=jobs; this.devices=devices; this.credentials=credentials; this.audit=audit; this.rustDesk=rustDesk; }
    @Transactional public RemoteSupportDTO.Response request(Long deviceId, RemoteSupportOperation operation, Long userId) {
        Long companyId=tenant(); MonitoredDevice device=devices.findByIdAndCompany_IdAndDeletedFalse(deviceId,companyId).orElseThrow(() -> new ResourceNotFoundException("Device not found in current company: "+deviceId));
        if (!device.isOnline()) throw new BadRequestException("The selected device is offline");
        if (operation != RemoteSupportOperation.DETECT && credentials.isConfigured()) {
            credentials.store(device.getCompany(), deviceId, credentials.generate(), RemoteSupportStatus.QUEUED);
        }
        RemoteSupportJob job=new RemoteSupportJob(); job.setCompany(device.getCompany()); job.setDevice(device); job.setRequestedBy(userId); job.setOperation(operation); job.setStatus(RemoteSupportStatus.JOB_CREATED); job.setCorrelationId(UUID.randomUUID().toString()); RemoteSupportJob saved=jobs.save(job);
        audit.log("RemoteSupportJob",saved.getId(),"REQUEST","Remote support "+operation+" requested for device '"+device.getDeviceName()+"'"); return RemoteSupportDTO.Response.from(saved, null);
    }
    @Transactional(readOnly=true) public List<RemoteSupportDTO.Response> list(Long deviceId){ ensure(deviceId); return jobs.findByCompany_IdAndDevice_IdAndDeletedFalseOrderByCreatedAtDesc(tenant(),deviceId).stream().map(job -> RemoteSupportDTO.Response.from(job, decryptPasswordForJob(job))).toList(); }
    @Transactional(readOnly=true) public RemoteSupportDTO.Response get(Long deviceId,Long jobId){ RemoteSupportJob job=find(jobId,deviceId); return RemoteSupportDTO.Response.from(job, decryptPasswordForJob(job)); }
    @Transactional(readOnly=true) public List<RemoteSupportDTO.AgentJob> agentJobs(MonitoredDevice device){ if(device==null||device.getId()==null)return List.of(); return jobs.findByDevice_IdAndStatusInAndDeletedFalseOrderByCreatedAtAsc(device.getId(), List.of(RemoteSupportStatus.QUEUED, RemoteSupportStatus.JOB_CREATED)).stream().map(job -> {
        String password = null;
        if ((job.getOperation()==RemoteSupportOperation.CONFIGURE || job.getOperation()==RemoteSupportOperation.ROTATE) && credentials.isConfigured()) {
            password = credentials.decryptForAgent(device.getId());
        }
        return new RemoteSupportDTO.AgentJob(job.getId(), job.getOperation().name(), job.getCorrelationId(), password, rustDesk.approvedConfigString(), rustDesk.installerPath(), rustDesk.installerSha256());
    }).toList(); }
    @Transactional public void result(MonitoredDevice device,Long id,RemoteSupportDTO.AgentResult result){ RemoteSupportJob job=jobs.findByIdAndDevice_IdAndDeletedFalse(id,device.getId()).orElseThrow(()->new ResourceNotFoundException("Remote support job not found")); if(!Objects.equals(job.getCompany().getId(),device.getCompany().getId()))throw new ResourceNotFoundException("Remote support job not found");
        RemoteSupportStatus status;
        String statusText = result == null ? "" : (result.provisioningState() != null && !result.provisioningState().isBlank()
                ? result.provisioningState()
                : result.status() == null ? "" : result.status());
        statusText = statusText == null ? "" : statusText.trim();
        try {
            status = RemoteSupportStatus.valueOf(statusText.isBlank() ? "FAILED" : statusText.toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            status = RemoteSupportStatus.FAILED;
        }
        RemoteSupportStatus previousStatus = job.getStatus();
        boolean previousTerminal = previousStatus == RemoteSupportStatus.READY || previousStatus == RemoteSupportStatus.FAILED;
        if (previousTerminal && status != previousStatus) {
            audit.log("RemoteSupportJob", id, "STALE_RESULT_IGNORED", "Ignored terminal state transition from " + previousStatus + " to " + status);
            return;
        }

        job.setStatus(status);
        job.setUltraViewerVersion(limit(result.version(),100));
        job.setUltraViewerId(limit(result.ultraViewerId()!=null?result.ultraViewerId():result.rustDeskId(),100));
        job.setExecutablePath(limit(result.executablePath(),500));
        job.setRunning(result.running());
        job.setUnattendedEnabled(result.unattendedEnabled());
        job.setErrorCode(status == RemoteSupportStatus.READY ? null : limit(result.errorCode(),80));
        job.setErrorMessage(status == RemoteSupportStatus.READY ? null : limit(result.errorMessage(),2000));
        if(job.getStartedAt()==null)job.setStartedAt(LocalDateTime.now());
        if (status == RemoteSupportStatus.READY || status == RemoteSupportStatus.FAILED) {
            job.setCompletedAt(LocalDateTime.now());
        } else {
            job.setCompletedAt(null);
        }
        jobs.save(job);
        audit.log("RemoteSupportJob",id,"RESULT","Remote support result status="+status);
    }
    @Transactional public RemoteSupportDTO.Response disable(Long deviceId,Long userId){ return request(deviceId,RemoteSupportOperation.DISABLE,userId); }
    private RemoteSupportJob find(Long id,Long deviceId){return jobs.findByIdAndCompany_IdAndDevice_IdAndDeletedFalse(id,tenant(),deviceId).orElseThrow(()->new ResourceNotFoundException("Remote support job not found"));}
    private void ensure(Long id){devices.findByIdAndCompany_IdAndDeletedFalse(id,tenant()).orElseThrow(()->new ResourceNotFoundException("Device not found"));}
    private String decryptPasswordForJob(RemoteSupportJob job){
        if (job == null || job.getDevice() == null || job.getDevice().getId() == null) return null;
        if ((job.getOperation() == RemoteSupportOperation.CONFIGURE || job.getOperation() == RemoteSupportOperation.ROTATE) && credentials.isConfigured()) {
            try {
                return credentials.decryptForAgent(job.getDevice().getId());
            } catch (Exception ex) {
                return null;
            }
        }
        return null;
    }
    private Long tenant(){Long id=TenantContext.getCurrentTenant();if(id==null)throw new BadRequestException("Company context is required");return id;}
    private String limit(String v,int max){return v==null?null:v.length()<=max?v:v.substring(0,max);}
}