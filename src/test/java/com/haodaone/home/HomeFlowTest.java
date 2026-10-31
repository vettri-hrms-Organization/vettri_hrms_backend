package com.haodaone.home;

import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.config.DataSeeder;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.leave.entity.LeaveType;
import com.haodaone.leave.repository.LeaveTypeRepository;
import com.haodaone.notifications.service.NotificationService;
import com.haodaone.security.JwtService;
import com.haodaone.user.entity.Role;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.RoleRepository;
import com.haodaone.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Home page: to-dos gathered across modules, today's team pulse, and setup progress for admins only. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HomeFlowTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private LeaveTypeRepository leaveTypeRepository;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DataSeeder dataSeeder;
    @MockitoBean private NotificationService notificationService;

    private Company company;
    private String hr, me;
    private Employee emp;

    @BeforeEach
    void setUp() {
        dataSeeder.run();
        String s = UUID.randomUUID().toString().substring(0, 8);
        Company draft = new Company();
        draft.setName("Home " + s);
        company = companyRepository.saveAndFlush(draft);
        User hrUser = user("HR_ADMIN", "hr-" + s);
        employee(hrUser, "H" + s, "Hema", LocalDate.of(2024, 1, 1));
        User empUser = user("EMPLOYEE", "emp-" + s);
        // Joined exactly two years ago tomorrow, so the anniversary is due.
        emp = employee(empUser, "E" + s, "Arjun", LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(1).minusYears(2));
        hr = "Bearer " + jwtService.generateAccessToken(hrUser.getUsername(), List.of("HR_ADMIN"));
        me = "Bearer " + jwtService.generateAccessToken(empUser.getUsername(), List.of("EMPLOYEE"));
    }

    @Test
    void todosTodayAndSetup() throws Exception {
        LeaveType cl = new LeaveType();
        cl.setCompany(company);
        cl.setName("Casual");
        cl.setCode("CL" + emp.getId());
        cl.setDefaultDaysPerYear(12);
        cl.setActive(true);
        cl = leaveTypeRepository.saveAndFlush(cl);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        String leave = mockMvc.perform(post("/api/leave-requests").header("Authorization", me).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employeeId\":" + emp.getId() + ",\"leaveTypeId\":" + cl.getId() + ",\"startDate\":\"" + today + "\",\"endDate\":\"" + today.plusDays(4) + "\",\"reason\":\"x\",\"halfDay\":false}"))
                .andReturn().getResponse().getContentAsString();

        String todos = mockMvc.perform(get("/api/home/todos").header("Authorization", hr)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        {
            List<Integer> leaveTodo = JsonPath.read(todos, "$[?(@.key == 'leave')].count");
            assertEquals(List.of(1), leaveTodo);
            Integer leaveId = JsonPath.read(leave, "$.id");
            mockMvc.perform(patch("/api/leave-requests/" + leaveId + "/approve").header("Authorization", hr).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isOk());
            String todayJson = mockMvc.perform(get("/api/home/today").header("Authorization", me)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            List<String> out = JsonPath.read(todayJson, "$.outToday[*].name");
            assertTrue(out.contains(emp.getFullName()), "on leave today: " + out);
        }
        // Employees get an empty list, never an error, from modules they can't use.
        mockMvc.perform(get("/api/home/todos").header("Authorization", me)).andExpect(status().isOk());

        String today2 = mockMvc.perform(get("/api/home/today").header("Authorization", me)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> anniversaries = JsonPath.read(today2, "$.anniversaries[*].detail");
        assertTrue(anniversaries.stream().anyMatch(d -> d.startsWith("2 years")), "anniversary: " + anniversaries);
        assertNull(JsonPath.read(today2, "$.it"), "no IT snapshot for employees");

        mockMvc.perform(get("/api/home/setup").header("Authorization", hr))
                .andExpect(jsonPath("$.visible").value(true))
                .andExpect(jsonPath("$.total").value(9))
                .andExpect(jsonPath("$.steps[?(@.key == 'leave')].done").value(org.hamcrest.Matchers.contains(true)));
        mockMvc.perform(get("/api/home/setup").header("Authorization", me)).andExpect(jsonPath("$.visible").value(false));
    }

    private User user(String roleName, String username) {
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

    private Employee employee(User user, String code, String first, LocalDate joined) {
        Employee e = new Employee();
        e.setEmployeeCode(code);
        e.setFirstName(first);
        e.setLastName(code);
        e.setEmail(user.getEmail());
        e.setDateOfJoining(joined);
        e.setCompany(company);
        e.setUser(user);
        return employeeRepository.saveAndFlush(e);
    }
}
