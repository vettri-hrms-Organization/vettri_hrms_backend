package com.haodaone.employee.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.employee.dto.CreateEmployeeRequest;
import com.haodaone.employee.dto.EmployeeImportResultDTO;
import com.haodaone.employee.dto.EmployeeImportRowDTO;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.entity.EmploymentStatus;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.org.entity.Department;
import com.haodaone.org.entity.Designation;
import com.haodaone.org.entity.Team;
import com.haodaone.org.repository.DepartmentRepository;
import com.haodaone.org.repository.DesignationRepository;
import com.haodaone.org.repository.TeamRepository;
import com.haodaone.tenant.TenantContext;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
public class EmployeeImportService {
    private static final List<String> HEADERS = List.of("Employee Code *", "First Name *", "Last Name", "Email *", "Phone", "Date of Birth", "Gender", "Date of Joining *", "Department", "Team", "Designation", "Manager Email", "Employment Type", "Work Location", "Status");
    private static final Set<String> TYPES = Set.of("FULL_TIME", "PART_TIME", "CONTRACT", "INTERN");
    private final EmployeeRepository employees;
    private final DepartmentRepository departments;
    private final TeamRepository teams;
    private final DesignationRepository designations;
    private final CompanyRepository companies;
    private final EmployeeService employeeService;
    private final AuditLogService audit;

    public EmployeeImportService(EmployeeRepository employees, DepartmentRepository departments, TeamRepository teams,
                                 DesignationRepository designations, CompanyRepository companies,
                                 EmployeeService employeeService, AuditLogService audit) {
        this.employees = employees; this.departments = departments; this.teams = teams; this.designations = designations;
        this.companies = companies; this.employeeService = employeeService; this.audit = audit;
    }

    public EmployeeImportResultDTO preview(MultipartFile file) {
        List<ParsedRow> parsed = parse(file);
        Long companyId = tenant();
        Set<String> codes = new HashSet<>(), emails = new HashSet<>();
        List<EmployeeImportRowDTO> rows = new ArrayList<>();
        for (ParsedRow row : parsed) {
            List<String> errors = validate(row, companyId, codes, emails);
            rows.add(row.preview(errors));
            if (row.code != null) codes.add(row.code.toLowerCase(Locale.ROOT));
            if (row.email != null) emails.add(row.email.toLowerCase(Locale.ROOT));
        }
        int valid = (int) rows.stream().filter(EmployeeImportRowDTO::valid).count();
        return new EmployeeImportResultDTO(rows.size(), valid, rows.size() - valid, rows,
                valid + " valid employees, " + (rows.size() - valid) + " rows need attention");
    }

    @Transactional
    public EmployeeImportResultDTO importEmployees(MultipartFile file, boolean validOnly) {
        EmployeeImportResultDTO result = preview(file);
        if (result.invalidRows() > 0 && !validOnly) throw new BadRequestException("Fix all invalid rows before importing");
        if (result.validRows() == 0) throw new BadRequestException("There are no valid employees to import");
        Long companyId = tenant();
        Company company = companies.findById(companyId).orElseThrow(() -> new BadRequestException("Company not found: " + companyId));
        int imported = 0;
        List<ParsedRow> parsed = parse(file);
        for (int i = 0; i < parsed.size(); i++) {
            ParsedRow row = parsed.get(i);
            if (!result.rows().get(i).valid()) { if (validOnly) continue; throw new BadRequestException("File changed since preview"); }
            CreateEmployeeRequest request = row.request(companyId);
            departments.findAllByCompany_IdAndDeletedFalseOrderByNameAsc(companyId).stream().filter(d -> d.getName().equalsIgnoreCase(row.department)).findFirst().ifPresent(d -> request.setDepartmentId(d.getId()));
            teams.findAllByCompany_IdAndDeletedFalseOrderByNameAsc(companyId).stream().filter(t -> t.getName().equalsIgnoreCase(row.team)).findFirst().ifPresent(t -> request.setTeamId(t.getId()));
            designations.findAllByDeletedFalseOrderByTitleAsc().stream().filter(d -> d.getTitle().equalsIgnoreCase(row.designation)).findFirst().ifPresent(d -> request.setDesignationId(d.getId()));
            if (!blank(row.managerEmail)) employees.findByEmailIgnoreCaseAndCompany_IdAndDeletedFalse(row.managerEmail, companyId).ifPresent(m -> request.setReportingManagerId(m.getId()));
            request.setStatus(blank(row.status) ? EmploymentStatus.ACTIVE : row.status);
            employeeService.createWithCode(request, row.code, company);
            imported++;
        }
        audit.log("Employee", null, "BULK_IMPORT", "Imported " + imported + " of " + result.totalRows() + " rows");
        return new EmployeeImportResultDTO(result.totalRows(), imported, result.totalRows() - imported, result.rows(), imported + " employees imported successfully.");
    }

    public byte[] template() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Employees"); Row header = sheet.createRow(0);
            CellStyle style = workbook.createCellStyle(); Font font = workbook.createFont(); font.setBold(true); style.setFont(font);
            for (int i = 0; i < HEADERS.size(); i++) { Cell cell = header.createCell(i); cell.setCellValue(HEADERS.get(i)); cell.setCellStyle(style); sheet.setColumnWidth(i, 18 * 256); }
            sheet.createRow(1).createCell(0).setCellValue("EMP0001"); sheet.getRow(1).createCell(1).setCellValue("Ada"); sheet.getRow(1).createCell(2).setCellValue("Lovelace"); sheet.getRow(1).createCell(3).setCellValue("ada@example.com"); sheet.getRow(1).createCell(7).setCellValue("2026-01-15");
            Sheet instructions = workbook.createSheet("Instructions");
            String[] lines = {"Required fields: Employee Code, First Name, Email, Date of Joining", "Dates must use YYYY-MM-DD format", "Employment Type: FULL_TIME, PART_TIME, CONTRACT, INTERN", "Status: Active, On Leave, Notice Period, Resigned, Terminated", "Department, Team, and Designation must already exist for the tenant", "Do not add company_id. The server determines the company."};
            for (int i = 0; i < lines.length; i++) instructions.createRow(i).createCell(0).setCellValue(lines[i]); instructions.setColumnWidth(0, 110 * 256);
            workbook.write(out); return out.toByteArray();
        } catch (IOException e) { throw new BadRequestException("Could not create Excel template"); }
    }

    private List<ParsedRow> parse(MultipartFile file) {
        String filename = file == null ? "" : Objects.toString(file.getOriginalFilename(), "");
        if (file == null || file.isEmpty() || !filename.toLowerCase(Locale.ROOT).matches(".*\\.(xlsx|xls)$")) throw new BadRequestException("Upload an .xlsx or .xls file");
        try (Workbook workbook = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0); if (sheet.getLastRowNum() < 1) throw new BadRequestException("The workbook has no employee rows");
            DataFormatter formatter = new DataFormatter(); Row header = sheet.getRow(0);
            for (int i = 0; i < HEADERS.size(); i++) {
                String actual = formatter.formatCellValue(header == null ? null : header.getCell(i)).trim();
                if (!HEADERS.get(i).equalsIgnoreCase(actual)) throw new BadRequestException("Invalid template: column " + (i + 1) + " must be '" + HEADERS.get(i) + "'");
            }
            List<ParsedRow> rows = new ArrayList<>();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) { Row r = sheet.getRow(i); if (r == null) continue; rows.add(ParsedRow.from(i + 1, r, formatter, workbook.getCreationHelper().createFormulaEvaluator())); }
            return rows;
        } catch (IOException | RuntimeException e) { if (e instanceof BadRequestException b) throw b; throw new BadRequestException("Could not read the Excel file: " + e.getMessage()); }
    }

    private List<String> validate(ParsedRow row, Long companyId, Set<String> codes, Set<String> emails) {
        List<String> errors = new ArrayList<>();
        if (blank(row.code)) errors.add("Employee code is required"); else if (!codes.add(row.code.toLowerCase(Locale.ROOT)) || employees.existsByEmployeeCode(row.code)) errors.add("Duplicate employee code");
        if (blank(row.firstName)) errors.add("First name is required"); if (blank(row.lastName)) errors.add("Last name is required"); if (blank(row.email)) errors.add("Email is required"); else if (!row.email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) errors.add("Email is invalid"); else if (!emails.add(row.email.toLowerCase(Locale.ROOT)) || employees.existsByEmail(row.email)) errors.add("Duplicate email");
        if (row.joining == null) errors.add("Date of joining is required or invalid");
        if (row.birthDateText != null && row.birthDate == null) errors.add("Date of birth is invalid");
        if (!blank(row.employmentType) && !TYPES.contains(row.employmentType.toUpperCase(Locale.ROOT))) errors.add("Invalid employment type");
        if (!blank(row.status) && !EmploymentStatus.VALID_STATUSES.contains(row.status)) errors.add("Invalid status");
        if (!blank(row.department) && departments.findAllByCompany_IdAndDeletedFalseOrderByNameAsc(companyId).stream().noneMatch(d -> d.getName().equalsIgnoreCase(row.department))) errors.add("Department \"" + row.department + "\" not found");
        if (!blank(row.team) && teams.findAllByCompany_IdAndDeletedFalseOrderByNameAsc(companyId).stream().noneMatch(t -> t.getName().equalsIgnoreCase(row.team))) errors.add("Team \"" + row.team + "\" not found");
        if (!blank(row.designation) && designations.findAllByDeletedFalseOrderByTitleAsc().stream().noneMatch(d -> d.getTitle().equalsIgnoreCase(row.designation))) errors.add("Designation \"" + row.designation + "\" not found");
        if (!blank(row.managerEmail) && employees.findByEmailIgnoreCaseAndCompany_IdAndDeletedFalse(row.managerEmail, companyId).isEmpty()) errors.add("Manager email \"" + row.managerEmail + "\" not found");
        return errors;
    }
    private Long tenant() { Long id = TenantContext.getCurrentTenant(); if (id == null) throw new BadRequestException("Company context is required"); return id; }
    private static boolean blank(String value) { return value == null || value.isBlank(); }

    private record ParsedRow(int number, String code, String firstName, String lastName, String email, String phone, String birthDateText, LocalDate birthDate, String gender, LocalDate joining, String department, String team, String designation, String managerEmail, String employmentType, String workLocation, String status) {
        static ParsedRow from(int number, Row row, DataFormatter f, FormulaEvaluator evaluator) { String[] v = new String[15]; for (int i = 0; i < v.length; i++) v[i] = f.formatCellValue(row.getCell(i), evaluator).trim(); return new ParsedRow(number,v[0],v[1],v[2],v[3],v[4],v[5],date(row.getCell(5), v[5]),v[6],date(row.getCell(7), v[7]),v[8],v[9],v[10],v[11],v[12],v[13],v[14]); }
        private static LocalDate date(Cell cell, String value) { if (cell != null && DateUtil.isCellDateFormatted(cell)) return cell.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDate(); return date(value); }
        private static LocalDate date(String value) { if (value == null || value.isBlank()) return null; try { return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE); } catch (DateTimeParseException e) { return null; } }
        EmployeeImportRowDTO preview(List<String> errors) { return new EmployeeImportRowDTO(number,code,firstName,lastName,email,department,designation,status,errors); }
        CreateEmployeeRequest request(Long companyId) { CreateEmployeeRequest r = new CreateEmployeeRequest(); r.setFirstName(firstName); r.setLastName(lastName); r.setEmail(email); r.setPhone(phone); r.setDateOfBirth(birthDate); r.setGender(gender); r.setDateOfJoining(joining); r.setEmploymentType(blank(employmentType) ? "FULL_TIME" : employmentType.toUpperCase(Locale.ROOT)); r.setAddress(workLocation); return r; }
    }
}