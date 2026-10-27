package com.haodaone.monitoring.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.monitoring.dto.BulkInventoryScanSummaryDTO;
import com.haodaone.monitoring.dto.HardwareInventoryPayload;
import com.haodaone.monitoring.dto.InventorySubmissionRequest;
import com.haodaone.monitoring.dto.MonitoredDeviceDTO;
import com.haodaone.monitoring.entity.DeviceInventoryScan;
import com.haodaone.monitoring.entity.MonitoredDevice;
import com.haodaone.monitoring.repository.DeviceEnrollmentRepository;
import com.haodaone.monitoring.repository.DeviceInventoryScanRepository;
import com.haodaone.monitoring.repository.MonitoredDeviceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeviceInventoryServiceTest {

    private final DeviceEnrollmentRepository enrollmentRepository = mock(DeviceEnrollmentRepository.class);
    private final DeviceInventoryScanRepository scanRepository = mock(DeviceInventoryScanRepository.class);
    private final MonitoredDeviceRepository deviceRepository = mock(MonitoredDeviceRepository.class);
    private final EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final AuditLogService auditLogService = mock(AuditLogService.class);

    private DeviceInventoryService service;
    private MonitoredDevice device;
    private DeviceInventoryScan scan;
    private UUID requestId;

    @BeforeEach
    void setUp() {
        com.haodaone.tenant.TenantContext.clear();
        service = new DeviceInventoryService(
                enrollmentRepository, scanRepository, deviceRepository,
                employeeRepository, objectMapper, auditLogService);
        device = new MonitoredDevice();
        device.setId(21L);
        device.setDeviceId("agent-device-21");
        scan = new DeviceInventoryScan();
        scan.setId(75L);
        scan.setDevice(device);
        requestId = UUID.randomUUID();
        scan.setRequestId(requestId);
        scan.setStatus("SCANNING");
        scan.setExpiresAt(OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(1).toLocalDateTime());
        when(deviceRepository.findByIdAndDeletedFalse(21L)).thenReturn(Optional.of(device));
        when(scanRepository.findByRequestIdForUpdate(requestId)).thenReturn(Optional.of(scan));
    }

    @Test
    void persistsInventoryForTheAuthenticatedRequestDevice() {
        InventorySubmissionRequest submission = new InventorySubmissionRequest();
        submission.setStatus("COMPLETED");
        HardwareInventoryPayload inventory = new HardwareInventoryPayload();
        inventory.setComputerName("VETTRI-LAPTOP");
        inventory.setManufacturer("Example Manufacturer");
        inventory.setModel("Example Model");
        inventory.setSerialNumber("SERIAL-123");
        inventory.setAgentVersion("2.0.1");
        inventory.setCollectedAt(OffsetDateTime.now(ZoneOffset.UTC));
        submission.setInventory(inventory);

        service.submitAgentInventory(device, requestId, submission);

        assertEquals("COMPLETED", scan.getStatus());
        assertEquals("COMPLETED", device.getInventoryStatus());
        assertEquals("SERIAL-123", device.getSerialNumber());
        assertNotNull(device.getLastInventoryScanAt());
        assertEquals("Example Model", device.getHardwareInventory().get("model").asText());
        verify(deviceRepository).save(device);
        verify(scanRepository).save(scan);
        verify(auditLogService).log("MonitoredDevice", 21L, "INVENTORY_SCAN_COMPLETED",
                "Agent submitted hardware inventory for authenticated device");
    }

    @Test
    void rejectsSubmissionForAnotherAgentsScanRequest() {
        MonitoredDevice otherDevice = new MonitoredDevice();
        otherDevice.setId(22L);
        scan.setDevice(otherDevice);

        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> service.submitAgentInventory(device, requestId, new InventorySubmissionRequest()));

        verify(deviceRepository, never()).save(any());
    }

    @Test
    void acknowledgesDuplicateSubmissionWithoutOverwritingInventory() {
        scan.setStatus("COMPLETED");
        device.setInventoryStatus("COMPLETED");

        service.submitAgentInventory(device, requestId, new InventorySubmissionRequest());

        verify(deviceRepository, never()).save(any());
        verify(scanRepository, never()).save(any());
    }

    @Test
    void rejectsInventoryCollectedOutsideTheFreshnessWindow() {
        InventorySubmissionRequest submission = new InventorySubmissionRequest();
        submission.setStatus("COMPLETED");
        HardwareInventoryPayload inventory = new HardwareInventoryPayload();
        inventory.setModel("Example Model");
        inventory.setAgentVersion("2.0.1");
        inventory.setCollectedAt(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));
        submission.setInventory(inventory);

        assertThrows(BadRequestException.class,
                () -> service.submitAgentInventory(device, requestId, submission));

        verify(deviceRepository, never()).save(any());
    }

    @Test
    void queuesAdminScanForOfflineDeviceAndReturnsQueuedStatus() {
        when(deviceRepository.findByIdAndDeletedFalseForUpdate(21L)).thenReturn(Optional.of(device));
        when(scanRepository.findFirstByDevice_IdAndDeletedFalseOrderByRequestedAtDesc(21L))
                .thenReturn(Optional.empty());
        when(scanRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        MonitoredDeviceDTO result = service.requestAdminScan(21L);

        assertEquals("PENDING", result.getInventoryStatus());
        assertEquals("PENDING", device.getInventoryStatus());
        var savedScan = org.mockito.ArgumentCaptor.forClass(DeviceInventoryScan.class);
        verify(scanRepository).save(savedScan.capture());
        assertTrue(savedScan.getValue().getExpiresAt()
                .isAfter(LocalDateTime.now(ZoneOffset.UTC).plusHours(23)));
        verify(auditLogService).log("MonitoredDevice", 21L, "INVENTORY_SCAN_REQUESTED",
                "Administrator queued a hardware inventory scan");
    }

    @Test
    void doesNotQueueDuplicateAdminScanWhileRequestIsPending() {
        scan.setStatus("PENDING");
        device.setInventoryStatus("PENDING");
        when(deviceRepository.findByIdAndDeletedFalseForUpdate(21L)).thenReturn(Optional.of(device));
        when(scanRepository.findFirstByDevice_IdAndDeletedFalseOrderByRequestedAtDesc(21L))
                .thenReturn(Optional.of(scan));

        MonitoredDeviceDTO result = service.requestAdminScan(21L);

        assertEquals("PENDING", result.getInventoryStatus());
        verify(scanRepository, never()).save(any());
        verify(deviceRepository, never()).save(any());
    }

    @Test
    void adminScanLookupIsScopedToCurrentTenant() {
        com.haodaone.tenant.TenantContext.setCurrentTenant(43L);
        when(deviceRepository.findByIdAndCompany_IdAndDeletedFalseForUpdate(21L, 43L))
                .thenReturn(Optional.of(device));
        when(scanRepository.findFirstByDevice_IdAndDeletedFalseOrderByRequestedAtDesc(21L))
                .thenReturn(Optional.empty());

        service.requestAdminScan(21L);

        verify(deviceRepository).findByIdAndCompany_IdAndDeletedFalseForUpdate(21L, 43L);
        verify(deviceRepository, never()).findByIdAndDeletedFalseForUpdate(anyLong());
    }

    @Test
    void rejectsAdminScanForInactiveDevice() {
        device.setActive(false);
        when(deviceRepository.findByIdAndDeletedFalseForUpdate(21L)).thenReturn(Optional.of(device));

        assertThrows(BadRequestException.class, () -> service.requestAdminScan(21L));

        verify(scanRepository, never()).save(any());
    }

    @Test
    void queuesBulkScanForEligibleDevicesAndCountsOfflineDevices() throws Exception {
        com.haodaone.tenant.TenantContext.setCurrentTenant(42L);

        MonitoredDevice online = new MonitoredDevice();
        online.setId(101L);
        online.setDeviceName("Online Laptop");
        online.setActive(true);
        online.setLastSeenAt(LocalDateTime.now(ZoneOffset.UTC));

        MonitoredDevice offline = new MonitoredDevice();
        offline.setId(102L);
        offline.setDeviceName("Offline Laptop");
        offline.setActive(true);
        offline.setLastSeenAt(LocalDateTime.now(ZoneOffset.UTC).minusMinutes(10));

        MonitoredDevice pending = new MonitoredDevice();
        pending.setId(103L);
        pending.setDeviceName("Pending Laptop");
        pending.setActive(true);
        pending.setLastSeenAt(LocalDateTime.now(ZoneOffset.UTC));

        DeviceInventoryScan pendingScan = new DeviceInventoryScan();
        pendingScan.setDevice(pending);
        pendingScan.setStatus("PENDING");
        pendingScan.setRequestedAt(LocalDateTime.now(ZoneOffset.UTC));
        pendingScan.setExpiresAt(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10));

        when(deviceRepository.findAllByCompany_IdAndDeletedFalseOrderByDeviceNameAsc(42L))
                .thenReturn(List.of(online, offline, pending));
        when(scanRepository.findFirstByDevice_IdAndDeletedFalseOrderByRequestedAtDesc(101L)).thenReturn(Optional.empty());
        when(scanRepository.findFirstByDevice_IdAndDeletedFalseOrderByRequestedAtDesc(102L)).thenReturn(Optional.empty());
        when(scanRepository.findFirstByDevice_IdAndDeletedFalseOrderByRequestedAtDesc(103L))
                .thenReturn(Optional.of(pendingScan));
        when(scanRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        BulkInventoryScanSummaryDTO summary = service.requestBulkInventoryScanForCurrentCompany();

        assertEquals(3, summary.getTotalDevices());
        assertEquals(2, summary.getQueuedCount());
        assertEquals(1, summary.getOfflineCount());
        verify(scanRepository, times(1)).save(any(DeviceInventoryScan.class));
        verify(deviceRepository, times(2)).save(any(MonitoredDevice.class));
    }

    @Test
    void sanitizesCsvExportsToPreventSpreadsheetFormulaInjection() throws Exception {
        com.haodaone.tenant.TenantContext.setCurrentTenant(55L);

        MonitoredDevice exportDevice = new MonitoredDevice();
        exportDevice.setId(555L);
        exportDevice.setDeviceId("device-555");
        exportDevice.setDeviceName("Laptop");
        exportDevice.setActive(true);
        exportDevice.setLastSeenAt(LocalDateTime.now(ZoneOffset.UTC));
        exportDevice.setInventoryStatus("COMPLETED");
        exportDevice.setHardwareInventory(objectMapper.readTree("{\"manufacturer\":\"Contoso\",\"model\":\"=cmd|'/C calc'\",\"windowsEdition\":\"Windows 11\",\"windowsVersion\":\"23H2\",\"agentVersion\":\"1.2.3\"}"));

        when(deviceRepository.findAllByCompany_IdAndDeletedFalseOrderByDeviceNameAsc(55L))
                .thenReturn(List.of(exportDevice));

        byte[] csv = service.exportInventoryReport("csv");
        String content = new String(csv, StandardCharsets.UTF_8);

        assertTrue(content.contains("'=cmd|'/C calc'"));
        assertFalse(content.contains("\n=cmd|'/C calc'"));
    }
}
