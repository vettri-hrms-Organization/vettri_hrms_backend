package com.haodaone.salary.fbp;

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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FBP: declare, approve (special allowance carved out), earn with payroll, claim, and get paid tax-free. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FbpFlowTest {

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

    @BeforeEach
    void restoreSystemRoles() {
        dataSeeder.run();
    }

    @Test
    void declareApproveEarnClaimAndGetPaidTaxFree() throws Exception {
        String s = UUID.randomUUID().toString().substring(0, 8);
        Company draft = new Company();
        draft.setName("FBP " + s);
        Company company = companyRepository.saveAndFlush(draft);
        User hrUser = user(company, "HR_ADMIN", "hr-" + s);
        employee(company, hrUser, "H" + s);
        User empUser = user(company, "EMPLOYEE", "emp-" + s);
        Employee emp = employee(company, empUser, "E" + s);
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
        String hr = token(hrUser, "HR_ADMIN");
        String me = token(empUser, "EMPLOYEE");

        String comps = mockMvc.perform(post("/api/fbp/components/defaults").header("Authorization", hr))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<Integer> fuelIds = JsonPath.read(comps, "$[?(@.name == 'Fuel & vehicle maintenance')].id");
        Integer fuel = fuelIds.get(0);
        List<Integer> phoneIds = JsonPath.read(comps, "$[?(@.name == 'Telephone & internet')].id");
        Integer phone = phoneIds.get(0);
        mockMvc.perform(post("/api/fbp/components/defaults").header("Authorization", me)).andExpect(status().is4xxClientError());

        // 24,000 + 12,000 a year = 3,000 a month, inside the 10,000 special allowance.
        mockMvc.perform(post("/api/fbp/me/declaration").header("Authorization", me).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[{\"componentId\":" + fuel + ",\"annualAmount\":24000},{\"componentId\":" + phone + ",\"annualAmount\":12000}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.declaration.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.declaration.monthlyTotal").value(3000.0));
        mockMvc.perform(post("/api/fbp/me/declaration").header("Authorization", me).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lines\":[{\"componentId\":" + fuel + ",\"annualAmount\":99999}]}"))
                .andExpect(status().isBadRequest());

        String declarations = mockMvc.perform(get("/api/fbp/declarations?status=SUBMITTED").header("Authorization", hr))
                .andReturn().getResponse().getContentAsString();
        Integer declarationId = JsonPath.read(declarations, "$[0].id");
        mockMvc.perform(post("/api/fbp/declarations/" + declarationId + "/approve").header("Authorization", hr)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.status").value("APPROVED"));
        assertEquals(0, new BigDecimal("7000").compareTo(
                salaryStructureRepository.findByEmployeeIdAndActiveTrueAndDeletedFalse(emp.getId()).orElseThrow().getComponents().getSpecialAllowance()));

        // One processed month earns 1,000 of phone allowance.
        YearMonth month = YearMonth.now(ZoneId.of("Asia/Kolkata"));
        processRun(hr, month);
        mockMvc.perform(get("/api/fbp/me").header("Authorization", me))
                .andExpect(jsonPath("$.declaration.monthsEarned").value(1));
        mockMvc.perform(post("/api/fbp/me/claims").header("Authorization", me).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"componentId\":" + phone + ",\"billDate\":\"" + LocalDate.now(ZoneId.of("Asia/Kolkata")) + "\",\"amount\":5000}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/fbp/me/claims").header("Authorization", me).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"componentId\":" + phone + ",\"billDate\":\"" + LocalDate.now(ZoneId.of("Asia/Kolkata")) + "\",\"billReference\":\"JIO-123\",\"amount\":800}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claims[0].status").value("SUBMITTED"));

        String pending = mockMvc.perform(get("/api/fbp/claims").header("Authorization", hr)).andReturn().getResponse().getContentAsString();
        Integer claimId = JsonPath.read(pending, "$[0].id");
        mockMvc.perform(post("/api/fbp/claims/" + claimId + "/approve").header("Authorization", hr)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // Next payroll pays it as a non-taxable line.
        String run = mockMvc.perform(post("/api/salary/payroll-runs").header("Authorization", hr).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"periodMonth\":" + month.plusMonths(1).getMonthValue() + ",\"periodYear\":" + month.plusMonths(1).getYear() + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        List<Object> fbpLines = JsonPath.read(run, "$.items[?(@.employeeId == " + emp.getId() + ")].adjustments[?(@.code == 'FBP')]");
        assertEquals(1, fbpLines.size());
        List<Boolean> taxable = JsonPath.read(run, "$.items[?(@.employeeId == " + emp.getId() + ")].adjustments[?(@.code == 'FBP')].taxable");
        assertEquals(false, taxable.get(0));
    }

    private void processRun(String hr, YearMonth month) throws Exception {
        String run = mockMvc.perform(post("/api/salary/payroll-runs").header("Authorization", hr).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"periodMonth\":" + month.getMonthValue() + ",\"periodYear\":" + month.getYear() + "}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Integer runId = JsonPath.read(run, "$.run.id");
        mockMvc.perform(post("/api/salary/payroll-runs/" + runId + "/process").header("Authorization", hr)).andExpect(status().isOk());
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
        Employee e = new Employee();
        e.setEmployeeCode(code);
        e.setFirstName("Person");
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
