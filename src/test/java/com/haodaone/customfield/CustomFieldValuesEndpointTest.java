package com.haodaone.customfield;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /api/custom-fields/employees/{id} is loaded by every employee profile (production returned 500). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CustomFieldValuesEndpointTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;

    @Test
    void employeeCanLoadTheirOwnCustomFields() throws Exception {
        Company company = company();
        User user = user(company, "EMPLOYEE");
        Employee employee = employee(company, user);

        mockMvc.perform(get("/api/custom-fields/employees/{id}", employee.getId())
                        .header("Authorization", "Bearer " + jwtService.generateAccessToken(user.getUsername(), List.of("EMPLOYEE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeId").value(employee.getId()));
    }

    @Test
    void hrAdminCanLoadAnEmployeesCustomFields() throws Exception {
        Company company = company();
        Employee employee = employee(company, user(company, "EMPLOYEE"));
        User hr = user(company, "HR_ADMIN");

        mockMvc.perform(get("/api/custom-fields/employees/{id}", employee.getId())
                        .header("Authorization", "Bearer " + jwtService.generateAccessToken(hr.getUsername(), List.of("HR_ADMIN"))))
                .andExpect(status().isOk());
    }

    /** Same @companySecurity.canManageEmployee guard on an employee-management endpoint (also 500'd before the fix). */
    @Test
    void hrAdminPassesTheManageEmployeeGuardForAnEmployeeWithALogin() throws Exception {
        Company company = company();
        Employee employee = employee(company, user(company, "EMPLOYEE"));
        User hr = user(company, "HR_ADMIN");

        mockMvc.perform(get("/api/employees/{id}/invitation", employee.getId())
                        .header("Authorization", "Bearer " + jwtService.generateAccessToken(hr.getUsername(), List.of("HR_ADMIN"))))
                .andExpect(result -> {
                    if (result.getResponse().getStatus() >= 500) {
                        throw new AssertionError("manage-employee guard failed with " + result.getResponse().getStatus()
                                + ": " + result.getResponse().getContentAsString());
                    }
                });
    }

    @Test
    void hrAdminCannotLoadCustomFieldsOfAnotherCompanysEmployee() throws Exception {
        Company other = company();
        Employee outsider = employee(other, user(other, "EMPLOYEE"));
        User hr = user(company(), "HR_ADMIN");

        mockMvc.perform(get("/api/custom-fields/employees/{id}", outsider.getId())
                        .header("Authorization", "Bearer " + jwtService.generateAccessToken(hr.getUsername(), List.of("HR_ADMIN"))))
                .andExpect(result -> {
                    int code = result.getResponse().getStatus();
                    if (code != 403 && code != 404) {
                        throw new AssertionError("cross-company read must be refused (403/404) but got " + code);
                    }
                });
    }

    private Company company() {
        Company company = new Company();
        company.setName("Custom fields " + UUID.randomUUID());
        return companyRepository.save(company);
    }

    private User user(Company company, String roleName) {
        Role role = roleRepository.findByName(roleName).orElseThrow();
        String username = "cf-" + UUID.randomUUID() + "@example.test";
        User user = new User();
        user.setUsername(username);
        user.setEmail(username);
        user.setFullName("Custom Field User");
        user.setPasswordHash(passwordEncoder.encode("Password1!"));
        user.setCompany(company);
        user.setActive(true);
        user.setAccountStatus("ACTIVE");
        user.setRoles(Set.of(role));
        return userRepository.save(user);
    }

    private Employee employee(Company company, User user) {
        Employee employee = new Employee();
        employee.setEmployeeCode("CF" + UUID.randomUUID().toString().substring(0, 8));
        employee.setFirstName("Sanjay");
        employee.setLastName("Ramesh");
        employee.setEmail(user.getEmail());
        employee.setDateOfJoining(LocalDate.of(2023, 1, 1));
        employee.setCompany(company);
        employee.setUser(user);
        employee.setStatus("Active");
        return employeeRepository.save(employee);
    }
}
