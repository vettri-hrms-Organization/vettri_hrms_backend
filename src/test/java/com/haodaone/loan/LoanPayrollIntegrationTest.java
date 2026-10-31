package com.haodaone.loan;

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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * A loan paid out through payroll and recovered as post-tax EMIs: gross and statutory are untouched,
 * net pay drops by the EMI, processing marks the EMI recovered, and pay too low to cover an EMI skips it.
 */
@SpringBootTest
@ActiveProfiles("test")
class LoanPayrollIntegrationTest {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private SalaryStructureRepository salaryStructureRepository;
    @Autowired private PayrollService payrollService;
    @Autowired private LoanService loanService;
    @Autowired private EmployeeLoanInstallmentRepository installmentRepository;

    @MockitoBean private AuthorizationService authorizationService;
    @MockitoBean private NotificationService notificationService;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void emiIsDeductedAfterTaxAndSettledWhenTheRunIsProcessed() {
        Company company = company();
        Employee asha = employee(company, "Asha");
        Employee low = employee(company, "Low");
        structure(company, asha, "50000", "20000", "10000", "1800");
        structure(company, low, "3000", "0", "0", "0");
        TenantContext.setCurrentTenant(company.getId());
        when(authorizationService.hasOrganizationScope("SALARY_MANAGE")).thenReturn(true);

        YearMonth first = YearMonth.now(ZoneId.of("Asia/Kolkata")).plusMonths(1);
        // 1,20,000 at 0% over 12 months = 10,000 EMI, paid out with the first run.
        LoanService.LoanDTO loan = loanService.create(new LoanService.CreateBody(asha.getId(), "LOAN", new BigDecimal("120000"), 12,
                BigDecimal.ZERO, first.toString(), true, "Medical"), "HR");
        assertEquals(new BigDecimal("10000.00"), loan.emiAmount());
        assertEquals(12, loan.schedule().size());
        // An advance bigger than the low earner's pay.
        loanService.create(new LoanService.CreateBody(low.getId(), "ADVANCE", new BigDecimal("6000"), 1,
                null, first.toString(), false, null), "HR");

        var run = payrollService.createRun(request(first));
        PayrollItemDTO a = item(run.getItems(), asha.getId());
        assertEquals(1, a.getDeductionLines().size());
        assertEquals("Loan EMI 1/12", a.getDeductionLines().get(0).label());
        // Pay-out arrives as a non-taxable earning; EMI comes off after tax.
        assertAmount("200000", a.getGrossSalary());       // 80,000 salary + 1,20,000 loan pay-out
        assertAmount("80000", a.getTaxableGross());
        assertAmount("1800", a.getComponents().getPf());
        assertAmount("11800", a.getTotalDeductions());    // PF 1,800 + EMI 10,000
        assertAmount("188200", a.getNetSalary());

        PayrollItemDTO l = item(run.getItems(), low.getId());
        assertTrue(l.getDeductionLines().isEmpty(), "an EMI larger than net pay is left for a later run");
        assertAmount("3000", l.getNetSalary());

        payrollService.process(run.getRun().getId());
        var schedule = installmentRepository.findAllByLoanIdAndDeletedFalseOrderBySequenceNoAsc(loan.id());
        assertEquals(EmployeeLoanInstallment.DEDUCTED, schedule.get(0).getStatus());
        assertEquals(EmployeeLoanInstallment.PENDING, schedule.get(1).getStatus());
        LoanService.LoanDTO after = loanService.get(loan.id());
        assertEquals(EmployeeLoan.ACTIVE, after.status());
        assertAmount("10000", after.recovered());
        assertAmount("110000", after.outstanding());

        // If Asha left now, full & final would recover the whole 1,10,000 still owed.
        var owed = loanService.outstandingForSettlement(company.getId(), asha.getId());
        assertEquals(1, owed.size());
        assertAmount("110000", owed.get(0).amount());

        // HR defers next month's EMI; the following run deducts nothing for it.
        loanService.defer(loan.id(), schedule.get(1).getId(), "Wedding month", "HR");
        var next = payrollService.createRun(request(first.plusMonths(1)));
        assertTrue(item(next.getItems(), asha.getId()).getDeductionLines().isEmpty());
        assertEquals(13, loanService.get(loan.id()).schedule().size());

        // Settlement approval recovers the rest and closes the loan.
        loanService.recoverInSettlement(loanService.outstandingForSettlement(company.getId(), asha.getId()), -99L);
        LoanService.LoanDTO closed = loanService.get(loan.id());
        assertEquals(EmployeeLoan.CLOSED, closed.status());
        assertAmount("0", closed.outstanding());
    }

    @Test
    void reducingBalanceScheduleAddsUpToThePrincipal() {
        var rows = LoanService.schedule(1L, 1L, new BigDecimal("100000"), 12, new BigDecimal("12"), YearMonth.of(2027, 1), 1);
        BigDecimal principal = rows.stream().map(EmployeeLoanInstallment::getPrincipal).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertAmount("100000", principal);
        assertAmount("8884.88", rows.get(0).getAmount());   // standard EMI for 1 lakh, 12%, 12 months
        assertAmount("1000", rows.get(0).getInterest());     // 1% of the opening balance
        assertEquals("2027-12", rows.get(11).getDueMonth());
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

    private PayrollItemDTO item(List<PayrollItemDTO> items, Long employeeId) {
        return items.stream().filter(i -> employeeId.equals(i.getEmployeeId())).findFirst().orElseThrow();
    }

    private Company company() {
        Company company = new Company();
        company.setName("Loan payroll " + UUID.randomUUID());
        return companyRepository.saveAndFlush(company);
    }

    private Employee employee(Company company, String firstName) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Employee employee = new Employee();
        employee.setEmployeeCode("L" + suffix);
        employee.setFirstName(firstName);
        employee.setLastName("Employee");
        employee.setEmail(suffix + "@loan.example");
        employee.setDateOfJoining(LocalDate.of(2024, 1, 1));
        employee.setCompany(company);
        return employeeRepository.saveAndFlush(employee);
    }

    private void structure(Company company, Employee employee, String basic, String hra, String special, String pf) {
        SalaryComponents components = new SalaryComponents();
        components.setBasicSalary(new BigDecimal(basic));
        components.setHra(new BigDecimal(hra));
        components.setSpecialAllowance(new BigDecimal(special));
        components.setPf(new BigDecimal(pf));
        SalaryStructure structure = new SalaryStructure();
        structure.setCompany(company);
        structure.setEmployee(employee);
        structure.setEffectiveFrom(LocalDate.of(2024, 1, 1));
        structure.setComponents(components);
        structure.setAutoStatutory(false);
        structure.recalculate();
        salaryStructureRepository.saveAndFlush(structure);
    }
}
