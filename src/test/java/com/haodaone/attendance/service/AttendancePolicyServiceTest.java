package com.haodaone.attendance.service;

import com.haodaone.attendance.entity.WfhRequest;
import com.haodaone.attendance.repository.AttendanceRecordRepository;
import com.haodaone.attendance.repository.AttendanceSessionRepository;
import com.haodaone.attendance.repository.DeviceRepository;
import com.haodaone.attendance.repository.WfhRequestRepository;
import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AttendancePolicyServiceTest {

    private WfhRequestRepository wfhRequestRepository;
    private AttendancePolicyService service;
    private Company company;
    private Employee employee;

    @BeforeEach
    void setUp() {
        wfhRequestRepository = mock(WfhRequestRepository.class);
        service = new AttendancePolicyService(
                mock(CompanyRepository.class),
                mock(EmployeeRepository.class),
                mock(DeviceRepository.class),
                wfhRequestRepository,
                mock(AttendanceSessionRepository.class),
                mock(AttendanceRecordRepository.class),
                mock(AuditLogService.class),
                Clock.fixed(java.time.Instant.parse("2026-10-05T06:00:00Z"), ZoneOffset.UTC));
        company = new Company();
        employee = new Employee();
    }

    @Test
    void biometricOnlyRejectsWebCheckInAndAllowsBiometricPunches() {
        company.setAttendanceMethod("BIOMETRIC_ONLY");

        assertThrows(BadRequestException.class,
                () -> service.validateWebCheckIn(employee, company, false, LocalDate.of(2026, 10, 5)));
        org.junit.jupiter.api.Assertions.assertTrue(service.isBiometricAllowed(employee, company));
    }

    @Test
    void webAppOnlyAllowsWebCheckInAndDoesNotAcceptBiometricAttendance() {
        company.setAttendanceMethod("WEB_APP_ONLY");

        assertDoesNotThrow(
                () -> service.validateWebCheckIn(employee, company, false, LocalDate.of(2026, 10, 5)));
        org.junit.jupiter.api.Assertions.assertFalse(service.isBiometricAllowed(employee, company));
    }

    @Test
    void hybridRequiresBiometricInOfficeAndApprovedWfhForRemoteCheckIn() {
        company.setAttendanceMethod("HYBRID");
        LocalDate date = LocalDate.of(2026, 10, 5);

        assertThrows(BadRequestException.class, () -> service.validateWebCheckIn(employee, company, false, date));
        assertThrows(BadRequestException.class, () -> service.validateWebCheckIn(employee, company, true, date));

        WfhRequest approval = new WfhRequest();
        approval.setStatus("APPROVED");
        when(wfhRequestRepository.findByEmployee_IdAndCompany_IdAndWorkDateAndDeletedFalse(null, null, date))
                .thenReturn(Optional.of(approval));

        assertDoesNotThrow(() -> service.validateWebCheckIn(employee, company, true, date));
    }

    @Test
    void authorizedEmployeeOverrideIsUsedForMethodResolution() {
        company.setAttendanceMethod("BIOMETRIC_ONLY");
        employee.setAttendanceMethodOverride("WEB_APP_ONLY");

        assertEquals("WEB_APP_ONLY", service.effectiveMethod(employee, company));
        assertDoesNotThrow(() -> service.validateWebCheckIn(employee, company, false, LocalDate.now()));
    }
}
