package com.haodaone.privacy.service;

import com.haodaone.attendance.entity.AttendanceRecord;
import com.haodaone.attendance.repository.AttendanceRecordRepository;
import com.haodaone.company.entity.Company;
import com.haodaone.document.entity.EmployeeDocument;
import com.haodaone.document.repository.EmployeeDocumentRepository;
import com.haodaone.employee.entity.Employee;
import com.haodaone.leave.entity.LeaveRequest;
import com.haodaone.leave.repository.LeaveRequestRepository;
import com.haodaone.monitoring.repository.MonitoredDeviceRepository;
import com.haodaone.privacy.repository.DataPrincipalRequestRepository;
import com.haodaone.privacy.repository.LegalAcceptanceRepository;
import com.haodaone.privacy.repository.MonitoringConsentRepository;
import com.haodaone.salary.repository.PayrollItemRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PersonalDataExportServiceTest {

    @Test
    void zipContainsOnlyTheRequestersOwnData() throws IOException {
        EmployeeDocumentRepository documents = mock(EmployeeDocumentRepository.class);
        AttendanceRecordRepository attendance = mock(AttendanceRecordRepository.class);
        LeaveRequestRepository leave = mock(LeaveRequestRepository.class);
        PayrollItemRepository payroll = mock(PayrollItemRepository.class);
        MonitoredDeviceRepository devices = mock(MonitoredDeviceRepository.class);
        MonitoringConsentRepository consents = mock(MonitoringConsentRepository.class);
        LegalAcceptanceRepository acceptances = mock(LegalAcceptanceRepository.class);
        DataPrincipalRequestRepository requests = mock(DataPrincipalRequestRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class); // employee_asset query -> empty list

        Company company = new Company();
        company.setId(3L);
        Employee me = employee(42L, "Priya", "Raman", company);
        Employee colleague = employee(43L, "Arjun", "Mehta", company);

        // A misbehaving repository that returns a colleague's rows too must not leak them.
        when(documents.findAllByCompany_IdAndEmployeeIdAndDeletedFalseOrderByExpiryDateAsc(3L, 42L))
                .thenReturn(List.of(document(me, "AADHAAR", "my-aadhaar.pdf"), document(colleague, "PAN", "arjun-pan.pdf")));
        when(attendance.findAllByCompany_IdAndEmployee_IdOrderByPunchTimeDesc(3L, 42L))
                .thenReturn(List.of(punch(me, "Main gate"), punch(colleague, "Arjun-only-device")));
        when(leave.findAllByEmployee_Company_IdAndEmployeeIdOrderByStartDateDesc(3L, 42L))
                .thenReturn(List.of(leave(me, "Family function"), leave(colleague, "Arjun wedding")));

        PersonalDataExportService service = new PersonalDataExportService(documents, attendance, leave, payroll, devices,
                consents, acceptances, requests, jdbc);

        Map<String, String> files = unzip(service.buildZip(me));

        assertTrue(files.keySet().containsAll(List.of("data.json", "profile.csv", "documents.csv", "attendance.csv",
                "leave_requests.csv", "payslips.csv", "assets.csv", "consents.csv", "README.txt")));
        String everything = String.join("\n", files.values());
        assertTrue(everything.contains("Priya"));
        assertTrue(everything.contains("my-aadhaar.pdf"));
        assertTrue(everything.contains("Family function"));
        assertTrue(everything.contains("Main gate"));
        assertFalse(everything.contains("Arjun"), "another employee's data leaked into the export");
        assertFalse(everything.contains("arjun-pan.pdf"));
        assertFalse(everything.contains("Mehta"));
    }

    private static Employee employee(Long id, String first, String last, Company company) {
        Employee e = new Employee();
        e.setId(id);
        e.setFirstName(first);
        e.setLastName(last);
        e.setEmail(first.toLowerCase() + "@acme.test");
        e.setEmployeeCode("EMP" + id);
        e.setCompany(company);
        e.setDateOfJoining(LocalDate.of(2024, 1, 1));
        return e;
    }

    private static EmployeeDocument document(Employee owner, String type, String fileName) {
        EmployeeDocument d = new EmployeeDocument();
        d.setEmployee(owner);
        d.setDocumentType(type);
        d.setOriginalFileName(fileName);
        return d;
    }

    private static AttendanceRecord punch(Employee owner, String deviceName) {
        AttendanceRecord r = new AttendanceRecord();
        r.setEmployee(owner);
        r.setEmployeeName(owner.getFullName());
        r.setPunchTime(LocalDateTime.of(2026, 10, 1, 9, 30));
        r.setDeviceName(deviceName);
        return r;
    }

    private static LeaveRequest leave(Employee owner, String reason) {
        LeaveRequest l = new LeaveRequest();
        l.setEmployee(owner);
        l.setStartDate(LocalDate.of(2026, 9, 1));
        l.setEndDate(LocalDate.of(2026, 9, 2));
        l.setReason(reason);
        return l;
    }

    private static Map<String, String> unzip(byte[] zip) throws IOException {
        Map<String, String> files = new HashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                files.put(entry.getName(), new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return files;
    }
}
