package com.haodaone.privacy.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.haodaone.attendance.entity.AttendanceRecord;
import com.haodaone.attendance.repository.AttendanceRecordRepository;
import com.haodaone.document.entity.EmployeeDocument;
import com.haodaone.document.repository.EmployeeDocumentRepository;
import com.haodaone.employee.entity.Employee;
import com.haodaone.leave.entity.LeaveRequest;
import com.haodaone.leave.repository.LeaveRequestRepository;
import com.haodaone.monitoring.entity.MonitoredDevice;
import com.haodaone.monitoring.repository.MonitoredDeviceRepository;
import com.haodaone.privacy.entity.DataPrincipalRequest;
import com.haodaone.privacy.entity.LegalAcceptance;
import com.haodaone.privacy.entity.MonitoringConsent;
import com.haodaone.privacy.repository.DataPrincipalRequestRepository;
import com.haodaone.privacy.repository.LegalAcceptanceRepository;
import com.haodaone.privacy.repository.MonitoringConsentRepository;
import com.haodaone.salary.entity.PayrollItem;
import com.haodaone.salary.repository.PayrollItemRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds the "copy of my personal data" ZIP for one employee (DPDP Act 2023,
 * right to access): data.json with every section plus one CSV per section.
 *
 * Only the requesting employee's own records are included. Every section is
 * loaded with a query scoped to (company, employee) and then filtered again
 * defensively by employee id, so a repository returning extra rows can never
 * leak another person's data. Fields that identify other people (approver,
 * reviewer) are reduced to "decided by HR/manager" style labels.
 */
@Service
public class PersonalDataExportService {

    private static final Logger log = LoggerFactory.getLogger(PersonalDataExportService.class);

    private final EmployeeDocumentRepository documentRepository;
    private final AttendanceRecordRepository attendanceRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final PayrollItemRepository payrollItemRepository;
    private final MonitoredDeviceRepository deviceRepository;
    private final MonitoringConsentRepository consentRepository;
    private final LegalAcceptanceRepository acceptanceRepository;
    private final DataPrincipalRequestRepository requestRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public PersonalDataExportService(EmployeeDocumentRepository documentRepository,
                                     AttendanceRecordRepository attendanceRepository,
                                     LeaveRequestRepository leaveRequestRepository,
                                     PayrollItemRepository payrollItemRepository,
                                     MonitoredDeviceRepository deviceRepository,
                                     MonitoringConsentRepository consentRepository,
                                     LegalAcceptanceRepository acceptanceRepository,
                                     DataPrincipalRequestRepository requestRepository,
                                     JdbcTemplate jdbcTemplate) {
        this.documentRepository = documentRepository;
        this.attendanceRepository = attendanceRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.payrollItemRepository = payrollItemRepository;
        this.deviceRepository = deviceRepository;
        this.consentRepository = consentRepository;
        this.acceptanceRepository = acceptanceRepository;
        this.requestRepository = requestRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public byte[] buildZip(Employee employee) {
        Map<String, List<Map<String, Object>>> sections = collect(employee);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("generatedAt", LocalDateTime.now().toString());
            json.put("employeeId", employee.getId());
            json.put("note", "Copy of your personal data held in Vettri HRMS by your employer. "
                    + "Generated under the Digital Personal Data Protection Act, 2023.");
            json.putAll(sections);
            write(zip, "data.json", objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(json));
            for (Map.Entry<String, List<Map<String, Object>>> section : sections.entrySet()) {
                write(zip, section.getKey() + ".csv", CsvWriter.toCsv(section.getValue()).getBytes(StandardCharsets.UTF_8));
            }
            write(zip, "README.txt", readme().getBytes(StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not build the personal data export", ex);
        }
        return bytes.toByteArray();
    }

    /** Section name -> rows. Package-visible for tests. */
    Map<String, List<Map<String, Object>>> collect(Employee employee) {
        Long employeeId = employee.getId();
        Long companyId = employee.getCompany() == null ? null : employee.getCompany().getId();
        Map<String, List<Map<String, Object>>> sections = new LinkedHashMap<>();

        sections.put("profile", List.of(profile(employee)));

        List<Map<String, Object>> documents = new ArrayList<>();
        for (EmployeeDocument d : documentRepository.findAllByCompany_IdAndEmployeeIdAndDeletedFalseOrderByExpiryDateAsc(companyId, employeeId)) {
            if (!belongsTo(d.getEmployee(), employeeId)) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", d.getId());
            row.put("documentType", d.getDocumentType());
            row.put("documentNumber", d.getDocumentNumber());
            row.put("fileName", d.getOriginalFileName());
            row.put("status", d.getStatus());
            row.put("issueDate", d.getIssueDate());
            row.put("expiryDate", d.getExpiryDate());
            row.put("uploadedAt", d.getUploadedAt());
            documents.add(clean(row));
        }
        sections.put("documents", documents);

        List<Map<String, Object>> attendance = new ArrayList<>();
        for (AttendanceRecord r : attendanceRepository.findAllByCompany_IdAndEmployee_IdOrderByPunchTimeDesc(companyId, employeeId)) {
            if (!belongsTo(r.getEmployee(), employeeId)) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("punchTime", r.getPunchTime());
            row.put("punchType", r.getPunchType());
            row.put("source", r.getSource());
            row.put("verifyMode", r.getVerifyMode());
            row.put("deviceName", r.getDeviceName());
            row.put("status", r.getStatus());
            attendance.add(clean(row));
        }
        sections.put("attendance", attendance);

        List<Map<String, Object>> leave = new ArrayList<>();
        for (LeaveRequest l : leaveRequestRepository.findAllByEmployee_Company_IdAndEmployeeIdOrderByStartDateDesc(companyId, employeeId)) {
            if (!belongsTo(l.getEmployee(), employeeId)) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", l.getId());
            row.put("leaveType", l.getLeaveType() == null ? null : l.getLeaveType().getName());
            row.put("startDate", l.getStartDate());
            row.put("endDate", l.getEndDate());
            row.put("days", l.getDays());
            row.put("halfDay", l.isHalfDay());
            row.put("reason", l.getReason());
            row.put("status", l.getStatus());
            row.put("decidedAt", l.getDecidedAt());
            row.put("decisionNote", l.getDecisionNote());
            leave.add(clean(row));
        }
        sections.put("leave_requests", leave);

        List<Map<String, Object>> payslips = new ArrayList<>();
        for (PayrollItem p : payrollItemRepository.findByEmployee_Company_IdAndEmployeeIdAndDeletedFalseOrderByCreatedAtDesc(companyId, employeeId)) {
            if (!belongsTo(p.getEmployee(), employeeId)) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", p.getId());
            row.put("periodMonth", p.getPayrollRun() == null ? null : p.getPayrollRun().getPeriodMonth());
            row.put("periodYear", p.getPayrollRun() == null ? null : p.getPayrollRun().getPeriodYear());
            row.put("grossSalary", p.getGrossSalary());
            row.put("totalDeductions", p.getTotalDeductions());
            row.put("netSalary", p.getNetSalary());
            row.put("status", p.getStatus());
            row.put("paymentDate", p.getPaymentDate());
            payslips.add(clean(row));
        }
        sections.put("payslips", payslips);

        List<Map<String, Object>> assets = new ArrayList<>();
        for (MonitoredDevice device : deviceRepository.findAllByCompany_IdAndEmployee_IdAndActiveTrueAndDeletedFalseOrderByDeviceNameAsc(companyId, employeeId)) {
            if (!belongsTo(device.getEmployee(), employeeId)) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("kind", "COMPUTER");
            row.put("name", device.getDeviceName());
            row.put("hostname", device.getHostname());
            row.put("serialNumber", device.getSerialNumber());
            row.put("assignedDate", device.getAssignedDate());
            assets.add(clean(row));
        }
        sections.put("assets", assets);

        List<Map<String, Object>> consents = new ArrayList<>();
        for (MonitoringConsent c : consentRepository.findAllByCompany_IdAndEmployee_IdAndDeletedFalseOrderByGrantedAtDesc(companyId, employeeId)) {
            if (!belongsTo(c.getEmployee(), employeeId)) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("kind", "MONITORING_CONSENT");
            row.put("version", c.getNoticeVersion());
            row.put("status", c.getStatus());
            row.put("at", c.getGrantedAt());
            row.put("withdrawnAt", c.getWithdrawnAt());
            consents.add(clean(row));
        }
        Long userId = employee.getUser() == null ? null : employee.getUser().getId();
        if (userId != null) {
            for (LegalAcceptance a : acceptanceRepository.findAllByUser_IdAndDeletedFalseOrderByAcceptedAtDesc(userId)) {
                if (a.getUser() != null && !Objects.equals(a.getUser().getId(), userId)) continue;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("kind", "LEGAL_ACCEPTANCE:" + a.getDocType());
                row.put("version", a.getDocVersion());
                row.put("status", "ACCEPTED");
                row.put("at", a.getAcceptedAt());
                row.put("withdrawnAt", null);
                consents.add(clean(row));
            }
        }
        sections.put("consents", consents);

        List<Map<String, Object>> requests = new ArrayList<>();
        for (DataPrincipalRequest r : requestRepository.findAllByCompany_IdAndEmployee_IdAndDeletedFalseOrderByCreatedAtDesc(companyId, employeeId)) {
            if (!belongsTo(r.getEmployee(), employeeId)) continue;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", r.getId());
            row.put("type", r.getRequestType());
            row.put("status", r.getStatus());
            row.put("createdAt", r.getCreatedAt());
            row.put("dueDate", r.getDueDate());
            row.put("resolvedAt", r.getResolvedAt());
            requests.add(clean(row));
        }
        sections.put("privacy_requests", requests);
        // Last on purpose: if this optional table query fails on Postgres it aborts the
        // transaction, so nothing else may run after it.
        assets.addAll(employeeAssets(companyId, employeeId));
        return sections;
    }

    private Map<String, Object> profile(Employee e) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("employeeCode", e.getEmployeeCode());
        row.put("firstName", e.getFirstName());
        row.put("lastName", e.getLastName());
        row.put("email", e.getEmail());
        row.put("phone", e.getPhone());
        row.put("dateOfBirth", e.getDateOfBirth());
        row.put("gender", e.getGender());
        row.put("address", e.getAddress());
        row.put("emergencyContactName", e.getEmergencyContactName());
        row.put("emergencyContactPhone", e.getEmergencyContactPhone());
        row.put("dateOfJoining", e.getDateOfJoining());
        row.put("employmentType", e.getEmploymentType());
        row.put("employmentStatus", e.getStatus());
        row.put("lastWorkingDate", e.getLastWorkingDate());
        row.put("department", e.getDepartment() == null ? null : e.getDepartment().getName());
        row.put("designation", e.getDesignation() == null ? null : e.getDesignation().getTitle());
        row.put("bankName", e.getBankName());
        row.put("bankAccountNumber", e.getBankAccountNumber());
        row.put("bankIfsc", e.getBankIfsc());
        row.put("panNumber", e.getPanNumber());
        row.put("uanNumber", e.getUanNumber());
        row.put("esiNumber", e.getEsiNumber());
        return clean(row);
    }

    /** HaodaAsset's employee_asset table exists only in the real schema (not an entity); absent -> no rows. */
    private List<Map<String, Object>> employeeAssets(Long companyId, Long employeeId) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                    select asset_type, asset_tag, description, status, assigned_at, returned_at
                    from employee_asset
                    where company_id = ? and employee_id = ? and deleted = false
                    """, companyId, employeeId);
            List<Map<String, Object>> result = new ArrayList<>();
            for (Map<String, Object> r : rows) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("kind", r.get("asset_type"));
                row.put("name", r.get("description"));
                row.put("hostname", null);
                row.put("serialNumber", r.get("asset_tag"));
                row.put("assignedDate", r.get("assigned_at"));
                result.add(clean(row));
            }
            return result;
        } catch (RuntimeException ex) {
            log.debug("employee_asset not available for export: {}", ex.getMessage());
            return List.of();
        }
    }

    private static boolean belongsTo(Employee owner, Long employeeId) {
        return owner != null && Objects.equals(owner.getId(), employeeId);
    }

    /** Converts values to JSON/CSV friendly scalars (dates and decimals as ISO strings). */
    private static Map<String, Object> clean(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : row.entrySet()) {
            Object v = e.getValue();
            if (v == null || v instanceof String || v instanceof Boolean || v instanceof Integer || v instanceof Long) {
                out.put(e.getKey(), v);
            } else {
                out.put(e.getKey(), v.toString());
            }
        }
        return out;
    }

    private static void write(ZipOutputStream zip, String name, byte[] content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content);
        zip.closeEntry();
    }

    private static String readme() {
        return """
                Vettri HRMS - copy of your personal data
                ========================================
                data.json            Everything below in one file.
                profile.csv          Your employee profile as held by your employer.
                documents.csv        Documents on file (metadata only; download files from My Documents).
                attendance.csv       Attendance punches.
                leave_requests.csv   Leave applications and decisions.
                payslips.csv         Payslip summaries (download payslip PDFs from My Payslip).
                assets.csv           Devices and assets assigned to you.
                consents.csv         Monitoring consents and legal documents you accepted.
                privacy_requests.csv Your privacy requests.

                Your employer is the data fiduciary for this data. To correct anything,
                raise a correction request from the Privacy page or contact the grievance
                officer shown there.
                """;
    }

    /** Minimal RFC 4180 CSV writer (header = union of keys in first-seen order). */
    static final class CsvWriter {
        private CsvWriter() {
        }

        static String toCsv(List<Map<String, Object>> rows) {
            Set<String> headers = new LinkedHashSet<>();
            for (Map<String, Object> row : rows) headers.addAll(row.keySet());
            StringBuilder sb = new StringBuilder();
            sb.append(String.join(",", headers.stream().map(CsvWriter::escape).toList())).append("\r\n");
            for (Map<String, Object> row : rows) {
                List<String> cells = new ArrayList<>();
                for (String h : headers) cells.add(escape(row.get(h)));
                sb.append(String.join(",", cells)).append("\r\n");
            }
            return sb.toString();
        }

        static String escape(Object value) {
            if (value == null) return "";
            String s = value.toString();
            // Neutralise spreadsheet formula injection.
            if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0) s = "'" + s;
            if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
                s = "\"" + s.replace("\"", "\"\"") + "\"";
            }
            return s;
        }
    }
}
