package com.haodaone.tenant;

import com.haodaone.attendance.entity.AttendanceRecord;
import com.haodaone.attendance.repository.AttendanceRecordRepository;
import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.security.JwtService;
import com.haodaone.user.entity.Permission;
import com.haodaone.user.entity.Role;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.PermissionRepository;
import com.haodaone.user.repository.RoleRepository;
import com.haodaone.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class EmployeeAndAttendanceApiIsolationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private AttendanceRecordRepository attendanceRecordRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @AfterEach
    public void cleanup() {
        TenantContext.clear();
    }

    @Test
    public void companyAUser_cannotViewCompanyBEmployee_butCompanyBUserCan() throws Exception {
        Company a = new Company();
        a.setName("Company A");
        a = companyRepository.save(a);

        Company b = new Company();
        b.setName("Company B");
        b = companyRepository.save(b);

        companyRepository.flush();

        Permission p = permissionRepository.findByCode("EMPLOYEE_VIEW")
                .orElseGet(() -> {
                    Permission permission = new Permission();
                    permission.setCode("EMPLOYEE_VIEW");
                    permission.setDescription("View employee");
                    permission.setModule("EMPLOYEE");
                    return permissionRepository.save(permission);
                });

        Role r = new Role();
        r.setName("EMP_VIEWER");
        r.setLabel("Employee Viewer");
        r.setPermissions(Set.of(p));
        roleRepository.save(r);

        User userA = new User();
        userA.setUsername("userA@companya.example");
        userA.setEmail("userA@companya.example");
        userA.setFullName("User A");
        userA.setPasswordHash(passwordEncoder.encode("password"));
        userA.setRoles(Set.of(r));
        userA.setCompany(a);
        userRepository.save(userA);

        User userB = new User();
        userB.setUsername("userB@companyb.example");
        userB.setEmail("userB@companyb.example");
        userB.setFullName("User B");
        userB.setPasswordHash(passwordEncoder.encode("password"));
        userB.setRoles(Set.of(r));
        userB.setCompany(b);
        userRepository.save(userB);

        Employee empB = new Employee();
        empB.setEmployeeCode("E-B-1");
        empB.setFirstName("Employee");
        empB.setLastName("B1");
        empB.setEmail("empb1@companyb.example");
        empB.setDateOfJoining(java.time.LocalDate.now());
        empB.setCompany(b);
        employeeRepository.save(empB);
        employeeRepository.flush();
        userRepository.flush();

        String tokenA = jwtService.generateAccessToken(userA.getUsername(), java.util.List.of(r.getName()));
        String tokenB = jwtService.generateAccessToken(userB.getUsername(), java.util.List.of(r.getName()));

        mockMvc.perform(get("/api/employees/" + empB.getId())
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/employees/" + empB.getId())
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    public void companyAUser_cannotViewCompanyBAttendance_butCompanyBUserCan() throws Exception {
        Company a = new Company();
        a.setName("Company A");
        a = companyRepository.save(a);

        Company b = new Company();
        b.setName("Company B");
        b = companyRepository.save(b);

        companyRepository.flush();

        Permission p = permissionRepository.findByCode("ATTENDANCE_VIEW")
                .orElseGet(() -> {
                    Permission permission = new Permission();
                    permission.setCode("ATTENDANCE_VIEW");
                    permission.setDescription("View attendance/work sessions");
                    permission.setModule("ATTENDANCE");
                    return permissionRepository.save(permission);
                });

        Role r = new Role();
        r.setName("ATT_VIEWER");
        r.setLabel("Attendance Viewer");
        r.setPermissions(Set.of(p));
        roleRepository.save(r);

        User userA = new User();
        userA.setUsername("userA2@companya.example");
        userA.setEmail("userA2@companya.example");
        userA.setFullName("User A2");
        userA.setPasswordHash(passwordEncoder.encode("password"));
        userA.setRoles(Set.of(r));
        userA.setCompany(a);
        userRepository.save(userA);

        User userB = new User();
        userB.setUsername("userB2@companyb.example");
        userB.setEmail("userB2@companyb.example");
        userB.setFullName("User B2");
        userB.setPasswordHash(passwordEncoder.encode("password"));
        userB.setRoles(Set.of(r));
        userB.setCompany(b);
        userRepository.save(userB);

        Employee empB = new Employee();
        empB.setEmployeeCode("E-B-2");
        empB.setFirstName("Employee");
        empB.setLastName("B2");
        empB.setEmail("empb2@companyb.example");
        empB.setDateOfJoining(java.time.LocalDate.now());
        empB.setCompany(b);
        employeeRepository.save(empB);

        // Create an AttendanceRecord under company B for empB
        AttendanceRecord ar = new AttendanceRecord();
        ar.setEmployee(empB);
        ar.setCompany(b);
        ar.setDeviceUserId("pin-123");
        ar.setEmployeeName(empB.getFirstName() + " " + empB.getLastName());
        ar.setPunchTime(LocalDateTime.now().minusHours(1));
        ar.setPunchType("IN");
        ar.setDeviceSerialNumber("dev-123");
        ar.setDeviceName("Biometric Device 1");
        attendanceRecordRepository.save(ar);
        attendanceRecordRepository.flush();
        userRepository.flush();

        String tokenA = jwtService.generateAccessToken(userA.getUsername(), java.util.List.of(r.getName()));
        String tokenB = jwtService.generateAccessToken(userB.getUsername(), java.util.List.of(r.getName()));

        mockMvc.perform(get("/api/attendance/employee/" + empB.getId())
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/attendance/employee/" + empB.getId())
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}
