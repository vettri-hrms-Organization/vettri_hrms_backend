package com.haodaone.leave.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.leave.dto.ApplyLeaveRequest;
import com.haodaone.leave.entity.LeaveType;
import com.haodaone.leave.repository.LeaveTypeRepository;
import com.haodaone.security.JwtService;
import com.haodaone.tenant.TenantContext;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LeaveModuleVerificationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private LeaveTypeRepository leaveTypeRepository;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void employee_self_service_can_view_own_leave_and_apply() throws Exception {
        Company company = createCompany("Self Service Ltd");
        Role employeeRole = ensureRole("EMPLOYEE", "SELF_LEAVE_VIEW", "SELF_LEAVE_APPLY");
        User user = createUser(company, employeeRole, "employee-self@acme.test");
        Employee employee = createEmployee(company, user, "EMP-SELF-001", "Asha", "Patel");
        LeaveType leaveType = createLeaveType(company, "Casual Leave", "CASUAL", 12.0);

        String token = jwtService.generateAccessToken(user.getUsername(), List.of(employeeRole.getName()));

        mockMvc.perform(get("/api/leave-types")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/leave-requests/employee/{employeeId}", employee.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/leave-requests/employee/{employeeId}/balance", employee.getId())
                        .param("year", "2025")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        ApplyLeaveRequest request = new ApplyLeaveRequest();
        request.setEmployeeId(employee.getId());
        request.setLeaveTypeId(leaveType.getId());
        request.setStartDate(LocalDate.of(2025, 7, 10));
        request.setEndDate(LocalDate.of(2025, 7, 12));
        request.setReason("Family commitment");

        mockMvc.perform(post("/api/leave-requests")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    void hr_admin_company_admin_and_manager_roles_can_view_pending_leave_and_apply_for_self() throws Exception {
        Company company = createCompany("Admin Leave Ltd");
        for (String roleName : List.of("HR_ADMIN", "COMPANY_ADMIN", "MANAGER")) {
            Role role = ensureRole(roleName, "LEAVE_VIEW", "LEAVE_APPLY");
            User user = createUser(company, role, roleName.toLowerCase() + "@acme.test");
            Employee employee = createEmployee(company, user, roleName.substring(0, Math.min(5, roleName.length())) + "-001", roleName, "Admin");
            LeaveType leaveType = createLeaveType(company, "Medical Leave", roleName.toLowerCase().replace("_", "") + "leave", 18.0);

            String token = jwtService.generateAccessToken(user.getUsername(), List.of(role.getName()));

            mockMvc.perform(get("/api/leave-requests")
                            .param("status", "PENDING")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/api/leave-types")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());

            ApplyLeaveRequest request = new ApplyLeaveRequest();
            request.setEmployeeId(employee.getId());
            request.setLeaveTypeId(leaveType.getId());
            request.setStartDate(LocalDate.of(2025, 8, 4));
            request.setEndDate(LocalDate.of(2025, 8, 6));
            request.setReason("Personal wellness");

            mockMvc.perform(post("/api/leave-requests")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }
    }

    private Company createCompany(String name) {
        Company company = new Company();
        company.setName(name);
        return companyRepository.save(company);
    }

    private User createUser(Company company, Role role, String username) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(username);
        user.setFullName(username);
        user.setPasswordHash(passwordEncoder.encode("password"));
        user.setCompany(company);
        user.setRoles(Set.of(role));
        return userRepository.save(user);
    }

    private Employee createEmployee(Company company, User user, String code, String firstName, String lastName) {
        Employee employee = new Employee();
        employee.setEmployeeCode(code);
        employee.setFirstName(firstName);
        employee.setLastName(lastName);
        employee.setEmail(user.getEmail());
        employee.setDateOfJoining(LocalDate.now());
        employee.setCompany(company);
        employee.setUser(user);
        employee.setStatus("Active");
        return employeeRepository.save(employee);
    }

    private LeaveType createLeaveType(Company company, String name, String code, double defaultDays) {
        LeaveType leaveType = new LeaveType();
        leaveType.setCompany(company);
        leaveType.setName(name);
        leaveType.setCode(code);
        leaveType.setDefaultDaysPerYear(defaultDays);
        leaveType.setActive(true);
        leaveType.setAutoApprove(false);
        return leaveTypeRepository.save(leaveType);
    }

    private Role ensureRole(String name, String... permissionCodes) {
        Role role = roleRepository.findByName(name).orElseGet(() -> {
            Role newRole = new Role();
            newRole.setName(name);
            newRole.setLabel(name.replace("_", " "));
            return roleRepository.save(newRole);
        });

        Set<Permission> permissions = Set.of(permissionCodes).stream()
                .map(code -> permissionRepository.findByCode(code)
                        .orElseGet(() -> {
                            Permission permission = new Permission();
                            permission.setCode(code);
                            permission.setDescription(code + " permission");
                            permission.setModule("Leave");
                            return permissionRepository.save(permission);
                        }))
                .collect(java.util.stream.Collectors.toSet());

        role.setPermissions(permissions);
        return roleRepository.save(role);
    }
}
