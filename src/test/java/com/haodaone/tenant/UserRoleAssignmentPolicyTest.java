package com.haodaone.tenant;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.security.CompanySecurity;
import com.haodaone.security.AuthorizationService;
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
    private com.haodaone.user.repository.UserPermissionGrantRepository permissionGrantRepository;
    private AuthorizationService authorizationService;
    private UserService userService;

    @BeforeEach
    void setup() {
        userRepository = mock(UserRepository.class);
        roleRepository = mock(RoleRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        auditLogService = mock(AuditLogService.class);
        companyRepository = mock(CompanyRepository.class);
        employeeRepository = mock(EmployeeRepository.class);
        authorizationService = mock(AuthorizationService.class);
        Mockito.when(authorizationService.hasOrganizationScope(Mockito.anyString())).thenReturn(true);
        Mockito.when(authorizationService.canAssignRoleToUser(Mockito.any(Role.class), Mockito.anyLong()))
                .thenReturn(true);
        permissionGrantRepository = mock(com.haodaone.user.repository.UserPermissionGrantRepository.class);
        Mockito.when(permissionGrantRepository.findAllByCompany_IdAndUser_IdAndRevokedAtIsNullAndDeletedFalse(
                Mockito.anyLong(), Mockito.anyLong())).thenReturn(java.util.List.of());
        userService = new UserService(userRepository, roleRepository, passwordEncoder, auditLogService,
                companyRepository, employeeRepository, authorizationService,
                permissionGrantRepository);
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
    void hrManagerWithOrganizationRoleAssignmentAndDelegationCanAssignApprovedRole() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("hr-manager", null, "ROLE_HR_ADMIN"));
        TenantContext.setCurrentTenant(7L);
        Company company = new Company();
        company.setId(7L);
        User target = new User();
        target.setId(80L);
        target.setCompany(company);
        target.setActive(true);
        target.setAccountStatus("ACTIVE");
        Role employee = new Role();
        employee.setName("EMPLOYEE");
        employee.setCompany(company);

        when(userRepository.findByIdAndCompanyIdAndDeletedFalse(80L, 7L)).thenReturn(Optional.of(target));
        when(roleRepository.findByNameAndCompany_Id("EMPLOYEE", 7L)).thenReturn(Optional.of(employee));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserDTO result = userService.assignRoles(80L, Set.of("EMPLOYEE"));

        assertEquals(Set.of("EMPLOYEE"), Set.copyOf(result.getRoles()));
    }

    @Test
    void hrManagerCannotAssignRoleWhosePermissionsTheyCannotDelegate() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("hr-manager", null, "ROLE_HR_ADMIN"));
        TenantContext.setCurrentTenant(7L);
        Company company = new Company();
        company.setId(7L);
        User target = new User();
        target.setId(80L);
        target.setCompany(company);
        target.setActive(true);
        target.setAccountStatus("ACTIVE");
        Role privilegedRole = new Role();
        privilegedRole.setName("IT_ADMINISTRATOR");
        privilegedRole.setCompany(company);

        when(userRepository.findByIdAndCompanyIdAndDeletedFalse(80L, 7L)).thenReturn(Optional.of(target));
        when(roleRepository.findByNameAndCompany_Id("IT_ADMINISTRATOR", 7L)).thenReturn(Optional.of(privilegedRole));
        Mockito.when(authorizationService.canAssignRoleToUser(privilegedRole, 80L)).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> userService.assignRoles(80L, Set.of("IT_ADMINISTRATOR")));
        Mockito.verify(userRepository, Mockito.never()).save(any(User.class));
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

    @Test
    void companyAdmin_canAssignCompanyAdminToAnotherUserInCompany() {
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

        Role companyAdmin = new Role();
        companyAdmin.setName("COMPANY_ADMIN");
        companyAdmin.setCompany(company);
        when(roleRepository.findByNameAndCompany_Id("COMPANY_ADMIN", 7L)).thenReturn(Optional.of(companyAdmin));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserDTO result = userService.assignRoles(80L, Set.of("COMPANY_ADMIN"));

        assertEquals(Set.of("COMPANY_ADMIN"), result.getRoles().stream().collect(Collectors.toSet()));
    }

    @Test
    void companyAdminCanPreserveAnExistingCompanyAdminRoleWhileEditingOtherRoles() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("company-admin", null, "ROLE_COMPANY_ADMIN"));
        TenantContext.setCurrentTenant(7L);

        Company company = new Company();
        company.setId(7L);
        Role existingCompanyAdmin = new Role();
        existingCompanyAdmin.setName("COMPANY_ADMIN");
        existingCompanyAdmin.setCompany(company);
        Role hrAdmin = new Role();
        hrAdmin.setName("HR_ADMIN");
        hrAdmin.setCompany(company);
        User target = new User();
        target.setId(80L);
        target.setCompany(company);
        target.setActive(true);
        target.setAccountStatus("ACTIVE");
        target.setRoles(Set.of(existingCompanyAdmin));

        when(userRepository.findByIdAndCompanyIdAndDeletedFalse(80L, 7L)).thenReturn(Optional.of(target));
        when(roleRepository.findByNameAndCompany_Id("COMPANY_ADMIN", 7L)).thenReturn(Optional.of(existingCompanyAdmin));
        when(roleRepository.findByNameAndCompany_Id("HR_ADMIN", 7L)).thenReturn(Optional.of(hrAdmin));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserDTO result = userService.assignRoles(80L, Set.of("COMPANY_ADMIN", "HR_ADMIN"));

        assertEquals(Set.of("COMPANY_ADMIN", "HR_ADMIN"),
                result.getRoles().stream().collect(Collectors.toSet()));
    }

    @Test
    void userCannotAssignRolesToTheirOwnAccount() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("self", null, "ROLE_COMPANY_ADMIN"));
        TenantContext.setCurrentTenant(7L);

        Company company = new Company();
        company.setId(7L);
        User self = new User();
        self.setId(80L);
        self.setUsername("self");
        self.setCompany(company);
        self.setActive(true);
        self.setAccountStatus("ACTIVE");

        when(userRepository.findByIdAndCompanyIdAndDeletedFalse(80L, 7L)).thenReturn(Optional.of(self));
        when(userRepository.findByUsernameAndDeletedFalse("self")).thenReturn(Optional.of(self));

        assertThrows(AccessDeniedException.class, () -> userService.assignRoles(80L, Set.of("IT_ADMINISTRATOR")));
    }

    @Test
    void roleAssignmentPermissionAllowsNonAdminRoleManagerWithinOrganization() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("role-manager", null, "ROLE_EMPLOYEE", "ROLE_ASSIGN"));
        TenantContext.setCurrentTenant(7L);
        Company company = new Company();
        company.setId(7L);
        User target = new User();
        target.setId(80L);
        target.setCompany(company);
        target.setActive(true);
        target.setAccountStatus("ACTIVE");
        Role employee = new Role();
        employee.setName("EMPLOYEE");
        employee.setCompany(company);
        when(userRepository.findByIdAndCompanyIdAndDeletedFalse(80L, 7L)).thenReturn(Optional.of(target));
        when(roleRepository.findByNameAndCompany_Id("EMPLOYEE", 7L)).thenReturn(Optional.of(employee));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserDTO result = userService.assignRoles(80L, Set.of("EMPLOYEE"));

        assertEquals(Set.of("EMPLOYEE"), result.getRoles().stream().collect(Collectors.toSet()));
    }

    @Test
    void nonAdminCannotAssignRolesWithoutOrganizationScopedPermission() {
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken("employee", null, "ROLE_EMPLOYEE"));
        TenantContext.setCurrentTenant(7L);
        Company company = new Company();
        company.setId(7L);
        User target = new User();
        target.setId(80L);
        target.setCompany(company);
        when(userRepository.findByIdAndCompanyIdAndDeletedFalse(80L, 7L)).thenReturn(Optional.of(target));
        Mockito.when(authorizationService.hasOrganizationScope(Mockito.anyString())).thenReturn(false);

        assertThrows(AccessDeniedException.class, () -> userService.assignRoles(80L, Set.of("EMPLOYEE")));
    }
}
