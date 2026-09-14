package com.haodaone.software.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.monitoring.entity.MonitoredDevice;
import com.haodaone.monitoring.repository.MonitoredDeviceRepository;
import com.haodaone.software.dto.SoftwareDeploymentDTO;
import com.haodaone.software.dto.SoftwareDeploymentTargetDTO;
import com.haodaone.software.dto.AgentSoftwareJobDTO;
import com.haodaone.software.dto.AgentSoftwareStatusRequest;
import com.haodaone.software.dto.SoftwarePackageDTO;
import com.haodaone.software.dto.SoftwareVersionDTO;
import com.haodaone.software.entity.*;
import com.haodaone.software.repository.*;
import com.haodaone.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.HashSet;

@Service
public class SoftwareManagementService {

    private final SoftwarePackageRepository packageRepository;
    private final SoftwareVersionRepository versionRepository;
    private final SoftwareDeploymentRepository deploymentRepository;
    private final SoftwareDeploymentTargetRepository deploymentTargetRepository;
    private final MonitoredDeviceRepository monitoredDeviceRepository;
    private final CompanyRepository companyRepository;
    private final AuditLogService auditLogService;
    private final SoftwarePackageStorageService packageStorageService;
    private final SoftwareDeploymentStageEventRepository stageEventRepository;

    public SoftwareManagementService(SoftwarePackageRepository packageRepository,
                                    SoftwareVersionRepository versionRepository,
                                    SoftwareDeploymentRepository deploymentRepository,
                                    SoftwareDeploymentTargetRepository deploymentTargetRepository,
                                    MonitoredDeviceRepository monitoredDeviceRepository,
                                    CompanyRepository companyRepository,
                                    AuditLogService auditLogService,
                                    SoftwarePackageStorageService packageStorageService,
                                    SoftwareDeploymentStageEventRepository stageEventRepository) {
        this.packageRepository = packageRepository;
        this.versionRepository = versionRepository;
        this.deploymentRepository = deploymentRepository;
        this.deploymentTargetRepository = deploymentTargetRepository;
        this.monitoredDeviceRepository = monitoredDeviceRepository;
        this.companyRepository = companyRepository;
        this.auditLogService = auditLogService;
        this.packageStorageService = packageStorageService;
        this.stageEventRepository = stageEventRepository;
    }

    @Transactional(readOnly = true)
    public List<SoftwarePackageDTO> listPackages() {
        Long tenantId = requiredTenant();
        return packageRepository.findByCompany_IdAndDeletedFalseOrderByNameAsc(tenantId)
                .stream().map(SoftwarePackageDTO::from).toList();
    }

    @Transactional(readOnly = true)
    public SoftwarePackageDTO getPackage(Long packageId) {
        Long tenantId = requiredTenant();
        return SoftwarePackageDTO.from(packageRepository.findByIdAndCompany_IdAndDeletedFalse(packageId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Software package not found: " + packageId)));
    }

    @Transactional
    public SoftwarePackageDTO createPackage(SoftwarePackageDTO.CreateRequest request) {
        Long tenantId = requiredTenant();
        if (request == null || request.getName() == null || request.getName().isBlank()) {
            throw new BadRequestException("Software package name is required");
        }
        Company company = companyRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found: " + tenantId));
        if (packageRepository.findByCompany_IdAndDeletedFalseOrderByNameAsc(tenantId).stream()
                .anyMatch(p -> p.getName().equalsIgnoreCase(request.getName().trim()))) {
            throw new BadRequestException("Software package already exists in this company");
        }

        SoftwarePackage entity = new SoftwarePackage();
        entity.setCompany(company);
        entity.setName(request.getName().trim());
        entity.setPublisher(request.getPublisher());
        entity.setDescription(request.getDescription());
        entity.setPlatform(request.getPlatform() == null ? SoftwarePlatform.WINDOWS : request.getPlatform());
        entity.setActive(request.isActive());

        SoftwarePackage saved = packageRepository.save(entity);
        auditLogService.log("SoftwarePackage", saved.getId(), "CREATE", "Created software package '" + saved.getName() + "'");
        return SoftwarePackageDTO.from(saved);
    }

    @Transactional(readOnly = true)
    public List<SoftwareVersionDTO> listVersions(Long packageId) {
        Long tenantId = requiredTenant();
        ensurePackageInCompany(packageId, tenantId);
        return versionRepository.findBySoftwarePackage_IdAndDeletedFalseOrderByVersionDesc(packageId)
                .stream().map(SoftwareVersionDTO::from).toList();
    }

    @Transactional
    public SoftwareVersionDTO createVersion(Long packageId, SoftwareVersionDTO.CreateRequest request) {
        return createVersion(packageId, request, null);
    }

    @Transactional
    public SoftwareVersionDTO createVersion(Long packageId, SoftwareVersionDTO.CreateRequest request, MultipartFile installer) {
        Long tenantId = requiredTenant();
        SoftwarePackage packageEntity = packageRepository.findByIdAndCompany_IdAndDeletedFalse(packageId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Software package not found: " + packageId));

        if (request == null || request.getVersion() == null || request.getVersion().isBlank()) {
            throw new BadRequestException("Software version is required");
        }
        if (installer == null || installer.isEmpty()) throw new BadRequestException("An installer upload is required");
        if (request.getInstallerType() == null || request.getInstallerType() == SoftwareInstallerType.EXE)
            request.setInstallerType(SoftwareInstallerType.INNO_SETUP);
        if (request.getInstallerType() != SoftwareInstallerType.INNO_SETUP)
            throw new BadRequestException("Only Inno Setup EXE uploads are enabled in this phase");
        if (request.getDetectionRule() == null || request.getDetectionRule().isBlank())
            throw new BadRequestException("A detection rule is required to verify installation");
        if (versionRepository.findBySoftwarePackage_IdAndDeletedFalseOrderByVersionDesc(packageId).stream()
                .anyMatch(v -> v.getPackageVersion().equalsIgnoreCase(request.getVersion().trim()))) {
            throw new BadRequestException("Version already exists for this package");
        }

        SoftwarePackageStorageService.StoredFile stored = packageStorageService.store(installer, tenantId);
        SoftwareVersion version = new SoftwareVersion();
        version.setSoftwarePackage(packageEntity);
        version.setPackageVersion(request.getVersion().trim());
        version.setArchitecture(request.getArchitecture() == null ? "x64" : request.getArchitecture());
        version.setInstallerType(request.getInstallerType());
        version.setInstallerUrl(null);
        version.setPackageStorageKey(stored.key());
        version.setChecksumSha256(stored.checksumSha256());
        version.setFileSizeBytes(stored.sizeBytes());
        version.setSilentInstallArguments(request.getSilentInstallArguments());
        version.setDetectionRule(request.getDetectionRule());
        version.setActive(request.isActive());

        SoftwareVersion saved = versionRepository.save(version);
        auditLogService.log("SoftwareVersion", saved.getId(), "CREATE",
                "Created version '" + saved.getPackageVersion() + "' for package '" + packageEntity.getName() + "'");
        return SoftwareVersionDTO.from(saved);
    }

    @Transactional(readOnly = true)
    public List<SoftwareDeploymentDTO> listDeployments() {
        Long tenantId = requiredTenant();
        return deploymentRepository.findByCompany_IdAndDeletedFalseOrderByCreatedAtDesc(tenantId)
            .stream().map(this::deploymentDto).toList();
    }

        @Transactional(readOnly = true)
        public List<SoftwareDeploymentTargetDTO> listDeploymentTargets(Long deploymentId) {
        Long tenantId = requiredTenant();
        deploymentRepository.findByIdAndCompany_IdAndDeletedFalse(deploymentId, tenantId)
            .orElseThrow(() -> new ResourceNotFoundException("Deployment not found: " + deploymentId));
        return deploymentTargetRepository.findByDeployment_IdAndDeletedFalseOrderByIdAsc(deploymentId)
            .stream().map(target -> {
                SoftwareDeploymentTargetDTO dto = SoftwareDeploymentTargetDTO.from(target);
                dto.setStages(stageEventRepository.findByTarget_IdOrderByOccurredAtAscIdAsc(target.getId()).stream()
                    .map(event -> new SoftwareDeploymentTargetDTO.StageEvent(event.getStatus(), event.getErrorCode(),
                        event.getErrorMessage(), event.getOccurredAt())).toList());
                return dto;
            }).toList();
        }

    @Transactional
    public SoftwareDeploymentDTO createDeployment(SoftwareDeploymentDTO.CreateRequest request) {
        return createDeployment(request, null);
    }

    @Transactional
    public SoftwareDeploymentDTO createDeployment(SoftwareDeploymentDTO.CreateRequest request, Long createdByUserId) {
        Long tenantId = requiredTenant();
        if (request == null) {
            throw new BadRequestException("Deployment request is required");
        }
        if (request.getSoftwareVersionId() == null) {
            throw new BadRequestException("Software version is required");
        }
        if (request.getTargetDeviceIds() == null || request.getTargetDeviceIds().isEmpty()) {
            throw new BadRequestException("At least one target device is required");
        }
        if (new HashSet<>(request.getTargetDeviceIds()).size() != request.getTargetDeviceIds().size())
            throw new BadRequestException("A target device may only be selected once");

        SoftwareVersion version = versionRepository.findByIdAndSoftwarePackage_Company_IdAndDeletedFalse(request.getSoftwareVersionId(), tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Software version not found: " + request.getSoftwareVersionId()));

        Company company = companyRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Company not found: " + tenantId));

        SoftwareDeployment deployment = new SoftwareDeployment();
        deployment.setCompany(company);
        deployment.setSoftwareVersion(version);
        deployment.setCreatedByUserId(createdByUserId);
        deployment.setStatus(SoftwareDeploymentStatus.DEPLOYMENT_QUEUED);
        deployment.setStartedAt(LocalDateTime.now());
        deployment.setNote(request.getNote());

        SoftwareDeployment savedDeployment = deploymentRepository.save(deployment);

        for (Long deviceId : request.getTargetDeviceIds()) {
            MonitoredDevice device = monitoredDeviceRepository.findByIdAndCompany_IdAndDeletedFalse(deviceId, tenantId)
                    .orElseThrow(() -> new ResourceNotFoundException("Device not found in current company: " + deviceId));

            SoftwareDeploymentTarget target = new SoftwareDeploymentTarget();
            target.setDeployment(savedDeployment);
            target.setDevice(device);
            target.setStatus(SoftwareDeploymentStatus.DEPLOYMENT_QUEUED);
            target.setStartedAt(LocalDateTime.now());
            target.setEmployee(device.getEmployee());
            SoftwareDeploymentTarget savedTarget = deploymentTargetRepository.save(target);
            recordStage(savedTarget, SoftwareDeploymentStatus.DEPLOYMENT_QUEUED, null, null);
        }

        auditLogService.log("SoftwareDeployment", savedDeployment.getId(), "CREATE",
                "Queued software deployment for package '" + version.getSoftwarePackage().getName() + "' to "
                        + request.getTargetDeviceIds().size() + " device(s)");
        return deploymentDto(savedDeployment);
    }

    @Transactional(readOnly = true)
    public List<AgentSoftwareJobDTO> getAgentJobs(MonitoredDevice device) {
        if (device == null || device.getCompany() == null) {
            return List.of();
        }

        String deviceId = device.getDeviceId();
        Long deviceDbId = device.getId();

        return deploymentTargetRepository.findByDevice_Company_IdAndStatusInAndDeletedFalse(
                        device.getCompany().getId(), List.of(SoftwareDeploymentStatus.DEPLOYMENT_QUEUED,
                            SoftwareDeploymentStatus.PENDING))
                .stream()
                .filter(target -> {
                    MonitoredDevice targetDevice = target.getDevice();
                    if (targetDevice == null) {
                        return false;
                    }
                    boolean sameDbId = deviceDbId != null && targetDevice.getId() != null && targetDevice.getId().equals(deviceDbId);
                    boolean sameHardwareId = deviceId != null && targetDevice.getDeviceId() != null && targetDevice.getDeviceId().equals(deviceId);
                    return sameDbId || sameHardwareId;
                })
                .filter(target -> target.getDeployment().getSoftwareVersion().isActive())
                .map(target -> {
                    AgentSoftwareJobDTO job = AgentSoftwareJobDTO.from(target);
                    job.setInstallerUrl(packageStorageService.generateDownloadUrl(
                            target.getDeployment().getSoftwareVersion().getPackageStorageKey()));
                    return job;
                })
                .toList();
    }

    @Transactional
    public void updateAgentJobStatus(MonitoredDevice device, Long targetId, AgentSoftwareStatusRequest request) {
        SoftwareDeploymentTarget target = deploymentTargetRepository.findByIdAndDevice_IdAndDeletedFalse(targetId, device.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Software deployment target not found: " + targetId));
        if (target.getDeployment().getCompany() == null || device.getCompany() == null
            || !target.getDeployment().getCompany().getId().equals(device.getCompany().getId())) {
            throw new ResourceNotFoundException("Software deployment target not found: " + targetId);
        }
        SoftwareDeploymentStatus status;
        try {
            status = SoftwareDeploymentStatus.valueOf(request.getStatus().trim().toUpperCase());
        } catch (Exception ex) {
            throw new BadRequestException("Unknown software deployment status");
        }
        target.setStatus(status);
        target.setErrorMessage(request.getErrorMessage());
        target.setInstalledVersion(request.getInstalledVersion());
        recordStage(target, status, request.getErrorCode(), request.getErrorMessage());
        if (target.getStartedAt() == null) target.setStartedAt(LocalDateTime.now());
        if (status == SoftwareDeploymentStatus.COMPLETED || status == SoftwareDeploymentStatus.INSTALLED
            || status == SoftwareDeploymentStatus.ALREADY_INSTALLED
                || status == SoftwareDeploymentStatus.FAILED || status == SoftwareDeploymentStatus.CANCELLED) {
            target.setCompletedAt(LocalDateTime.now());
        }
        deploymentTargetRepository.save(target);
        refreshDeploymentStatus(target.getDeployment());
    }

    private Long requiredTenant() {
        Long tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            throw new BadRequestException("Company context is required");
        }
        return tenantId;
    }

    private void ensurePackageInCompany(Long packageId, Long tenantId) {
        if (!packageRepository.findByIdAndCompany_IdAndDeletedFalse(packageId, tenantId).isPresent()) {
            throw new ResourceNotFoundException("Software package not found: " + packageId);
        }
    }

    private SoftwareDeploymentDTO deploymentDto(SoftwareDeployment deployment) {
        SoftwareDeploymentDTO dto = SoftwareDeploymentDTO.from(deployment);
        List<SoftwareDeploymentTarget> targets = deploymentTargetRepository
            .findByDeployment_IdAndDeletedFalseOrderByIdAsc(deployment.getId());
        dto.setTotalTargets(targets.size());
        dto.setInstalledTargets((int) targets.stream().filter(t -> t.getStatus() == SoftwareDeploymentStatus.COMPLETED
            || t.getStatus() == SoftwareDeploymentStatus.INSTALLED
            || t.getStatus() == SoftwareDeploymentStatus.ALREADY_INSTALLED).count());
        dto.setInstallingTargets((int) targets.stream().filter(t -> t.getStatus() == SoftwareDeploymentStatus.DEVICE_REACHED
            || t.getStatus() == SoftwareDeploymentStatus.JOB_RECEIVED
            || t.getStatus() == SoftwareDeploymentStatus.DOWNLOADING
            || t.getStatus() == SoftwareDeploymentStatus.DOWNLOAD_VERIFIED
            || t.getStatus() == SoftwareDeploymentStatus.INSTALLING).count());
        dto.setFailedTargets((int) targets.stream().filter(t -> t.getStatus() == SoftwareDeploymentStatus.FAILED).count());
        dto.setPendingTargets((int) targets.stream().filter(t -> t.getStatus() == SoftwareDeploymentStatus.DEPLOYMENT_QUEUED
            || t.getStatus() == SoftwareDeploymentStatus.PENDING
            || t.getStatus() == SoftwareDeploymentStatus.DEVICE_OFFLINE).count());
        return dto;
    }

    private void refreshDeploymentStatus(SoftwareDeployment deployment) {
        List<SoftwareDeploymentTarget> targets = deploymentTargetRepository
            .findByDeployment_IdAndDeletedFalseOrderByIdAsc(deployment.getId());
        boolean allSuccessful = !targets.isEmpty() && targets.stream().allMatch(t -> t.getStatus() == SoftwareDeploymentStatus.COMPLETED
            || t.getStatus() == SoftwareDeploymentStatus.INSTALLED
            || t.getStatus() == SoftwareDeploymentStatus.ALREADY_INSTALLED);
        boolean anyActive = targets.stream().anyMatch(t -> t.getStatus() == SoftwareDeploymentStatus.DEVICE_REACHED
            || t.getStatus() == SoftwareDeploymentStatus.JOB_RECEIVED
            || t.getStatus() == SoftwareDeploymentStatus.DOWNLOADING
            || t.getStatus() == SoftwareDeploymentStatus.DOWNLOAD_VERIFIED
            || t.getStatus() == SoftwareDeploymentStatus.INSTALLING
            || t.getStatus() == SoftwareDeploymentStatus.INSTALLATION_COMPLETED
            || t.getStatus() == SoftwareDeploymentStatus.VERIFYING);
        boolean anyFailed = targets.stream().anyMatch(t -> t.getStatus() == SoftwareDeploymentStatus.FAILED);
        if (allSuccessful) {
            deployment.setStatus(SoftwareDeploymentStatus.COMPLETED);
            deployment.setCompletedAt(LocalDateTime.now());
        } else if (anyActive) {
            deployment.setStatus(SoftwareDeploymentStatus.INSTALLING);
        } else if (anyFailed && targets.stream().allMatch(t -> t.getStatus() == SoftwareDeploymentStatus.FAILED
            || t.getStatus() == SoftwareDeploymentStatus.COMPLETED
            || t.getStatus() == SoftwareDeploymentStatus.INSTALLED
            || t.getStatus() == SoftwareDeploymentStatus.ALREADY_INSTALLED)) {
            deployment.setStatus(SoftwareDeploymentStatus.FAILED);
            deployment.setCompletedAt(LocalDateTime.now());
        }
        deploymentRepository.save(deployment);
    }

    private void recordStage(SoftwareDeploymentTarget target, SoftwareDeploymentStatus status,
                             String errorCode, String errorMessage) {
        SoftwareDeploymentStageEvent event = new SoftwareDeploymentStageEvent();
        event.setTarget(target);
        event.setStatus(status);
        event.setErrorCode(errorCode);
        event.setErrorMessage(errorMessage);
        event.setOccurredAt(LocalDateTime.now());
        stageEventRepository.save(event);
    }
}
