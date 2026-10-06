package com.haodaone.org;

import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.org.entity.Department;
import com.haodaone.org.entity.Designation;
import com.haodaone.org.entity.Team;
import com.haodaone.org.repository.DepartmentRepository;
import com.haodaone.org.repository.DesignationRepository;
import com.haodaone.org.repository.TeamRepository;
import com.haodaone.security.JwtService;
import com.haodaone.tenant.TenantContext;
import com.haodaone.user.entity.Permission;
import com.haodaone.user.entity.Role;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.PermissionRepository;
import com.haodaone.user.repository.RoleRepository;
import com.haodaone.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
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
class OrganizationStructureApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PermissionRepository permissionRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private DepartmentRepository departmentRepository;
    @Autowired private DesignationRepository designationRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void structureReturnsCompanyHierarchyAndReportingNamesWithinAuthenticatedTenant() throws Exception {
        String suffix = suffix();
        Company company = createCompany("Organization " + suffix);
        Company otherCompany = createCompany("Other organization " + suffix);
        Role role = createRole("ORG_STRUCTURE_" + suffix, true);
        User user = createUser(company, role, suffix);

        Department department = new Department();
        department.setName("Engineering " + suffix);
        department.setCode("D" + suffix);
        department.setCompany(company);
        department = departmentRepository.saveAndFlush(department);

        Designation designation = new Designation();
        designation.setTitle("Engineer " + suffix);
        designation.setDepartment(department);
        designation = designationRepository.saveAndFlush(designation);

        Employee manager = createEmployee(company, "Zara", "Manager", "M" + suffix);
        Employee employee = createEmployee(company, "Ava", "Engineer", "E" + suffix);
        manager.setDepartment(department);
        manager = employeeRepository.saveAndFlush(manager);
        employee.setDepartment(department);
        employee.setDesignation(designation);
        employee.setReportingManager(manager);
        employee = employeeRepository.saveAndFlush(employee);

        Team team = new Team();
        team.setName("Platform " + suffix);
        team.setCompany(company);
        team.setDepartment(department);
        team.setLeadEmployeeId(employee.getId());
        team = teamRepository.saveAndFlush(team);
        employee.setTeam(team);
        employeeRepository.saveAndFlush(employee);

        createEmployee(company, "Una", "Assigned", "U" + suffix);
        Employee foreignEmployee = createEmployee(otherCompany, "Foreign", "Person", "F" + suffix);
        String token = tokenFor(user, role);

        mockMvc.perform(get("/api/organization/structure")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyId", is(company.getId().intValue())))
                .andExpect(jsonPath("$.companyName", is(company.getName())))
                .andExpect(jsonPath("$.employeeCount", is(3)))
                .andExpect(jsonPath("$.departmentCount", is(1)))
                .andExpect(jsonPath("$.employees", hasSize(3)))
                .andExpect(jsonPath("$.departments[0].teams[0].employees[0].fullName", containsString("Ava Engineer")))
                .andExpect(jsonPath("$.departments[0].teams[0].employees[0].designation", is(designation.getTitle())))
                .andExpect(jsonPath("$.departments[0].teams[0].employees[0].reportingManagerId", is(manager.getId().intValue())))
                .andExpect(jsonPath("$.departments[0].teams[0].employees[0].reportingManagerName", is(manager.getFullName())))
                .andExpect(jsonPath("$.departments[0].teams[0].leadEmployeeName", containsString("Ava Engineer")))
                .andExpect(jsonPath("$.employees[2].id", is(manager.getId().intValue())))
                .andExpect(jsonPath("$.employees[2].fullName", containsString("Zara Manager")))
                .andExpect(jsonPath("$.employees[?(@.id == " + foreignEmployee.getId() + ")]", hasSize(0)))
                .andExpect(jsonPath("$.departments[1].name", is("Unassigned Department")))
                .andExpect(jsonPath("$.departments[1].teams[0].name", is("Unassigned Team")))
                .andExpect(jsonPath("$.departments[1].teams[0].employeeCount", is(1)));
    }

    @Test
    void structureRequiresAuthenticationAndOrganizationViewPermission() throws Exception {
        String suffix = suffix();
        Company company = createCompany("Protected organization " + suffix);
        Role role = createRole("ORG_STRUCTURE_NO_PERMISSION_" + suffix, false);
        User user = createUser(company, role, suffix);
        String token = tokenFor(user, role);

        mockMvc.perform(get("/api/organization/structure"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/organization/structure")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/organization/structure")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void nonSuperAdminCannotSelectAnotherCompanyForStructure() throws Exception {
        String suffix = suffix();
        Company company = createCompany("Tenant organization " + suffix);
        Company foreignCompany = createCompany("Foreign tenant " + suffix);
        Role role = createRole("ORG_STRUCTURE_TENANT_" + suffix, true);
        User user = createUser(company, role, suffix);
        String token = tokenFor(user, role);

        mockMvc.perform(get("/api/organization/structure")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Company-Id", foreignCompany.getId().toString()))
                .andExpect(status().isForbidden());
    }

    private Company createCompany(String name) {
        Company company = new Company();
        company.setName(name);
        return companyRepository.saveAndFlush(company);
    }

    private Role createRole(String name, boolean withOrgView) {
        Permission permission = null;
        if (withOrgView) {
            permission = permissionRepository.findByCode("ORG_VIEW").orElseGet(() -> {
                Permission created = new Permission();
                created.setCode("ORG_VIEW");
                created.setDescription("View organization");
                created.setModule("Organization");
                return permissionRepository.saveAndFlush(created);
            });
        }
        Role role = new Role();
        role.setName(name);
        role.setLabel(name);
        role.setPermissions(permission == null ? Set.of() : Set.of(permission));
        return roleRepository.saveAndFlush(role);
    }

    private User createUser(Company company, Role role, String suffix) {
        User user = new User();
        user.setUsername("org-structure-" + suffix);
        user.setEmail("org-structure-" + suffix + "@example.test");
        user.setFullName("Organization Structure User");
        user.setPasswordHash(passwordEncoder.encode("test-password"));
        user.setCompany(company);
        user.setRoles(Set.of(role));
        return userRepository.saveAndFlush(user);
    }

    private Employee createEmployee(Company company, String firstName, String lastName, String code) {
        Employee employee = new Employee();
        employee.setEmployeeCode(code);
        employee.setFirstName(firstName);
        employee.setLastName(lastName);
        employee.setEmail(code.toLowerCase() + "@example.test");
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
