package com.haodaone.salary.bank;

import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.salary.dto.CreatePayrollRunRequest;
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
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/** Processed run -> bank file: valid payees in, missing bank details reported, bank presets need the debit account. */
@SpringBootTest
@ActiveProfiles("test")
class BankTransferIntegrationTest {

    @Autowired private CompanyRepository companyRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private SalaryStructureRepository salaryStructureRepository;
    @Autowired private PayrollService payrollService;
    @Autowired private BankTransferService bankTransferService;

    @MockitoBean private AuthorizationService authorizationService;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void buildsGenericHdfcAndIciciFilesFromAProcessedRun() {
        Company draft = new Company();
        draft.setName("Bank file " + UUID.randomUUID());
        Company company = companyRepository.saveAndFlush(draft);
        Employee asha = employee(company, "Asha, Rao", "50100012345678", "HDFC0001234");
        Employee ravi = employee(company, "Ravi Kumar", "30001234567", "SBIN0000456");
        Employee noIfsc = employee(company, "No Ifsc", "123456789", null);
        structure(company, asha, "40000");
        structure(company, ravi, "30000");
        structure(company, noIfsc, "20000");
        TenantContext.setCurrentTenant(company.getId());
        when(authorizationService.hasOrganizationScope("SALARY_MANAGE")).thenReturn(true);

        CreatePayrollRunRequest request = new CreatePayrollRunRequest();
        request.setPeriodMonth(9);
        request.setPeriodYear(2026);
        var run = payrollService.createRun(request);
        Long runId = run.getRun().getId();
        assertThrows(com.haodaone.common.exception.BadRequestException.class, () -> bankTransferService.preview(runId),
                "draft runs can't be paid");
        payrollService.process(runId);

        var preview = bankTransferService.preview(runId);
        assertEquals(2, preview.payable());
        assertEquals(0, new BigDecimal("70000").compareTo(preview.totalAmount()));
        assertEquals(1, preview.problems().size());
        assertEquals("No IFSC", preview.problems().get(0).issue());
        assertEquals("XXXX5678", preview.rows().get(0).accountMasked());

        String generic = new String(bankTransferService.file(runId, "GENERIC", LocalDate.of(2026, 9, 30)).content(), StandardCharsets.UTF_8);
        String[] lines = generic.split("\n");
        assertEquals(3, lines.length);
        assertTrue(lines[0].startsWith("S.No,Employee Code,Beneficiary Name,Account Number,IFSC"));
        assertTrue(lines[1].contains(",Asha  Rao,".replace("  ", " ")) || lines[1].contains("Asha Rao"), lines[1]);
        assertTrue(lines[1].contains(",50100012345678,HDFC0001234,") && lines[1].contains(",40000.00,NEFT,SALARY SEP 2026,"), lines[1]);

        assertThrows(com.haodaone.common.exception.BadRequestException.class, () -> bankTransferService.file(runId, "HDFC", null));
        bankTransferService.updateSettings(new BankTransferService.SettingsDTO("50200099998888", "HDFC0000001", "HDFC Bank", "HDFC", null));

        String hdfc = new String(bankTransferService.file(runId, "HDFC", LocalDate.of(2026, 9, 30)).content(), StandardCharsets.UTF_8);
        String[] hdfcLines = hdfc.split("\n");
        assertEquals(2, hdfcLines.length, "no header in ENet files");
        String[] first = hdfcLines[0].split(",", -1);
        assertEquals("I", first[0]);                       // HDFC to HDFC
        assertEquals("50100012345678", first[2]);
        assertEquals("40000.00", first[3]);
        assertEquals("ASHA RAO", first[4]);
        assertEquals("30/09/2026", first[22]);
        assertEquals("N", hdfcLines[1].split(",", -1)[0]); // SBI over NEFT
        assertEquals("SBIN0000456", hdfcLines[1].split(",", -1)[24]);

        String icici = new String(bankTransferService.file(runId, "ICICI", LocalDate.of(2026, 9, 30)).content(), StandardCharsets.UTF_8);
        String[] iciciLines = icici.split("\n");
        assertTrue(iciciLines[0].startsWith("PYMT_PROD_TYPE_CODE,PYMT_MODE,DEBIT_ACC_NO"));
        assertTrue(iciciLines[1].startsWith("PAB_VENDOR,NEFT,50200099998888,Asha Rao,50100012345678,HDFC0001234,40000.00,"), iciciLines[1]);
    }

    private Employee employee(Company company, String name, String account, String ifsc) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Employee e = new Employee();
        e.setEmployeeCode("B" + suffix);
        e.setFirstName(name.split(" ")[0]);
        e.setLastName(name.split(" ")[1]);
        e.setEmail(suffix + "@bank.example");
        e.setDateOfJoining(LocalDate.of(2024, 1, 1));
        e.setBankAccountNumber(account);
        e.setBankIfsc(ifsc);
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
