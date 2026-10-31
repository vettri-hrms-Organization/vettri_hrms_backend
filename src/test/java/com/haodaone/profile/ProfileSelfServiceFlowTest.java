package com.haodaone.profile;

import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.notifications.service.NotificationService;
import com.haodaone.security.JwtService;
import com.haodaone.user.entity.Role;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.RoleRepository;
import com.haodaone.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import com.haodaone.config.DataSeeder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Keka-style self-service profile: employees edit their own everyday details directly, identity and
 * bank details wait for HR approval, HR edits others directly, and nobody edits or approves for themselves
 * what they shouldn't.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProfileSelfServiceFlowTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;
    /** notification is a Flyway-only table; the flow itself is what's under test here. */
    @MockitoBean private NotificationService notificationService;
    @Autowired private DataSeeder dataSeeder;

    /** Other tests share this database and rewrite system roles; start from the canonical ones. */
    @BeforeEach
    void restoreSystemRoles() {
        dataSeeder.run();
    }

    @Test
    void employeeEditsOwnDetailsAndBankChangeWaitsForHrApproval() throws Exception {
        String s = suffix();
        Company company = company("Profile Co " + s);
        User employeeUser = user(company, "EMPLOYEE", "emp-" + s);
        Employee employee = employee(company, employeeUser, "E-" + s);
        User hrUser = user(company, "HR_ADMIN", "hr-" + s);
        employee(company, hrUser, "H-" + s);
        String emp = token(employeeUser, "EMPLOYEE");
        String hr = token(hrUser, "HR_ADMIN");

        // Everyday details: saved immediately.
        mockMvc.perform(patch("/api/profile/" + employee.getId() + "/details").header("Authorization", emp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"phone\":\"+91 98765 43210\",\"personalEmail\":\"Me@Example.com\",\"bloodGroup\":\"O+\","
                                + "\"maritalStatus\":\"married\",\"emergencyContactName\":\"Ravi\",\"emergencyContactRelation\":\"Brother\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.personalEmail").value("me@example.com"))
                .andExpect(jsonPath("$.maritalStatus").value("MARRIED"))
                .andExpect(jsonPath("$.bloodGroup").value("O+"));

        mockMvc.perform(patch("/api/profile/" + employee.getId() + "/details").header("Authorization", emp)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"bloodGroup\":\"Z+\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/profile/" + employee.getId() + "/family").header("Authorization", emp)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Lakshmi\",\"relation\":\"Mother\",\"dependent\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.family[0].name").value("Lakshmi"));
        mockMvc.perform(post("/api/profile/" + employee.getId() + "/education").header("Authorization", emp)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"qualification\":\"B.E.\",\"institution\":\"Anna University\",\"startYear\":2014,\"endYear\":2018}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.education[0].institution").value("Anna University"));

        // Bank details: a bad IFSC is refused at once; a valid one waits for HR.
        mockMvc.perform(post("/api/profile/" + employee.getId() + "/change-requests").header("Authorization", emp)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"section\":\"BANK\",\"values\":{\"bankIfsc\":\"BAD\"}}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/profile/" + employee.getId() + "/change-requests").header("Authorization", emp)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"section\":\"BANK\",\"values\":{\"bankAccountNumber\":\"123456789012\",\"bankIfsc\":\"hdfc0001234\"},\"reason\":\"New salary account\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changeRequests[0].status").value("PENDING"));
        assertNull(employeeRepository.findById(employee.getId()).orElseThrow().getBankAccountNumber());
        verify(notificationService, atLeastOnce()).notifyPermissionRecipients(eq(company.getId()), eq(employee.getId()),
                eq("EMPLOYEE_MANAGE"), eq("PROFILE_CHANGE_REQUEST"), any(), any(), any(), anyLong());

        // The employee cannot approve; HR sees and approves it.
        mockMvc.perform(get("/api/profile/change-requests/pending").header("Authorization", emp))
                .andExpect(status().isForbidden());
        String pending = mockMvc.perform(get("/api/profile/change-requests/pending").header("Authorization", hr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].employeeName").value("Asha" + s + " Person"))
                .andReturn().getResponse().getContentAsString();
        Integer requestId = JsonPath.read(pending, "$[0].id");
        mockMvc.perform(post("/api/profile/change-requests/" + requestId + "/approve").header("Authorization", hr)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        Employee updated = employeeRepository.findById(employee.getId()).orElseThrow();
        assertEquals("123456789012", updated.getBankAccountNumber());
        assertEquals("HDFC0001234", updated.getBankIfsc());
    }

    @Test
    void employeesCannotEditColleaguesAndHrCannotApproveTheirOwnChange() throws Exception {
        String s = suffix();
        Company company = company("Guard Co " + s);
        User aliceUser = user(company, "EMPLOYEE", "alice-" + s);
        Employee alice = employee(company, aliceUser, "A-" + s);
        User bobUser = user(company, "EMPLOYEE", "bob-" + s);
        employee(company, bobUser, "B-" + s);
        User hrUser = user(company, "HR_ADMIN", "hr2-" + s);
        Employee hrEmployee = employee(company, hrUser, "H2-" + s);
        String hr = token(hrUser, "HR_ADMIN");

        mockMvc.perform(patch("/api/profile/" + alice.getId() + "/details").header("Authorization", token(bobUser, "EMPLOYEE"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"phone\":\"9876543210\"}"))
                .andExpect(status().isForbidden());

        // HR changes a colleague's legal name directly.
        mockMvc.perform(post("/api/profile/" + alice.getId() + "/change-requests").header("Authorization", hr)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"section\":\"IDENTITY\",\"values\":{\"lastName\":\"Kumar\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Kumar"));

        // HR's own bank change becomes a request, and they cannot approve it themselves.
        mockMvc.perform(post("/api/profile/" + hrEmployee.getId() + "/change-requests").header("Authorization", hr)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"section\":\"BANK\",\"values\":{\"panNumber\":\"ABCDE1234F\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changeRequests[0].status").value("PENDING"));
        String pending = mockMvc.perform(get("/api/profile/change-requests/pending").header("Authorization", hr))
                .andReturn().getResponse().getContentAsString();
        Integer requestId = JsonPath.read(pending, "$[0].id");
        mockMvc.perform(post("/api/profile/change-requests/" + requestId + "/approve").header("Authorization", hr)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        assertNull(employeeRepository.findById(hrEmployee.getId()).orElseThrow().getPanNumber());
    }

    private Company company(String name) {
        Company company = new Company();
        company.setName(name);
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

    private Employee employee(Company company, User user, String code) {
        Employee employee = new Employee();
        employee.setEmployeeCode(code);
        employee.setFirstName("Asha" + code.substring(code.indexOf('-') + 1));
        employee.setLastName("Person");
        employee.setEmail(user.getEmail());
        employee.setDateOfJoining(LocalDate.of(2026, 1, 1));
        employee.setCompany(company);
        employee.setUser(user);
        return employeeRepository.saveAndFlush(employee);
    }

    private String token(User user, String role) {
        return "Bearer " + jwtService.generateAccessToken(user.getUsername(), List.of(role));
    }

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
