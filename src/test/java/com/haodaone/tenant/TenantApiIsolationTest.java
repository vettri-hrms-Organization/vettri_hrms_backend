package com.haodaone.tenant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.monitoring.entity.MonitoredDevice;
import com.haodaone.monitoring.repository.MonitoredDeviceRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.ActiveProfiles;

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
        companyRepository.save(a);

        Company b = new Company();
        b.setName("Company B");
        companyRepository.save(b);

        // Permission + Role
        Permission p = new Permission();
        p.setCode("MONITORING_VIEW");
        p.setDescription("View monitored devices");
        p.setModule("MONITORING");
        permissionRepository.save(p);

        Role r = new Role();
        r.setName("MONITORING_VIEWER");
        r.setLabel("Monitoring Viewer");
        r.setPermissions(Set.of(p));
        roleRepository.save(r);

        // Users
        User userA = new User();
        userA.setUsername("userA@example.com");
        userA.setEmail("userA@example.com");
        userA.setFullName("User A");
        userA.setPasswordHash(passwordEncoder.encode("password"));
        userA.setRoles(Set.of(r));
        userA.setCompany(a);
        userRepository.save(userA);

        User userB = new User();
        userB.setUsername("userB@example.com");
        userB.setEmail("userB@example.com");
        userB.setFullName("User B");
        userB.setPasswordHash(passwordEncoder.encode("password"));
        userB.setRoles(Set.of(r));
        userB.setCompany(b);
        userRepository.save(userB);

        // Device under company B
        MonitoredDevice deviceB = new MonitoredDevice();
        deviceB.setDeviceId("dev-b-1");
        deviceB.setDeviceName("Device B1");
        deviceB.setAgentTokenHash("hash-b-1");
        deviceB.setCompany(b);
        deviceB.setActive(true);
        deviceRepository.save(deviceB);

        // Generate access tokens
        String tokenA = jwtService.generateAccessToken(userA.getUsername(), List.of(r.getName()));
        String tokenB = jwtService.generateAccessToken(userB.getUsername(), List.of(r.getName()));

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
}
