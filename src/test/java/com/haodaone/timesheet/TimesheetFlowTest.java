package com.haodaone.timesheet;

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Keka-style timesheet flow: HR sets up a project, the employee logs time against it, submits the week,
 * their manager sends it back, the employee fixes and resubmits, and HR approves - after which it's locked.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TimesheetFlowTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;
    /** notification is a Flyway-only table; the flow itself is what's under test here. */
    @MockitoBean private NotificationService notificationService;

    @Test
    void employeeLogsSubmitsAndGetsApproved() throws Exception {
        String s = suffix();
        Company company = company("Timesheet Co " + s);
        User hrUser = user(company, "HR_ADMIN", "hr-" + s);
        employee(company, hrUser, "H-" + s, null);
        User managerUser = user(company, "MANAGER", "mgr-" + s);
        Employee manager = employee(company, managerUser, "M-" + s, null);
        User otherManagerUser = user(company, "MANAGER", "mgr2-" + s);
        employee(company, otherManagerUser, "M2-" + s, null);
        User empUser = user(company, "EMPLOYEE", "emp-" + s);
        Employee emp = employee(company, empUser, "E-" + s, manager);
        String hr = token(hrUser, "HR_ADMIN");
        String mgr = token(managerUser, "MANAGER");
        String otherMgr = token(otherManagerUser, "MANAGER");
        String me = token(empUser, "EMPLOYEE");

        // HR creates a billable client project with a task, open to this employee only.
        String project = mockMvc.perform(post("/api/timesheets/projects").header("Authorization", hr).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Acme Portal\",\"clientName\":\"Acme\",\"billable\":true,\"memberEmployeeIds\":[" + emp.getId() + "]}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Integer projectId = JsonPath.read(project, "$.id");
        String withTask = mockMvc.perform(post("/api/timesheets/projects/" + projectId + "/tasks").header("Authorization", hr)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Development\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Integer taskId = JsonPath.read(withTask, "$.tasks[0].id");
        String closed = mockMvc.perform(post("/api/timesheets/projects").header("Authorization", hr).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Secret Project\"}"))
                .andReturn().getResponse().getContentAsString();
        Integer closedId = JsonPath.read(closed, "$.id");
        mockMvc.perform(post("/api/timesheets/projects").header("Authorization", me).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Mine\"}"))
                .andExpect(status().isForbidden());

        LocalDate monday = LocalDate.now(ZoneId.of("Asia/Kolkata")).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1);
        mockMvc.perform(get("/api/timesheets/me?date=" + monday).header("Authorization", me))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.projects[0].name").value("Acme Portal"))
                .andExpect(jsonPath("$.projects.length()").value(1));

        mockMvc.perform(put("/api/timesheets/me/entries").header("Authorization", me).contentType(MediaType.APPLICATION_JSON)
                        .content(entry(monday, projectId, taskId, 240, "Login page")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalMinutes").value(240))
                .andExpect(jsonPath("$.billableMinutes").value(240));
        mockMvc.perform(put("/api/timesheets/me/entries").header("Authorization", me).contentType(MediaType.APPLICATION_JSON)
                        .content(entry(monday.plusDays(1), null, null, 120, "Team meeting")))
                .andExpect(jsonPath("$.totalMinutes").value(360));
        // Same cell again updates instead of duplicating.
        mockMvc.perform(put("/api/timesheets/me/entries").header("Authorization", me).contentType(MediaType.APPLICATION_JSON)
                        .content(entry(monday, projectId, taskId, 300, "Login page + tests")))
                .andExpect(jsonPath("$.totalMinutes").value(420))
                .andExpect(jsonPath("$.entries.length()").value(2));

        mockMvc.perform(put("/api/timesheets/me/entries").header("Authorization", me).contentType(MediaType.APPLICATION_JSON)
                        .content(entry(LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(2), null, null, 60, null)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/timesheets/me/entries").header("Authorization", me).contentType(MediaType.APPLICATION_JSON)
                        .content(entry(monday, closedId, null, 60, null)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/timesheets/me/submit?date=" + monday).header("Authorization", me))
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
        mockMvc.perform(put("/api/timesheets/me/entries").header("Authorization", me).contentType(MediaType.APPLICATION_JSON)
                        .content(entry(monday.plusDays(2), null, null, 60, null)))
                .andExpect(status().isBadRequest());

        // Only the employee's own manager (and HR) see it; the manager sends it back with a note.
        mockMvc.perform(get("/api/timesheets/approvals").header("Authorization", otherMgr))
                .andExpect(jsonPath("$.length()").value(0));
        String approvals = mockMvc.perform(get("/api/timesheets/approvals").header("Authorization", mgr))
                .andExpect(jsonPath("$[0].totalMinutes").value(420))
                .andReturn().getResponse().getContentAsString();
        Integer weekId = JsonPath.read(approvals, "$[0].weekId");
        mockMvc.perform(post("/api/timesheets/weeks/" + weekId + "/reject").header("Authorization", mgr)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/timesheets/weeks/" + weekId + "/reject").header("Authorization", mgr)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\":\"Add Wednesday\"}"))
                .andExpect(jsonPath("$.status").value("REJECTED"));

        // Employee fixes and resubmits; HR approves; the week locks.
        mockMvc.perform(put("/api/timesheets/me/entries").header("Authorization", me).contentType(MediaType.APPLICATION_JSON)
                        .content(entry(monday.plusDays(2), projectId, null, 480, "Release")))
                .andExpect(jsonPath("$.totalMinutes").value(900));
        mockMvc.perform(post("/api/timesheets/me/submit?date=" + monday).header("Authorization", me))
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
        mockMvc.perform(post("/api/timesheets/weeks/" + weekId + "/approve").header("Authorization", hr)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.status").value("APPROVED"));
        mockMvc.perform(put("/api/timesheets/me/entries").header("Authorization", me).contentType(MediaType.APPLICATION_JSON)
                        .content(entry(monday, null, null, 30, null)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/timesheets/reports/projects?from=" + monday + "&to=" + monday.plusDays(6)).header("Authorization", hr))
                .andExpect(jsonPath("$.totalMinutes").value(900))
                .andExpect(jsonPath("$.billableMinutes").value(780));
        mockMvc.perform(get("/api/timesheets/team?date=" + monday).header("Authorization", mgr))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].weekStatus").value("APPROVED"));
    }

    private static String entry(LocalDate date, Integer projectId, Integer taskId, int minutes, String notes) {
        return "{\"workDate\":\"" + date + "\",\"projectId\":" + projectId + ",\"taskId\":" + taskId + ",\"minutes\":" + minutes
                + ",\"notes\":" + (notes == null ? "null" : "\"" + notes + "\"") + "}";
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
        employee.setDateOfJoining(LocalDate.of(2025, 1, 1));
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
