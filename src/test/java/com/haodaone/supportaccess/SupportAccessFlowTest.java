package com.haodaone.supportaccess;

import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.security.JwtService;
import com.haodaone.user.entity.Role;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.RoleRepository;
import com.haodaone.user.repository.UserRepository;
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
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Platform staff see business data only; a company's HR data opens to them just while the company
 * admin allows it (read-only by default), and demo workspaces stay open for sales demos.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SupportAccessFlowTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;

    @Test
    void superAdminNeedsTheCompanysPermissionAndReadOnlyBlocksWrites() throws Exception {
        String suffix = suffix();
        Company customer = company("Customer " + suffix, false);
        User companyAdmin = user(customer, "COMPANY_ADMIN", "admin-" + suffix);
        String superAdmin = superAdminToken(suffix);
        employee(customer, suffix);

        // No grant: the company switch is refused.
        mockMvc.perform(get("/api/employees").header("Authorization", superAdmin).header("X-Company-Id", customer.getId().toString()))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString("has not granted Vettri support access")));

        // The company admin allows 4 hours of read-only support access.
        mockMvc.perform(post("/api/support-access").header("Authorization", token(companyAdmin, "COMPANY_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"hours\":4,\"accessLevel\":\"READ_ONLY\",\"reason\":\"Payroll question\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.accessLevel").value("READ_ONLY"));

        mockMvc.perform(get("/api/support-access/status").header("Authorization", token(companyAdmin, "COMPANY_ADMIN")))
                .andExpect(jsonPath("$.active").value(true));
        mockMvc.perform(get("/api/admin/support-access/companies").header("Authorization", superAdmin))
                .andExpect(content().string(containsString("Customer " + suffix)));
        mockMvc.perform(get("/api/employees").header("Authorization", superAdmin).header("X-Company-Id", customer.getId().toString()))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/employees").header("Authorization", superAdmin).header("X-Company-Id", customer.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(content().string(containsString("read-only")));

        // Revoked: closed again.
        mockMvc.perform(delete("/api/support-access").header("Authorization", token(companyAdmin, "COMPANY_ADMIN")))
                .andExpect(jsonPath("$.active").value(false));
        mockMvc.perform(get("/api/employees").header("Authorization", superAdmin).header("X-Company-Id", customer.getId().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    void platformStaffCannotGrantThemselvesAccess() throws Exception {
        String suffix = suffix();
        Company customer = company("Self grant " + suffix, false);
        User companyAdmin = user(customer, "COMPANY_ADMIN", "self-" + suffix);
        mockMvc.perform(post("/api/support-access").header("Authorization", token(companyAdmin, "COMPANY_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"hours\":1,\"accessLevel\":\"FULL\"}"))
                .andExpect(status().isOk());

        // Even inside a FULL support session, the platform admin cannot extend or re-grant access.
        mockMvc.perform(post("/api/support-access").header("Authorization", superAdminToken(suffix))
                        .header("X-Company-Id", customer.getId().toString())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"hours\":72,\"accessLevel\":\"FULL\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void demoWorkspacesStayOpenToPlatformStaff() throws Exception {
        String suffix = suffix();
        Company demo = company("Demo " + suffix, true);
        mockMvc.perform(get("/api/employees").header("Authorization", superAdminToken(suffix)).header("X-Company-Id", demo.getId().toString()))
                .andExpect(status().isOk());
    }

    @Test
    void platformOverviewShowsCountsButNoEmployeeDetails() throws Exception {
        String suffix = suffix();
        Company customer = company("Overview " + suffix, false);
        user(customer, "COMPANY_ADMIN", "owner-" + suffix);
        employee(customer, suffix);

        mockMvc.perform(get("/api/admin/platform-overview").header("Authorization", superAdminToken(suffix)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Overview " + suffix)))
                .andExpect(content().string(not(containsString("Secretname" + suffix))));

        User companyAdmin = user(customer, "COMPANY_ADMIN", "other-" + suffix);
        mockMvc.perform(get("/api/admin/platform-overview").header("Authorization", token(companyAdmin, "COMPANY_ADMIN")))
                .andExpect(status().isForbidden());
    }

    private Company company(String name, boolean demo) {
        Company company = new Company();
        company.setName(name);
        company.setDemo(demo);
        return companyRepository.saveAndFlush(company);
    }

    private User user(Company company, String roleName, String username) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        User user = new User();
        user.setUsername(username + "@example.test");
        user.setEmail(username + "@example.test");
        user.setFullName("User " + username);
        user.setPasswordHash(passwordEncoder.encode("test-password"));
        user.setActive(true);
        user.setAccountStatus("ACTIVE");
        user.setCompany(company);
        user.setRoles(Set.of(role));
        return userRepository.saveAndFlush(user);
    }

    private String superAdminToken(String suffix) {
        Company home = company("Platform home " + suffix, false);
        return token(user(home, "SUPER_ADMIN", "platform-" + suffix), "SUPER_ADMIN");
    }

    private void employee(Company company, String suffix) {
        Employee employee = new Employee();
        employee.setEmployeeCode("SA-" + suffix);
        employee.setFirstName("Secretname" + suffix);
        employee.setLastName("Person");
        employee.setEmail("person-" + suffix + "@example.test");
        employee.setDateOfJoining(LocalDate.of(2026, 1, 1));
        employee.setCompany(company);
        employeeRepository.saveAndFlush(employee);
    }

    private String token(User user, String role) {
        return "Bearer " + jwtService.generateAccessToken(user.getUsername(), List.of(role));
    }

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
