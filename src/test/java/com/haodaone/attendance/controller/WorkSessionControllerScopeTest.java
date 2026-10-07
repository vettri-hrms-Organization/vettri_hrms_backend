package com.haodaone.attendance.controller;

import com.haodaone.attendance.repository.WorkSessionRepository;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.monitoring.repository.MonitoredDeviceRepository;
import com.haodaone.security.AuthorizationService;
import com.haodaone.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkSessionControllerScopeTest {

    private final WorkSessionRepository workSessionRepository = mock(WorkSessionRepository.class);
    private final AuthorizationService authorizationService = mock(AuthorizationService.class);
    private final WorkSessionController controller = new WorkSessionController(
            workSessionRepository,
            mock(EmployeeRepository.class),
            mock(MonitoredDeviceRepository.class),
            Clock.fixed(Instant.parse("2026-03-04T12:00:00Z"), ZoneOffset.UTC),
            authorizationService);

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void listQueriesOnlyEmployeesAllowedByAttendanceScope() {
        TenantContext.setCurrentTenant(41L);
        when(authorizationService.resolveEmployeeIds("ATTENDANCE_VIEW"))
                .thenReturn(Optional.of(Set.of(7L, 8L)));

        controller.list(null, null);

        verify(workSessionRepository)
                .findAllByCompany_IdAndEmployee_IdInAndSessionDateOrderByLoginTimeDesc(
                        41L, Set.of(7L, 8L), LocalDate.of(2026, 3, 4));
    }
}
