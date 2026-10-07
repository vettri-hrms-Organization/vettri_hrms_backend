package com.haodaone.monitoring.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ConflictException;
import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.monitoring.dto.AgentEnrollmentResponse;
import com.haodaone.monitoring.dto.DeviceEnrollmentDTO;
import com.haodaone.monitoring.dto.DeviceEnrollmentRequest;
import com.haodaone.monitoring.dto.DeviceInfoPayload;
import com.haodaone.monitoring.dto.PublicDeviceEnrollmentDTO;
import com.haodaone.monitoring.entity.DeviceEnrollment;
import com.haodaone.monitoring.entity.DeviceEnrollmentStatus;
import com.haodaone.monitoring.entity.MonitoredDevice;
import com.haodaone.monitoring.repository.DeviceEnrollmentRepository;
import com.haodaone.monitoring.repository.MonitoredDeviceRepository;
import com.haodaone.recruitment.service.EmailService;
import com.haodaone.security.AuthorizationService;
import com.haodaone.tenant.TenantContext;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

@Service
public class DeviceOnboardingService {

    private static final String WINDOWS_PC = "WINDOWS_PC";
    private static final int ENROLLMENT_EXPIRY_HOURS = 24;
    private static final String FRONTEND_ENROLLMENT_PATH = "/device-enrollment/";

    private final DeviceEnrollmentRepository enrollmentRepository;
    private final EmployeeRepository employeeRepository;
    private final MonitoredDeviceRepository deviceRepository;
    private final CompanyRepository companyRepository;
    private final com.haodaone.company.repository.SubscriptionService subscriptionService;
    private final AuthorizationService authorizationService;
    private final AuditLogService auditLogService;
    private final EmailService emailService;
    private final AgentInstallerStorageService installerStorageService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${app.account-invitation.encryption-key}")
    private String tokenEncryptionKey;

    public DeviceOnboardingService(DeviceEnrollmentRepository enrollmentRepository,
                                   EmployeeRepository employeeRepository,
                                   MonitoredDeviceRepository deviceRepository,
                                   CompanyRepository companyRepository,
                                   com.haodaone.company.repository.SubscriptionService subscriptionService,
                                   AuthorizationService authorizationService,
                                   AuditLogService auditLogService,
                                   EmailService emailService,
                                   AgentInstallerStorageService installerStorageService) {
        this.enrollmentRepository = enrollmentRepository;
        this.employeeRepository = employeeRepository;
        this.deviceRepository = deviceRepository;
        this.companyRepository = companyRepository;
        this.subscriptionService = subscriptionService;
        this.authorizationService = authorizationService;
        this.auditLogService = auditLogService;
        this.emailService = emailService;
        this.installerStorageService = installerStorageService;
    }

    @Transactional
    public DeviceEnrollmentDTO create(DeviceEnrollmentRequest request) {
        long companyId = requiredTenant();
        if (!WINDOWS_PC.equals(request.getDeviceType())) {
            throw new BadRequestException("Only Windows PC enrollment is currently supported.");
        }
        if (!authorizationService.isAllowed("MONITORING_MANAGE", "EMPLOYEE", request.getEmployeeId())) {
            throw new AccessDeniedException("The employee is outside your device-management scope.");
        }
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new BadRequestException("Company context is invalid."));
        Employee employee = employeeRepository.findByIdAndCompany_IdAndDeletedFalse(request.getEmployeeId(), companyId)
                .orElseThrow(() -> new BadRequestException("Employee not found in the current company."));

        String rawToken = newEnrollmentToken();
        DeviceEnrollment enrollment = new DeviceEnrollment();
        enrollment.setCompany(company);
        enrollment.setEmployee(employee);
        enrollment.setDeviceType(WINDOWS_PC);
        enrollment.setTokenHash(hashHex(rawToken));
        enrollment.setEncryptedToken(encrypt(rawToken));
        enrollment.setStatus(DeviceEnrollmentStatus.PENDING);
        enrollment.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusHours(ENROLLMENT_EXPIRY_HOURS));
        DeviceEnrollment saved = enrollmentRepository.save(enrollment);
        auditLogService.log("DeviceEnrollment", saved.getId(), "CREATE",
                "Created Windows agent enrollment for employee " + employee.getId());

        String url = enrollmentUrl(rawToken);
        return DeviceEnrollmentDTO.from(saved, url);
    }

    @Transactional
    public List<DeviceEnrollmentDTO> listForCurrentCompany() {
        long companyId = requiredTenant();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return enrollmentRepository.findAllByCompany_IdAndDeletedFalseOrderByCreatedAtDesc(companyId).stream()
                .map(enrollment -> {
                    refreshExpiredStatus(enrollment, now);
                    String url = enrollment.getStatus() == DeviceEnrollmentStatus.PENDING
                            ? enrollmentUrl(decrypt(enrollment.getEncryptedToken())) : null;
                    return DeviceEnrollmentDTO.from(enrollment, url);
                })
                .toList();
    }

    @Transactional
    public PublicDeviceEnrollmentDTO status(String rawToken) {
        DeviceEnrollment enrollment = findByToken(rawToken, false);
        refreshExpiredStatus(enrollment, LocalDateTime.now(ZoneOffset.UTC));
        return PublicDeviceEnrollmentDTO.from(enrollment);
    }

    @Transactional(readOnly = true)
    public String installerDownloadUrl(String rawToken) {
        DeviceEnrollment enrollment = findByToken(rawToken, false);
        requirePendingAndUnexpired(enrollment, LocalDateTime.now(ZoneOffset.UTC));
        return installerStorageService.createDownloadUrl();
    }

    @Transactional
    public void revoke(Long id) {
        long companyId = requiredTenant();
        DeviceEnrollment enrollment = enrollmentRepository.findByIdAndCompany_IdAndDeletedFalse(id, companyId)
                .orElseThrow(() -> new BadRequestException("Enrollment request not found."));
        if (enrollment.getStatus() != DeviceEnrollmentStatus.PENDING
                || !enrollment.getExpiresAt().isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
            refreshExpiredStatus(enrollment, LocalDateTime.now(ZoneOffset.UTC));
            throw new ConflictException("Only a pending enrollment can be revoked.");
        }
        enrollment.setStatus(DeviceEnrollmentStatus.REVOKED);
        enrollment.setRevokedAt(LocalDateTime.now(ZoneOffset.UTC));
        enrollment.setEncryptedToken(null);
        auditLogService.log("DeviceEnrollment", id, "REVOKE", "Revoked pending agent enrollment");
    }

    @Transactional
    public void sendEnrollmentEmail(Long id) {
        long companyId = requiredTenant();
        DeviceEnrollment enrollment = enrollmentRepository.findByIdAndCompany_IdAndDeletedFalse(id, companyId)
                .orElseThrow(() -> new BadRequestException("Enrollment request not found."));
        requirePendingAndUnexpired(enrollment, LocalDateTime.now(ZoneOffset.UTC));
        String email = enrollment.getEmployee().getEmail();
        if (email == null || email.isBlank()) {
            throw new BadRequestException("The selected employee does not have an email address.");
        }
        boolean sent = emailService.sendDeviceEnrollmentEmail(
                email,
                enrollment.getEmployee().getFullName(),
                enrollmentUrl(decrypt(enrollment.getEncryptedToken())),
                enrollment.getCompany().getName());
        if (!sent) {
            throw new IllegalStateException("The enrollment email could not be sent. Check the configured email service.");
        }
        auditLogService.log("DeviceEnrollment", id, "EMAIL_SENT",
                "Sent agent enrollment instructions to employee " + enrollment.getEmployee().getId());
    }

    @Transactional
    public AgentEnrollmentResponse completeAgentEnrollment(String rawToken, DeviceInfoPayload info, String remoteIp) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BadRequestException("Enrollment token is required.");
        }
        DeviceEnrollment enrollment = findByToken(rawToken, true);
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        requirePendingAndUnexpired(enrollment, now);
        validateDeviceInfo(info);
        Long companyId = enrollment.getCompany().getId();
        String deviceId = info.getDeviceId().trim();
        if (deviceRepository.findByDeviceIdAndDeletedFalse(deviceId).isPresent()) {
            throw new ConflictException("This computer is already registered. Contact your IT administrator.");
        }

        long currentCount = deviceRepository.countByCompany_IdAndDeletedFalse(companyId);
        subscriptionService.ensureDeviceLimitNotExceeded(companyId, currentCount);

        String permanentToken = newEnrollmentToken();
        MonitoredDevice device = new MonitoredDevice();
        device.setDeviceId(deviceId);
        device.setDeviceName(safeDeviceName(info.getDeviceName(), enrollment.getEmployee().getFullName()));
        device.setHostname(safeOptional(info.getHostname(), 150));
        device.setMacAddress(safeOptional(info.getMacAddress(), 50));
        device.setMachineGuid(safeOptional(info.getMachineGuid(), 100));
        device.setOperatingSystem("Windows");
        device.setOsVersion(safeOptional(info.getOsVersion(), 50));
        device.setAgentVersion(safeOptional(info.getAgentVersion(), 50));
        device.setIpAddress(safeOptional(remoteIp, 50));
        device.setLastIpAddress(safeOptional(remoteIp, 50));
        device.setCompany(enrollment.getCompany());
        device.setEmployee(enrollment.getEmployee());
        device.setAssignedDate(now.toLocalDate());
        device.setAgentTokenHash(hashAgentToken(permanentToken));
        device.setStatus("OFFLINE");
        device.setActive(true);
        MonitoredDevice savedDevice = deviceRepository.save(device);

        enrollment.setDevice(savedDevice);
        enrollment.setStatus(DeviceEnrollmentStatus.USED);
        enrollment.setUsedAt(now);
        enrollment.setEncryptedToken(null);
        auditLogService.log("DeviceEnrollment", enrollment.getId(), "USED",
                "Agent enrolled device " + savedDevice.getId() + " for employee " + enrollment.getEmployee().getId());
        return new AgentEnrollmentResponse(permanentToken);
    }

    private DeviceEnrollment findByToken(String rawToken, boolean lock) {
        if (rawToken == null || rawToken.length() < 32 || rawToken.length() > 64) {
            throw new BadRequestException("Enrollment link is invalid.");
        }
        return (lock ? enrollmentRepository.findByTokenHashForUpdate(hashHex(rawToken))
                : enrollmentRepository.findByTokenHashAndDeletedFalse(hashHex(rawToken)))
                .orElseThrow(() -> new BadRequestException("Enrollment link is invalid."));
    }

    private void requirePendingAndUnexpired(DeviceEnrollment enrollment, LocalDateTime now) {
        if (enrollment.getStatus() != DeviceEnrollmentStatus.PENDING) {
            throw new ConflictException("This enrollment link is no longer available.");
        }
        if (!enrollment.getExpiresAt().isAfter(now)) {
            throw new ConflictException("This enrollment link has expired. Ask your IT administrator for a new link.");
        }
    }

    private void refreshExpiredStatus(DeviceEnrollment enrollment, LocalDateTime now) {
        if (enrollment.getStatus() == DeviceEnrollmentStatus.PENDING && !enrollment.getExpiresAt().isAfter(now)) {
            enrollment.setStatus(DeviceEnrollmentStatus.EXPIRED);
            enrollment.setEncryptedToken(null);
        }
    }

    private void validateDeviceInfo(DeviceInfoPayload info) {
        if (info == null || info.getDeviceId() == null || info.getDeviceId().trim().isEmpty()
                || info.getDeviceId().trim().length() > 100) {
            throw new BadRequestException("The agent supplied an invalid computer identifier.");
        }
        if (info.getOperatingSystem() == null || !info.getOperatingSystem().toLowerCase().contains("windows")) {
            throw new BadRequestException("This enrollment is for a Windows computer.");
        }
    }

    private String safeDeviceName(String name, String employeeName) {
        String value = name == null || name.isBlank() ? employeeName + "'s Windows PC" : name.trim();
        return value.substring(0, Math.min(value.length(), 150));
    }

    private String safeOptional(String value, int maxLength) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        return trimmed.substring(0, Math.min(trimmed.length(), maxLength));
    }

    private long requiredTenant() {
        Long companyId = TenantContext.getCurrentTenant();
        if (companyId == null) throw new BadRequestException("Company context is required.");
        return companyId;
    }

    private String enrollmentUrl(String rawToken) {
        String value = frontendUrl == null ? "" : frontendUrl.trim();
        if (value.isBlank() || value.contains(",")
                || !(value.startsWith("http://") || value.startsWith("https://"))) {
            throw new IllegalStateException("APP_FRONTEND_URL must contain exactly one HTTP application URL");
        }
        return value.replaceAll("/+$", "") + FRONTEND_ENROLLMENT_PATH + rawToken;
    }

    private String newEnrollmentToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashHex(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not hash enrollment token", exception);
        }
    }

    private String hashAgentToken(String value) {
        try {
            return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not hash agent token", exception);
        }
    }

    private String encrypt(String value) {
        try {
            byte[] iv = new byte[12];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(encryptionKey(), "AES"), new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(iv) + "."
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(encrypted);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not encrypt temporary enrollment token", exception);
        }
    }

    private String decrypt(String value) {
        if (value == null || value.isBlank()) {
            throw new ConflictException("This enrollment link is no longer available.");
        }
        try {
            String[] parts = value.split("\\.", 2);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(encryptionKey(), "AES"),
                    new GCMParameterSpec(128, Base64.getUrlDecoder().decode(parts[0])));
            return new String(cipher.doFinal(Base64.getUrlDecoder().decode(parts[1])), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Could not decrypt temporary enrollment token", exception);
        }
    }

    private byte[] encryptionKey() throws Exception {
        return java.util.Arrays.copyOf(
                MessageDigest.getInstance("SHA-256").digest(tokenEncryptionKey.getBytes(StandardCharsets.UTF_8)), 16);
    }
}
