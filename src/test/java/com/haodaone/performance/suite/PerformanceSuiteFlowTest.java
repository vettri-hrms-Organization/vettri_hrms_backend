package com.haodaone.performance.suite;

import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.config.DataSeeder;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.notifications.service.NotificationService;
import com.haodaone.salary.entity.SalaryComponents;
import com.haodaone.salary.entity.SalaryStructure;
import com.haodaone.salary.repository.SalaryStructureRepository;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Review cycle end to end, OKRs, feedback, 1:1s, a PIP, and a compensation round applied as salary revisions. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PerformanceSuiteFlowTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SalaryStructureRepository salaryStructureRepository;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DataSeeder dataSeeder;
    @MockitoBean private NotificationService notificationService;

    private String hr, mgr, me, peer;
    private Employee emp, peerEmp;

    @BeforeEach
    void setUp() {
        dataSeeder.run();
        String s = UUID.randomUUID().toString().substring(0, 8);
        Company draft = new Company();
        draft.setName("Perf " + s);
        Company company = companyRepository.saveAndFlush(draft);
        User hrUser = user(company, "HR_ADMIN", "hr-" + s);
        employee(company, hrUser, "H" + s, null);
        User mgrUser = user(company, "MANAGER", "mgr-" + s);
        Employee manager = employee(company, mgrUser, "M" + s, null);
        User empUser = user(company, "EMPLOYEE", "emp-" + s);
        emp = employee(company, empUser, "E" + s, manager);
        User peerUser = user(company, "EMPLOYEE", "peer-" + s);
        peerEmp = employee(company, peerUser, "P" + s, manager);
        SalaryComponents c = new SalaryComponents();
        c.setBasicSalary(new BigDecimal("30000"));
        c.setSpecialAllowance(new BigDecimal("10000"));
        SalaryStructure st = new SalaryStructure();
        st.setCompany(company);
        st.setEmployee(emp);
        st.setEffectiveFrom(LocalDate.of(2024, 1, 1));
        st.setComponents(c);
        st.setAutoStatutory(false);
        st.recalculate();
        salaryStructureRepository.saveAndFlush(st);
        hr = token(hrUser, "HR_ADMIN");
        mgr = token(mgrUser, "MANAGER");
        me = token(empUser, "EMPLOYEE");
        peer = token(peerUser, "EMPLOYEE");
    }

    @Test
    void reviewCycleCalibrateReleaseAndCompensation() throws Exception {
        mockMvc.perform(json(post("/api/performance/cycles"), me, "{\"name\":\"x\",\"periodStart\":\"2026-04-01\",\"periodEnd\":\"2026-09-30\"}"))
                .andExpect(status().isForbidden());
        String cycle = body(json(post("/api/performance/cycles"), hr,
                "{\"name\":\"H1 2026\",\"periodStart\":\"2026-04-01\",\"periodEnd\":\"2026-09-30\",\"peerReviews\":true,\"scope\":\"ALL\"}"));
        Integer cycleId = JsonPath.read(cycle, "$.id");
        assertEquals(4, (Integer) JsonPath.read(cycle, "$.participants"));
        mockMvc.perform(post("/api/performance/cycles/" + cycleId + "/advance").header("Authorization", hr)).andExpect(jsonPath("$.status").value("SELF_REVIEW"));

        // Self review; the manager review stays hidden until the manager stage.
        String tasks = body(get("/api/performance/reviews/tasks").header("Authorization", me));
        Integer selfTask = JsonPath.read(tasks, "$[0].responseId");
        assertEquals("SELF", JsonPath.read(tasks, "$[0].kind"));
        List<Object> mgrManagerTasks = JsonPath.read(body(get("/api/performance/reviews/tasks").header("Authorization", mgr)), "$[?(@.kind == 'MANAGER')]");
        assertTrue(mgrManagerTasks.isEmpty());
        mockMvc.perform(json(post("/api/performance/reviews/" + selfTask), peer, "{\"rating\":5,\"comments\":\"not mine\"}")).andExpect(status().isForbidden());
        mockMvc.perform(json(post("/api/performance/reviews/" + selfTask), me, "{\"rating\":4,\"strengths\":\"Shipped the billing revamp\"}")).andExpect(status().isOk());

        // Manager names a peer; the peer reviews.
        String people = body(get("/api/performance/cycles/" + cycleId + "/participants").header("Authorization", mgr));
        List<Integer> empParticipant = JsonPath.read(people, "$[?(@.employeeId == " + emp.getId() + ")].id");
        assertEquals(2, ((List<?>) JsonPath.read(people, "$")).size());
        mockMvc.perform(json(post("/api/performance/participants/" + empParticipant.get(0) + "/peers"), me, "[" + peerEmp.getId() + "]")).andExpect(status().isForbidden());
        mockMvc.perform(json(post("/api/performance/participants/" + empParticipant.get(0) + "/peers"), mgr, "[" + peerEmp.getId() + "]")).andExpect(status().isOk());
        List<Integer> peerTask = JsonPath.read(body(get("/api/performance/reviews/tasks").header("Authorization", peer)), "$[?(@.kind == 'PEER')].responseId");
        mockMvc.perform(json(post("/api/performance/reviews/" + peerTask.get(0)), peer, "{\"rating\":4,\"strengths\":\"Great collaborator\"}")).andExpect(status().isOk());

        mockMvc.perform(post("/api/performance/cycles/" + cycleId + "/advance").header("Authorization", hr)).andExpect(jsonPath("$.status").value("MANAGER_REVIEW"));
        List<Integer> mgrTask = JsonPath.read(body(get("/api/performance/reviews/tasks").header("Authorization", mgr)),
                "$[?(@.kind == 'MANAGER' && @.employeeId == " + emp.getId() + ")].responseId");
        mockMvc.perform(json(post("/api/performance/reviews/" + mgrTask.get(0)), mgr, "{\"rating\":4,\"strengths\":\"Strong half\"}")).andExpect(status().isOk());

        // Before release the employee sees no manager rating.
        mockMvc.perform(get("/api/performance/reviews/mine").header("Authorization", me)).andExpect(jsonPath("$[0].managerRating").doesNotExist());
        mockMvc.perform(json(post("/api/performance/participants/" + empParticipant.get(0) + "/calibrate"), mgr, "{\"finalRating\":5}")).andExpect(status().isForbidden());
        mockMvc.perform(json(post("/api/performance/participants/" + empParticipant.get(0) + "/calibrate"), hr, "{\"finalRating\":5,\"note\":\"Top of the band\"}"))
                .andExpect(jsonPath("$.finalRating").value(5.0));
        mockMvc.perform(post("/api/performance/cycles/" + cycleId + "/release").header("Authorization", hr)).andExpect(jsonPath("$.status").value("RELEASED"));
        String mine = body(get("/api/performance/reviews/mine").header("Authorization", me));
        assertEquals(5.0, ((Number) JsonPath.read(mine, "$[0].finalRating")).doubleValue());
        List<String> peerNames = JsonPath.read(mine, "$[0].responses[?(@.kind == 'PEER')].reviewerName");
        assertEquals(List.of("A colleague"), peerNames);
        mockMvc.perform(post("/api/performance/participants/" + empParticipant.get(0) + "/acknowledge").header("Authorization", me))
                .andExpect(jsonPath("$.acknowledgedAt").exists());

        // Compensation round from the released ratings: rating 5 -> 15% by default.
        LocalDate effective = LocalDate.now(ZoneId.of("Asia/Kolkata")).withDayOfMonth(1);
        mockMvc.perform(json(post("/api/performance/compensation"), mgr, "{\"name\":\"x\",\"effectiveFrom\":\"" + effective + "\",\"budgetPercent\":10}"))
                .andExpect(status().isForbidden());
        String round = body(json(post("/api/performance/compensation"), hr,
                "{\"name\":\"Appraisal 2026\",\"effectiveFrom\":\"" + effective + "\",\"budgetPercent\":10,\"performanceCycleId\":" + cycleId + "}"));
        Integer roundId = JsonPath.read(round, "$.id");
        List<Double> pct = JsonPath.read(round, "$.recommendations[?(@.employeeId == " + emp.getId() + ")].proposedPercent");
        assertEquals(15.0, pct.get(0));
        String applied = body(post("/api/performance/compensation/" + roundId + "/apply").header("Authorization", hr));
        assertEquals(1, (Integer) JsonPath.read(applied, "$.applied"));
        assertEquals("APPLIED", JsonPath.read(applied, "$.cycle.status"));
        assertEquals(0, new BigDecimal("46000").compareTo(
                salaryStructureRepository.findByEmployeeIdAndActiveTrueAndDeletedFalse(emp.getId()).orElseThrow().getGrossSalary()));
    }

    @Test
    void okrsFeedbackOneOnOnesAndPip() throws Exception {
        String company = body(json(post("/api/performance/okrs"), hr, "{\"level\":\"COMPANY\",\"period\":\"2026-Q4\",\"title\":\"Grow revenue\"}"));
        Integer companyOkr = JsonPath.read(company, "$.id");
        mockMvc.perform(json(post("/api/performance/okrs"), me, "{\"level\":\"COMPANY\",\"period\":\"2026-Q4\",\"title\":\"Nope\"}")).andExpect(status().isForbidden());
        String personal = body(json(post("/api/performance/okrs"), me, "{\"level\":\"PERSONAL\",\"period\":\"2026-Q4\",\"title\":\"Close deals\",\"parentId\":" + companyOkr
                + ",\"keyResults\":[{\"title\":\"Deals closed\",\"startValue\":0,\"targetValue\":10}]}"));
        Integer kr = JsonPath.read(personal, "$.keyResults[0].id");
        Integer personalId = JsonPath.read(personal, "$.id");
        mockMvc.perform(json(post("/api/performance/key-results/" + kr + "/check-in"), peer, "{\"currentValue\":9}")).andExpect(status().isForbidden());
        mockMvc.perform(json(post("/api/performance/key-results/" + kr + "/check-in"), me, "{\"currentValue\":5,\"note\":\"Halfway\"}"))
                .andExpect(jsonPath("$.progress").value(50.0));
        mockMvc.perform(json(post("/api/performance/okrs/" + personalId + "/status"), mgr, "{\"status\":\"AT_RISK\"}")).andExpect(jsonPath("$.status").value("AT_RISK"));
        mockMvc.perform(get("/api/performance/okrs?period=2026-Q4").header("Authorization", peer)).andExpect(jsonPath("$.length()").value(2));

        // Feedback: public praise reaches the wall; constructive feedback is always private.
        mockMvc.perform(json(post("/api/performance/feedback"), peer, "{\"toEmployeeId\":" + emp.getId() + ",\"kind\":\"PRAISE\",\"visibility\":\"PUBLIC\",\"valueTag\":\"Ownership\",\"message\":\"Thanks for the weekend fix\"}"))
                .andExpect(jsonPath("$.visibility").value("PUBLIC"));
        mockMvc.perform(json(post("/api/performance/feedback"), peer, "{\"toEmployeeId\":" + emp.getId() + ",\"kind\":\"CONSTRUCTIVE\",\"visibility\":\"PUBLIC\",\"message\":\"Shorter standups please\"}"))
                .andExpect(jsonPath("$.visibility").value("PRIVATE"));
        mockMvc.perform(get("/api/performance/feedback/wall").header("Authorization", me)).andExpect(jsonPath("$[0].message").value("Thanks for the weekend fix"));
        mockMvc.perform(get("/api/performance/feedback/received?employeeId=" + emp.getId()).header("Authorization", peer)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/performance/feedback/received?employeeId=" + emp.getId()).header("Authorization", mgr)).andExpect(jsonPath("$.length()").value(2));

        // 1:1 - private notes stay with the manager.
        mockMvc.perform(json(post("/api/performance/one-on-ones"), peer, "{\"employeeId\":" + emp.getId() + ",\"scheduledAt\":\"2026-11-03T10:00:00\"}")).andExpect(status().isForbidden());
        String meeting = body(json(post("/api/performance/one-on-ones"), mgr, "{\"employeeId\":" + emp.getId() + ",\"scheduledAt\":\"2026-11-03T10:00:00\",\"agenda\":\"Q4 goals\"}"));
        Integer meetingId = JsonPath.read(meeting, "$.id");
        mockMvc.perform(json(put("/api/performance/one-on-ones/" + meetingId), mgr, "{\"privateNotes\":\"Consider for lead role\"}")).andExpect(status().isOk());
        mockMvc.perform(json(put("/api/performance/one-on-ones/" + meetingId), me, "{\"agenda\":\"Q4 goals; training budget\",\"privateNotes\":\"hack\"}"))
                .andExpect(jsonPath("$.privateNotes").doesNotExist());
        mockMvc.perform(get("/api/performance/one-on-ones").header("Authorization", mgr)).andExpect(jsonPath("$[0].privateNotes").value("Consider for lead role"));

        // PIP: manager opens, employee acknowledges, peers can't see it.
        String pip = body(json(post("/api/performance/pips"), mgr, "{\"employeeId\":" + emp.getId() + ",\"startDate\":\"2026-11-01\",\"endDate\":\"2026-12-31\","
                + "\"reason\":\"Missed targets\",\"expectations\":[{\"goal\":\"Close 5 deals\",\"measure\":\"CRM\"}]}"));
        Integer pipId = JsonPath.read(pip, "$.id");
        mockMvc.perform(get("/api/performance/pips").header("Authorization", peer)).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/performance/pips").header("Authorization", hr)).andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(json(post("/api/performance/pips/" + pipId + "/check-in"), mgr, "{\"note\":\"2 deals so far\",\"rating\":\"SOME_PROGRESS\"}"))
                .andExpect(jsonPath("$.checkIns[0].note").value("2 deals so far"));
        mockMvc.perform(post("/api/performance/pips/" + pipId + "/acknowledge").header("Authorization", me)).andExpect(jsonPath("$.acknowledgedAt").exists());
        mockMvc.perform(json(post("/api/performance/pips/" + pipId + "/close"), mgr, "{\"outcome\":\"EXTENDED\",\"newEndDate\":\"2027-01-31\"}"))
                .andExpect(jsonPath("$.status").value("ACTIVE")).andExpect(jsonPath("$.endDate").value("2027-01-31"));
        mockMvc.perform(json(post("/api/performance/pips/" + pipId + "/close"), mgr, "{\"outcome\":\"SUCCESSFUL\",\"note\":\"Targets met\"}"))
                .andExpect(jsonPath("$.status").value("SUCCESSFUL"));
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String token, String content) {
        return b.header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(content);
    }

    private String body(MockHttpServletRequestBuilder b) throws Exception {
        return mockMvc.perform(b).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
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
        Employee e = new Employee();
        e.setEmployeeCode(code);
        e.setFirstName("Person");
        e.setLastName(code);
        e.setEmail(user.getEmail());
        e.setDateOfJoining(LocalDate.of(2024, 1, 1));
        e.setCompany(company);
        e.setUser(user);
        e.setReportingManager(manager);
        return employeeRepository.saveAndFlush(e);
    }

    private String token(User user, String role) {
        return "Bearer " + jwtService.generateAccessToken(user.getUsername(), List.of(role));
    }
}
