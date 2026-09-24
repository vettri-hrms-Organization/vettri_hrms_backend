package com.haodaone.tenant;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.security.CompanySecurity;
import com.haodaone.tenant.TenantContext;
import com.haodaone.user.dto.UserDTO;
import com.haodaone.user.entity.Role;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.RoleRepository;
import com.haodaone.user.repository.UserRepository;
import com.haodaone.user.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class UserRoleAssignmentPolicyTest {

    private UserRepository userRepository;
    private RoleRepository roleRepository;
    private PasswordEncoder passwordEncoder;
    private AuditLogService auditLogService;
    private CompanyRepository companyRepository;
    private EmployeeRepository employeeRepository;
    private UserService userService;

    @BeforeEach
    void setup() {
        userRepository = mock(UserRepository.class);
        roleRepository = mock(RoleRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        auditLogService = mock(AuditLogService.class);
        companyRepository = mock(CompanyRepository.class);
        employeeRepository = mock(EmployeeRepository.class);
        userService = new UserService(userRepository, roleRepository, passwordEncoder, auditLogService, companyRepository, employeeRepository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void companyAdmin_canAssignHrAdminWithinCompany() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("company-admin", null, "ROLE_COMPANY_ADMIN"));
        TenantContext.setCurrentTenant(7L);

        Company company = new Company();
        company.setId(7L);

        User target = new User();
        target.setId(80L);
        target.setCompany(company);
        target.setActive(true);
        target.setAccountStatus("ACTIVE");

        Role hrAdmin = new Role();
        hrAdmin.setName("HR_ADMIN");
        hrAdmin.setCompany(company);

        when(userRepository.findByIdAndCompanyIdAndDeletedFalse(80L, 7L)).thenReturn(Optional.of(target));
        when(roleRepository.findByNameAndCompany_Id("HR_ADMIN", 7L)).thenReturn(Optional.of(hrAdmin));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserDTO result = userService.assignRoles(80L, Set.of("HR_ADMIN"));

        assertEquals(Set.of("HR_ADMIN"), result.getRoles().stream().collect(Collectors.toSet()));
    }

    @Test
    void companyAdmin_cannotAssignSuperAdmin() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("company-admin", null, "ROLE_COMPANY_ADMIN"));
        TenantContext.setCurrentTenant(7L);

        Company company = new Company();
        company.setId(7L);

        User target = new User();
        target.setId(80L);
        target.setCompany(company);
        target.setActive(true);
        target.setAccountStatus("ACTIVE");

        when(userRepository.findByIdAndCompanyIdAndDeletedFalse(80L, 7L)).thenReturn(Optional.of(target));

        assertThrows(AccessDeniedException.class, () -> userService.assignRoles(80L, Set.of("SUPER_ADMIN")));
    }
}
