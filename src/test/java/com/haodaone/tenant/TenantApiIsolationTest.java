package com.haodaone.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.monitoring.entity.MonitoredDevice;
import com.haodaone.monitoring.repository.MonitoredDeviceRepository;
import com.haodaone.security.JwtService;
import com.haodaone.user.entity.Permission;
import com.haodaone.user.entity.Role;
import com.haodaone.user.entity.RolePermissionScope;
import com.haodaone.user.entity.PermissionScope;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.transaction.TestTransaction;

import java.util.List;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class TenantApiIsolationTest {

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
    private MonitoredDeviceRepository deviceRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @AfterEach
    public void cleanup() {
        TenantContext.clear();
    }

    @Test
    public void companyAUser_cannotViewCompanyBDevice_butCompanyBUserCan() throws Exception {
        // Create companies
        Company a = new Company();
        a.setName("Company A");
        a = companyRepository.save(a);

        Company b = new Company();
        b.setName("Company B");
        b = companyRepository.save(b);

        // Permission + Role
        Permission p = permissionRepository.findByCode("MONITORING_VIEW")
            .orElseGet(() -> {
                Permission permission = new Permission();
                permission.setCode("MONITORING_VIEW");
                permission.setDescription("View monitored devices");
                permission.setModule("MONITORING");
                return permissionRepository.save(permission);
            });
        Permission itManagementAccess = permissionRepository.findByCode("IT_MANAGEMENT_ACCESS")
            .orElseGet(() -> {
                Permission permission = new Permission();
                permission.setCode("IT_MANAGEMENT_ACCESS");
                permission.setDescription("Access IT Management");
                permission.setModule("IT Management");
                return permissionRepository.save(permission);
            });
        Permission itDeviceView = permissionRepository.findByCode("IT_DEVICE_VIEW")
            .orElseGet(() -> {
                Permission permission = new Permission();
                permission.setCode("IT_DEVICE_VIEW");
                permission.setDescription("View IT device inventory");
                permission.setModule("IT Management");
                return permissionRepository.save(permission);
            });

        Role r = new Role();
        r.setName("MONITORING_VIEWER");
        r.setLabel("Monitoring Viewer");
        r.setPermissions(Set.of(p, itManagementAccess, itDeviceView));
        r.setPermissionScopes(Set.of(scope(r, p), scope(r, itManagementAccess), scope(r, itDeviceView)));
        r = roleRepository.save(r);

        // Users
        User userA = new User();
        userA.setUsername("userA@example.com");
        userA.setEmail("userA@example.com");
        userA.setFullName("User A");
        userA.setPasswordHash(passwordEncoder.encode("password"));
        userA.setRoles(Set.of(r));
        userA.setCompany(a);
        userA = userRepository.save(userA);

        User userB = new User();
        userB.setUsername("userB@example.com");
        userB.setEmail("userB@example.com");
        userB.setFullName("User B");
        userB.setPasswordHash(passwordEncoder.encode("password"));
        userB.setRoles(Set.of(r));
        userB.setCompany(b);
        userB = userRepository.save(userB);
        userRepository.flush();
        // Device under company B
        MonitoredDevice deviceB = new MonitoredDevice();
        deviceB.setDeviceId("dev-b-1");
        deviceB.setDeviceName("Device B1");
        deviceB.setAgentTokenHash("hash-b-1");
        deviceB.setCompany(b);
        deviceB.setActive(true);
        deviceB = deviceRepository.save(deviceB);

        // Generate access tokens
        String tokenA = jwtService.generateAccessToken(userA.getUsername(), List.of(r.getName()));
        String tokenB = jwtService.generateAccessToken(userB.getUsername(), List.of(r.getName()));
        deviceRepository.flush();
        TestTransaction.flagForCommit();
        TestTransaction.end();

        // Company A user should be forbidden from viewing company B's device
        mockMvc.perform(get("/api/monitoring/devices/" + deviceB.getId())
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        // Company B user should be allowed
        mockMvc.perform(get("/api/monitoring/devices/" + deviceB.getId())
                        .header("Authorization", "Bearer " + tokenB)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    private RolePermissionScope scope(Role role, Permission permission) {
        RolePermissionScope scope = new RolePermissionScope();
        scope.setRole(role);
        scope.setPermission(permission);
        scope.setScope(PermissionScope.ORGANIZATION);
        return scope;
    }

    @Test
    public void requirementsApi_requiresViewPermission() throws Exception {
        Company company = new Company();
        company.setName("Requirements API Company");
        company = companyRepository.save(company);

        Permission requirementView = permissionRepository.findByCode("REQUIREMENT_VIEW")
            .orElseGet(() -> {
                Permission permission = new Permission();
                permission.setCode("REQUIREMENT_VIEW");
                permission.setDescription("View business requirements");
                permission.setModule("Requirements");
                return permissionRepository.save(permission);
            });

        String suffix = java.util.UUID.randomUUID().toString();
        Role employeeRole = new Role();
        employeeRole.setName("REQUIREMENTS_EMPLOYEE_" + suffix);
        employeeRole.setLabel("Employee");
        employeeRole.setPermissions(Set.of());
        employeeRole = roleRepository.save(employeeRole);

        Role hrRole = new Role();
        hrRole.setName("REQUIREMENTS_HR_" + suffix);
        hrRole.setLabel("HR");
        hrRole.setPermissions(Set.of(requirementView));
        RolePermissionScope hrScope = new RolePermissionScope();
        hrScope.setRole(hrRole);
        hrScope.setPermission(requirementView);
        hrScope.setScope(PermissionScope.ORGANIZATION);
        hrRole.getPermissionScopes().add(hrScope);
        hrRole = roleRepository.save(hrRole);

        User employee = createApiUser("requirements-employee-" + suffix, employeeRole, company);
        User hr = createApiUser("requirements-hr-" + suffix, hrRole, company);
        String employeeToken = jwtService.generateAccessToken(employee.getUsername(), List.of(employeeRole.getName()));
        String hrToken = jwtService.generateAccessToken(hr.getUsername(), List.of(hrRole.getName()));
        userRepository.flush();
        TestTransaction.flagForCommit();
        TestTransaction.end();

        mockMvc.perform(get("/api/requirements")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/requirements")
                        .header("Authorization", "Bearer " + hrToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    private User createApiUser(String username, Role role, Company company) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setFullName(username);
        user.setPasswordHash(passwordEncoder.encode("password"));
        user.setRoles(Set.of(role));
        user.setCompany(company);
        return userRepository.save(user);
    }
}
