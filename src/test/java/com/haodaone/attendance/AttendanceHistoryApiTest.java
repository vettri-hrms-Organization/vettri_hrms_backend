package com.haodaone.attendance;

import com.haodaone.attendance.entity.AttendanceSession;
import com.haodaone.attendance.entity.AttendanceRecord;
import com.haodaone.attendance.repository.AttendanceRecordRepository;
import com.haodaone.attendance.repository.AttendanceSessionRepository;
import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.security.JwtService;
import com.haodaone.tenant.TenantContext;
import com.haodaone.user.entity.Role;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.RoleRepository;
import com.haodaone.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AttendanceHistoryApiTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private AttendanceRecordRepository attendanceRecordRepository;
    @Autowired private AttendanceSessionRepository attendanceSessionRepository;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void employeeHistoryReturnsOnlyAuthenticatedEmployeesRecordsForRequestedDates() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Company company = new Company();
        company.setName("Attendance history " + suffix);
        company = companyRepository.save(company);

        Role role = new Role();
        role.setName("ATT_HISTORY_" + suffix);
        role.setLabel("Attendance history test");
        role.setPermissions(Set.of());
        role = roleRepository.save(role);

        User user = new User();
        user.setUsername("attendance-history-" + suffix);
        user.setEmail("attendance-history-" + suffix + "@example.test");
        user.setFullName("Attendance History User");
        user.setPasswordHash(passwordEncoder.encode("test-password"));
        user.setCompany(company);
        user.setRoles(Set.of(role));
        user = userRepository.save(user);

        Employee self = createEmployee(company, user, "SELF-" + suffix, "History", "Employee");
        Employee other = createEmployee(company, null, "OTHER-" + suffix, "Other", "Employee");

        attendanceSessionRepository.save(session(company, self, LocalDate.of(2026, 10, 5),
                LocalDateTime.of(2026, 10, 5, 9, 30), LocalDateTime.of(2026, 10, 5, 18, 0)));
        attendanceSessionRepository.save(session(company, self, LocalDate.of(2026, 10, 15),
                LocalDateTime.of(2026, 10, 15, 9, 15), LocalDateTime.of(2026, 10, 15, 18, 5)));
        attendanceSessionRepository.save(session(company, self, LocalDate.of(2026, 9, 30),
                LocalDateTime.of(2026, 9, 30, 9, 0), LocalDateTime.of(2026, 9, 30, 17, 0)));
        attendanceSessionRepository.save(session(company, other, LocalDate.of(2026, 10, 10),
                LocalDateTime.of(2026, 10, 10, 8, 45), LocalDateTime.of(2026, 10, 10, 17, 30)));
        AttendanceRecord biometricPunch = new AttendanceRecord();
        biometricPunch.setEmployee(self);
        biometricPunch.setCompany(company);
        biometricPunch.setDeviceUserId("PIN-" + suffix);
        biometricPunch.setEmployeeName("History Employee");
        biometricPunch.setPunchTime(LocalDateTime.of(2026, 10, 20, 9, 5));
        biometricPunch.setPunchType("IN");
        biometricPunch.setDeviceSerialNumber("DEVICE-" + suffix);
        biometricPunch.setDeviceName("Test biometric device");
        attendanceRecordRepository.save(biometricPunch);
        attendanceSessionRepository.flush();
        attendanceRecordRepository.flush();

        String token = jwtService.generateAccessToken(user.getUsername(), java.util.List.of(role.getName()));

        mockMvc.perform(get("/api/attendance/me/history")
                        .param("from", "2026-10-01")
                        .param("to", "2026-10-31")
                        .param("employeeId", other.getId().toString())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].attendanceDate", is("2026-10-20")))
                .andExpect(jsonPath("$[0].employeeId", is(self.getId().intValue())))
                .andExpect(jsonPath("$[0].checkInTime").exists())
                .andExpect(jsonPath("$[0].source", is("BIOMETRIC")))
                .andExpect(jsonPath("$[0].status", is("RECEIVED")))
                .andExpect(jsonPath("$[1].attendanceDate", is("2026-10-15")))
                .andExpect(jsonPath("$[1].checkInTime").exists())
                .andExpect(jsonPath("$[1].checkOutTime").exists())
                .andExpect(jsonPath("$[1].durationMinutes", is(530)))
                .andExpect(jsonPath("$[1].source", is("WEB")))
                .andExpect(jsonPath("$[2].attendanceDate", is("2026-10-05")));

        mockMvc.perform(get("/api/attendance/employee/{employeeId}/sessions", other.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    private Employee createEmployee(Company company, User user, String code, String firstName, String lastName) {
        Employee employee = new Employee();
        employee.setEmployeeCode(code);
        employee.setFirstName(firstName);
        employee.setLastName(lastName);
        employee.setEmail(code.toLowerCase() + "@example.test");
        employee.setDateOfJoining(LocalDate.of(2026, 1, 1));
        employee.setCompany(company);
        employee.setUser(user);
        employee.setStatus("Active");
        return employeeRepository.save(employee);
    }

    private AttendanceSession session(Company company, Employee employee, LocalDate date,
                                      LocalDateTime checkIn, LocalDateTime checkOut) {
        AttendanceSession session = new AttendanceSession();
        session.setCompany(company);
        session.setEmployee(employee);
        session.setAttendanceDate(date);
        session.setCheckInTime(checkIn);
        session.setCheckOutTime(checkOut);
        session.setStatus("CHECKED_OUT");
        session.setSource("WEB");
        session.setLocationType("OFFICE");
        session.setDurationMinutes(java.time.Duration.between(checkIn, checkOut).toMinutes());
        return session;
    }
}
