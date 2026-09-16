package com.haodaone.attendance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haodaone.attendance.dto.AttendanceCheckInRequest;
import com.haodaone.attendance.entity.OfficeLocation;
import com.haodaone.attendance.repository.OfficeLocationRepository;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class AttendancePresenceFlowTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private PermissionRepository permissionRepository;
    @Autowired private OfficeLocationRepository officeLocationRepository;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    @Test
    public void employee_can_check_in_inside_office() throws Exception {
        Company company = companyRepository.save(newCompany("Acme"));
        Role employeeRole = roleRepository.findByName("EMPLOYEE").orElseGet(() -> {
            Role role = new Role();
            role.setName("EMPLOYEE");
            role.setLabel("Employee");
            role.setPermissions(Set.of());
            return roleRepository.save(role);
        });
        User user = new User();
        user.setUsername("emp-check-in@acme.test");
        user.setEmail("emp-check-in@acme.test");
        user.setFullName("Employee One");
        user.setPasswordHash(passwordEncoder.encode("password"));
        user.setCompany(company);
        user.setRoles(Set.of(employeeRole));
        user = userRepository.save(user);

        Employee employee = new Employee();
        employee.setEmployeeCode("E-100");
        employee.setFirstName("Employee");
        employee.setLastName("One");
        employee.setEmail("emp-one@acme.test");
        employee.setDateOfJoining(java.time.LocalDate.now());
        employee.setCompany(company);
        employee.setUser(user);
        employee.setStatus("Active");
        employeeRepository.save(employee);

        OfficeLocation location = new OfficeLocation();
        location.setCompany(company);
        location.setName("HQ");
        location.setAddress("HQ Main");
        location.setLatitude(13.0827);
        location.setLongitude(80.2707);
        location.setAllowedRadiusMeters(150);
        location.setActive(true);
        officeLocationRepository.save(location);

        String token = jwtService.generateAccessToken(user.getUsername(), java.util.List.of(employeeRole.getName()));

        AttendanceCheckInRequest req = new AttendanceCheckInRequest();
        req.setLatitude(13.0829);
        req.setLongitude(80.2709);
        req.setAccuracy(12.0);
        req.setSource("MOBILE");
        req.setOfficeLocationId(location.getId());

        mockMvc.perform(post("/api/attendance/check-in")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    public void employee_cannot_check_in_outside_geofence() throws Exception {
        Company company = companyRepository.save(newCompany("Acme 2"));
        Role employeeRole = roleRepository.findByName("EMPLOYEE").orElseGet(() -> {
            Role role = new Role();
            role.setName("EMPLOYEE");
            role.setLabel("Employee");
            role.setPermissions(Set.of());
            return roleRepository.save(role);
        });
        User user = new User();
        user.setUsername("emp-outside@acme.test");
        user.setEmail("emp-outside@acme.test");
        user.setFullName("Employee Two");
        user.setPasswordHash(passwordEncoder.encode("password"));
        user.setCompany(company);
        user.setRoles(Set.of(employeeRole));
        user = userRepository.save(user);

        Employee employee = new Employee();
        employee.setEmployeeCode("E-101");
        employee.setFirstName("Employee");
        employee.setLastName("Two");
        employee.setEmail("emp-two@acme.test");
        employee.setDateOfJoining(java.time.LocalDate.now());
        employee.setCompany(company);
        employee.setUser(user);
        employee.setStatus("Active");
        employeeRepository.save(employee);

        OfficeLocation location = new OfficeLocation();
        location.setCompany(company);
        location.setName("HQ");
        location.setAddress("HQ Main");
        location.setLatitude(13.0827);
        location.setLongitude(80.2707);
        location.setAllowedRadiusMeters(150);
        location.setActive(true);
        officeLocationRepository.save(location);

        String token = jwtService.generateAccessToken(user.getUsername(), java.util.List.of(employeeRole.getName()));

        AttendanceCheckInRequest req = new AttendanceCheckInRequest();
        req.setLatitude(12.9000);
        req.setLongitude(77.6000);
        req.setAccuracy(15.0);
        req.setSource("MOBILE");
        req.setOfficeLocationId(location.getId());

        mockMvc.perform(post("/api/attendance/check-in")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void employee_today_lookup_accepts_email_as_principal_name() throws Exception {
        Company company = companyRepository.save(newCompany("Acme 3"));
        Role employeeRole = roleRepository.findByName("EMPLOYEE").orElseGet(() -> {
            Role role = new Role();
            role.setName("EMPLOYEE");
            role.setLabel("Employee");
            role.setPermissions(Set.of());
            return roleRepository.save(role);
        });
        User user = new User();
        user.setUsername("emp-email-lookup");
        user.setEmail("emp-email-lookup@acme.test");
        user.setFullName("Employee Three");
        user.setPasswordHash(passwordEncoder.encode("password"));
        user.setCompany(company);
        user.setRoles(Set.of(employeeRole));
        user = userRepository.save(user);

        Employee employee = new Employee();
        employee.setEmployeeCode("E-102");
        employee.setFirstName("Employee");
        employee.setLastName("Three");
        employee.setEmail(user.getEmail());
        employee.setDateOfJoining(java.time.LocalDate.now());
        employee.setCompany(company);
        employee.setUser(user);
        employee.setStatus("Active");
        employeeRepository.save(employee);

        mockMvc.perform(get("/api/attendance/today")
                        .principal(new UsernamePasswordAuthenticationToken(user.getEmail(), null,
                                AuthorityUtils.createAuthorityList("ROLE_EMPLOYEE"))))
                .andExpect(status().isOk());
    }

    private Company newCompany(String name) {
        Company company = new Company();
        company.setName(name);
        return company;
    }
}
