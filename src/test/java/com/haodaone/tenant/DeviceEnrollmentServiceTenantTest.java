package com.haodaone.tenant;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.monitoring.dto.MonitoredDeviceDTO;
import com.haodaone.monitoring.repository.MonitoredDeviceRepository;
import com.haodaone.monitoring.service.DeviceEnrollmentService;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.company.repository.SubscriptionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.mockito.Mockito.*;

public class DeviceEnrollmentServiceTenantTest {

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

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        reset(deviceRepository);
    }

    @Test
    void listAll_usesCompanyScopedRepoWhenTenantContextPresent() {
        // set tenant
        TenantContext.setCurrentTenant(5L);

        when(deviceRepository.findAllByCompany_IdAndDeletedFalseOrderByDeviceNameAsc(5L)).thenReturn(List.of());

        List<MonitoredDeviceDTO> result = deviceEnrollmentService.listAll();

        verify(deviceRepository, times(1)).findAllByCompany_IdAndDeletedFalseOrderByDeviceNameAsc(5L);
    }

    @Test
    void listAll_usesGlobalRepoWhenNoTenantContext() {
        TenantContext.clear();
        when(deviceRepository.findAllByDeletedFalseOrderByDeviceNameAsc()).thenReturn(List.of());

        deviceEnrollmentService.listAll();

        verify(deviceRepository, times(1)).findAllByDeletedFalseOrderByDeviceNameAsc();
    }
}
