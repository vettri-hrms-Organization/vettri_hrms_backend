package com.haodaone.employee.controller;

import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.config.DataSeeder;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.security.JwtService;
import com.haodaone.tenant.TenantContext;
import com.haodaone.user.entity.PermissionScope;
import com.haodaone.user.entity.Role;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.RoleRepository;
import com.haodaone.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmployeeDirectoryAuthorizationApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DataSeeder dataSeeder;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void hrAdminCanListEmployeesInTheirTenantAndLegacyRoleIsReconciled() throws Exception {
        String suffix = suffix();
        Company company = createCompany("HR directory " + suffix);
        Company otherCompany = createCompany("Other directory " + suffix);

        Role hrAdminRole = role("HR_ADMIN");
        hrAdminRole.setSystemDefined(false);
        hrAdminRole.setPermissions(Set.of());
        hrAdminRole.getPermissionScopes().clear();
        roleRepository.saveAndFlush(hrAdminRole);

        dataSeeder.run();
        hrAdminRole = role("HR_ADMIN");
        org.junit.jupiter.api.Assertions.assertTrue(hrAdminRole.isSystemDefined());
        org.junit.jupiter.api.Assertions.assertTrue(hrAdminRole.getPermissions().stream()
                .anyMatch(permission -> "EMPLOYEE_VIEW".equals(permission.getCode())));
        org.junit.jupiter.api.Assertions.assertEquals(PermissionScope.ORGANIZATION,
                hrAdminRole.getPermissionScopes().stream()
                        .filter(scope -> "EMPLOYEE_VIEW".equals(scope.getPermission().getCode()))
                        .findFirst().orElseThrow().getScope());

        User hrAdmin = createUser(company, hrAdminRole, suffix);
        Employee localEmployee = createEmployee(company, suffix);
        createEmployee(otherCompany, suffix);
        String token = tokenFor(hrAdmin, hrAdminRole);

        mockMvc.perform(get("/api/employees")
                        .param("search", suffix)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(localEmployee.getId().intValue())))
                .andExpect(jsonPath("$[0].fullName", containsString(suffix)));

        mockMvc.perform(get("/api/employees")
                        .param("search", suffix)
                        .header("Authorization", "Bearer " + token)
                        .header("X-Company-Id", otherCompany.getId().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeeAndCompanyAdminCannotListDirectoryAndMonitoringRemainsProtected() throws Exception {
        String suffix = suffix();
        Company company = createCompany("Protected directory " + suffix);
        Role employeeRole = role("EMPLOYEE");
        Role companyAdminRole = role("COMPANY_ADMIN");
        Role hrAdminRole = role("HR_ADMIN");
        User employee = createUser(company, employeeRole, suffix + "-employee");
        User companyAdmin = createUser(company, companyAdminRole, suffix + "-company-admin");
        User hrAdmin = createUser(company, hrAdminRole, suffix + "-hr-admin");

        mockMvc.perform(get("/api/employees"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/employees")
                        .header("Authorization", "Bearer " + tokenFor(employee, employeeRole)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/employees")
                        .header("Authorization", "Bearer " + tokenFor(companyAdmin, companyAdminRole)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/monitoring/devices")
                        .header("Authorization", "Bearer " + tokenFor(hrAdmin, hrAdminRole)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/monitoring/devices/employee-options")
                        .header("Authorization", "Bearer " + tokenFor(hrAdmin, hrAdminRole)))
                .andExpect(status().isForbidden());
    }

    @Test
    void itAdminUsesScopedEmployeeOptionsWithoutEmployeeDirectoryPermission() throws Exception {
        String suffix = suffix();
        Company company = createCompany("IT device mapping " + suffix);
        Company otherCompany = createCompany("Other IT tenant " + suffix);
        Role itAdminRole = role("IT_ADMINISTRATOR");
        User itAdmin = createUser(company, itAdminRole, suffix);
        Employee localEmployee = createEmployee(company, suffix);
        createEmployee(otherCompany, suffix);
        String token = tokenFor(itAdmin, itAdminRole);

        mockMvc.perform(get("/api/monitoring/devices/employee-options")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(localEmployee.getId().intValue())))
                .andExpect(jsonPath("$[0].fullName", containsString(suffix)))
                .andExpect(jsonPath("$[0].email").doesNotExist());

        mockMvc.perform(get("/api/monitoring/sessions/employee-options")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(localEmployee.getId().intValue())));

        mockMvc.perform(get("/api/employees")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/monitoring/devices")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/monitoring/devices/employee-options")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Company-Id", otherCompany.getId().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void superAdminCanSelectTenantAndListOnlyThatTenantEmployees() throws Exception {
        String suffix = suffix();
        Company homeCompany = createCompany("Super admin home " + suffix);
        Company selectedCompany = createCompany("Super admin selected " + suffix);
        Role superAdminRole = role("SUPER_ADMIN");
        User superAdmin = createUser(homeCompany, superAdminRole, suffix);
        Employee selectedEmployee = createEmployee(selectedCompany, suffix);
        createEmployee(homeCompany, suffix);

        mockMvc.perform(get("/api/employees")
                        .param("search", suffix)
                        .header("Authorization", "Bearer " + tokenFor(superAdmin, superAdminRole))
                        .header("X-Company-Id", selectedCompany.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is(selectedEmployee.getId().intValue())));
    }

    private Company createCompany(String name) {
        Company company = new Company();
        company.setName(name);
        return companyRepository.saveAndFlush(company);
    }

    private Role role(String name) {
        return roleRepository.findByName(name).orElseThrow();
    }

    private User createUser(Company company, Role role, String suffix) {
        User user = new User();
        user.setUsername("employee-directory-" + suffix);
        user.setEmail("employee-directory-" + suffix + "@example.test");
        user.setFullName("Directory User " + suffix);
        user.setPasswordHash(passwordEncoder.encode("test-password"));
        user.setActive(true);
        user.setAccountStatus("ACTIVE");
        user.setCompany(company);
        user.setRoles(Set.of(role));
        return userRepository.saveAndFlush(user);
    }

    private Employee createEmployee(Company company, String suffix) {
        Employee employee = new Employee();
        employee.setEmployeeCode("ED-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        employee.setFirstName("Directory" + suffix);
        employee.setLastName("Employee");
        employee.setEmail("directory-" + suffix + "-" + UUID.randomUUID() + "@example.test");
        employee.setDateOfJoining(LocalDate.of(2026, 1, 1));
        employee.setCompany(company);
        return employeeRepository.saveAndFlush(employee);
    }

    private String tokenFor(User user, Role role) {
        return jwtService.generateAccessToken(user.getUsername(), List.of(role.getName()));
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
