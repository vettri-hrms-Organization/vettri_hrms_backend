package com.haodaone.attendance.service;

import com.haodaone.attendance.entity.Device;
import com.haodaone.attendance.repository.AttendanceRecordRepository;
import com.haodaone.attendance.repository.DeviceRepository;
import com.haodaone.company.entity.Company;
import com.haodaone.employee.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AttendanceIngestServiceTest {
    private DeviceRepository deviceRepository;
    private AttendanceIngestService service;

    @BeforeEach
    void setUp() {
        deviceRepository = mock(DeviceRepository.class);
        service = new AttendanceIngestService(
                deviceRepository,
                mock(EmployeeRepository.class),
                mock(AttendanceRecordRepository.class),
                mock(AttendanceEventPublisher.class),
                Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void unknownDeviceCannotRegisterItselfDuringHandshake() {
        when(deviceRepository.findBySerialNumberAndDeletedFalse("UNKNOWN"))
                .thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class,
                () -> service.handleHandshake("UNKNOWN", null, "192.0.2.10"));

        verify(deviceRepository, never()).save(any(Device.class));
    }

    @Test
    void registeredDeviceMustHaveCompanyBeforeItCanSendPunches() {
        Device device = new Device();
        device.setSerialNumber("BIO-1");
        device.setDeviceName("Front Desk");
        when(deviceRepository.findBySerialNumberAndDeletedFalse("BIO-1"))
                .thenReturn(Optional.of(device));

        assertThrows(AccessDeniedException.class,
                () -> service.handleAttendanceLogs("BIO-1", "1001\t2026-10-01 09:00:00\t0", "192.0.2.10"));
    }

    @Test
    void registeredCompanyDeviceCompletesHandshake() {
        Company company = new Company();
        company.setId(7L);
        Device device = new Device();
        device.setSerialNumber("BIO-7");
        device.setDeviceName("Office Device");
        device.setCompany(company);
        when(deviceRepository.findBySerialNumberAndDeletedFalse("BIO-7"))
                .thenReturn(Optional.of(device));
        when(deviceRepository.save(any(Device.class))).thenAnswer(call -> call.getArgument(0));

        String response = service.handleHandshake("BIO-7", "1.0", "192.0.2.7");

        assertTrue(response.startsWith("GET OPTION FROM: BIO-7"));
        verify(deviceRepository, atLeastOnce()).save(device);
    }
}