package com.haodaone.workflow;

import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.config.DataSeeder;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.leave.entity.LeaveType;
import com.haodaone.leave.repository.LeaveTypeRepository;
import com.haodaone.letters.entity.IssuedLetter;
import com.haodaone.letters.repository.IssuedLetterRepository;
import com.haodaone.letters.service.LetterStorageService;
import com.haodaone.notifications.service.NotificationService;
import com.haodaone.probation.ProbationService;
import com.haodaone.security.JwtService;
import com.haodaone.travel.TravelService;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phase C: sandwich leave, multi-level leave approval, probation, helpdesk, e-sign, travel advances and surveys. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PhaseCFlowTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private LeaveTypeRepository leaveTypeRepository;
    @Autowired private IssuedLetterRepository issuedLetterRepository;
    @Autowired private TravelService travelService;
    @Autowired private ProbationService probationService;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DataSeeder dataSeeder;
    @MockitoBean private NotificationService notificationService;
    @MockitoBean private LetterStorageService letterStorage;

    private final Map<String, byte[]> files = new ConcurrentHashMap<>();
    private Company company;
    private String hr, skip, mgr, me;
    private Employee emp, manager;
    private User empUser;

    @BeforeEach
    void setUp() {
        dataSeeder.run();
        doAnswer(inv -> { files.put(inv.getArgument(0), inv.getArgument(1)); return null; }).when(letterStorage).put(anyString(), any(), anyString());
        when(letterStorage.get(anyString())).thenAnswer(inv -> files.get((String) inv.getArgument(0)));
        String s = UUID.randomUUID().toString().substring(0, 8);
        Company draft = new Company();
        draft.setName("PhaseC " + s);
        company = companyRepository.saveAndFlush(draft);
        User hrUser = user("HR_ADMIN", "hr-" + s);
        employee(hrUser, "H" + s, "Hema", null);
        User skipUser = user("MANAGER", "skip-" + s);
        Employee skipEmp = employee(skipUser, "S" + s, "Suresh", null);
        User mgrUser = user("MANAGER", "mgr-" + s);
        manager = employee(mgrUser, "M" + s, "Meera", skipEmp);
        empUser = user("EMPLOYEE", "emp-" + s);
        emp = employee(empUser, "E" + s, "Arjun", manager);
        hr = token(hrUser, "HR_ADMIN");
        skip = token(skipUser, "MANAGER");
        mgr = token(mgrUser, "MANAGER");
        me = token(empUser, "EMPLOYEE");
    }

    @Test
    void sandwichRuleAndThreeLevelLeaveApproval() throws Exception {
        LeaveType sandwich = leaveType("Sandwich CL", "SCL" + emp.getId(), true);
        LeaveType plain = leaveType("Plain CL", "PCL" + emp.getId(), false);
        // Fri 8 Jan 2027 -> Mon 11 Jan 2027: the weekend in between counts only under the sandwich rule.
        Number plainDays = JsonPath.read(body(json(post("/api/leave-requests"), me, apply(plain, "2027-01-08", "2027-01-11")), 201), "$.days");
        assertTrue(plainDays.doubleValue() < 4.0);

        mockMvc.perform(json(put("/api/approval-workflows/LEAVE"), mgr, "{\"levels\":[\"REPORTING_MANAGER\",\"SKIP_LEVEL_MANAGER\",\"HR\"]}")).andExpect(status().isForbidden());
        mockMvc.perform(json(put("/api/approval-workflows/LEAVE"), hr, "{\"levels\":[\"REPORTING_MANAGER\",\"SKIP_LEVEL_MANAGER\",\"HR\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));

        String leave = body(json(post("/api/leave-requests"), me, apply(sandwich, "2027-01-15", "2027-01-18")), 201);
        assertEquals(4.0, ((Number) JsonPath.read(leave, "$.days")).doubleValue());
        Integer id = JsonPath.read(leave, "$.id");
        assertTrue(((String) JsonPath.read(leave, "$.pendingWith")).contains("Meera"));

        // Skip-level can't jump the queue; the reporting manager approves level 1.
        mockMvc.perform(json(patch("/api/leave-requests/" + id + "/approve"), skip, "{}")).andExpect(status().isForbidden());
        mockMvc.perform(json(patch("/api/leave-requests/" + id + "/approve"), mgr, "{\"note\":\"ok\"}"))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.approvalLevel").value(1));
        // The skip-level manager now sees it in their team list and approves level 2.
        String team = body(get("/api/leave-requests/team?status=PENDING").header("Authorization", skip));
        assertFalse(((List<?>) JsonPath.read(team, "$[?(@.id == " + id + ")]")).isEmpty());
        mockMvc.perform(json(patch("/api/leave-requests/" + id + "/approve"), skip, "{}"))
                .andExpect(jsonPath("$.status").value("PENDING")).andExpect(jsonPath("$.pendingWith").value("HR"));
        mockMvc.perform(json(patch("/api/leave-requests/" + id + "/approve"), hr, "{}"))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.approvalTrail.length()").value(3));
    }

    @Test
    void probationHelpdeskAndSurvey() throws Exception {
        // Probation: HR starts it, the review opens, the manager recommends, HR extends then confirms.
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Kolkata"));
        String p = body(json(post("/api/probation"), hr, "{\"employeeId\":" + emp.getId() + ",\"probationEnd\":\"" + today.plusDays(5) + "\"}"));
        Integer pid = JsonPath.read(p, "$.id");
        assertEquals("MANAGER_REVIEW", JsonPath.read(p, "$.status"));
        mockMvc.perform(json(post("/api/probation/" + pid + "/recommend"), me, "{\"recommendation\":\"CONFIRM\",\"feedback\":\"self\"}")).andExpect(status().isForbidden());
        mockMvc.perform(json(post("/api/probation/" + pid + "/recommend"), mgr, "{\"recommendation\":\"EXTEND\",\"feedback\":\"Needs another month on reporting\"}"))
                .andExpect(jsonPath("$.status").value("HR_REVIEW"));
        mockMvc.perform(get("/api/probation/me").header("Authorization", me)).andExpect(jsonPath("$.managerFeedback").doesNotExist());
        mockMvc.perform(json(post("/api/probation/" + pid + "/decide"), hr, "{\"decision\":\"EXTEND\",\"newEnd\":\"" + today.plusDays(40) + "\"}"))
                .andExpect(jsonPath("$.status").value("ON_PROBATION")).andExpect(jsonPath("$.extensions").value(1));
        mockMvc.perform(json(post("/api/probation/" + pid + "/decide"), hr, "{\"decision\":\"CONFIRM\",\"note\":\"Well done\"}"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        // Helpdesk: default categories, raise, internal note hidden from the employee, resolve and rate.
        String cats = body(get("/api/helpdesk/categories").header("Authorization", me));
        List<Integer> payroll = JsonPath.read(cats, "$[?(@.name == 'Payroll & salary')].id");
        String ticket = body(json(post("/api/helpdesk/tickets"), me, "{\"categoryId\":" + payroll.get(0) + ",\"subject\":\"Payslip missing\",\"description\":\"October payslip not visible\"}"));
        Integer tid = JsonPath.read(ticket, "$.id");
        assertTrue(((String) JsonPath.read(ticket, "$.ticketNumber")).startsWith("HD-"));
        mockMvc.perform(get("/api/helpdesk/tickets").header("Authorization", me)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/helpdesk/tickets/" + tid).header("Authorization", mgr)).andExpect(status().isNotFound());
        mockMvc.perform(json(post("/api/helpdesk/tickets/" + tid + "/comments"), hr, "{\"body\":\"Payroll run was late\",\"internal\":true}")).andExpect(status().isOk());
        mockMvc.perform(json(post("/api/helpdesk/tickets/" + tid + "/comments"), hr, "{\"body\":\"Fixed - please check\",\"status\":\"RESOLVED\"}"))
                .andExpect(jsonPath("$.status").value("RESOLVED"));
        mockMvc.perform(get("/api/helpdesk/tickets/" + tid).header("Authorization", me)).andExpect(jsonPath("$.comments.length()").value(1));
        mockMvc.perform(json(post("/api/helpdesk/tickets/" + tid + "/rate"), me, "{\"rating\":5}")).andExpect(jsonPath("$.status").value("CLOSED"));

        // Anonymous eNPS: results hidden under 3 answers; no double answers.
        String survey = body(json(post("/api/surveys"), hr, "{\"title\":\"Pulse\",\"kind\":\"ENPS\"}"));
        Integer sid = JsonPath.read(survey, "$.id");
        mockMvc.perform(post("/api/surveys/" + sid + "/publish").header("Authorization", hr)).andExpect(jsonPath("$.status").value("OPEN"));
        mockMvc.perform(json(post("/api/surveys/" + sid + "/answers"), me, "{\"enps\":10,\"why\":\"Great team\"}")).andExpect(jsonPath("$.answered").value(true));
        mockMvc.perform(json(post("/api/surveys/" + sid + "/answers"), me, "{\"enps\":3}")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/surveys/" + sid + "/results").header("Authorization", hr)).andExpect(jsonPath("$.hidden").value(true));
        mockMvc.perform(json(post("/api/surveys/" + sid + "/answers"), mgr, "{\"enps\":9}")).andExpect(status().isOk());
        mockMvc.perform(json(post("/api/surveys/" + sid + "/answers"), skip, "{\"enps\":5}")).andExpect(status().isOk());
        mockMvc.perform(get("/api/surveys/" + sid + "/results").header("Authorization", hr))
                .andExpect(jsonPath("$.hidden").value(false))
                .andExpect(jsonPath("$.questions[0].enps").value(33));
    }

    @Test
    void esignAndTravelAdvanceRecovery() throws Exception {
        byte[] pdf = blankPdf();
        files.put("letters/test.pdf", pdf);
        IssuedLetter letter = new IssuedLetter();
        letter.setCompany(company);
        letter.setEmployee(emp);
        letter.setLetterType("APPOINTMENT");
        letter.setTitle("Appointment letter");
        letter.setS3ObjectKey("letters/test.pdf");
        letter.setIssuedAt(LocalDateTime.now());
        letter = issuedLetterRepository.saveAndFlush(letter);

        mockMvc.perform(json(post("/api/esign"), me, "{\"letterId\":" + letter.getId() + "}")).andExpect(status().isForbidden());
        Integer rid = JsonPath.read(body(json(post("/api/esign"), hr, "{\"letterId\":" + letter.getId() + ",\"message\":\"Please sign\"}")), "$.id");
        mockMvc.perform(get("/api/esign/" + rid + "/document").header("Authorization", me)).andExpect(status().isOk());
        mockMvc.perform(json(post("/api/esign/" + rid + "/sign"), me, "{\"fullName\":\"Someone Else\",\"consent\":true}")).andExpect(status().isBadRequest());
        mockMvc.perform(json(post("/api/esign/" + rid + "/sign"), mgr, "{\"fullName\":\"Meera\",\"consent\":true}")).andExpect(status().isNotFound());
        // No saved signature yet: signing is refused with a clear message.
        mockMvc.perform(json(post("/api/esign/" + rid + "/sign"), me, "{\"fullName\":\"" + emp.getFullName() + "\",\"consent\":true}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Add your signature first")));
        mockMvc.perform(get("/api/esign/my-signature").header("Authorization", me)).andExpect(status().isNotFound());
        // A blank white photo has no signature in it.
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/esign/my-signature").file(new MockMultipartFile("file", "blank.png", "image/png", signaturePhoto(false)))
                .header("Authorization", me)).andExpect(status().isBadRequest());
        mockMvc.perform(multipart(HttpMethod.PUT, "/api/esign/my-signature").file(new MockMultipartFile("file", "sign.jpg", "image/png", signaturePhoto(true)))
                .param("source", "UPLOAD").header("Authorization", me))
                .andExpect(status().isOk()).andExpect(jsonPath("$.saved").value(true));
        byte[] saved = mockMvc.perform(get("/api/esign/my-signature").header("Authorization", me)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        java.awt.image.BufferedImage cleaned = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(saved));
        assertTrue(cleaned.getWidth() < 600, "cropped to the ink");
        assertEquals(0, (cleaned.getRGB(0, 0) >>> 24), "paper made transparent");
        mockMvc.perform(json(post("/api/esign/" + rid + "/sign"), me, "{\"fullName\":\"" + emp.getFullName() + "\",\"consent\":true}").header("X-Forwarded-For", "203.0.113.9"))
                .andExpect(jsonPath("$.status").value("SIGNED"))
                .andExpect(jsonPath("$.signerIp").value("203.0.113.9"));
        mockMvc.perform(get("/api/esign/" + rid + "/document").header("Authorization", me)).andExpect(status().isOk());

        // Travel: manager approves, finance pays a 10,000 advance; 7,500 spent -> 2,500 recovered via payroll.
        LocalDate depart = LocalDate.now(ZoneId.of("Asia/Kolkata")).plusDays(10);
        String trip = body(json(post("/api/travel"), me, "{\"purpose\":\"Client visit\",\"fromCity\":\"Chennai\",\"toCity\":\"Pune\",\"departDate\":\"" + depart
                + "\",\"returnDate\":\"" + depart.plusDays(2) + "\",\"travelMode\":\"FLIGHT\",\"estimatedCost\":15000,\"advanceAmount\":10000}"));
        Integer trId = JsonPath.read(trip, "$.id");
        assertEquals("REQUESTED", JsonPath.read(trip, "$.advanceStatus"));
        mockMvc.perform(json(post("/api/travel/" + trId + "/decision"), skip, "{\"approve\":true}")).andExpect(status().isNotFound());
        mockMvc.perform(json(post("/api/travel/" + trId + "/decision"), mgr, "{\"approve\":true}")).andExpect(jsonPath("$.status").value("APPROVED"));
        mockMvc.perform(json(post("/api/travel/" + trId + "/advance/pay"), mgr, "{\"reference\":\"UTR1\"}")).andExpect(status().isForbidden());
        mockMvc.perform(json(post("/api/travel/" + trId + "/advance/pay"), hr, "{\"reference\":\"UTR123\"}")).andExpect(jsonPath("$.advanceStatus").value("PAID"));
        assertEquals(1, travelService.settlementRecoveries(company.getId(), emp.getId()).size());
        mockMvc.perform(json(post("/api/travel/" + trId + "/settle"), hr, "{\"spentAmount\":7500,\"excessHandling\":\"PAYROLL\"}"))
                .andExpect(jsonPath("$.advanceStatus").value("RECOVERY")).andExpect(jsonPath("$.status").value("COMPLETED"));
        var deductions = travelService.deductionsFor(company.getId(), emp.getId(), null);
        assertEquals(1, deductions.size());
        assertEquals(0, new BigDecimal("2500").compareTo(deductions.get(0).amount()));
        travelService.markSettled(deductions, 99L);
        assertTrue(travelService.deductionsFor(company.getId(), emp.getId(), null).isEmpty());
    }

    // ------------------------------------------------------------------ helpers

    /** A 800x400 "photo": white paper, optionally with a dark pen stroke across the middle. */
    private static byte[] signaturePhoto(boolean withInk) throws Exception {
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(800, 400, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setColor(new java.awt.Color(246, 244, 240));
        g.fillRect(0, 0, 800, 400);
        if (withInk) {
            g.setColor(new java.awt.Color(20, 30, 90));
            g.setStroke(new java.awt.BasicStroke(6));
            g.drawPolyline(new int[]{250, 300, 340, 390, 450, 520}, new int[]{220, 160, 230, 170, 240, 190}, 6);
        }
        g.dispose();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private static byte[] blankPdf() throws Exception {
        try (var doc = new org.apache.pdfbox.pdmodel.PDDocument(); var out = new java.io.ByteArrayOutputStream()) {
            doc.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            doc.save(out);
            return out.toByteArray();
        }
    }

    private String apply(LeaveType type, String from, String to) {
        return "{\"employeeId\":" + emp.getId() + ",\"leaveTypeId\":" + type.getId() + ",\"startDate\":\"" + from + "\",\"endDate\":\"" + to + "\",\"reason\":\"Trip\"}";
    }

    private LeaveType leaveType(String name, String code, boolean sandwich) {
        LeaveType t = new LeaveType();
        t.setCompany(company);
        t.setName(name);
        t.setCode(code);
        t.setDefaultDaysPerYear(12);
        t.setActive(true);
        t.setAutoApprove(false);
        t.setSandwichRule(sandwich);
        return leaveTypeRepository.saveAndFlush(t);
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String token, String content) {
        return b.header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(content);
    }

    private String body(MockHttpServletRequestBuilder b) throws Exception {
        return body(b, 200);
    }

    private String body(MockHttpServletRequestBuilder b, int expected) throws Exception {
        return mockMvc.perform(b).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
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

    private Employee employee(User user, String code, String first, Employee reportsTo) {
        Employee e = new Employee();
        e.setEmployeeCode(code);
        e.setFirstName(first);
        e.setLastName(code);
        e.setEmail(user.getEmail());
        e.setDateOfJoining(LocalDate.of(2024, 1, 1));
        e.setCompany(company);
        e.setUser(user);
        e.setReportingManager(reportsTo);
        return employeeRepository.saveAndFlush(e);
    }

    private String token(User user, String role) {
        return "Bearer " + jwtService.generateAccessToken(user.getUsername(), List.of(role));
    }
}
