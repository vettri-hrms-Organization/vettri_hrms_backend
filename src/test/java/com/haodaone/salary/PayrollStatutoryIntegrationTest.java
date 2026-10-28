package com.haodaone.salary;

import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.salary.adjustment.PayrollAdjustment;
import com.haodaone.salary.adjustment.PayrollAdjustmentProvider;
import com.haodaone.salary.dto.CreatePayrollRunRequest;
import com.haodaone.salary.dto.PayrollItemDTO;
import com.haodaone.salary.entity.SalaryComponents;
import com.haodaone.salary.entity.SalaryStructure;
import com.haodaone.salary.entity.StatutorySettings;
import com.haodaone.salary.repository.PayrollItemRepository;
import com.haodaone.salary.repository.SalaryStructureRepository;
import com.haodaone.salary.repository.StatutorySettingsRepository;
import com.haodaone.salary.service.PayrollService;
import com.haodaone.salary.service.StatutoryReturnsService;
import com.haodaone.security.AuthorizationService;
import com.haodaone.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * createRun with auto-calculated statutory deductions and a PayrollAdjustmentProvider, then process
 * (settles adjustments) and the PF ECR / summary exports.
 */
@SpringBootTest
@ActiveProfiles("test")
class PayrollStatutoryIntegrationTest {

    static final RecordingProvider PROVIDER = new RecordingProvider();

    @TestConfiguration
    static class ProviderConfig {
        @Bean
        PayrollAdjustmentProvider recordingAdjustmentProvider() {
            return PROVIDER;
        }
    }

    /** Pays one employee 5,000 taxable overtime + 1,000 non-taxable reimbursement and records settlement. */
    static class RecordingProvider implements PayrollAdjustmentProvider {
        volatile Long employeeId;
        final List<PayrollAdjustment> settled = new CopyOnWriteArrayList<>();
        volatile Long settledItemId;

        @Override
        public List<PayrollAdjustment> adjustmentsFor(Long companyId, Long employeeId, YearMonth period) {
            if (!employeeId.equals(this.employeeId)) return List.of();
            return List.of(
                    new PayrollAdjustment("OVERTIME", "Overtime (10 h)", new BigDecimal("5000"), true, "TEST_OT", 11L),
                    new PayrollAdjustment("REIMBURSEMENT", "Fuel reimbursement", new BigDecimal("1000"), false, "TEST_OT", 12L));
        }

        @Override
        public Set<String> sourceTypes() {
            return Set.of("TEST_OT");
        }

        @Override
        public void markSettled(List<PayrollAdjustment> adjustments, Long payrollItemId) {
            settled.addAll(adjustments);
            settledItemId = payrollItemId;
        }
    }

    @Autowired private CompanyRepository companyRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private SalaryStructureRepository salaryStructureRepository;
    @Autowired private StatutorySettingsRepository statutorySettingsRepository;
    @Autowired private PayrollItemRepository payrollItemRepository;
    @Autowired private PayrollService payrollService;
    @Autowired private StatutoryReturnsService statutoryReturnsService;
    @Autowired private TransactionTemplate transactionTemplate;

    @MockitoBean
    private AuthorizationService authorizationService;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void autoStatutoryRunWithAdjustmentsProcessesAndExports() {
        Company company = createCompany();
        StatutorySettings settings = new StatutorySettings();
        settings.setCompany(company);
        settings.setPtState("KA");
        statutorySettingsRepository.saveAndFlush(settings);

        Employee high = createEmployee(company, "Asha", "100200300400");
        Employee low = createEmployee(company, "Low", null);
        Employee manual = createEmployee(company, "Manual", "999888777666");
        createStructure(company, high, "200000", "80000", "120000", null, true);
        createStructure(company, low, "10000", "4000", "4000", null, true);
        createStructure(company, manual, "30000", "0", "0", "1800", false);
        PROVIDER.employeeId = high.getId();

        TenantContext.setCurrentTenant(company.getId());
        when(authorizationService.hasOrganizationScope("SALARY_MANAGE")).thenReturn(true);

        CreatePayrollRunRequest request = new CreatePayrollRunRequest();
        request.setPeriodMonth(11);
        request.setPeriodYear(2026);
        var run = payrollService.createRun(request);
        assertEquals(3, run.getItems().size());

        PayrollItemDTO h = item(run.getItems(), high.getId());
        assertTrue(h.isStatutoryAuto());
        assertEquals(2, h.getAdjustments().size());
        assertAmount("6000", h.getAdjustmentsTotal());
        assertAmount("406000", h.getGrossSalary());
        assertAmount("405000", h.getTaxableGross());
        assertAmount("1800", h.getComponents().getPf());          // 12% of the 15,000 ceiling
        assertAmount("0", h.getComponents().getEsi());            // above the 21,000 threshold
        assertAmount("200", h.getComponents().getProfessionalTax()); // Karnataka
        // Projected FY taxable: 4,05,000 + 4 x 4,00,000 - 75,000 = 19,30,000 -> 1,93,440 tax over 5 months.
        assertAmount("38688", h.getComponents().getTds());
        assertAmount("1250", h.getEmployerEps());
        assertAmount("550", h.getEmployerEpf());
        assertAmount("365312", h.getNetSalary());

        PayrollItemDTO l = item(run.getItems(), low.getId());
        assertAmount("1200", l.getComponents().getPf());
        assertAmount("135", l.getComponents().getEsi());
        assertAmount("585", l.getEmployerEsi());
        assertAmount("0", l.getComponents().getProfessionalTax());
        assertAmount("0", l.getComponents().getTds());
        assertAmount("1785", l.getEmployerContributions());
        assertAmount("16665", l.getNetSalary());

        PayrollItemDTO m = item(run.getItems(), manual.getId());
        assertFalse(m.isStatutoryAuto());
        assertAmount("1800", m.getComponents().getPf());
        assertAmount("28200", m.getNetSalary());

        Long runId = run.getRun().getId();
        payrollService.process(runId);
        assertEquals(2, PROVIDER.settled.size());
        assertEquals(h.getId(), PROVIDER.settledItemId);
        Boolean allSettled = transactionTemplate.execute(status -> payrollItemRepository.findById(h.getId()).orElseThrow()
                .getAdjustments().stream().allMatch(a -> a.isSettled()));
        assertTrue(Boolean.TRUE.equals(allSettled));

        String ecr = new String(statutoryReturnsService.ecr(runId).content(), StandardCharsets.UTF_8);
        String[] lines = ecr.split("\r\n");
        assertEquals(2, lines.length);
        assertEquals("100200300400#~#ASHA EMPLOYEE#~#406000#~#15000#~#15000#~#15000#~#1800#~#1250#~#550#~#0#~#0", lines[0]);
        assertEquals("999888777666#~#MANUAL EMPLOYEE#~#30000#~#15000#~#15000#~#15000#~#1800#~#1250#~#550#~#0#~#0", lines[1]);

        var summary = statutoryReturnsService.summary(runId);
        assertEquals(3, summary.pfMembers());
        assertEquals(1, summary.esiMembers());
        assertTrue(summary.warnings().stream().anyMatch(w -> w.employeeId().equals(low.getId()) && w.message().contains("UAN")));

        String esi = new String(statutoryReturnsService.esiCsv(runId).content(), StandardCharsets.UTF_8);
        assertTrue(esi.contains("\"Low Employee\",30,18000.00,135.00,585.00"));
    }

    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    private PayrollItemDTO item(List<PayrollItemDTO> items, Long employeeId) {
        return items.stream().filter(i -> i.getEmployeeId().equals(employeeId)).findFirst().orElseThrow();
    }

    private Company createCompany() {
        Company company = new Company();
        company.setName("Statutory payroll " + UUID.randomUUID());
        return companyRepository.saveAndFlush(company);
    }

    private Employee createEmployee(Company company, String firstName, String uan) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Employee employee = new Employee();
        employee.setEmployeeCode("S" + suffix);
        employee.setFirstName(firstName);
        employee.setLastName("Employee");
        employee.setEmail(suffix + "@statutory.example");
        employee.setDateOfJoining(LocalDate.of(2024, 1, 1));
        employee.setUanNumber(uan);
        employee.setCompany(company);
        return employeeRepository.saveAndFlush(employee);
    }

    private void createStructure(Company company, Employee employee, String basic, String hra, String special,
                                 String pf, boolean auto) {
        SalaryComponents components = new SalaryComponents();
        components.setBasicSalary(new BigDecimal(basic));
        components.setHra(new BigDecimal(hra));
        components.setSpecialAllowance(new BigDecimal(special));
        if (pf != null) components.setPf(new BigDecimal(pf));
        SalaryStructure structure = new SalaryStructure();
        structure.setCompany(company);
        structure.setEmployee(employee);
        structure.setEffectiveFrom(LocalDate.of(2024, 1, 1));
        structure.setComponents(components);
        structure.setAutoStatutory(auto);
        structure.recalculate();
        salaryStructureRepository.saveAndFlush(structure);
    }
}
