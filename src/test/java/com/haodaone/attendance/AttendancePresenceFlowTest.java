package com.haodaone.attendance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haodaone.attendance.dto.AttendanceCheckInRequest;
import com.haodaone.attendance.dto.OfficeLocationRequest;
import com.haodaone.attendance.entity.OfficeLocation;
import com.haodaone.attendance.repository.OfficeLocationRepository;
import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.security.JwtService;
import com.haodaone.user.entity.Permission;
import com.haodaone.user.entity.PermissionScope;
import com.haodaone.user.entity.Role;
import com.haodaone.user.entity.RolePermissionScope;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.PermissionRepository;
import com.haodaone.user.repository.RoleRepository;
import com.haodaone.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
        req.setTimestamp(System.currentTimeMillis());
        req.setSource("MOBILE");
        req.setOfficeLocationId(location.getId());

        mockMvc.perform(post("/api/attendance/check-in")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/attendance/today")
                .header("Authorization", "Bearer " + token))
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
        req.setTimestamp(System.currentTimeMillis());
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

        String token = jwtService.generateAccessToken(user.getEmail(), java.util.List.of(employeeRole.getName()));

        mockMvc.perform(get("/api/attendance/today")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    public void self_service_permission_allows_employee_profile_without_employee_role_name() throws Exception {
        Company company = companyRepository.save(newCompany("Acme 4"));
        Permission selfCheckIn = permissionRepository.findByCode("SELF_ATTENDANCE_CHECKIN")
                .orElseThrow(() -> new IllegalStateException("SELF_ATTENDANCE_CHECKIN permission missing"));
        Role selfServiceRole = roleRepository.findByName("OPS_MANAGER").orElseGet(() -> {
            Role role = new Role();
            role.setName("OPS_MANAGER");
            role.setLabel("Operations Manager");
            role.setPermissions(Set.of(selfCheckIn));
            return roleRepository.save(role);
        });

        User user = new User();
        user.setUsername("ops-self-checkin");
        user.setEmail("ops-self-checkin@acme.test");
        user.setFullName("Ops Manager");
        user.setPasswordHash(passwordEncoder.encode("password"));
        user.setCompany(company);
        user.setRoles(Set.of(selfServiceRole));
        user = userRepository.save(user);

        Employee employee = new Employee();
        employee.setEmployeeCode("E-103");
        employee.setFirstName("Ops");
        employee.setLastName("Manager");
        employee.setEmail(user.getEmail());
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

        String token = jwtService.generateAccessToken(user.getUsername(), java.util.List.of(selfServiceRole.getName()));

        AttendanceCheckInRequest req = new AttendanceCheckInRequest();
        req.setLatitude(13.0829);
        req.setLongitude(80.2709);
        req.setAccuracy(12.0);
        req.setTimestamp(System.currentTimeMillis());
        req.setSource("MOBILE");
        req.setOfficeLocationId(location.getId());

        mockMvc.perform(post("/api/attendance/check-in")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    public void organization_location_permissions_control_location_api_and_tenant_scope() throws Exception {
        Company company = companyRepository.save(newCompany("Location API " + UUID.randomUUID()));
        Company otherCompany = companyRepository.save(newCompany("Other Location API " + UUID.randomUUID()));
        User locationManager = createLocationUser(company, "OFFICE_LOCATION_VIEW", "OFFICE_LOCATION_CREATE",
                "OFFICE_LOCATION_UPDATE", "OFFICE_LOCATION_DEACTIVATE");
        String token = jwtService.generateAccessToken(locationManager.getUsername(),
                java.util.List.of(locationManager.getRoles().iterator().next().getName()));

        OfficeLocationRequest request = new OfficeLocationRequest();
        request.setName("Primary Office");
        request.setAddress("1 Main Street");
        request.setCity("Chennai");
        request.setState("Tamil Nadu");
        request.setCountry("India");
        request.setLatitude(13.0827);
        request.setLongitude(80.2707);
        request.setAllowedRadiusMeters(150);

        String locationResponse = mockMvc.perform(post("/api/attendance/office-locations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Primary Office"))
                .andReturn().getResponse().getContentAsString();
        Long ownedLocationId = objectMapper.readTree(locationResponse).get("id").asLong();

        OfficeLocation foreignLocation = new OfficeLocation();
        foreignLocation.setCompany(otherCompany);
        foreignLocation.setName("Foreign Office");
        foreignLocation.setAddress("2 Other Street");
        foreignLocation.setLatitude(13.0);
        foreignLocation.setLongitude(80.0);
        foreignLocation.setAllowedRadiusMeters(150);
        foreignLocation.setActive(true);
        foreignLocation = officeLocationRepository.save(foreignLocation);

        mockMvc.perform(get("/api/attendance/office-locations")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Primary Office')]").exists())
                .andExpect(jsonPath("$[?(@.name == 'Foreign Office')]").doesNotExist());

        request.setName("Updated Primary Office");
        mockMvc.perform(put("/api/attendance/office-locations/{id}", ownedLocationId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Primary Office"));

        mockMvc.perform(patch("/api/attendance/office-locations/{id}/status", ownedLocationId)
                        .queryParam("active", "false")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(put("/api/attendance/office-locations/{id}", foreignLocation.getId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/attendance/office-locations/{id}/status", foreignLocation.getId())
                        .queryParam("active", "false")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void office_location_view_permission_does_not_allow_create() throws Exception {
        Company company = companyRepository.save(newCompany("Location Read Only " + UUID.randomUUID()));
        User locationViewer = createLocationUser(company, "OFFICE_LOCATION_VIEW");
        String token = jwtService.generateAccessToken(locationViewer.getUsername(),
                java.util.List.of(locationViewer.getRoles().iterator().next().getName()));

        OfficeLocationRequest request = new OfficeLocationRequest();
        request.setName("Unauthorized Office");
        request.setLatitude(13.0827);
        request.setLongitude(80.2707);
        request.setAllowedRadiusMeters(150);

        mockMvc.perform(get("/api/attendance/office-locations")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/attendance/office-locations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        org.junit.jupiter.api.Assertions.assertFalse(
                officeLocationRepository.findAllByCompany_IdAndDeletedFalseOrderByNameAsc(company.getId()).stream()
                        .anyMatch(location -> location.getName().equals("Unauthorized Office")));
    }

    private User createLocationUser(Company company, String... permissionCodes) {
        Set<Permission> permissions = java.util.Arrays.stream(permissionCodes)
                .map(code -> permissionRepository.findByCode(code)
                        .orElseThrow(() -> new IllegalStateException("Missing permission " + code)))
                .collect(java.util.stream.Collectors.toSet());

        Role role = new Role();
        role.setName("LOCATION_MANAGER_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
        role.setLabel("Location Manager");
        role.setCompany(company);
        role.setPermissions(permissions);
        Role persistedRole = roleRepository.save(role);
        Set<RolePermissionScope> scopes = new java.util.HashSet<>();
        for (Permission permission : permissions) {
            RolePermissionScope scope = new RolePermissionScope();
            scope.setRole(persistedRole);
            scope.setPermission(permission);
            scope.setScope(PermissionScope.ORGANIZATION);
            scopes.add(scope);
        }
        persistedRole.setPermissionScopes(scopes);
        persistedRole = roleRepository.save(persistedRole);

        User user = new User();
        String username = "location-manager-" + UUID.randomUUID();
        user.setUsername(username);
        user.setEmail(username + "@acme.test");
        user.setFullName("Location Manager");
        user.setPasswordHash(passwordEncoder.encode("password"));
        user.setCompany(company);
        user.setRoles(Set.of(persistedRole));
        return userRepository.save(user);
    }

    private Company newCompany(String name) {
        Company company = new Company();
        company.setName(name);
        company.setAttendanceMethod("WEB_APP_ONLY");
        return company;
    }
}
