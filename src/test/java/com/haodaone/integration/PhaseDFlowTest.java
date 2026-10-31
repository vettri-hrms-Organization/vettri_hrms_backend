package com.haodaone.integration;

import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.config.DataSeeder;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.notifications.service.NotificationService;
import com.haodaone.reports.builder.ReportBuilderService;
import com.haodaone.security.JwtService;
import com.haodaone.timesheet.*;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Phase D: public API keys, calendar feed, URL guard, report builder, project billing. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PhaseDFlowTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private TimesheetProjectRepository projectRepository;
    @Autowired private TimesheetEntryRepository entryRepository;
    @Autowired private TimesheetWeekRepository weekRepository;
    @Autowired private OutboundUrlGuard guard;
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
        draft.setName("PhaseD " + s);
        company = companyRepository.saveAndFlush(draft);
        User hrUser = user("HR_ADMIN", "hr-" + s);
        employee(hrUser, "H" + s, "Hema");
        User empUser = user("EMPLOYEE", "emp-" + s);
        emp = employee(empUser, "E" + s, "Arjun");
        hr = token(hrUser, "HR_ADMIN");
        me = token(empUser, "EMPLOYEE");
    }

    @Test
    void apiKeysAndPublicApi() throws Exception {
        mockMvc.perform(json(post("/api/integrations/api-keys"), me, "{\"name\":\"x\",\"scopes\":[\"employees:read\"]}")).andExpect(status().isForbidden());
        String created = body(json(post("/api/integrations/api-keys"), hr, "{\"name\":\"Payroll ERP\",\"scopes\":[\"employees:read\"]}"));
        String key = JsonPath.read(created, "$.key");
        Integer keyId = JsonPath.read(created, "$.id");
        assertTrue(key.startsWith("vk_"));
        // The full key is never returned again.
        mockMvc.perform(get("/api/integrations/api-keys").header("Authorization", hr)).andExpect(jsonPath("$[0].key").doesNotExist());

        mockMvc.perform(get("/api/public/v1/employees")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/public/v1/employees").header("X-API-Key", "vk_nope_nope")).andExpect(status().isUnauthorized());
        String list = mockMvc.perform(get("/api/public/v1/employees").header("X-API-Key", key)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertEquals(2, (Integer) JsonPath.read(list, "$.count"));
        assertFalse(list.contains("bankAccount"));
        mockMvc.perform(get("/api/public/v1/leave?from=2026-01-01&to=2026-01-31").header("X-API-Key", key)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/integrations/api-keys/" + keyId + "/revoke").header("Authorization", hr)).andExpect(status().isOk());
        mockMvc.perform(get("/api/public/v1/employees").header("X-API-Key", key)).andExpect(status().isUnauthorized());

        // SSRF guard: no http, no private addresses.
        assertThrows(Exception.class, () -> guard.check("http://example.com/hook", OutboundUrlGuard.Kind.WEBHOOK));
        assertThrows(Exception.class, () -> guard.check("https://127.0.0.1/hook", OutboundUrlGuard.Kind.WEBHOOK));
        assertThrows(Exception.class, () -> guard.check("https://10.0.0.5/hook", OutboundUrlGuard.Kind.WEBHOOK));
        assertThrows(Exception.class, () -> guard.check("https://evil.example.com/services/x", OutboundUrlGuard.Kind.SLACK));
        mockMvc.perform(json(post("/api/integrations/webhooks"), hr, "{\"url\":\"https://192.168.1.10/x\",\"events\":[\"LEAVE_APPROVED\"]}")).andExpect(status().isBadRequest());
    }

    @Test
    void calendarFeedAndReportBuilder() throws Exception {
        String link = body(post("/api/calendar/link").header("Authorization", me));
        String path = JsonPath.read(link, "$.path");
        String ics = mockMvc.perform(get(path)).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/calendar")))
                .andReturn().getResponse().getContentAsString();
        assertTrue(ics.startsWith("BEGIN:VCALENDAR\r\n"));
        assertTrue(ics.contains("END:VCALENDAR"));
        String reset = body(post("/api/calendar/link").header("Authorization", me));
        mockMvc.perform(get(path)).andExpect(status().isNotFound());
        mockMvc.perform(get((String) JsonPath.read(reset, "$.path"))).andExpect(status().isOk());

        mockMvc.perform(get("/api/report-builder/datasets").header("Authorization", me)).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(json(post("/api/report-builder/run"), me, "{\"dataset\":\"EMPLOYEES\"}")).andExpect(status().isForbidden());
        String result = body(json(post("/api/report-builder/run"), hr, "{\"dataset\":\"EMPLOYEES\",\"columns\":[\"name\",\"employeeCode\"],\"groupBy\":\"status\"}"));
        assertEquals("count", JsonPath.read(result, "$.columns[1].key"));
        String csv = mockMvc.perform(json(post("/api/report-builder/export"), hr, "{\"dataset\":\"EMPLOYEES\",\"columns\":[\"name\",\"employeeCode\"]}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(csv.contains("\"Name\",\"Employee code\""));
        mockMvc.perform(json(post("/api/report-builder/run"), hr, "{\"dataset\":\"ATTENDANCE\"}")).andExpect(status().isBadRequest());
        assertEquals("\"'=HYPERLINK(1)\"", com.haodaone.reports.builder.ReportBuilderServiceAccess.cell("=HYPERLINK(1)"));
        mockMvc.perform(json(post("/api/report-builder/saved"), hr, "{\"name\":\"Headcount\",\"shared\":true,\"definition\":{\"dataset\":\"EMPLOYEES\",\"groupBy\":\"department\"}}"))
                .andExpect(jsonPath("$.mine").value(true));
    }

    @Test
    void projectBillingInvoicesApprovedHoursOnce() throws Exception {
        TimesheetProject p = new TimesheetProject();
        p.setCompanyId(company.getId());
        p.setName("Acme Portal");
        p.setClientName("Acme");
        p.setBillable(true);
        p.setAllEmployees(true);
        p = projectRepository.saveAndFlush(p);
        LocalDate monday = LocalDate.now(ZoneId.of("Asia/Kolkata")).minusWeeks(2).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        entry(p, monday, 480, true);
        entry(p, monday.plusDays(1), 240, true);
        entry(p, monday.plusDays(1), 60, false);
        entry(p, monday.plusWeeks(1), 120, true); // week not approved
        TimesheetWeek w = new TimesheetWeek();
        w.setCompanyId(company.getId());
        w.setEmployeeId(emp.getId());
        w.setWeekStart(monday);
        w.setStatus(TimesheetWeek.APPROVED);
        weekRepository.saveAndFlush(w);

        mockMvc.perform(json(put("/api/timesheets/billing/rates/" + p.getId()), me, "{\"billingRate\":1500}")).andExpect(status().isForbidden());
        mockMvc.perform(json(put("/api/timesheets/billing/rates/" + p.getId()), hr, "{\"billingRate\":1500}")).andExpect(jsonPath("$.billingRate").value(1500));
        String req = "{\"projectId\":" + p.getId() + ",\"from\":\"" + monday + "\",\"to\":\"" + monday.plusDays(13) + "\",\"taxPercent\":18}";
        mockMvc.perform(json(post("/api/timesheets/billing/invoices/preview"), hr, req))
                .andExpect(jsonPath("$.subtotal").value(18000.0))
                .andExpect(jsonPath("$.unapprovedHours").value(2.0));
        String inv = body(json(post("/api/timesheets/billing/invoices"), hr, req));
        assertEquals(0, new BigDecimal("21240").compareTo(new BigDecimal(JsonPath.read(inv, "$.total").toString())));
        mockMvc.perform(json(post("/api/timesheets/billing/invoices"), hr, req)).andExpect(status().isBadRequest());
        Integer invId = JsonPath.read(inv, "$.id");
        mockMvc.perform(json(post("/api/timesheets/billing/invoices/" + invId + "/status"), hr, "{\"status\":\"VOID\"}")).andExpect(jsonPath("$.status").value("VOID"));
        mockMvc.perform(json(post("/api/timesheets/billing/invoices"), hr, req)).andExpect(status().isOk());
        mockMvc.perform(get("/api/timesheets/billing/utilization?from=" + monday + "&to=" + monday.plusDays(13)).header("Authorization", hr))
                .andExpect(jsonPath("$.people[0].totalHours").value(15.0))
                .andExpect(jsonPath("$.projects[0].billableValue").value(21000.0));
    }

    // ------------------------------------------------------------------ helpers

    private void entry(TimesheetProject p, LocalDate date, int minutes, boolean billable) {
        TimesheetEntry e = new TimesheetEntry();
        e.setCompanyId(company.getId());
        e.setEmployeeId(emp.getId());
        e.setProjectId(p.getId());
        e.setWorkDate(date);
        e.setMinutes(minutes);
        e.setBillable(billable);
        entryRepository.saveAndFlush(e);
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String token, String content) {
        return b.header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(content);
    }

    private String body(MockHttpServletRequestBuilder b) throws Exception {
        return mockMvc.perform(b).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
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

    private Employee employee(User user, String code, String first) {
        Employee e = new Employee();
        e.setEmployeeCode(code);
        e.setFirstName(first);
        e.setLastName(code);
        e.setEmail(user.getEmail());
        e.setDateOfJoining(LocalDate.of(2024, 1, 1));
        e.setCompany(company);
        e.setUser(user);
        return employeeRepository.saveAndFlush(e);
    }

    private String token(User user, String role) {
        return "Bearer " + jwtService.generateAccessToken(user.getUsername(), List.of(role));
    }
}
