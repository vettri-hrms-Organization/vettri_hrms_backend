package com.haodaone.tenant;

import com.haodaone.company.entity.Company;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.monitoring.repository.MonitoredDeviceRepository;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.UserRepository;
import com.haodaone.security.CompanySecurity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

public class CompanySecurityTest {

    private EmployeeRepository employeeRepository = Mockito.mock(EmployeeRepository.class);
    private MonitoredDeviceRepository monitoredDeviceRepository = Mockito.mock(MonitoredDeviceRepository.class);
    private UserRepository userRepository = Mockito.mock(UserRepository.class);

    private CompanySecurity companySecurity;

    @BeforeEach
    void setup() {
        companySecurity = new CompanySecurity(employeeRepository, monitoredDeviceRepository, userRepository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void superAdmin_canManageAnyEmployee() {
        // authentication with SUPER_ADMIN authority
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("sysadmin", null, "ROLE_SUPER_ADMIN"));

        assertTrue(companySecurity.isSuperAdmin());
        assertTrue(companySecurity.canManageEmployee(123L)); // no repository interaction required for super admin
    }

    @Test
    void companyAdmin_sameCompany_canManageEmployee() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("alice", null, "ROLE_COMPANY_ADMIN"));

        User current = new User();
        Company c = new Company();
        c.setId(1L);
        current.setCompany(c);

        when(userRepository.findByUsernameAndDeletedFalse("alice")).thenReturn(Optional.of(current));

        Employee target = new Employee();
        target.setCompany(c);
        when(employeeRepository.findById(42L)).thenReturn(Optional.of(target));

        assertTrue(companySecurity.canManageEmployee(42L));
    }

    @Test
    void companyAdmin_differentCompany_cannotManageEmployee() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("bob", null, "ROLE_COMPANY_ADMIN"));

        User current = new User();
        Company c1 = new Company();
        c1.setId(1L);
        current.setCompany(c1);

        Company c2 = new Company();
        c2.setId(2L);

        when(userRepository.findByUsernameAndDeletedFalse("bob")).thenReturn(Optional.of(current));

        Employee target = new Employee();
        target.setCompany(c2);
        when(employeeRepository.findById(anyLong())).thenReturn(Optional.of(target));

        assertFalse(companySecurity.canManageEmployee(99L));
    }
}
