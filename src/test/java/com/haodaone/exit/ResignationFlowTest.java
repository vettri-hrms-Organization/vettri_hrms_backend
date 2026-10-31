package com.haodaone.exit;

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
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Employee resigns -> manager approves -> HR accepts -> employee serves notice and completes the exit interview. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ResignationFlowTest {

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
    void resignationGoesThroughManagerAndHrThenExitInterview() throws Exception {
        String s = suffix();
        Company company = company("Exit Co " + s);
        User hrUser = user(company, "HR_ADMIN", "hr-" + s);
        employee(company, hrUser, "H-" + s, null);
        User managerUser = user(company, "MANAGER", "mgr-" + s);
        Employee manager = employee(company, managerUser, "M-" + s, null);
        User empUser = user(company, "EMPLOYEE", "emp-" + s);
        Employee emp = employee(company, empUser, "E-" + s, manager);
        User otherUser = user(company, "EMPLOYEE", "other-" + s);
        employee(company, otherUser, "O-" + s, null);
        String hr = token(hrUser, "HR_ADMIN");
        String mgr = token(managerUser, "MANAGER");
        String me = token(empUser, "EMPLOYEE");
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));

        mockMvc.perform(get("/api/resignations/me").header("Authorization", me))
                .andExpect(jsonPath("$.canResign").value(true))
                .andExpect(jsonPath("$.settings.noticePeriodDays").value(30));

        String view = mockMvc.perform(post("/api/resignations/me").header("Authorization", me).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonCategory\":\"Higher studies\",\"reason\":\"MS abroad\",\"requestedLastWorkingDate\":\"" + today.plusDays(20) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.status").value("PENDING_MANAGER"))
                .andExpect(jsonPath("$.current.noticeShortfallDays").value(10))
                .andReturn().getResponse().getContentAsString();
        Integer id = JsonPath.read(view, "$.current.id");
        mockMvc.perform(post("/api/resignations/me").header("Authorization", me).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonCategory\":\"Other\"}"))
                .andExpect(status().isBadRequest());

        // A colleague can't see or act on it; the employee can't approve their own.
        mockMvc.perform(get("/api/resignations/" + id).header("Authorization", token(otherUser, "EMPLOYEE")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/resignations/" + id + "/approve").header("Authorization", me)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/resignations/pending").header("Authorization", mgr))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].canManagerReview").value(true));
        mockMvc.perform(post("/api/resignations/" + id + "/approve").header("Authorization", mgr).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"note\":\"Supportive\",\"lastWorkingDate\":\"" + today.plusDays(25) + "\"}"))
                .andExpect(jsonPath("$.status").value("PENDING_HR"));

        mockMvc.perform(post("/api/resignations/" + id + "/approve").header("Authorization", hr)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.approvedLastWorkingDate").value(today.plusDays(25).toString()));
        Employee after = employeeRepository.findById(emp.getId()).orElseThrow();
        assertEquals("Resigned", after.getStatus());
        assertEquals(today.plusDays(25), after.getLastWorkingDate());

        mockMvc.perform(post("/api/resignations/me/" + id + "/exit-interview").header("Authorization", me).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":4,\"manager\":5,\"compensation\":2,\"wouldRecommend\":true,\"improve\":\"Pay\"}"))
                .andExpect(jsonPath("$.current.interviewDone").value(true));
        mockMvc.perform(post("/api/resignations/me/" + id + "/exit-interview").header("Authorization", me).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":9}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/resignations/summary").header("Authorization", hr))
                .andExpect(jsonPath("$.servingNotice").value(1))
                .andExpect(jsonPath("$.reasonsLast12Months['Higher studies']").value(1))
                .andExpect(jsonPath("$.averageRatings.manager").value(5.0))
                .andExpect(jsonPath("$.wouldRecommendPercent").value(100.0));
        mockMvc.perform(get("/api/resignations/summary").header("Authorization", mgr))
                .andExpect(status().isForbidden());

        // HR can still reverse it before the last day.
        mockMvc.perform(post("/api/resignations/" + id + "/cancel").header("Authorization", hr)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Counter offer accepted\"}"))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        assertEquals("Active", employeeRepository.findById(emp.getId()).orElseThrow().getStatus());
    }

    @Test
    void employeeCanWithdrawAndManagerCanRetain() throws Exception {
        String s = suffix();
        Company company = company("Retain Co " + s);
        User managerUser = user(company, "MANAGER", "mgr-" + s);
        Employee manager = employee(company, managerUser, "M-" + s, null);
        User empUser = user(company, "EMPLOYEE", "emp-" + s);
        employee(company, empUser, "E-" + s, manager);
        String me = token(empUser, "EMPLOYEE");
        String mgr = token(managerUser, "MANAGER");

        String first = mockMvc.perform(post("/api/resignations/me").header("Authorization", me).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonCategory\":\"Compensation\"}"))
                .andReturn().getResponse().getContentAsString();
        Integer firstId = JsonPath.read(first, "$.current.id");
        mockMvc.perform(post("/api/resignations/me/" + firstId + "/withdraw").header("Authorization", me))
                .andExpect(jsonPath("$.canResign").value(true))
                .andExpect(jsonPath("$.history[0].status").value("WITHDRAWN"));

        String second = mockMvc.perform(post("/api/resignations/me").header("Authorization", me).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonCategory\":\"Compensation\"}"))
                .andReturn().getResponse().getContentAsString();
        Integer secondId = JsonPath.read(second, "$.current.id");
        mockMvc.perform(post("/api/resignations/" + secondId + "/retain").header("Authorization", mgr)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/resignations/" + secondId + "/retain").header("Authorization", mgr)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Let's talk about a raise\"}"))
                .andExpect(jsonPath("$.status").value("RETAINED"));
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

    private Employee employee(Company company, User user, String code, Employee manager) {
        Employee employee = new Employee();
        employee.setEmployeeCode(code);
        employee.setFirstName("Person");
        employee.setLastName(code);
        employee.setEmail(user.getEmail());
        employee.setDateOfJoining(LocalDate.of(2024, 1, 1));
        employee.setCompany(company);
        employee.setUser(user);
        employee.setReportingManager(manager);
        return employeeRepository.saveAndFlush(employee);
    }

    private String token(User user, String role) {
        return "Bearer " + jwtService.generateAccessToken(user.getUsername(), List.of(role));
    }

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
