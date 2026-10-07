package com.haodaone.monitoring.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ConflictException;
import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.company.repository.SubscriptionService;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.monitoring.dto.AgentEnrollmentResponse;
import com.haodaone.monitoring.dto.DeviceEnrollmentDTO;
import com.haodaone.monitoring.dto.DeviceEnrollmentRequest;
import com.haodaone.monitoring.dto.DeviceInfoPayload;
import com.haodaone.monitoring.entity.DeviceEnrollment;
import com.haodaone.monitoring.entity.DeviceEnrollmentStatus;
import com.haodaone.monitoring.entity.MonitoredDevice;
import com.haodaone.monitoring.repository.DeviceEnrollmentRepository;
import com.haodaone.monitoring.repository.MonitoredDeviceRepository;
import com.haodaone.security.AuthorizationService;
import com.haodaone.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DeviceOnboardingServiceTest {

    private final DeviceEnrollmentRepository enrollmentRepository = mock(DeviceEnrollmentRepository.class);
    private final EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
    private final MonitoredDeviceRepository deviceRepository = mock(MonitoredDeviceRepository.class);
    private final CompanyRepository companyRepository = mock(CompanyRepository.class);
    private final SubscriptionService subscriptionService = mock(SubscriptionService.class);
    private final AuthorizationService authorizationService = mock(AuthorizationService.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final com.haodaone.recruitment.service.EmailService emailService =
            mock(com.haodaone.recruitment.service.EmailService.class);
    private final AgentInstallerStorageService installerStorageService = mock(AgentInstallerStorageService.class);

    private DeviceOnboardingService service;
    private Company company;
    private Employee employee;

    @BeforeEach
    void setUp() {
        service = new DeviceOnboardingService(enrollmentRepository, employeeRepository, deviceRepository,
                companyRepository, subscriptionService, authorizationService, auditLogService, emailService,
                installerStorageService);
        ReflectionTestUtils.setField(service, "frontendUrl", "https://app.example.test");
        ReflectionTestUtils.setField(service, "tokenEncryptionKey", "test-enrollment-encryption-key");

        TenantContext.setCurrentTenant(15L);
        company = new Company();
        company.setId(15L);
        company.setName("Acme");
        employee = new Employee();
        employee.setId(42L);
        employee.setFirstName("Pat");
        employee.setLastName("Employee");
        employee.setEmail("pat@example.test");

        when(companyRepository.findById(15L)).thenReturn(Optional.of(company));
        when(employeeRepository.findByIdAndCompany_IdAndDeletedFalse(42L, 15L)).thenReturn(Optional.of(employee));
        when(authorizationService.isAllowed("MONITORING_MANAGE", "EMPLOYEE", 42L)).thenReturn(true);
        when(enrollmentRepository.save(any(DeviceEnrollment.class))).thenAnswer(invocation -> {
            DeviceEnrollment enrollment = invocation.getArgument(0);
            enrollment.setId(77L);
            enrollment.setCreatedAt(LocalDateTime.now());
            return enrollment;
        });
    }

    @AfterEach
    void cleanTenant() {
        TenantContext.clear();
    }

    @Test
    void createsTenantBoundEnrollmentAndExchangesTokenOnce() {
        DeviceEnrollmentDTO created = service.create(requestFor(42L));
        DeviceEnrollment enrollment = captureEnrollment();
        String rawToken = created.enrollmentUrl().substring(created.enrollmentUrl().lastIndexOf('/') + 1);
        when(enrollmentRepository.findByTokenHashForUpdate(anyString())).thenReturn(Optional.of(enrollment));
        when(deviceRepository.findByDeviceIdAndDeletedFalse("machine-1")).thenReturn(Optional.empty());
        when(deviceRepository.countByCompany_IdAndDeletedFalse(15L)).thenReturn(0L);
        when(deviceRepository.save(any(MonitoredDevice.class))).thenAnswer(invocation -> {
            MonitoredDevice device = invocation.getArgument(0);
            device.setId(91L);
            return device;
        });

        AgentEnrollmentResponse response = service.completeAgentEnrollment(rawToken, windowsDevice(), "192.0.2.10");

        assertNotNull(response.agentToken());
        assertEquals(DeviceEnrollmentStatus.USED, enrollment.getStatus());
        assertNull(enrollment.getEncryptedToken());
        assertEquals(15L, enrollment.getCompany().getId());
        assertEquals(42L, enrollment.getEmployee().getId());
        verify(deviceRepository).save(argThat(device ->
                device.getCompany().getId().equals(15L)
                        && device.getEmployee().getId().equals(42L)
                        && device.getAgentTokenHash() != null));
        assertThrows(ConflictException.class,
                () -> service.completeAgentEnrollment(rawToken, windowsDevice(), "192.0.2.10"));
        verify(deviceRepository, times(1)).save(any(MonitoredDevice.class));
    }

    @Test
    void refusesAnEmployeeOutsideTheCurrentTenant() {
        when(employeeRepository.findByIdAndCompany_IdAndDeletedFalse(999L, 15L)).thenReturn(Optional.empty());
        when(authorizationService.isAllowed("MONITORING_MANAGE", "EMPLOYEE", 999L)).thenReturn(true);

        assertThrows(BadRequestException.class, () -> service.create(requestFor(999L)));

        verify(employeeRepository).findByIdAndCompany_IdAndDeletedFalse(999L, 15L);
        verifyNoInteractions(enrollmentRepository);
    }

    @Test
    void refusesEnrollmentWhenManageScopeDoesNotIncludeEmployee() {
        when(authorizationService.isAllowed("MONITORING_MANAGE", "EMPLOYEE", 42L)).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> service.create(requestFor(42L)));

        verifyNoInteractions(enrollmentRepository);
        verifyNoInteractions(companyRepository);
    }

    private DeviceEnrollment captureEnrollment() {
        var captor = org.mockito.ArgumentCaptor.forClass(DeviceEnrollment.class);
        verify(enrollmentRepository).save(captor.capture());
        return captor.getValue();
    }

    private static DeviceEnrollmentRequest requestFor(Long employeeId) {
        DeviceEnrollmentRequest request = new DeviceEnrollmentRequest();
        request.setEmployeeId(employeeId);
        return request;
    }

    private static DeviceInfoPayload windowsDevice() {
        DeviceInfoPayload device = new DeviceInfoPayload();
        device.setDeviceId("machine-1");
        device.setDeviceName("Pat's PC");
        device.setOperatingSystem("Windows 11");
        device.setAgentVersion("2.0.1");
        return device;
    }
}
