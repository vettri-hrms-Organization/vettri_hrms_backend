package com.haodaone.monitoring.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ConflictException;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.monitoring.dto.EmployeeDeviceInventoryDTO;
import com.haodaone.monitoring.dto.EmployeeTrustedDeviceDTO;
import com.haodaone.monitoring.dto.HardwareInventoryPayload;
import com.haodaone.monitoring.dto.InventoryScanJobDTO;
import com.haodaone.monitoring.dto.InventorySubmissionRequest;
import com.haodaone.monitoring.dto.MonitoredDeviceDTO;
import com.haodaone.monitoring.entity.DeviceEnrollment;
import com.haodaone.monitoring.entity.DeviceInventoryScan;
import com.haodaone.monitoring.entity.MonitoredDevice;
import com.haodaone.monitoring.repository.DeviceEnrollmentRepository;
import com.haodaone.monitoring.repository.DeviceInventoryScanRepository;
import com.haodaone.monitoring.repository.MonitoredDeviceRepository;
import com.haodaone.security.CustomUserPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class DeviceInventoryService {

    private static final int SCAN_TIMEOUT_MINUTES = 5;
    private static final int OFFLINE_QUEUE_TIMEOUT_HOURS = 24;
    private static final int MINIMUM_SCAN_INTERVAL_SECONDS = 30;

    private final DeviceEnrollmentRepository enrollmentRepository;
    private final DeviceInventoryScanRepository scanRepository;
    private final MonitoredDeviceRepository deviceRepository;
    private final EmployeeRepository employeeRepository;
    private final ObjectMapper objectMapper;
    private final AuditLogService auditLogService;

    public DeviceInventoryService(
            DeviceEnrollmentRepository enrollmentRepository,
            DeviceInventoryScanRepository scanRepository,
            MonitoredDeviceRepository deviceRepository,
            EmployeeRepository employeeRepository,
            ObjectMapper objectMapper,
            AuditLogService auditLogService) {
        this.enrollmentRepository = enrollmentRepository;
        this.scanRepository = scanRepository;
        this.deviceRepository = deviceRepository;
        this.employeeRepository = employeeRepository;
        this.objectMapper = objectMapper;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public void authorizeEmployeeEnrollment(String rawToken) {
        requireEmployeeEnrollment(rawToken);
    }

    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void expireStaleScans() {
        List<DeviceInventoryScan> expired = scanRepository.findExpiredForUpdate(LocalDateTime.now(ZoneOffset.UTC));
        for (DeviceInventoryScan scan : expired) {
            MonitoredDevice device = scan.getDevice();
            fail(scan, device, "SCAN_TIMEOUT", "The agent did not respond before the scan timed out.");
            auditLogService.log("MonitoredDevice", device.getId(), "INVENTORY_SCAN_FAILED",
                    "Hardware inventory scan timed out before the agent responded");
        }
    }

    @Transactional
    public EmployeeDeviceInventoryDTO getEmployeeInventory(String rawToken, Long selectedDeviceId) {
        DeviceEnrollment enrollment = requireEmployeeEnrollment(rawToken);
        Employee employee = enrollment.getEmployee();
        List<MonitoredDevice> trustedDevices = trustedDevices(employee);
        MonitoredDevice device = resolveSelectedDevice(enrollment, employee, selectedDeviceId);
        if (device == null) {
            return toEmployeeDTO(enrollment, null, null, trustedDevices);
        }
        DeviceInventoryScan latest = latestScan(device);
        expireIfNeeded(device, latest, LocalDateTime.now(ZoneOffset.UTC));
        return toEmployeeDTO(enrollment, device, latest, trustedDevices);
    }

    @Transactional
    public EmployeeDeviceInventoryDTO requestEmployeeScan(String rawToken, Long selectedDeviceId) {
        DeviceEnrollment enrollment = requireEmployeeEnrollment(rawToken);
        Employee employee = enrollment.getEmployee();
        List<MonitoredDevice> trustedDevices = trustedDevices(employee);
        MonitoredDevice device = resolveSelectedDevice(enrollment, employee, selectedDeviceId);
        if (device == null) {
            return toEmployeeDTO(enrollment, null, null, trustedDevices);
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        DeviceInventoryScan latest = latestScan(device);
        expireIfNeeded(device, latest, now);

        if (!device.isActive() || !device.isOnline()) {
            device.setInventoryStatus("OFFLINE");
            deviceRepository.save(device);
            auditLogService.log("MonitoredDevice", device.getId(), "INVENTORY_SCAN_UNAVAILABLE",
                    "Employee requested a hardware scan while the agent was offline");
            return toEmployeeDTO(enrollment, device, latest, trustedDevices);
        }

        if (latest != null && ("PENDING".equals(latest.getStatus()) || "SCANNING".equals(latest.getStatus()))) {
            return toEmployeeDTO(enrollment, device, latest, trustedDevices);
        }
        if (latest != null && latest.getRequestedAt().isAfter(now.minusSeconds(MINIMUM_SCAN_INTERVAL_SECONDS))) {
            throw new ConflictException("Please wait briefly before requesting another inventory scan.");
        }

        DeviceInventoryScan scan = new DeviceInventoryScan();
        scan.setDevice(device);
        scan.setRequestId(UUID.randomUUID());
        scan.setStatus("PENDING");
        scan.setRequestedAt(now);
        scan.setExpiresAt(now.plusMinutes(SCAN_TIMEOUT_MINUTES));
        DeviceInventoryScan saved = scanRepository.save(scan);
        device.setInventoryStatus("PENDING");
        deviceRepository.save(device);
        auditLogService.log("MonitoredDevice", device.getId(), "INVENTORY_SCAN_REQUESTED",
                "Employee " + enrollment.getEmployee().getId() + " consented to a hardware inventory scan");
        return toEmployeeDTO(enrollment, device, saved, trustedDevices);
    }

    @Transactional
    public MonitoredDeviceDTO requestAdminScan(Long deviceId) {
        Long companyId = com.haodaone.tenant.TenantContext.getCurrentTenant();
        MonitoredDevice device = (companyId == null
                ? deviceRepository.findByIdAndDeletedFalseForUpdate(deviceId)
                : deviceRepository.findByIdAndCompany_IdAndDeletedFalseForUpdate(deviceId, companyId))
                .orElseThrow(() -> new com.haodaone.common.exception.ResourceNotFoundException(
                        "Monitored device not found: " + deviceId));
        if (!device.isActive()) {
            throw new BadRequestException("Hardware inventory cannot be requested for an inactive device.");
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        DeviceInventoryScan latest = latestScan(device);
        expireIfNeeded(device, latest, now);
        if (latest != null && ("PENDING".equals(latest.getStatus()) || "SCANNING".equals(latest.getStatus()))) {
            return MonitoredDeviceDTO.from(device);
        }

        DeviceInventoryScan scan = new DeviceInventoryScan();
        scan.setDevice(device);
        scan.setRequestId(UUID.randomUUID());
        scan.setStatus("PENDING");
        scan.setRequestedAt(now);
        scan.setExpiresAt(now.plusMinutes(device.isOnline()
                ? SCAN_TIMEOUT_MINUTES
                : OFFLINE_QUEUE_TIMEOUT_HOURS * 60L));
        scanRepository.save(scan);
        device.setInventoryStatus("PENDING");
        deviceRepository.save(device);
        auditLogService.log("MonitoredDevice", device.getId(), "INVENTORY_SCAN_REQUESTED",
                "Administrator queued a hardware inventory scan");
        return MonitoredDeviceDTO.from(device);
    }

    @Transactional
    public List<InventoryScanJobDTO> pollAgent(MonitoredDevice authenticatedDevice) {
        MonitoredDevice device = deviceRepository.findByIdAndDeletedFalse(authenticatedDevice.getId())
                .orElseThrow(() -> new AccessDeniedException("Authenticated agent device is unavailable."));
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        List<DeviceInventoryScan> pendingScans = scanRepository.findPendingForDeviceForUpdate(device.getId());
        List<InventoryScanJobDTO> jobs = pendingScans.stream()
                .filter(scan -> {
                    if (!scan.getExpiresAt().isAfter(now)) {
                        fail(scan, device, "SCAN_TIMEOUT", "The agent did not respond before the scan timed out.");
                        return false;
                    }
                    scan.setStatus("SCANNING");
                    scan.setExpiresAt(now.plusMinutes(SCAN_TIMEOUT_MINUTES));
                    device.setInventoryStatus("SCANNING");
                    return true;
                })
                .map(scan -> new InventoryScanJobDTO(scan.getRequestId()))
                .toList();
        if (!pendingScans.isEmpty()) deviceRepository.save(device);
        return jobs;
    }

    @Transactional
    public void submitAgentInventory(
            MonitoredDevice authenticatedDevice, UUID requestId, InventorySubmissionRequest submission) {
        MonitoredDevice device = deviceRepository.findByIdAndDeletedFalse(authenticatedDevice.getId())
                .orElseThrow(() -> new AccessDeniedException("Authenticated agent device is unavailable."));
        DeviceInventoryScan scan = scanRepository.findByRequestIdForUpdate(requestId)
                .orElseThrow(() -> new BadRequestException("Inventory scan request was not found."));
        if (!scan.getDevice().getId().equals(device.getId())) {
            throw new AccessDeniedException("Inventory scan request does not belong to this agent.");
        }

        if ("COMPLETED".equals(scan.getStatus()) || "FAILED".equals(scan.getStatus())) {
            return;
        }

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (!scan.getExpiresAt().isAfter(now)) {
            fail(scan, device, "SCAN_TIMEOUT", "The agent responded after the inventory scan expired.");
            return;
        }
        if (!"SCANNING".equals(scan.getStatus()) && !"PENDING".equals(scan.getStatus())) {
            throw new ConflictException("Inventory scan request is no longer active.");
        }

        if ("FAILED".equals(submission.getStatus())) {
            String errorCode = "INVENTORY_UNAVAILABLE".equals(submission.getErrorCode())
                    ? "INVENTORY_UNAVAILABLE" : "INVENTORY_COLLECTION_FAILED";
            fail(scan, device, errorCode, errorCode.equals("INVENTORY_UNAVAILABLE")
                    ? "Hardware information was not available on this computer."
                    : "The hardware inventory scan failed.");
            auditLogService.log("MonitoredDevice", device.getId(), "INVENTORY_SCAN_FAILED",
                    "Agent reported a hardware inventory scan failure");
            return;
        }

        HardwareInventoryPayload inventory = submission.getInventory();
        if (!hasHardwareDetails(inventory)) {
            throw new BadRequestException("Inventory submission did not include readable hardware specifications.");
        }
        OffsetDateTime collectedAt = inventory.getCollectedAt();
        if (collectedAt.isAfter(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5))
                || collectedAt.isBefore(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(30))) {
            throw new BadRequestException("Inventory collection timestamp is outside the accepted time window.");
        }
        device.setHardwareInventory(objectMapper.valueToTree(inventory));
        String serialNumber = clean(inventory.getSerialNumber());
        if (serialNumber != null) device.setSerialNumber(serialNumber.substring(0, Math.min(150, serialNumber.length())));
        String hostname = clean(inventory.getComputerName());
        if (hostname != null) device.setHostname(hostname.substring(0, Math.min(150, hostname.length())));
        if (clean(inventory.getWindowsEdition()) != null) device.setOperatingSystem(clean(inventory.getWindowsEdition()));
        String osVersion = clean(inventory.getWindowsVersion());
        String build = clean(inventory.getWindowsBuild());
        if (osVersion != null) device.setOsVersion((osVersion + (build == null ? "" : " (build " + build + ")"))
                .substring(0, Math.min(50, (osVersion + (build == null ? "" : " (build " + build + ")")).length())));
        if (clean(inventory.getAgentVersion()) != null) device.setAgentVersion(inventory.getAgentVersion().trim());
        device.setInventoryStatus("COMPLETED");
        device.setLastInventoryScanAt(now);
        scan.setStatus("COMPLETED");
        scan.setCompletedAt(now);
        scan.setErrorCode(null);
        scan.setErrorMessage(null);
        deviceRepository.save(device);
        scanRepository.save(scan);
        auditLogService.log("MonitoredDevice", device.getId(), "INVENTORY_SCAN_COMPLETED",
                "Agent submitted hardware inventory for authenticated device");
    }

    private DeviceEnrollment requireEmployeeEnrollment(String rawToken) {
        if (rawToken == null || rawToken.length() < 32 || rawToken.length() > 64) {
            throw new BadRequestException("Enrollment link is invalid.");
        }
        DeviceEnrollment enrollment = enrollmentRepository.findByTokenHashAndDeletedFalse(hashHex(rawToken))
                .orElseThrow(() -> new BadRequestException("Enrollment link is invalid."));
        if (!"PENDING".equals(enrollment.getStatus().name()) && !"USED".equals(enrollment.getStatus().name())) {
            throw new ConflictException("This enrollment is no longer active.");
        }
        if ("PENDING".equals(enrollment.getStatus().name())
                && !enrollment.getExpiresAt().isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
            throw new ConflictException("This enrollment link has expired.");
        }
        Employee employee = currentEmployee();
        if (!employee.getId().equals(enrollment.getEmployee().getId())
                || employee.getCompany() == null
                || !employee.getCompany().getId().equals(enrollment.getCompany().getId())) {
            throw new AccessDeniedException("This device enrollment does not belong to the authenticated employee.");
        }
        return enrollment;
    }

    private List<MonitoredDevice> trustedDevices(Employee employee) {
        return deviceRepository.findAllByCompany_IdAndEmployee_IdAndActiveTrueAndDeletedFalseOrderByDeviceNameAsc(
                        employee.getCompany().getId(), employee.getId());
    }

    private MonitoredDevice resolveSelectedDevice(
            DeviceEnrollment enrollment, Employee employee, Long selectedDeviceId) {
        if ("USED".equals(enrollment.getStatus().name())) {
            MonitoredDevice linked = enrollment.getDevice();
            if (linked == null || linked.getEmployee() == null
                    || !linked.getEmployee().getId().equals(employee.getId())
                    || linked.getCompany() == null
                    || !linked.getCompany().getId().equals(employee.getCompany().getId())) {
                throw new AccessDeniedException("The enrolled agent is not assigned to this employee.");
            }
            if (selectedDeviceId != null && !selectedDeviceId.equals(linked.getId())) {
                throw new AccessDeniedException("The selected device does not match this enrollment.");
            }
            return linked;
        }
        if (selectedDeviceId == null) return null;
        return deviceRepository.findByIdAndCompany_IdAndEmployee_IdAndActiveTrueAndDeletedFalse(
                        selectedDeviceId, employee.getCompany().getId(), employee.getId())
                .orElseThrow(() -> new AccessDeniedException("The selected device is not assigned to this employee."));
    }

    private Employee currentEmployee() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Employee authentication is required.");
        }
        Object principal = authentication.getPrincipal();
        var employee = employeeRepository.findByUser_UsernameAndDeletedFalse(authentication.getName())
                .or(() -> employeeRepository.findByEmailIgnoreCaseAndDeletedFalse(authentication.getName()))
                .or(() -> principal instanceof CustomUserPrincipal custom
                        ? employeeRepository.findByUser_IdAndDeletedFalse(custom.getId())
                        : java.util.Optional.empty())
                .orElseThrow(() -> new AccessDeniedException("The authenticated account is not linked to an employee."));
        if (employee.getCompany() == null) {
            throw new AccessDeniedException("The authenticated employee is not assigned to an organization.");
        }
        return employee;
    }

    private DeviceInventoryScan latestScan(MonitoredDevice device) {
        return scanRepository.findFirstByDevice_IdAndDeletedFalseOrderByRequestedAtDesc(device.getId()).orElse(null);
    }

    private void expireIfNeeded(MonitoredDevice device, DeviceInventoryScan scan, LocalDateTime now) {
        if (scan != null && ("PENDING".equals(scan.getStatus()) || "SCANNING".equals(scan.getStatus()))
                && !scan.getExpiresAt().isAfter(now)) {
            fail(scan, device, "SCAN_TIMEOUT", "The agent did not respond before the scan timed out.");
            deviceRepository.save(device);
            scanRepository.save(scan);
        }
    }

    private void fail(DeviceInventoryScan scan, MonitoredDevice device, String errorCode, String message) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        scan.setStatus("FAILED");
        scan.setCompletedAt(now);
        scan.setErrorCode(errorCode);
        scan.setErrorMessage(message);
        device.setInventoryStatus("FAILED");
        deviceRepository.save(device);
        scanRepository.save(scan);
    }

    private EmployeeDeviceInventoryDTO toEmployeeDTO(
            DeviceEnrollment enrollment,
            MonitoredDevice device,
            DeviceInventoryScan scan,
            List<MonitoredDevice> trustedDevices) {
        boolean deviceOnline = device != null && device.isActive() && device.isOnline();
        boolean scanActive = scan != null && ("PENDING".equals(scan.getStatus()) || "SCANNING".equals(scan.getStatus()));
        String status = device == null
                ? (trustedDevices.isEmpty() ? "NOT_ENROLLED" : "SELECT_DEVICE")
                : "OFFLINE".equals(device.getInventoryStatus()) && !scanActive
                    ? "OFFLINE"
                    : scan == null ? device.getInventoryStatus() : scan.getStatus();
        String message = switch (status) {
            case "SELECT_DEVICE" -> "Choose one of your already registered devices. The browser cannot identify a Windows agent directly.";
            case "NOT_ENROLLED" -> "No active Vettri Agent is registered to your employee account. Continue with the existing enrollment steps below.";
            case "PENDING" -> deviceOnline
                    ? "The scan is queued and will start when the agent checks in."
                    : "The registered agent is offline. The scan is pending until it reconnects.";
            case "SCANNING" -> deviceOnline
                    ? "The agent is collecting device specifications."
                    : "The registered agent is offline. The scan remains pending until it reconnects or times out.";
            case "COMPLETED" -> "Device inventory is available.";
            case "FAILED" -> scan != null && scan.getErrorMessage() != null
                    ? scan.getErrorMessage() : "The inventory scan failed. You can retry when the agent is online.";
            case "OFFLINE" -> deviceOnline
                    ? "The latest scan could not start. The agent is available now; retry the scan."
                    : "The registered agent is offline. Connect the laptop to the internet and retry.";
            default -> "No hardware inventory has been collected yet.";
        };
        return new EmployeeDeviceInventoryDTO(
                enrollment.getStatus().name(),
                status,
                deviceOnline,
                device == null ? null : device.getId(),
                trustedDevices.stream()
                        .map(trusted -> new EmployeeTrustedDeviceDTO(
                                trusted.getId(), trusted.getDeviceName(), trusted.isOnline(), trusted.getInventoryStatus()))
                        .toList(),
                scan == null ? null : scan.getRequestId(),
                scan == null ? null : scan.getRequestedAt(),
                device == null ? null : device.getLastInventoryScanAt(),
                message,
                device == null ? null : device.getHardwareInventory());
    }

    private String hashHex(String value) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String clean(String value) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        if (trimmed.equalsIgnoreCase("To be filled by O.E.M.")
                || trimmed.equalsIgnoreCase("Default string")
                || trimmed.equalsIgnoreCase("System Serial Number")
                || trimmed.equalsIgnoreCase("Unknown")
                || trimmed.chars().allMatch(character -> character == '0' || character == '-')) {
            return null;
        }
        return trimmed;
    }

    private boolean hasHardwareDetails(HardwareInventoryPayload inventory) {
        return clean(inventory.getManufacturer()) != null
                || clean(inventory.getModel()) != null
                || clean(inventory.getSerialNumber()) != null
                || clean(inventory.getProcessorName()) != null
                || inventory.getPhysicalCoreCount() != null
                || inventory.getInstalledMemoryBytes() != null
                || inventory.getPhysicalDisks() != null && inventory.getPhysicalDisks().stream()
                    .anyMatch(disk -> disk != null
                            && (clean(disk.getModel()) != null || disk.getCapacityBytes() != null))
                || clean(inventory.getWindowsEdition()) != null
                || clean(inventory.getBiosVersion()) != null
                || clean(inventory.getSystemUuid()) != null;
    }
}
