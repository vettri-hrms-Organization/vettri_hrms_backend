package com.haodaone.tenant;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.monitoring.dto.MonitoredDeviceDTO;
import com.haodaone.monitoring.repository.MonitoredDeviceRepository;
import com.haodaone.monitoring.service.DeviceEnrollmentService;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.company.repository.SubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

public class DeviceLimitEnforcementTest {

    private MonitoredDeviceRepository deviceRepository = Mockito.mock(MonitoredDeviceRepository.class);
    private EmployeeRepository employeeRepository = Mockito.mock(EmployeeRepository.class);
    private AuditLogService auditLogService = Mockito.mock(AuditLogService.class);
    private CompanyRepository companyRepository = Mockito.mock(CompanyRepository.class);
    private SubscriptionService subscriptionService = Mockito.mock(SubscriptionService.class);

    private DeviceEnrollmentService deviceEnrollmentService;

    @BeforeEach
    void setup() {
        deviceEnrollmentService = new DeviceEnrollmentService(deviceRepository, employeeRepository, auditLogService, companyRepository, subscriptionService);
    }

    @Test
    void enroll_rejectsWhenSubscriptionServiceThrows() {
        MonitoredDeviceDTO.EnrollRequest req = new MonitoredDeviceDTO.EnrollRequest();
        req.setDeviceName("TestDevice");

        // Simulate subscription service rejecting due to limit
        doThrow(new BadRequestException("Device limit reached")).when(subscriptionService).ensureDeviceLimitNotExceeded(anyLong(), anyLong());

        // Set tenant in context so companyId is available
        com.haodaone.tenant.TenantContext.setCurrentTenant(7L);

        assertThrows(BadRequestException.class, () -> deviceEnrollmentService.enroll(req));

        verify(subscriptionService, times(1)).ensureDeviceLimitNotExceeded(eq(7L), anyLong());
    }
}
