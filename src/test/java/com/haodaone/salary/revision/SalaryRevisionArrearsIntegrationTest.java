package com.haodaone.salary.revision;

import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.notifications.service.NotificationService;
import com.haodaone.salary.dto.CreatePayrollRunRequest;
import com.haodaone.salary.dto.PayrollItemDTO;
import com.haodaone.salary.dto.SalaryComponentsDTO;
import com.haodaone.salary.entity.SalaryComponents;
import com.haodaone.salary.entity.SalaryStructure;
import com.haodaone.salary.repository.SalaryStructureRepository;
import com.haodaone.salary.service.PayrollService;
import com.haodaone.security.AuthorizationService;
import com.haodaone.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/** Back-dated raise: arrears for processed months are paid as one taxable line in the next payroll. */
@SpringBootTest
@ActiveProfiles("test")
class SalaryRevisionArrearsIntegrationTest {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private SalaryStructureRepository salaryStructureRepository;
    @Autowired private PayrollService payrollService;
    @Autowired private SalaryRevisionService revisionService;

    @MockitoBean private AuthorizationService authorizationService;
    @MockitoBean private NotificationService notificationService;

    private final YearMonth thisMonth = YearMonth.now(ZoneId.of("Asia/Kolkata"));

    @BeforeEach
    void permissions() {
        when(authorizationService.hasOrganizationScope("SALARY_MANAGE")).thenReturn(true);
        when(authorizationService.isAllowed(eq("SALARY_MANAGE"), any(), anyLong())).thenReturn(true);
        when(authorizationService.isAllowed(eq("SALARY_VIEW"), any(), anyLong())).thenReturn(true);
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void backDatedRaisePaysArrearsForProcessedMonths() {
        Company company = company();
        Employee asha = employee(company);
        structure(company, asha, "40000", "20000", "0");   // 60,000 a month
        TenantContext.setCurrentTenant(company.getId());

        YearMonth m1 = thisMonth.minusMonths(2);
        YearMonth m2 = thisMonth.minusMonths(1);
        processRun(m1);
        processRun(m2);

        // 60,000 -> 72,000 (20%) from the 1st of m1: 12,000 owed for each of the two processed months.
        var request = new SalaryRevisionService.RevisionRequest(asha.getId(), "ANNUAL_APPRAISAL", m1.atDay(1),
                components("48000", "24000", "0"), false, "Appraisal 2026", null);
        var preview = revisionService.preview(request);
        assertAmount("20", preview.incrementPercent());
        assertEquals(2, preview.arrears().size());
        assertAmount("24000", preview.arrearsTotal());

        var revision = revisionService.create(request, "HR");
        assertEquals(SalaryRevision.APPLIED, revision.status());
        assertEquals(SalaryRevision.ARREARS_PENDING, revision.arrearsStatus());
        assertAmount("72000", salaryStructureRepository.findByEmployeeIdAndActiveTrueAndDeletedFalse(asha.getId()).orElseThrow().getGrossSalary());

        // This month's run: new salary + arrears as a taxable line.
        var run = payrollService.createRun(request(thisMonth));
        PayrollItemDTO item = run.getItems().stream().filter(i -> asha.getId().equals(i.getEmployeeId())).findFirst().orElseThrow();
        assertEquals(1, item.getAdjustments().size());
        assertTrue(item.getAdjustments().get(0).label().startsWith("Salary arrears ("));
        assertTrue(item.getAdjustments().get(0).taxable());
        assertAmount("96000", item.getGrossSalary());   // 72,000 + 24,000 arrears
        payrollService.process(run.getRun().getId());
        assertEquals(SalaryRevision.ARREARS_PAID, revisionService.list(asha.getId()).get(0).arrearsStatus());

        // A second revision is allowed once arrears are paid; future-dated ones wait.
        var future = revisionService.create(new SalaryRevisionService.RevisionRequest(asha.getId(), "PROMOTION", thisMonth.plusMonths(1).atDay(1),
                components("50000", "25000", "0"), false, null, null), "HR");
        assertEquals(SalaryRevision.SCHEDULED, future.status());
        assertThrows(com.haodaone.common.exception.BadRequestException.class, () -> revisionService.create(
                new SalaryRevisionService.RevisionRequest(asha.getId(), "OTHER", thisMonth.atDay(1), components("1", "0", "0"), false, null, null), "HR"));
        assertEquals(1, revisionService.applyDue(thisMonth.plusMonths(1).atDay(1)));
        TenantContext.setCurrentTenant(company.getId());
        assertAmount("75000", salaryStructureRepository.findByEmployeeIdAndActiveTrueAndDeletedFalse(asha.getId()).orElseThrow().getGrossSalary());
    }

    @Test
    void midMonthEffectiveDateProratesTheFirstMonth() {
        Company company = company();
        Employee ravi = employee(company);
        structure(company, ravi, "30000", "0", "0");
        TenantContext.setCurrentTenant(company.getId());
        YearMonth m1 = thisMonth.minusMonths(1);
        processRun(m1);

        int days = m1.lengthOfMonth();
        LocalDate effective = m1.atDay(days - 9);   // last 10 days of the month at the new rate
        var preview = revisionService.preview(new SalaryRevisionService.RevisionRequest(ravi.getId(), "OTHER", effective,
                components(String.valueOf(30000 + days * 100), "0", "0"), false, null, null));
        // (new - old) x 10 / days = days*100 x 10 / days = 1,000
        assertAmount("1000", preview.arrearsTotal());
    }

    private void processRun(YearMonth month) {
        var run = payrollService.createRun(request(month));
        payrollService.process(run.getRun().getId());
    }

    private static SalaryComponentsDTO components(String basic, String hra, String special) {
        SalaryComponents c = new SalaryComponents();
        c.setBasicSalary(new BigDecimal(basic));
        c.setHra(new BigDecimal(hra));
        c.setSpecialAllowance(new BigDecimal(special));
        return SalaryComponentsDTO.from(c);
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

    private Company company() {
        Company company = new Company();
        company.setName("Revision " + UUID.randomUUID());
        return companyRepository.saveAndFlush(company);
    }

    private Employee employee(Company company) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Employee employee = new Employee();
        employee.setEmployeeCode("R" + suffix);
        employee.setFirstName("Asha");
        employee.setLastName("Rao");
        employee.setEmail(suffix + "@revision.example");
        employee.setDateOfJoining(LocalDate.of(2024, 1, 1));
        employee.setCompany(company);
        return employeeRepository.saveAndFlush(employee);
    }

    private void structure(Company company, Employee employee, String basic, String hra, String special) {
        SalaryStructure structure = new SalaryStructure();
        structure.setCompany(company);
        structure.setEmployee(employee);
        structure.setEffectiveFrom(LocalDate.of(2024, 1, 1));
        structure.setComponents(components(basic, hra, special).toEntity());
        structure.setAutoStatutory(false);
        structure.recalculate();
        salaryStructureRepository.saveAndFlush(structure);
    }
}
