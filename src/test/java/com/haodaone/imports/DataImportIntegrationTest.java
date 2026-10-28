package com.haodaone.imports;

import com.haodaone.common.exception.BadRequestException;
import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.imports.dto.ImportDTOs.ImportRowDTO;
import com.haodaone.imports.dto.ImportDTOs.PreviewDTO;
import com.haodaone.imports.dto.ImportDTOs.ResultDTO;
import com.haodaone.imports.entity.DataImportBatch;
import com.haodaone.imports.service.DataImportService;
import com.haodaone.imports.service.ImportOptions;
import com.haodaone.imports.service.OpeningYtdService;
import com.haodaone.leave.entity.Holiday;
import com.haodaone.leave.entity.LeaveType;
import com.haodaone.leave.repository.HolidayRepository;
import com.haodaone.leave.repository.LeaveBalanceAdjustmentRepository;
import com.haodaone.leave.repository.LeaveTypeRepository;
import com.haodaone.leave.service.LeaveBalanceService;
import com.haodaone.salary.entity.SalaryStructure;
import com.haodaone.salary.repository.SalaryStructureRepository;
import com.haodaone.salary.service.OpeningYtdProvider;
import com.haodaone.salary.service.TaxDeclarationService;
import com.haodaone.security.AuthorizationService;
import com.haodaone.tenant.TenantContext;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** End-to-end imports on H2: validation errors per row, valid-only vs all-or-nothing, idempotent leave balances. */
@SpringBootTest
@ActiveProfiles("test")
class DataImportIntegrationTest {

    @Autowired private DataImportService importService;
    @Autowired private OpeningYtdService openingYtdService;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private SalaryStructureRepository salaryStructureRepository;
    @Autowired private LeaveTypeRepository leaveTypeRepository;
    @Autowired private LeaveBalanceAdjustmentRepository adjustmentRepository;
    @Autowired private LeaveBalanceService leaveBalanceService;
    @Autowired private HolidayRepository holidayRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    @MockitoBean
    private AuthorizationService authorizationService;

    private Company company;
    private Employee asha;
    private Employee ravi;

    @BeforeEach
    void setUp() {
        company = new Company();
        company.setName("Imports " + UUID.randomUUID());
        company = companyRepository.saveAndFlush(company);
        asha = employee("Asha");
        ravi = employee("Ravi");
        TenantContext.setCurrentTenant(company.getId());
        when(authorizationService.hasOrganizationScope(anyString())).thenReturn(true);
        when(authorizationService.isAllowed(anyString(), anyString(), any())).thenReturn(true);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    // ------------------------------------------------------------------ salary structures

    @Test
    void salaryImportFlagsBadRowsAndImportsValidOnes() throws IOException {
        MockMultipartFile file = xlsx(List.of("Employee Code *", "Effective From *", "Basic *", "HRA", "Special Allowance", "Auto Statutory", "PF"),
                List.of(asha.getEmployeeCode(), "2026-04-01", "30,000", "12000", "8000", "Y", "1800"),
                List.of("NOPE-1", "2026-04-01", "20000", "", "", "", ""),
                List.of(asha.getEmployeeCode(), "2026-05-01", "31000", "", "", "", ""),
                List.of(ravi.getEmployeeCode(), "2026-04-01", "abc", "", "", "", ""),
                List.of(ravi.getEmployeeCode(), "not-a-date", "0", "-5", "", "maybe", ""));

        PreviewDTO preview = importService.preview("salary-structures", file, ImportOptions.DEFAULT);
        assertEquals(5, preview.totalRows());
        assertEquals(1, preview.validRows());
        assertTrue(row(preview.rows(), 2).valid());
        assertTrue(row(preview.rows(), 2).warnings().stream().anyMatch(w -> w.contains("ignored")));
        assertTrue(hasError(row(preview.rows(), 3), "was not found"));
        assertTrue(hasError(row(preview.rows(), 4), "Duplicate"));
        assertTrue(hasError(row(preview.rows(), 5), "not a valid amount"));
        assertTrue(hasError(row(preview.rows(), 6), "not a valid date"));
        assertTrue(hasError(row(preview.rows(), 6), "cannot be negative"));
        assertTrue(hasError(row(preview.rows(), 6), "Y or N"));

        assertThrows(BadRequestException.class, () -> importService.commit("salary-structures", file, true, ImportOptions.DEFAULT));
        assertTrue(salaryStructureRepository.findByEmployeeIdAndActiveTrueAndDeletedFalse(asha.getId()).isEmpty());

        ResultDTO result = importService.commit("salary-structures", file, false, ImportOptions.DEFAULT);
        assertEquals(1, result.importedRows());
        assertEquals(4, result.failedRows());
        assertEquals(DataImportBatch.STATUS_PARTIAL, result.status());
        SalaryStructure structure = salaryStructureRepository.findByEmployeeIdAndActiveTrueAndDeletedFalse(asha.getId()).orElseThrow();
        assertEquals(0, new BigDecimal("30000").compareTo(structure.getComponents().getBasicSalary()));
        assertTrue(structure.isAutoStatutory());
        assertEquals(0, BigDecimal.ZERO.compareTo(structure.getComponents().getPf()));
        assertTrue(importService.batches("salary-structures", 10).stream().anyMatch(b -> b.id().equals(result.batchId())));
    }

    // ------------------------------------------------------------------ leave balances

    @Test
    void openingLeaveBalanceReimportDoesNotDoubleCount() throws IOException {
        LeaveType casual = new LeaveType();
        casual.setCompany(company);
        casual.setName("Casual Leave");
        casual.setCode("CL" + UUID.randomUUID().toString().substring(0, 4));
        casual.setDefaultDaysPerYear(12);
        casual.setActive(true);
        casual = leaveTypeRepository.saveAndFlush(casual);
        int year = LeaveBalanceService.today().getYear();
        String yearText = String.valueOf(year);

        MockMultipartFile file = xlsx(List.of("Employee Code *", "Leave Type Code *", "Year *", "Opening Balance *"),
                List.of(asha.getEmployeeCode(), casual.getCode(), yearText, "5"),
                List.of(asha.getEmployeeCode(), "XX", yearText, "2"),
                List.of(ravi.getEmployeeCode(), casual.getCode(), yearText, "lots"));
        PreviewDTO preview = importService.preview("leave-balances", file, ImportOptions.DEFAULT);
        assertEquals(1, preview.validRows());
        assertTrue(hasError(row(preview.rows(), 3), "not found"));
        assertTrue(hasError(row(preview.rows(), 4), "not a valid number"));

        ResultDTO first = importService.commit("leave-balances", file, false, ImportOptions.DEFAULT);
        assertEquals(1, first.importedRows());
        assertEquals(5.0, remaining(asha, casual, year), 0.001);

        // Same file again: rejected per employee/type/year, balance unchanged.
        PreviewDTO again = importService.preview("leave-balances", file, ImportOptions.DEFAULT);
        assertEquals(0, again.validRows());
        assertTrue(hasError(row(again.rows(), 2), "already imported"));
        assertNotNull(again.previousImportOfSameFile());
        assertThrows(BadRequestException.class, () -> importService.commit("leave-balances", file, false, ImportOptions.DEFAULT));
        assertEquals(5.0, remaining(asha, casual, year), 0.001);

        // Replace with a new figure: the earlier adjustment is reversed, not stacked.
        MockMultipartFile corrected = xlsx(List.of("Employee Code *", "Leave Type Code *", "Year *", "Opening Balance *"),
                List.of(asha.getEmployeeCode(), casual.getCode(), yearText, "7.5"));
        ImportOptions replace = ImportOptions.of(true, null);
        importService.commit("leave-balances", corrected, true, replace);
        assertEquals(7.5, remaining(asha, casual, year), 0.001);
        importService.commit("leave-balances", corrected, true, replace);
        assertEquals(7.5, remaining(asha, casual, year), 0.001);
        Long typeId = casual.getId();
        long liveAdjustments = adjustmentRepository.findAllByCompany_IdAndEmployee_IdAndDeletedFalseOrderByEffectiveDateAscIdAsc(company.getId(), asha.getId())
                .stream().filter(a -> a.getLeaveType().getId().equals(typeId)).count();
        assertEquals(1, liveAdjustments);

        // Carry-forward mode replaces the adjustment with a carried-forward balance.
        importService.commit("leave-balances", corrected, true, ImportOptions.of(true, "CARRY_FORWARD"));
        Double carried = transactionTemplate.execute(status -> leaveBalanceService
                .compute(company.getId(), employeeRepository.findById(asha.getId()).orElseThrow(),
                        leaveTypeRepository.findById(typeId).orElseThrow(), year, LeaveBalanceService.asOfFor(year)).carried());
        assertEquals(7.5, carried, 0.001);
    }

    // ------------------------------------------------------------------ holidays

    @Test
    void holidayImportSkipsExistingDatesAndRejectsBadRows() throws IOException {
        Holiday existing = new Holiday();
        existing.setCompany(company);
        existing.setName("Pongal");
        existing.setDate(LocalDate.of(2027, 1, 14));
        holidayRepository.saveAndFlush(existing);

        MockMultipartFile file = xlsx(List.of("Date *", "Holiday Name *"),
                List.of("2027-01-14", "Pongal"),
                List.of("2027-01-26", "Republic Day"),
                List.of("2027-01-26", "Republic Day again"),
                List.of("2027-13-40", "Bad date"),
                List.of("2027-08-15", ""));
        ResultDTO result = importService.commit("holidays", file, false, ImportOptions.DEFAULT);
        assertEquals(1, result.importedRows());
        assertEquals(1, result.skippedRows());
        assertEquals(3, result.failedRows());
        assertTrue(row(result.rows(), 2).skip());
        assertTrue(hasError(row(result.rows(), 4), "Duplicate"));
        assertTrue(hasError(row(result.rows(), 5), "not a valid date"));
        assertTrue(hasError(row(result.rows(), 6), "required"));
        assertTrue(holidayRepository.existsByCompany_IdAndDateAndDeletedFalse(company.getId(), LocalDate.of(2027, 1, 26)));
    }

    // ------------------------------------------------------------------ opening payroll YTD

    @Test
    void openingYtdImportValidatesAndFeedsTheProvider() throws IOException {
        int fy = TaxDeclarationService.currentFinancialYear();
        String label = fy + "-" + String.format("%02d", (fy + 1) % 100);
        List<String> headers = List.of("Employee Code *", "Financial Year *", "Gross Salary Paid *", "Taxable Salary Paid",
                "TDS Deducted *", "Months Covered *", "Previous Employer Income", "Previous Employer TDS");
        MockMultipartFile file = xlsx(headers,
                List.of(asha.getEmployeeCode(), label, "600000", "", "25000", "6", "", ""),
                List.of("NOPE-2", label, "1000", "", "0", "1", "", ""),
                List.of(ravi.getEmployeeCode(), label, "1000", "2000", "0", "13", "", ""),
                List.of(ravi.getEmployeeCode(), fy + "-" + (fy + 3), "1000", "", "0", "1", "", "500"));
        PreviewDTO preview = importService.preview("opening-ytd", file, ImportOptions.DEFAULT);
        assertEquals(1, preview.validRows());
        assertTrue(hasError(row(preview.rows(), 3), "was not found"));
        assertTrue(hasError(row(preview.rows(), 4), "cannot exceed"));
        assertTrue(hasError(row(preview.rows(), 4), "between 0 and 12"));
        assertTrue(hasError(row(preview.rows(), 5), "not valid"));
        assertTrue(hasError(row(preview.rows(), 5), "Previous Employer"));

        importService.commit("opening-ytd", file, false, ImportOptions.DEFAULT);
        OpeningYtdProvider.OpeningYtd opening = openingYtdService.openingFor(company.getId(), asha.getId(), fy).orElseThrow();
        assertEquals(0, new BigDecimal("600000").compareTo(opening.totalTaxable()));
        assertEquals(0, new BigDecimal("25000").compareTo(opening.totalTds()));
        assertEquals(6, opening.monthsCovered());

        MockMultipartFile again = xlsx(headers, List.of(asha.getEmployeeCode(), label, "650000", "640000", "27000", "7", "", ""));
        assertTrue(hasError(row(importService.preview("opening-ytd", again, ImportOptions.DEFAULT).rows(), 2), "already exists"));
        importService.commit("opening-ytd", again, true, ImportOptions.of(true, null));
        assertEquals(0, new BigDecimal("27000").compareTo(
                openingYtdService.openingFor(company.getId(), asha.getId(), fy).orElseThrow().totalTds()));
        assertEquals(1, openingYtdService.list(fy).size());
    }

    // ------------------------------------------------------------------ bank / statutory details

    @Test
    void bankDetailsImportReusesEmployeeValidation() throws IOException {
        String pan = "ABCDE" + (1000 + (int) (Math.random() * 8999)) + "F";
        MockMultipartFile file = xlsx(List.of("Employee Code *", "Bank Account Number", "IFSC", "Bank Name", "PAN", "UAN", "ESI Number"),
                List.of(asha.getEmployeeCode(), "50100012345678", "hdfc0001234", "HDFC Bank", pan, "100200300400", ""),
                List.of(ravi.getEmployeeCode(), "", "HDFC1234", "", pan, "123", ""),
                List.of(ravi.getEmployeeCode(), "", "", "", "", "", ""));
        PreviewDTO preview = importService.preview("bank-details", file, ImportOptions.DEFAULT);
        assertEquals(1, preview.validRows());
        assertEquals("XXXX5678", row(preview.rows(), 2).values().get("Bank Account Number"));
        assertTrue(hasError(row(preview.rows(), 3), "IFSC"));
        assertTrue(hasError(row(preview.rows(), 3), "UAN"));
        assertTrue(hasError(row(preview.rows(), 3), "also on row 2"));
        assertTrue(hasError(row(preview.rows(), 4), "Nothing to update"));

        importService.commit("bank-details", file, false, ImportOptions.DEFAULT);
        Employee updated = employeeRepository.findById(asha.getId()).orElseThrow();
        assertEquals("HDFC0001234", updated.getBankIfsc());
        assertEquals(pan, updated.getPanNumber());
        assertEquals("100200300400", updated.getUanNumber());
    }

    @Test
    void wrongTemplateIsRejected() throws IOException {
        MockMultipartFile file = xlsx(List.of("Name", "Something"), List.of("a", "b"));
        BadRequestException error = assertThrows(BadRequestException.class,
                () -> importService.preview("holidays", file, ImportOptions.DEFAULT));
        assertTrue(error.getMessage().contains("missing column"));
        assertTrue(importService.template("opening-ytd").length > 0);
    }

    // ------------------------------------------------------------------ helpers

    private double remaining(Employee employee, LeaveType type, int year) {
        Double value = transactionTemplate.execute(status -> leaveBalanceService.compute(company.getId(),
                employeeRepository.findById(employee.getId()).orElseThrow(), leaveTypeRepository.findById(type.getId()).orElseThrow(),
                year, LeaveBalanceService.asOfFor(year)).remaining());
        return value == null ? Double.NaN : value;
    }

    private static ImportRowDTO row(List<ImportRowDTO> rows, int excelRow) {
        return rows.stream().filter(r -> r.row() == excelRow).findFirst().orElseThrow();
    }

    private static boolean hasError(ImportRowDTO row, String fragment) {
        return row.errors().stream().anyMatch(e -> e.contains(fragment));
    }

    @SafeVarargs
    private static MockMultipartFile xlsx(List<String> headers, List<String>... rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Data");
            Row header = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) header.createCell(i).setCellValue(headers.get(i));
            for (int r = 0; r < rows.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int i = 0; i < rows[r].size(); i++) row.createCell(i).setCellValue(rows[r].get(i));
            }
            workbook.write(out);
            return new MockMultipartFile("file", "import.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        }
    }

    private Employee employee(String firstName) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        Employee employee = new Employee();
        employee.setEmployeeCode("I" + suffix);
        employee.setFirstName(firstName);
        employee.setLastName("Importer");
        employee.setEmail(suffix + "@imports.example");
        employee.setDateOfJoining(LocalDate.of(2020, 1, 1));
        employee.setCompany(company);
        return employeeRepository.saveAndFlush(employee);
    }
}
