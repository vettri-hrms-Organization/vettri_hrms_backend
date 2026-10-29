package com.haodaone.monitoring;

import com.haodaone.attendance.entity.WfhRequest;
import com.haodaone.attendance.repository.WfhRequestRepository;
import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.monitoring.entity.AgentRelease;
import com.haodaone.monitoring.entity.MonitoredDevice;
import com.haodaone.monitoring.repository.AgentReleaseRepository;
import com.haodaone.monitoring.repository.MonitoredDeviceRepository;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.Base64;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Agent tray endpoints (attendance, notifications, auto-update) authenticated with a real device token. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AgentTrayEndpointsTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private MonitoredDeviceRepository deviceRepository;
    @Autowired private WfhRequestRepository wfhRequestRepository;
    @Autowired private AgentReleaseRepository agentReleaseRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void trayAttendanceFollowsWfhRulesAndTracksBreaks() throws Exception {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Company company = companyRepository.save(company("Tray " + unique));
        Employee employee = employee(company, unique);
        String token = device(company, employee, unique);

        mockMvc.perform(agentGet("/api/agent/attendance").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.state").value("NOT_CHECKED_IN"))
                .andExpect(jsonPath("$.data.canClockIn").value(false))
                .andExpect(jsonPath("$.data.companyName").value(company.getName()));

        // Office day: the tray cannot prove location, so clock-in is refused with a plain explanation.
        mockMvc.perform(agentPost("/api/agent/attendance/CLOCK_IN").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ok").value(false))
                .andExpect(jsonPath("$.data.message").value(containsString("work-from-home")));

        WfhRequest wfh = new WfhRequest();
        wfh.setCompany(company);
        wfh.setEmployee(employee);
        wfh.setWorkDate(LocalDate.now());
        wfh.setReason("Remote day");
        wfh.setStatus("APPROVED");
        wfhRequestRepository.save(wfh);

        mockMvc.perform(agentPost("/api/agent/attendance/CLOCK_IN").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.ok").value(true))
                .andExpect(jsonPath("$.data.state").value("CHECKED_IN"));
        mockMvc.perform(agentPost("/api/agent/attendance/CLOCK_IN").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.ok").value(false))
                .andExpect(jsonPath("$.data.message").value("You are already clocked in."));
        mockMvc.perform(agentPost("/api/agent/attendance/BREAK_START").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.state").value("ON_BREAK"));
        mockMvc.perform(agentPost("/api/agent/attendance/BREAK_END").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.state").value("CHECKED_IN"));
        mockMvc.perform(agentPost("/api/agent/attendance/CLOCK_OUT").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.data.ok").value(true))
                .andExpect(jsonPath("$.data.state").value("CHECKED_OUT"));
    }

    @Test
    void unassignedLaptopIsToldToContactIt() throws Exception {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Company company = companyRepository.save(company("Unassigned " + unique));
        String token = device(company, null, unique);

        mockMvc.perform(agentPost("/api/agent/attendance/CLOCK_IN").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ok").value(false))
                .andExpect(jsonPath("$.data.state").value("NOT_ASSIGNED"));
    }

    @Test
    void notificationsReachOnlyTheAssignedEmployee() throws Exception {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Company company = companyRepository.save(company("Notify " + unique));
        Employee employee = employee(company, unique);
        String token = device(company, employee, unique);
        // notification is a Flyway/JDBC-only table (no entity), so the H2 test schema needs it created.
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS notification (
                    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY, company_id BIGINT, recipient_user_id BIGINT,
                    type VARCHAR(60), title VARCHAR(255), message VARCHAR(2000), entity_type VARCHAR(60), entity_id BIGINT,
                    priority VARCHAR(20), deduplication_key VARCHAR(255), read_at TIMESTAMP, deleted BOOLEAN DEFAULT FALSE,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)""");
        jdbcTemplate.update("INSERT INTO notification (company_id, recipient_user_id, type, title, message, priority) VALUES (?, ?, ?, ?, ?, ?)",
                company.getId(), employee.getUser().getId(), "LEAVE_APPROVED", "Leave approved", "Your leave on Friday was approved", "NORMAL");

        mockMvc.perform(agentGet("/api/agent/notifications").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].title").value("Leave approved"));

        Employee other = employee(company, unique + "x");
        String otherToken = device(company, other, unique + "x");
        mockMvc.perform(agentGet("/api/agent/notifications").header("Authorization", "Bearer " + otherToken))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void updateIsOfferedOnlyWhenNewerThanTheAgent() throws Exception {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        Company company = companyRepository.save(company("Update " + unique));
        String token = device(company, null, unique);
        agentReleaseRepository.findAll().forEach(release -> { release.setActive(false); agentReleaseRepository.save(release); });

        AgentRelease release = new AgentRelease();
        release.setReleaseVersion("90.1." + Math.abs(unique.hashCode() % 1000));
        release.setDownloadUrl("https://downloads.vettrihrms.in/VettriAgentSetup.exe");
        release.setSha256("a".repeat(64));
        release.setActive(true);
        agentReleaseRepository.save(release);

        mockMvc.perform(agentGet("/api/agent/update").header("Authorization", "Bearer " + token).header("X-Agent-Version", "2.1.0.0"))
                .andExpect(jsonPath("$.data.updateAvailable").value(true))
                .andExpect(jsonPath("$.data.sha256").value("a".repeat(64)));
        mockMvc.perform(agentGet("/api/agent/update").header("Authorization", "Bearer " + token).header("X-Agent-Version", "99.0.0.0"))
                .andExpect(jsonPath("$.data.updateAvailable").value(false));

        release.setActive(false);
        agentReleaseRepository.save(release);
    }

    private static MockHttpServletRequestBuilder agentGet(String path) { return get(path).servletPath(path); }

    private static MockHttpServletRequestBuilder agentPost(String path) { return post(path).servletPath(path); }

    private Company company(String name) {
        Company company = new Company();
        company.setName(name);
        company.setAttendanceMethod("WEB_APP_ONLY");
        return company;
    }

    private Employee employee(Company company, String unique) {
        User user = new User();
        user.setUsername("tray-" + unique + "@acme.test");
        user.setEmail("tray-" + unique + "@acme.test");
        user.setFullName("Tray User");
        user.setPasswordHash("x");
        user.setCompany(company);
        user = userRepository.save(user);

        Employee employee = new Employee();
        employee.setEmployeeCode("T-" + unique);
        employee.setFirstName("Tray");
        employee.setLastName("User");
        employee.setEmail("tray-emp-" + unique + "@acme.test");
        employee.setDateOfJoining(LocalDate.now().minusDays(30));
        employee.setCompany(company);
        employee.setUser(user);
        employee.setStatus("Active");
        return employeeRepository.save(employee);
    }

    /** Saves an active managed laptop and returns its plaintext agent token. */
    private String device(Company company, Employee employee, String unique) throws Exception {
        String token = "agent-token-" + unique;
        MonitoredDevice device = new MonitoredDevice();
        device.setDeviceId("tray-device-" + unique);
        device.setDeviceName("Laptop " + unique);
        device.setAgentTokenHash(Base64.getEncoder().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))));
        device.setCompany(company);
        device.setEmployee(employee);
        device.setActive(true);
        deviceRepository.save(device);
        return token;
    }
}
