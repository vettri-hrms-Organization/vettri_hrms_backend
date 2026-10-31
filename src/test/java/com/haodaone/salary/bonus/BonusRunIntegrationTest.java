package com.haodaone.salary.bonus;

import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.notifications.service.NotificationService;
import com.haodaone.salary.dto.CreatePayrollRunRequest;
import com.haodaone.salary.dto.PayrollItemDTO;
import com.haodaone.salary.entity.SalaryComponents;
import com.haodaone.salary.entity.SalaryStructure;
import com.haodaone.salary.repository.SalaryStructureRepository;
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
import java.time.ZoneId;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/** Statutory and festival bonus batches: calculation, editing, approval and payment through payroll. */
@SpringBootTest
@ActiveProfiles("test")
class BonusRunIntegrationTest {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private SalaryStructureRepository salaryStructureRepository;
    @Autowired private PayrollService payrollService;
    @Autowired private BonusService bonusService;

    @MockitoBean private AuthorizationService authorizationService;
    @MockitoBean private NotificationService notificationService;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void statutoryAndFestivalBonusesArePaidThroughPayroll() {
        Company draft = new Company();
        draft.setName("Bonus " + UUID.randomUUID());
        Company company = companyRepository.saveAndFlush(draft);
        Employee low = employee(company, "Low");
        Employee high = employee(company, "High");
        structure(company, low, "15000");
        structure(company, high, "50000");
        TenantContext.setCurrentTenant(company.getId());
        when(authorizationService.hasOrganizationScope("SALARY_MANAGE")).thenReturn(true);

        processRun(YearMonth.of(2025, 4));
        processRun(YearMonth.of(2025, 5));

        // Statutory: 8.33% of (7,000 x 2 months) for the employee on 15,000 basic; the 50,000 one isn't covered.
        var statutory = bonusService.create(new BonusService.CreateRequest("Statutory bonus FY 2025-26", "STATUTORY", null, null, 2025,
                null, null, true, null, "ALL", null, null), "HR");
        assertEquals(1, statutory.employees());
        assertAmount("1166", statutory.total());
        assertAmount("14000", statutory.lines().get(0).basisAmount());
        assertTrue(statutory.skipped().stream().anyMatch(s -> s.startsWith("High") && s.contains("21,000")));

        // Festival: 5,000 each, one edited to 7,500, approved, paid with this month's payroll.
        YearMonth thisMonth = YearMonth.now(ZoneId.of("Asia/Kolkata"));
        var festival = bonusService.create(new BonusService.CreateRequest("Diwali bonus", "FESTIVAL", "FIXED", new BigDecimal("5000"), null,
                null, thisMonth.toString(), true, null, "ALL", null, null), "HR");
        assertAmount("10000", festival.total());
        Long highLine = festival.lines().stream().filter(l -> l.employeeId().equals(high.getId())).findFirst().orElseThrow().id();
        festival = bonusService.updateLine(festival.id(), highLine, new BonusService.LineUpdate(new BigDecimal("7500"), "Top performer"));
        assertAmount("12500", festival.total());
        bonusService.approve(festival.id(), "HR");
        Long festivalId = festival.id();
        assertThrows(com.haodaone.common.exception.BadRequestException.class,
                () -> bonusService.updateLine(festivalId, highLine, new BonusService.LineUpdate(BigDecimal.ONE, null)));

        var run = payrollService.createRun(request(thisMonth));
        PayrollItemDTO highItem = run.getItems().stream().filter(i -> i.getEmployeeId().equals(high.getId())).findFirst().orElseThrow();
        assertEquals(1, highItem.getAdjustments().size(), "the draft statutory batch isn't paid");
        assertEquals("Diwali bonus", highItem.getAdjustments().get(0).label());
        assertAmount("7500", highItem.getAdjustments().get(0).amount());
        assertAmount("57500", highItem.getGrossSalary());
        payrollService.process(run.getRun().getId());
        assertEquals(BonusBatch.PAID, bonusService.get(festivalId).status());
        assertThrows(com.haodaone.common.exception.BadRequestException.class, () -> bonusService.cancel(festivalId, "HR"));
    }

    private void processRun(YearMonth month) {
        var run = payrollService.createRun(request(month));
        payrollService.process(run.getRun().getId());
    }

    private static CreatePayrollRunRequest request(YearMonth month) {
        CreatePayrollRunRequest request = new CreatePayrollRunRequest();
        request.setPeriodMonth(month.getMonthValue());
        request.setPeriodYear(month.getYear());
        return request;
    }

    private static void assertAmount(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    private Employee employee(Company company, String name) {
        Employee e = new Employee();
        e.setEmployeeCode("BN" + UUID.randomUUID().toString().substring(0, 8));
        e.setFirstName(name);
        e.setLastName("Employee");
        e.setEmail(UUID.randomUUID() + "@bonus.example");
        e.setDateOfJoining(LocalDate.of(2024, 1, 1));
        e.setCompany(company);
        return employeeRepository.saveAndFlush(e);
    }

    private void structure(Company company, Employee employee, String basic) {
        SalaryComponents c = new SalaryComponents();
        c.setBasicSalary(new BigDecimal(basic));
        SalaryStructure s = new SalaryStructure();
        s.setCompany(company);
        s.setEmployee(employee);
        s.setEffectiveFrom(LocalDate.of(2024, 1, 1));
        s.setComponents(c);
        s.setAutoStatutory(false);
        s.recalculate();
        salaryStructureRepository.saveAndFlush(s);
    }
}
