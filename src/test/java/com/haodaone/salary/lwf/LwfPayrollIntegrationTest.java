package com.haodaone.salary.lwf;

import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.salary.dto.CreatePayrollRunRequest;
import com.haodaone.salary.dto.PayrollItemDTO;
import com.haodaone.salary.entity.SalaryComponents;
import com.haodaone.salary.entity.SalaryStructure;
import com.haodaone.salary.entity.StatutorySettings;
import com.haodaone.salary.repository.SalaryStructureRepository;
import com.haodaone.salary.repository.StatutorySettingsRepository;
import com.haodaone.salary.service.PayrollService;
import com.haodaone.security.AuthorizationService;
import com.haodaone.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/** Karnataka LWF: deducted only in December, employer share recorded for the return. */
@SpringBootTest
@ActiveProfiles("test")
class LwfPayrollIntegrationTest {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private SalaryStructureRepository salaryStructureRepository;
    @Autowired private StatutorySettingsRepository statutorySettingsRepository;
    @Autowired private PayrollService payrollService;
    @Autowired private LwfService lwfService;

    @MockitoBean private AuthorizationService authorizationService;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void karnatakaLwfIsDeductedInDecemberOnly() {
        Company draft = new Company();
        draft.setName("LWF " + UUID.randomUUID());
        Company company = companyRepository.saveAndFlush(draft);
        StatutorySettings settings = new StatutorySettings();
        settings.setCompany(company);
        settings.setPtState("KA");
        statutorySettingsRepository.saveAndFlush(settings);
        Employee e = new Employee();
        e.setEmployeeCode("L" + UUID.randomUUID().toString().substring(0, 8));
        e.setFirstName("Asha");
        e.setLastName("Rao");
        e.setEmail(UUID.randomUUID() + "@lwf.example");
        e.setDateOfJoining(LocalDate.of(2024, 1, 1));
        e.setCompany(company);
        Employee asha = employeeRepository.saveAndFlush(e);
        SalaryComponents c = new SalaryComponents();
        c.setBasicSalary(new BigDecimal("30000"));
        SalaryStructure s = new SalaryStructure();
        s.setCompany(company);
        s.setEmployee(asha);
        s.setEffectiveFrom(LocalDate.of(2024, 1, 1));
        s.setComponents(c);
        s.setAutoStatutory(false);
        s.recalculate();
        salaryStructureRepository.saveAndFlush(s);
        TenantContext.setCurrentTenant(company.getId());
        when(authorizationService.hasOrganizationScope("SALARY_MANAGE")).thenReturn(true);

        lwfService.addState("KA");
        assertTrue(lwfService.states().stream().anyMatch(x -> x.code().equals("KA") && x.added()));

        PayrollItemDTO november = run(2025, 11).getItems().get(0);
        assertTrue(november.getDeductionLines().isEmpty(), "Karnataka LWF is yearly, in December");

        var december = run(2025, 12);
        PayrollItemDTO item = december.getItems().get(0);
        assertEquals(1, item.getDeductionLines().size());
        assertEquals("Labour Welfare Fund (Karnataka)", item.getDeductionLines().get(0).label());
        assertAmount("50", item.getDeductionLines().get(0).amount());
        assertAmount("29950", item.getNetSalary());
        payrollService.process(december.getRun().getId());

        var lwfReturn = lwfService.returnFor(YearMonth.of(2025, 12));
        assertEquals(1, lwfReturn.employees());
        assertAmount("50", lwfReturn.employeeTotal());
        assertAmount("100", lwfReturn.employerTotal());
        assertTrue(new String(lwfService.returnCsv(YearMonth.of(2025, 12)).content()).contains("KA,"));
    }

    @Test
    void percentageRulesAreCappedAndStatesAreRecognised() {
        LwfRule haryana = new LwfRule();
        haryana.setDeductionMonths("ALL");
        haryana.setEmployeePercent(new BigDecimal("0.2"));
        haryana.setEmployeeMax(new BigDecimal("31"));
        haryana.setEmployerMultiplier(new BigDecimal("2"));
        var small = LwfService.calculate(haryana, new BigDecimal("10000"), 5);
        assertAmount("20", small.employee());
        assertAmount("40", small.employer());
        var capped = LwfService.calculate(haryana, new BigDecimal("90000"), 5);
        assertAmount("31", capped.employee());
        assertAmount("62", capped.employer());

        LwfRule maharashtra = new LwfRule();
        maharashtra.setDeductionMonths("6,12");
        maharashtra.setEmployeeFixed(new BigDecimal("25"));
        maharashtra.setEmployerFixed(new BigDecimal("75"));
        assertNull(LwfService.calculate(maharashtra, new BigDecimal("30000"), 7));
        assertAmount("75", LwfService.calculate(maharashtra, new BigDecimal("30000"), 12).employer());

        assertEquals("TN", LwfService.stateCode("Tamil Nadu"));
        assertEquals("TN", LwfService.stateCode("tamilnadu"));
        assertEquals("OD", LwfService.stateCode("Orissa"));
        assertEquals("DL", LwfService.stateCode("New Delhi"));
        assertNull(LwfService.stateCode("Atlantis"));
    }

    private com.haodaone.salary.dto.PayrollRunDTO run(int year, int month) {
        CreatePayrollRunRequest request = new CreatePayrollRunRequest();
        request.setPeriodMonth(month);
        request.setPeriodYear(year);
        return payrollService.createRun(request);
    }

    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }
}
