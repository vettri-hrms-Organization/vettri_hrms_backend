package com.haodaone.remotecommand.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.monitoring.entity.MonitoredDevice;
import com.haodaone.monitoring.repository.MonitoredDeviceRepository;
import com.haodaone.remotecommand.dto.RemoteCommandDTO;
import com.haodaone.remotecommand.entity.*;
import com.haodaone.remotecommand.repository.RemoteCommandJobRepository;
import com.haodaone.security.AuthorizationService;
import com.haodaone.tenant.TenantContext;
import com.haodaone.user.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class RemoteCommandService {
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    /** Raw scripts: only with REMOTE_SCRIPT_RUN plus the admin's password. Normal use is shellType ACTION. */
    private static final Set<String> SCRIPT_SHELLS = Set.of("POWERSHELL", "CMD");
    static final String ACTION_SHELL = "ACTION";
    static final String SCRIPT_PERMISSION = "REMOTE_SCRIPT_RUN";
    private final RemoteCommandJobRepository jobRepository;
    private final MonitoredDeviceRepository deviceRepository;
    private final AuditLogService auditLogService;
    private final AuthorizationService authorizationService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public RemoteCommandService(RemoteCommandJobRepository jobRepository, MonitoredDeviceRepository deviceRepository, AuditLogService auditLogService,
                                AuthorizationService authorizationService, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.jobRepository = jobRepository;
        this.deviceRepository = deviceRepository;
        this.auditLogService = auditLogService;
        this.authorizationService = authorizationService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<RemoteCommandDTO.ActionInfo> actions() {
        return Arrays.stream(RemoteAction.values()).map(RemoteCommandDTO.ActionInfo::from).toList();
    }

    // noRollbackFor: a refused custom script still leaves its SCRIPT_DENIED audit row.
    @Transactional(noRollbackFor = BadRequestException.class)
    public RemoteCommandDTO.Response create(Long deviceId, RemoteCommandDTO.CreateRequest request, Long requestedBy) {
        Long companyId = tenant();
        MonitoredDevice device = deviceRepository.findByIdAndCompany_IdAndDeletedFalse(deviceId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Device not found in current company: " + deviceId));
        String shell = request.shellType().trim().toUpperCase(Locale.ROOT);
        String command = request.command().trim();
        int timeout = DEFAULT_TIMEOUT_SECONDS;
        String auditDetail;
        if (ACTION_SHELL.equals(shell)) {
            String requestedKey = command;
            RemoteAction action = RemoteAction.fromKey(requestedKey)
                    .orElseThrow(() -> new BadRequestException("Unknown remote action: " + requestedKey));
            command = action.name();
            timeout = action.timeoutSeconds();
            auditDetail = "action=" + action.name();
        } else if (SCRIPT_SHELLS.contains(shell)) {
            requireScriptPermission(requestedBy, request.confirmPassword());
            // The script itself may hold secrets, so the audit trail records its fingerprint, not its text.
            auditDetail = "RAW_SCRIPT shell=" + shell + " length=" + command.length() + " sha256=" + sha256(command);
        } else {
            throw new BadRequestException("Choose an approved action");
        }
        RemoteCommandJob job = new RemoteCommandJob();
        job.setCompany(device.getCompany());
        job.setDevice(device);
        job.setRequestedBy(requestedBy);
        job.setCommand(command);
        job.setShellType(shell);
        job.setTimeoutSeconds(timeout);
        job.setCorrelationId(UUID.randomUUID().toString());
        RemoteCommandJob saved = jobRepository.save(job);
        auditLogService.log("RemoteCommandJob", saved.getId(), "CREATE", "Queued remote command for device '" + device.getDeviceName() + "' " + auditDetail + " correlationId=" + saved.getCorrelationId());
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

    /** Raw scripts run as SYSTEM on the laptop: a dedicated permission plus re-entering the password. */
    private void requireScriptPermission(Long userId, String confirmPassword) {
        if (!authorizationService.hasOrganizationScope(SCRIPT_PERMISSION)) {
            throw new AccessDeniedException("Running custom scripts needs the REMOTE_SCRIPT_RUN permission. Use an approved action instead.");
        }
        if (confirmPassword == null || confirmPassword.isBlank()) {
            throw new BadRequestException("Confirm your password to run a custom script");
        }
        var user = userRepository.findById(userId).orElseThrow(() -> new AccessDeniedException("User not found"));
        if (user.getPasswordHash() == null || !passwordEncoder.matches(confirmPassword, user.getPasswordHash())) {
            auditLogService.log("RemoteCommandJob", null, "SCRIPT_DENIED", "Custom script refused: password confirmation failed");
            throw new BadRequestException("Password is incorrect");
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private String limit(String value) {
        if (value == null) return null;
        int max = 1_000_000;
        return value.length() <= max ? value : value.substring(0, max) + System.lineSeparator() + "[output truncated]";
    }
    private MonitoredDevice ensureDevice(Long id, Long companyId) { return deviceRepository.findByIdAndCompany_IdAndDeletedFalse(id, companyId).orElseThrow(() -> new ResourceNotFoundException("Device not found in current company: " + id)); }
    private Long tenant() { Long id = TenantContext.getCurrentTenant(); if (id == null) throw new BadRequestException("Company context is required"); return id; }
}