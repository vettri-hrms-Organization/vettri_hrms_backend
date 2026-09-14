package com.haodaone.software;

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class SoftwareDeploymentAccessTest {

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

    @Test
    void companyAdminCanCreateDeploymentForOwnCompany() throws Exception {
        Company company = new Company();
        company.setName("Alpha Co");
        companyRepository.save(company);

        Permission softwareDeploy = new Permission();
        softwareDeploy.setCode("SOFTWARE_DEPLOY");
        softwareDeploy.setDescription("Deploy software");
        softwareDeploy.setModule("Software");
        permissionRepository.save(softwareDeploy);

        Role adminRole = new Role();
        adminRole.setName("SOFTWARE_ADMIN");
        adminRole.setLabel("Software Admin");
        adminRole.setPermissions(Set.of(softwareDeploy));
        roleRepository.save(adminRole);

        User admin = new User();
        admin.setUsername("admin@alpha");
        admin.setEmail("admin@alpha");
        admin.setFullName("Alpha Admin");
        admin.setPasswordHash(passwordEncoder.encode("password"));
        admin.setCompany(company);
        admin.setRoles(Set.of(adminRole));
        userRepository.save(admin);

        MonitoredDevice device = new MonitoredDevice();
        device.setDeviceId("alpha-device-1");
        device.setDeviceName("Alpha Device");
        device.setAgentTokenHash("alpha-token-hash-1");
        device.setCompany(company);
        device.setActive(true);
        deviceRepository.save(device);

        String token = jwtService.generateAccessToken(admin.getUsername(), List.of(adminRole.getName()));

        mockMvc.perform(post("/api/software/deployments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"softwarePackageId\":1,\"softwareVersionId\":1,\"targetDeviceIds\":[" + device.getId() + "]}"))
                .andExpect(status().isOk());
    }

    @Test
    void employeeCannotCreateDeploymentWithoutPermission() throws Exception {
        Company company = new Company();
        company.setName("Bravo Co");
        companyRepository.save(company);

        Role employeeRole = new Role();
        employeeRole.setName("EMPLOYEE");
        employeeRole.setLabel("Employee");
        employeeRole.setPermissions(Set.of());
        roleRepository.save(employeeRole);

        User employee = new User();
        employee.setUsername("emp@bravo");
        employee.setEmail("emp@bravo");
        employee.setFullName("Bravo Employee");
        employee.setPasswordHash(passwordEncoder.encode("password"));
        employee.setCompany(company);
        employee.setRoles(Set.of(employeeRole));
        userRepository.save(employee);

        String token = jwtService.generateAccessToken(employee.getUsername(), List.of(employeeRole.getName()));

        mockMvc.perform(post("/api/software/deployments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"softwarePackageId\":1,\"softwareVersionId\":1,\"targetDeviceIds\":[]}"))
                .andExpect(status().isForbidden());
    }
}
