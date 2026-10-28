package com.haodaone.attendance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haodaone.attendance.dto.AttendanceCheckInRequest;
import com.haodaone.attendance.entity.OfficeLocation;
import com.haodaone.attendance.repository.OfficeLocationRepository;
import com.haodaone.attendance.service.AttendanceSelfieStorageService;
import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.security.JwtService;
import com.haodaone.user.entity.Role;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.RoleRepository;
import com.haodaone.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Base64;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Check-in through the real controller with the "require selfie" policy switched on. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AttendanceSelfieCheckInFlowTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private OfficeLocationRepository officeLocationRepository;
    @Autowired private JwtService jwtService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private AttendanceSelfieStorageService selfieStorage;

    @Test
    void checkInWithoutSelfieFailsWithSelfieRequired() throws Exception {
        Fixture fixture = fixture();
        AttendanceCheckInRequest request = officeRequest(fixture.locationId());

        mockMvc.perform(post("/api/attendance/check-in")
                        .header("Authorization", "Bearer " + fixture.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("SELFIE_REQUIRED"));
        verify(selfieStorage, never()).store(any(), anyLong(), anyLong(), any());
    }

    @Test
    void checkInWithSelfieIsStoredAndFlagged() throws Exception {
        Fixture fixture = fixture();
        when(selfieStorage.store(any(), anyLong(), anyLong(), any()))
                .thenReturn("attendance-selfies/1/1/2026-10-28/test.jpg");
        AttendanceCheckInRequest request = officeRequest(fixture.locationId());
        byte[] jpeg = new byte[2048];
        jpeg[0] = (byte) 0xFF;
        jpeg[1] = (byte) 0xD8;
        jpeg[2] = (byte) 0xFF;
        request.setSelfieImage("data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpeg));

        mockMvc.perform(post("/api/attendance/check-in")
                        .header("Authorization", "Bearer " + fixture.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasSelfie").value(true));
    }

    private AttendanceCheckInRequest officeRequest(Long locationId) {
        AttendanceCheckInRequest req = new AttendanceCheckInRequest();
        req.setLatitude(13.0829);
        req.setLongitude(80.2709);
        req.setAccuracy(12.0);
        req.setTimestamp(System.currentTimeMillis());
        req.setSource("MOBILE");
        req.setOfficeLocationId(locationId);
        return req;
    }

    private record Fixture(String token, Long locationId) {}

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Company company = new Company();
        company.setName("Selfie Co " + suffix);
        company.setAttendanceMethod("WEB_APP_ONLY");
        company.setRequireSelfieAtCheckIn(true);
        company = companyRepository.save(company);

        Role employeeRole = roleRepository.findByName("EMPLOYEE").orElseGet(() -> {
            Role role = new Role();
            role.setName("EMPLOYEE");
            role.setLabel("Employee");
            role.setPermissions(Set.of());
            return roleRepository.save(role);
        });
        User user = new User();
        user.setUsername("selfie-" + suffix + "@acme.test");
        user.setEmail("selfie-" + suffix + "@acme.test");
        user.setFullName("Selfie Tester");
        user.setPasswordHash(passwordEncoder.encode("password"));
        user.setCompany(company);
        user.setRoles(Set.of(employeeRole));
        user = userRepository.save(user);

        Employee employee = new Employee();
        employee.setEmployeeCode("S-" + suffix);
        employee.setFirstName("Selfie");
        employee.setLastName("Tester");
        employee.setEmail("selfie-emp-" + suffix + "@acme.test");
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
        return new Fixture(token, location.getId());
    }
}
