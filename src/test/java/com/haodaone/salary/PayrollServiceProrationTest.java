package com.haodaone.salary;

import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.leave.entity.LeaveRequest;
import com.haodaone.leave.entity.LeaveType;
import com.haodaone.leave.repository.LeaveRequestRepository;
import com.haodaone.leave.repository.LeaveTypeRepository;
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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/** createRun end to end: LOP from unpaid leave, mid-month joiners, and future joiners skipped. */
@SpringBootTest
@ActiveProfiles("test")
class PayrollServiceProrationTest {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private SalaryStructureRepository salaryStructureRepository;
    @Autowired private LeaveTypeRepository leaveTypeRepository;
    @Autowired private LeaveRequestRepository leaveRequestRepository;
    @Autowired private PayrollService payrollService;

    @MockitoBean
    private AuthorizationService authorizationService;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void createRunProratesJoinersAndDocksUnpaidLeave() {
        Company company = createCompany();
        Employee joiner = createEmployee(company, "Joiner", LocalDate.of(2026, 11, 16));
        Employee onLeave = createEmployee(company, "Leaver", LocalDate.of(2024, 1, 1));
        Employee future = createEmployee(company, "Future", LocalDate.of(2026, 12, 1));
        createStructure(company, joiner, LocalDate.of(2026, 11, 16));
        createStructure(company, onLeave, LocalDate.of(2024, 1, 1));
        createStructure(company, future, LocalDate.of(2026, 12, 1));

        LeaveType unpaid = createLeaveType(company, "LOP", false);
        LeaveType paid = createLeaveType(company, "CL", true);
        createLeave(company, onLeave, unpaid, LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 11), 3, "APPROVED");
        createLeave(company, onLeave, paid, LocalDate.of(2026, 11, 16), LocalDate.of(2026, 11, 16), 1, "APPROVED");
        createLeave(company, onLeave, unpaid, LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 20), 1, "PENDING");

        TenantContext.setCurrentTenant(company.getId());
        when(authorizationService.hasOrganizationScope("SALARY_MANAGE")).thenReturn(true);

        CreatePayrollRunRequest request = new CreatePayrollRunRequest();
        request.setPeriodMonth(11);
        request.setPeriodYear(2026);
        var run = payrollService.createRun(request);

        assertEquals(2, run.getItems().size());
        assertTrue(run.getItems().stream().noneMatch(i -> i.getEmployeeId().equals(future.getId())));

        PayrollItemDTO joinerItem = item(run.getItems(), joiner.getId());
        // 1-15 Nov not payable: 15 of 30 days.
        assertEquals(0, new BigDecimal("15").compareTo(joinerItem.getPaidDays()));
        assertEquals(0, BigDecimal.ZERO.compareTo(joinerItem.getLopDays()));
        assertEquals(new BigDecimal("15000.00"), joinerItem.getGrossSalary());
        assertEquals(new BigDecimal("13200.00"), joinerItem.getNetSalary());

        PayrollItemDTO leaveItem = item(run.getItems(), onLeave.getId());
        // Only the approved unpaid request counts; paid and pending leave don't.
        assertEquals(0, new BigDecimal("3").compareTo(leaveItem.getLopDays()));
        assertEquals(0, new BigDecimal("27").compareTo(leaveItem.getPaidDays()));
        assertEquals(new BigDecimal("27000.00"), leaveItem.getGrossSalary());
        assertEquals(30, leaveItem.getDaysInPeriod());
    }

    private PayrollItemDTO item(java.util.List<PayrollItemDTO> items, Long employeeId) {
        return items.stream().filter(i -> i.getEmployeeId().equals(employeeId)).findFirst().orElseThrow();
    }

    private Company createCompany() {
        Company company = new Company();
        company.setName("Payroll proration " + UUID.randomUUID());
        return companyRepository.saveAndFlush(company);
    }

    private Employee createEmployee(Company company, String firstName, LocalDate joined) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        Employee employee = new Employee();
        employee.setEmployeeCode("L" + suffix);
        employee.setFirstName(firstName);
        employee.setLastName("Employee");
        employee.setEmail(suffix + "@payroll-lop.example");
        employee.setDateOfJoining(joined);
        employee.setCompany(company);
        return employeeRepository.saveAndFlush(employee);
    }

    private void createStructure(Company company, Employee employee, LocalDate effectiveFrom) {
        SalaryComponents components = new SalaryComponents();
        components.setBasicSalary(new BigDecimal("30000.00"));
        components.setPf(new BigDecimal("1800.00"));
        SalaryStructure structure = new SalaryStructure();
        structure.setCompany(company);
        structure.setEmployee(employee);
        structure.setEffectiveFrom(effectiveFrom);
        structure.setComponents(components);
        structure.recalculate();
        salaryStructureRepository.saveAndFlush(structure);
    }

    private LeaveType createLeaveType(Company company, String code, boolean paid) {
        LeaveType type = new LeaveType();
        type.setCompany(company);
        type.setName(code + " leave");
        type.setCode(code);
        type.setDefaultDaysPerYear(12);
        type.setPaid(paid);
        return leaveTypeRepository.saveAndFlush(type);
    }

    private void createLeave(Company company, Employee employee, LeaveType type, LocalDate start, LocalDate end,
                             double days, String status) {
        LeaveRequest leave = new LeaveRequest();
        leave.setCompany(company);
        leave.setEmployee(employee);
        leave.setLeaveType(type);
        leave.setStartDate(start);
        leave.setEndDate(end);
        leave.setDays(days);
        leave.setStatus(status);
        leaveRequestRepository.saveAndFlush(leave);
    }
}
