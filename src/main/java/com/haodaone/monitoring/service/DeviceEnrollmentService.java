package com.haodaone.monitoring.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.monitoring.dto.MonitoredDeviceDTO;
import com.haodaone.monitoring.entity.MonitoredDevice;
import com.haodaone.monitoring.repository.MonitoredDeviceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

/**
 * Admin-side device lifecycle: enroll (mint a token before the agent is
 * ever installed), list/inspect, pause/resume monitoring, decommission.
 * Token generation and hashing follow the exact pattern AuthService uses
 * for refresh tokens - SHA-256 of a SecureRandom value, raw value returned
 * to the caller exactly once and never stored.
 */
@Service
public class DeviceEnrollmentService {

    private final MonitoredDeviceRepository deviceRepository;
    private final EmployeeRepository employeeRepository;
    private final AuditLogService auditLogService;
    private final com.haodaone.company.repository.CompanyRepository companyRepository;
    private final com.haodaone.company.repository.SubscriptionService subscriptionService;
    private final SecureRandom secureRandom = new SecureRandom();

    public DeviceEnrollmentService(MonitoredDeviceRepository deviceRepository, EmployeeRepository employeeRepository,
                                    AuditLogService auditLogService, com.haodaone.company.repository.CompanyRepository companyRepository,
                                    com.haodaone.company.repository.SubscriptionService subscriptionService) {
        this.deviceRepository = deviceRepository;
        this.employeeRepository = employeeRepository;
        this.auditLogService = auditLogService;
        this.companyRepository = companyRepository;
        this.subscriptionService = subscriptionService;
    }
    @Transactional(readOnly = true)
    public List<MonitoredDeviceDTO> listAll() {
        Long currentTenant = com.haodaone.tenant.TenantContext.getCurrentTenant();
        if (currentTenant == null) {
            throw new BadRequestException("Company context is required");
        }
        return deviceRepository.findAllByCompany_IdAndDeletedFalseOrderByDeviceNameAsc(currentTenant).stream()
                .map(MonitoredDeviceDTO::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public MonitoredDeviceDTO get(Long id) {
        return MonitoredDeviceDTO.from(findOrThrow(id));
    }

    /**
     * Creates the device record and returns a one-time raw token. deviceId
     * (the agent's hardware-derived identifier) is intentionally left null
     * here - it's unknown until the agent's first heartbeat, at which point
     * AgentIngestService#resolveDevice fills it in by matching the token.
     */
    @Transactional
    public MonitoredDeviceDTO.EnrollResponse enroll(MonitoredDeviceDTO.EnrollRequest request) {
        MonitoredDevice device = new MonitoredDevice();
        device.setDeviceName(request.getDeviceName());
        device.setDeviceId("PENDING-" + java.util.UUID.randomUUID());
        device.setActive(true);
        device.setHostname(request.getHostname());
        device.setSerialNumber(request.getSerialNumber());
        device.setMacAddress(request.getMacAddress());
        device.setAssignedDate(request.getAssignedDate() != null ? request.getAssignedDate() : java.time.LocalDate.now());

        if (request.getEmployeeId() != null) {
            Employee employee = findEmployeeInTenant(request.getEmployeeId())
                    .orElseThrow(() -> new BadRequestException("Unknown employee: " + request.getEmployeeId()));
            device.setEmployee(employee);
        }

        String rawToken = generateRawToken();
        device.setAgentTokenHash(hash(rawToken));

        // Determine the company for this enrollment: prefer TenantContext but fall back to assigned employee's company
        Long companyId = null;
        Long currentTenant = com.haodaone.tenant.TenantContext.getCurrentTenant();
        if (currentTenant != null) {
            companyId = currentTenant;
            companyRepository.findById(currentTenant).ifPresent(device::setCompany);
        } else if (device.getEmployee() != null && device.getEmployee().getCompany() != null) {
            companyId = device.getEmployee().getCompany().getId();
            device.setCompany(device.getEmployee().getCompany());
        }

        // Enforce device limits via SubscriptionService if company is known
        if (companyId != null) {
            long currentCount = deviceRepository.countByCompany_IdAndDeletedFalse(companyId);
            subscriptionService.ensureDeviceLimitNotExceeded(companyId, currentCount);
        }

        MonitoredDevice saved = deviceRepository.save(device);
        auditLogService.log("MonitoredDevice", saved.getId(), "CREATE",
                "Enrolled device '" + saved.getDeviceName() + "' pending first agent check-in");

        return new MonitoredDeviceDTO.EnrollResponse(MonitoredDeviceDTO.from(saved), rawToken);
    }

    /**
     * Device Assignment module's edit action: (re)assign to an employee
     * and/or update hostname/serial/MAC/assigned date/active status in one
     * call. Unlike enroll(), never touches the agent token.
     */
    @Transactional
    public MonitoredDeviceDTO updateAssignment(Long id, MonitoredDeviceDTO.AssignmentRequest request) {
        MonitoredDevice device = findOrThrow(id);

        if (request.getEmployeeId() != null) {
            Employee employee = findEmployeeInTenant(request.getEmployeeId())
                    .orElseThrow(() -> new BadRequestException("Unknown employee: " + request.getEmployeeId()));
            device.setEmployee(employee);
            if (device.getAssignedDate() == null) {
                device.setAssignedDate(java.time.LocalDate.now());
            }
        }
        if (request.getHostname() != null) {
            device.setHostname(request.getHostname());
        }
        if (request.getSerialNumber() != null) {
            device.setSerialNumber(request.getSerialNumber());
        }
        if (request.getMacAddress() != null) {
            device.setMacAddress(request.getMacAddress());
        }
        if (request.getAssignedDate() != null) {
            device.setAssignedDate(request.getAssignedDate());
        }
        if (request.getActive() != null) {
            device.setActive(request.getActive());
        }

        MonitoredDevice saved = deviceRepository.save(device);
        auditLogService.log("MonitoredDevice", id, "UPDATE",
                "Device assignment updated: employeeId=" + (device.getEmployee() != null ? device.getEmployee().getId() : null)
                        + ", active=" + device.isActive());
        return MonitoredDeviceDTO.from(saved);
    }

    @Transactional
    public void setActive(Long id, boolean active) {
        MonitoredDevice device = findOrThrow(id);
        device.setActive(active);
        deviceRepository.save(device);
        auditLogService.log("MonitoredDevice", id, active ? "ACTIVATE" : "DEACTIVATE", "active: " + active);
    }

    @Transactional
    public void deleteDevice(Long id) {
        MonitoredDevice device = findOrThrow(id);
        device.setActive(false);
        device.setDeleted(true);
        device.setDeletedAt(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
        deviceRepository.save(device);
        auditLogService.log("MonitoredDevice", id, "DELETE",
                "Deleted device '" + device.getDeviceName() + "' (soft delete; history retained)");
    }

    @Transactional
    public MonitoredDeviceDTO applyDirective(Long id, MonitoredDeviceDTO.DirectiveRequest request) {
        MonitoredDevice device = findOrThrow(id);
        if (request.getHeartbeatIntervalSeconds() != null) {
            device.setHeartbeatIntervalSeconds(request.getHeartbeatIntervalSeconds());
        }
        if (request.getMonitoringPaused() != null) {
            device.setMonitoringPaused(request.getMonitoringPaused());
        }
        MonitoredDevice saved = deviceRepository.save(device);
        auditLogService.log("MonitoredDevice", id, "UPDATE",
                "Directive applied: intervalSeconds=" + device.getHeartbeatIntervalSeconds()
                        + ", paused=" + device.isMonitoringPaused());
        return MonitoredDeviceDTO.from(saved);
    }

    private MonitoredDevice findOrThrow(Long id) {
        Long currentTenant = com.haodaone.tenant.TenantContext.getCurrentTenant();
        if (currentTenant != null) {
            return deviceRepository.findByIdAndCompany_IdAndDeletedFalse(id, currentTenant)
                    .orElseThrow(() -> new ResourceNotFoundException("Monitored device not found: " + id));
        }
        return deviceRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Monitored device not found: " + id));
    }

    private java.util.Optional<Employee> findEmployeeInTenant(Long employeeId) {
        Long tenantId = com.haodaone.tenant.TenantContext.getCurrentTenant();
        return tenantId == null
                ? employeeRepository.findById(employeeId)
                : employeeRepository.findByIdAndCompany_IdAndDeletedFalse(employeeId, tenantId);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Same SHA-256-of-a-high-entropy-token approach as AuthService#hash for refresh tokens - not a password, no need for slow/salted hashing. */
    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
