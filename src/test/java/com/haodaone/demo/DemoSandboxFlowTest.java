package com.haodaone.demo;

import com.haodaone.common.exception.BadRequestException;
import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.config.DataSeeder;
import com.haodaone.demo.service.DemoCompanyService;
import com.haodaone.demo.service.DemoSandboxService;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.notifications.service.NotificationService;
import com.haodaone.recruitment.service.EmailService;
import com.haodaone.security.JwtService;
import com.haodaone.user.entity.Role;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.RoleRepository;
import com.haodaone.user.repository.UserRepository;
import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Customer demo workspace: opened once per company, isolated from the real company, role switching, and cleanup. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DemoSandboxFlowTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private DataSeeder dataSeeder;
    @Autowired private DemoSandboxService sandboxService;
    @Autowired private DemoCompanyService demoCompanyService;
    @Autowired private TransactionTemplate tx;
    @PersistenceContext private EntityManager em;
    @MockitoBean private NotificationService notificationService;
    @MockitoBean private EmailService emailService;

    private Company company;
    private String admin;
    private String employee;

    @BeforeEach
    void setUp() {
        dataSeeder.run();
        String s = UUID.randomUUID().toString().substring(0, 8);
        Company draft = new Company();
        draft.setName("Real " + s);
        company = companyRepository.saveAndFlush(draft);
        User adminUser = user("COMPANY_ADMIN", "owner-" + s);
        User employeeUser = user("EMPLOYEE", "staff-" + s);
        realEmployee(employeeUser, "R" + s);
        admin = bearer(adminUser.getUsername(), "COMPANY_ADMIN");
        employee = bearer(employeeUser.getUsername(), "EMPLOYEE");
    }

    @Test
    void opensOneSandboxPerCompanyAndKeepsTheRealCompanyClean() throws Exception {
        mockMvc.perform(get("/api/demo-sandbox").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("NONE"))
                .andExpect(jsonPath("$.choice").doesNotExist());

        String first = mockMvc.perform(post("/api/demo-sandbox").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ACTIVE"))
                .andExpect(jsonPath("$.choice").value("DEMO"))
                .andExpect(jsonPath("$.employees").value(40))
                .andReturn().getResponse().getContentAsString();
        String second = mockMvc.perform(post("/api/demo-sandbox").header("Authorization", admin))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Integer demoId = JsonPath.read(first, "$.demoCompanyId");
        assertEquals(demoId, (Integer) JsonPath.read(second, "$.demoCompanyId"), "a second click must not create another sandbox");

        Company sandbox = companyRepository.findById(demoId.longValue()).orElseThrow();
        assertTrue(sandbox.isDemo());
        assertEquals(company.getId(), sandbox.getDemoParentCompanyId());
        assertTrue(sandbox.getDemoExpiresAt().isAfter(LocalDateTime.now().plusDays(13)), "paid companies get 14 demo days");
        assertEquals(1L, count("SELECT COUNT(e) FROM Employee e WHERE e.company.id = :c AND e.deleted = false", company.getId()),
                "no demo employee may land in the real company");

        // Only admins may open it.
        mockMvc.perform(post("/api/demo-sandbox").header("Authorization", employee)).andExpect(status().isForbidden());
    }

    @Test
    void switchesBetweenDemoRolesWithoutReachingAnyOtherCompany() throws Exception {
        String created = mockMvc.perform(post("/api/demo-sandbox").header("Authorization", admin))
                .andReturn().getResponse().getContentAsString();
        Integer demoId = JsonPath.read(created, "$.demoCompanyId");

        String hrSession = mockMvc.perform(post("/api/demo-sandbox/session").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"HR\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String hr = "Bearer " + JsonPath.read(hrSession, "$.accessToken");
        mockMvc.perform(get("/api/auth/me").header("Authorization", hr))
                .andExpect(jsonPath("$.companyId").value(demoId))
                .andExpect(jsonPath("$.roles[?(@ == 'HR_ADMIN')]").isNotEmpty());
        mockMvc.perform(get("/api/demo-sandbox/current").header("Authorization", hr))
                .andExpect(jsonPath("$.demo").value(true))
                .andExpect(jsonPath("$.viewingAs").value("HR"))
                .andExpect(jsonPath("$.customerSandbox").value(true));

        // Inside the demo the explorer can switch role; the employee view holds only the Employee role.
        String employeeSession = mockMvc.perform(post("/api/demo-sandbox/session").header("Authorization", hr)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"EMPLOYEE\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> roles = JsonPath.read(employeeSession, "$.user.roles");
        assertEquals(List.of("EMPLOYEE"), roles);

        // A different company's admin has no sandbox and cannot reach this one.
        Company otherDraft = new Company();
        otherDraft.setName("Other " + UUID.randomUUID().toString().substring(0, 8));
        Company other = companyRepository.saveAndFlush(otherDraft);
        company = other;
        User otherAdmin = user("COMPANY_ADMIN", "other-" + UUID.randomUUID().toString().substring(0, 8));
        mockMvc.perform(post("/api/demo-sandbox/session").header("Authorization", bearer(otherAdmin.getUsername(), "COMPANY_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isNotFound());

        // The real company's own employees see no demo banner.
        mockMvc.perform(get("/api/demo-sandbox/current").header("Authorization", employee))
                .andExpect(jsonPath("$.demo").value(false));
    }

    @Test
    void cleansUpOnlyExpiredSandboxesAndNeverARealCompany() throws Exception {
        String created = mockMvc.perform(post("/api/demo-sandbox").header("Authorization", admin))
                .andReturn().getResponse().getContentAsString();
        Long demoId = ((Integer) JsonPath.read(created, "$.demoCompanyId")).longValue();

        assertEquals(0, sandboxService.cleanupExpired(), "a live sandbox is left alone");
        tx.executeWithoutResult(status -> em.find(Company.class, demoId).setDemoExpiresAt(LocalDateTime.now().minusMinutes(5)));

        mockMvc.perform(post("/api/demo-sandbox/session").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest());

        assertTrue(sandboxService.cleanupExpired() >= 1);
        assertNotNull(companyRepository.findById(demoId).orElseThrow().getDemoCleanedAt());
        assertEquals(0L, count("SELECT COUNT(e) FROM Employee e WHERE e.company.id = :c AND e.deleted = false", demoId));
        assertEquals(0L, count("SELECT COUNT(u) FROM User u WHERE u.company.id = :c AND u.active = true", demoId),
                "demo logins are disabled");
        assertEquals(1L, count("SELECT COUNT(e) FROM Employee e WHERE e.company.id = :c AND e.deleted = false", company.getId()),
                "real company untouched");
        assertEquals(0, sandboxService.cleanupExpired(), "cleanup is idempotent");

        mockMvc.perform(get("/api/demo-sandbox").header("Authorization", admin)).andExpect(jsonPath("$.state").value("EXPIRED"));
        assertThrows(BadRequestException.class, () -> demoCompanyService.cleanupSandbox(company.getId()),
                "a real company can never be wiped");
    }

    @Test
    void remembersTheSetupChoice() throws Exception {
        mockMvc.perform(post("/api/demo-sandbox/choice").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"choice\":\"SETUP\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.choice").value("SETUP"));
        mockMvc.perform(post("/api/demo-sandbox/choice").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"choice\":\"MAYBE\"}"))
                .andExpect(status().isBadRequest());
    }

    private long count(String jpql, Long companyId) {
        return tx.execute(status -> em.createQuery(jpql, Long.class).setParameter("c", companyId).getSingleResult());
    }

    private String bearer(String username, String role) {
        return "Bearer " + jwtService.generateAccessToken(username, List.of(role));
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

    private Employee realEmployee(User user, String code) {
        Employee e = new Employee();
        e.setEmployeeCode(code);
        e.setFirstName("Real");
        e.setLastName(code);
        e.setEmail(user.getEmail());
        e.setDateOfJoining(LocalDate.of(2024, 1, 1));
        e.setCompany(company);
        e.setUser(user);
        return employeeRepository.saveAndFlush(e);
    }
}
