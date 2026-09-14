package com.haodaone.remotecommand.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.monitoring.entity.MonitoredDevice;
import com.haodaone.monitoring.repository.MonitoredDeviceRepository;
import com.haodaone.remotecommand.dto.RemoteCommandDTO;
import com.haodaone.remotecommand.entity.*;
import com.haodaone.remotecommand.repository.RemoteCommandJobRepository;
import com.haodaone.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class RemoteCommandService {
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final Set<String> SHELLS = Set.of("POWERSHELL", "CMD");
    private final RemoteCommandJobRepository jobRepository;
    private final MonitoredDeviceRepository deviceRepository;
    private final AuditLogService auditLogService;

    public RemoteCommandService(RemoteCommandJobRepository jobRepository, MonitoredDeviceRepository deviceRepository, AuditLogService auditLogService) {
        this.jobRepository = jobRepository;
        this.deviceRepository = deviceRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public RemoteCommandDTO.Response create(Long deviceId, RemoteCommandDTO.CreateRequest request, Long requestedBy) {
        Long companyId = tenant();
        MonitoredDevice device = deviceRepository.findByIdAndCompany_IdAndDeletedFalse(deviceId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found in current company: " + deviceId));
        String shell = request.shellType().trim().toUpperCase(Locale.ROOT);
        if (!SHELLS.contains(shell)) throw new BadRequestException("Shell type must be PowerShell or CMD");
        RemoteCommandJob job = new RemoteCommandJob();
        job.setCompany(device.getCompany());
        job.setDevice(device);
        job.setRequestedBy(requestedBy);
        job.setCommand(request.command().trim());
        job.setShellType(shell);
        job.setTimeoutSeconds(DEFAULT_TIMEOUT_SECONDS);
        job.setCorrelationId(UUID.randomUUID().toString());
        RemoteCommandJob saved = jobRepository.save(job);
        auditLogService.log("RemoteCommandJob", saved.getId(), "CREATE", "Queued remote command for device '" + device.getDeviceName() + "' correlationId=" + saved.getCorrelationId());
        return RemoteCommandDTO.Response.from(saved);
    }

    @Transactional(readOnly = true)
    public List<RemoteCommandDTO.Response> list(Long deviceId) {
        Long companyId = tenant();
        ensureDevice(deviceId, companyId);
        return jobRepository.findByCompany_IdAndDevice_IdAndDeletedFalseOrderByCreatedAtDesc(companyId, deviceId).stream().map(RemoteCommandDTO.Response::from).toList();
    }

    @Transactional(readOnly = true)
    public RemoteCommandDTO.Response get(Long deviceId, Long id) {
        RemoteCommandJob job = jobRepository.findByIdAndCompany_IdAndDevice_IdAndDeletedFalse(id, tenant(), deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Remote command not found: " + id));
        return RemoteCommandDTO.Response.from(job);
    }

    @Transactional
    public RemoteCommandDTO.Response cancel(Long deviceId, Long id) {
        RemoteCommandJob job = jobRepository.findByIdAndCompany_IdAndDevice_IdAndDeletedFalse(id, tenant(), deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Remote command not found: " + id));
        if (job.getStatus() == RemoteCommandStatus.QUEUED || job.getStatus() == RemoteCommandStatus.RUNNING) {
            job.setStatus(RemoteCommandStatus.CANCELLED);
            job.setCompletedAt(LocalDateTime.now());
            jobRepository.save(job);
            auditLogService.log("RemoteCommandJob", id, "CANCEL", "Cancelled remote command correlationId=" + job.getCorrelationId());
        }
        return RemoteCommandDTO.Response.from(job);
    }

    @Transactional(readOnly = true)
    public List<RemoteCommandDTO.AgentJob> agentJobs(MonitoredDevice device) {
        if (device == null || device.getId() == null) return List.of();
        return jobRepository.findByDevice_IdAndStatusAndDeletedFalseOrderByCreatedAtAsc(device.getId(), RemoteCommandStatus.QUEUED)
                .stream().map(RemoteCommandDTO.AgentJob::from).toList();
    }

    @Transactional(readOnly = true)
    public List<Long> cancelledAgentJobs(MonitoredDevice device) {
        if (device == null || device.getId() == null) return List.of();
        return jobRepository.findByDevice_IdAndStatusAndDeletedFalseOrderByCreatedAtAsc(device.getId(), RemoteCommandStatus.CANCELLED)
                .stream().map(RemoteCommandJob::getId).toList();
    }

    @Transactional
    public void updateAgentJob(MonitoredDevice device, Long id, RemoteCommandDTO.AgentResult result) {
        RemoteCommandJob job = jobRepository.findByIdAndDevice_IdAndDeletedFalse(id, device.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Remote command not found: " + id));
        if (!Objects.equals(job.getDevice().getCompany().getId(), device.getCompany().getId())) throw new ResourceNotFoundException("Remote command not found: " + id);
        if (job.getStatus() == RemoteCommandStatus.CANCELLED) return;
        RemoteCommandStatus status;
        try { status = RemoteCommandStatus.valueOf(result.status().trim().toUpperCase(Locale.ROOT)); }
        catch (Exception ex) { throw new BadRequestException("Unknown remote command status"); }
        job.setStatus(status);
        job.setStdout(limit(result.stdout()));
        job.setStderr(limit(result.stderr()));
        job.setExitCode(result.exitCode());
        job.setErrorMessage(result.errorMessage());
        if (job.getStartedAt() == null) job.setStartedAt(LocalDateTime.now());
        if (status != RemoteCommandStatus.RUNNING) job.setCompletedAt(LocalDateTime.now());
        jobRepository.save(job);
        auditLogService.log("RemoteCommandJob", id, "RESULT", "Remote command result status=" + status + " correlationId=" + job.getCorrelationId());
    }

    private String limit(String value) {
        if (value == null) return null;
        int max = 1_000_000;
        return value.length() <= max ? value : value.substring(0, max) + System.lineSeparator() + "[output truncated]";
    }
    private MonitoredDevice ensureDevice(Long id, Long companyId) { return deviceRepository.findByIdAndCompany_IdAndDeletedFalse(id, companyId).orElseThrow(() -> new ResourceNotFoundException("Device not found in current company: " + id)); }
    private Long tenant() { Long id = TenantContext.getCurrentTenant(); if (id == null) throw new BadRequestException("Company context is required"); return id; }
}