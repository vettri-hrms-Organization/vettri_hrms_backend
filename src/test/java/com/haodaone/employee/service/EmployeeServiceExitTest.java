package com.haodaone.employee.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.auth.repository.RefreshTokenRepository;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.company.repository.SubscriptionService;
import com.haodaone.employee.dto.EmployeeDetailDTO;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.entity.EmploymentStatus;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.org.repository.DepartmentRepository;
import com.haodaone.org.repository.DesignationRepository;
import com.haodaone.org.repository.TeamRepository;
import com.haodaone.security.AuthorizationService;
import com.haodaone.tenant.TenantContext;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class EmployeeServiceExitTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Kolkata"));

    private final EmployeeRepository employeeRepository = mock(EmployeeRepository.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);
    private final EmployeeService employeeService = new EmployeeService(employeeRepository,
            mock(DepartmentRepository.class), mock(DesignationRepository.class), mock(TeamRepository.class),
            auditLogService, userRepository, mock(CompanyRepository.class), mock(SubscriptionService.class),
            mock(AuthorizationService.class), refreshTokenRepository);

    private Employee employee;
    private User user;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(10L);
        Company company = new Company();
        company.setId(10L);
        user = new User();
        user.setId(99L);
        user.setAccountStatus("ACTIVE");
        user.setActive(true);
        employee = new Employee();
        employee.setId(7L);
        employee.setEmployeeCode("EMP0007");
        employee.setFirstName("Exit");
        employee.setLastName("Test");
        employee.setEmail("exit@example.com");
        employee.setDateOfJoining(TODAY.minusYears(2));
        employee.setCompany(company);
        employee.setUser(user);
        when(employeeRepository.findByIdAndCompany_IdAndDeletedFalse(7L, 10L)).thenReturn(Optional.of(employee));
        when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void resignationWithoutDateDisablesLoginTodayAndRevokesTokens() {
        EmployeeDetailDTO dto = employeeService.updateStatus(7L, "RESIGNED", "moving on", null);

        assertEquals(EmploymentStatus.RESIGNED, dto.getStatus());
        assertEquals(TODAY, employee.getLastWorkingDate());
        assertEquals(TODAY, dto.getLastWorkingDate());
        assertEquals("DISABLED", user.getAccountStatus());
        assertFalse(user.isActive());
        verify(refreshTokenRepository).revokeAllForUser(99L);
        verify(userRepository).save(user);
    }

    @Test
    void terminationWithFutureLastWorkingDateKeepsLoginActive() {
        LocalDate future = TODAY.plusDays(14);

        employeeService.updateStatus(7L, "Terminated", null, future);

        assertEquals(future, employee.getLastWorkingDate());
        assertEquals("ACTIVE", user.getAccountStatus());
        assertTrue(user.isActive());
        verify(refreshTokenRepository, never()).revokeAllForUser(anyLong());
    }

    @Test
    void reactivatingDoesNotReEnableLogin() {
        employeeService.updateStatus(7L, "Resigned", null, null);
        assertEquals("DISABLED", user.getAccountStatus());

        employeeService.updateStatus(7L, "Active", "rehired", null);

        assertEquals(EmploymentStatus.ACTIVE, employee.getStatus());
        assertNull(employee.getLastWorkingDate());
        assertEquals("DISABLED", user.getAccountStatus());
        assertFalse(user.isActive());
    }

    @Test
    void nonExitStatusDoesNotTouchLogin() {
        employeeService.updateStatus(7L, "Notice Period", null, TODAY.minusDays(1));

        assertNull(employee.getLastWorkingDate());
        assertEquals("ACTIVE", user.getAccountStatus());
        verifyNoInteractions(refreshTokenRepository);
    }

    @Test
    void lastWorkingDateBeforeJoiningIsRejected() {
        assertThrows(BadRequestException.class,
                () -> employeeService.updateStatus(7L, "Resigned", null, TODAY.minusYears(3)));
        assertEquals("ACTIVE", user.getAccountStatus());
    }

    @Test
    void manualDisableRevokesRefreshTokens() {
        employeeService.setAccountStatus(7L, "DISABLED");

        assertEquals("DISABLED", user.getAccountStatus());
        verify(refreshTokenRepository).revokeAllForUser(99L);
    }

    @Test
    void dailySweepDisablesLoginsPastLastWorkingDate() {
        TenantContext.clear();
        employee.setStatus(EmploymentStatus.RESIGNED);
        employee.setLastWorkingDate(TODAY.minusDays(1));
        when(employeeRepository.findExitedWithActiveLogin(anySet(), eq(TODAY))).thenReturn(List.of(employee));

        int disabled = employeeService.disableLoginsPastLastWorkingDate(TODAY);

        assertEquals(1, disabled);
        assertEquals("DISABLED", user.getAccountStatus());
        assertFalse(user.isActive());
        verify(refreshTokenRepository).revokeAllForUser(99L);
        assertNull(TenantContext.getCurrentTenant());
    }

    @Test
    void dailySweepQueriesBothExitStatuses() {
        when(employeeRepository.findExitedWithActiveLogin(anySet(), any())).thenReturn(List.of());

        employeeService.disableLoginsPastLastWorkingDate(TODAY);

        verify(employeeRepository).findExitedWithActiveLogin(argThat((Set<String> s) ->
                s.contains(EmploymentStatus.RESIGNED) && s.contains(EmploymentStatus.TERMINATED)
                        && !s.contains(EmploymentStatus.ACTIVE)), eq(TODAY));
    }
}
