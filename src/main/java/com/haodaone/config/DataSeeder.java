package com.haodaone.config;

import com.haodaone.leave.entity.LeaveType;
import com.haodaone.leave.repository.LeaveTypeRepository;
import com.haodaone.user.entity.Permission;
import com.haodaone.user.entity.Role;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.PermissionRepository;
import com.haodaone.user.repository.RoleRepository;
import com.haodaone.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Boot-time seeder for the platform's baseline RBAC data. Idempotent -
 * checks for existing rows before inserting, so it's safe to run on every
 * startup rather than needing a one-off migration script.
 *
 * Every future module (Employee, Attendance, Leave, ...) should add its own
 * permission codes here (or in its own seeder following this pattern)
 * rather than hardcoding role checks - that's what keeps "Settings > Roles
 * & Permissions" a real, complete picture of what the platform can do.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.admin-username:admin}")
    private String adminUsername;

    @Value("${app.seed.admin-email:admin@haodaone.local}")
    private String adminEmail;

    @Value("${app.seed.admin-password:ChangeMe123!}")
    private String adminPassword;

    public DataSeeder(PermissionRepository permissionRepository, RoleRepository roleRepository,
                       UserRepository userRepository, LeaveTypeRepository leaveTypeRepository,
                       PasswordEncoder passwordEncoder) {
        this.permissionRepository = permissionRepository;
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.leaveTypeRepository = leaveTypeRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedPermissions();
        Role superAdmin = seedRole("SUPER_ADMIN", "Full platform access", allPermissions());
        seedRole("HR_ADMIN", "HR administration - full platform HR management short of user/role administration",
                permissionsByCode("USER_VIEW", "ROLE_VIEW", "AUDIT_VIEW",
                        "EMPLOYEE_VIEW", "EMPLOYEE_CREATE", "EMPLOYEE_MANAGE", "ORG_VIEW", "ORG_MANAGE",
                        "ATTENDANCE_VIEW", "ATTENDANCE_MANAGE", "DEVICE_MANAGE",
                        "LEAVE_APPLY", "LEAVE_VIEW", "LEAVE_APPROVE", "LEAVE_MANAGE",
                        "RECRUITMENT_VIEW", "RECRUITMENT_MANAGE", "PERFORMANCE_VIEW", "PERFORMANCE_MANAGE",
                    "SALARY_VIEW", "SALARY_MANAGE", "REPORTS_VIEW", "MONITORING_VIEW",
                    "SOFTWARE_VIEW", "SOFTWARE_DEPLOY", "SOFTWARE_MANAGE"));
        // Company Admin - company-scoped administrative role. Permissions are the same
        // functional set as HR_ADMIN but this role is intended to be scoped to a
        // single tenant/company (tenant enforcement is enforced server-side).
        seedRole("COMPANY_ADMIN", "Company-level administrator (tenant-scoped)",
                permissionsByCode("USER_VIEW", "EMPLOYEE_VIEW", "EMPLOYEE_CREATE", "EMPLOYEE_MANAGE", "ORG_VIEW", "ORG_MANAGE",
                        "ATTENDANCE_VIEW", "ATTENDANCE_MANAGE", "DEVICE_MANAGE",
                        "LEAVE_APPLY", "LEAVE_VIEW", "LEAVE_APPROVE", "LEAVE_MANAGE",
                        "RECRUITMENT_VIEW", "RECRUITMENT_MANAGE", "PERFORMANCE_VIEW", "PERFORMANCE_MANAGE",
                    "SALARY_VIEW", "SALARY_MANAGE", "REPORTS_VIEW", "MONITORING_VIEW", "MONITORING_MANAGE",
                    "SOFTWARE_VIEW", "SOFTWARE_DEPLOY", "SOFTWARE_MANAGE"));
        seedRole("MANAGER", "Team lead - visibility into their reports, leave approval, and performance management for their team",
                permissionsByCode("EMPLOYEE_VIEW", "ORG_VIEW", "ATTENDANCE_VIEW", "LEAVE_APPLY", "LEAVE_VIEW", "LEAVE_APPROVE",
                        "RECRUITMENT_VIEW", "INTERVIEW_DECISION", "PERFORMANCE_VIEW", "PERFORMANCE_MANAGE", "REPORTS_VIEW"));
        seedRole("EMPLOYEE", "Baseline self-service access - expanded once the ESS module scopes leave/attendance to \"self\"", Set.of());

        seedSuperAdminUser(superAdmin);
        seedDefaultLeaveTypes();
    }

    private void seedDefaultLeaveTypes() {
        if (leaveTypeRepository.count() > 0) {
            return;
        }
        seedLeaveType("Casual Leave", "CL", 12, false);
        seedLeaveType("Sick Leave", "SL", 10, false);
        seedLeaveType("Earned Leave", "EL", 15, true);
        log.info("Seeded 3 default leave types (CL/SL/EL). Adjust or add more via Settings > Leave Types.");
    }

    private void seedLeaveType(String name, String code, double daysPerYear, boolean carryForward) {
        LeaveType type = new LeaveType();
        type.setName(name);
        type.setCode(code);
        type.setDefaultDaysPerYear(daysPerYear);
        type.setCarryForward(carryForward);
        leaveTypeRepository.save(type);
    }

    private void seedPermissions() {
        List<String[]> permissions = List.of(
                new String[]{"USER_VIEW", "View user accounts", "User Management"},
                new String[]{"USER_CREATE", "Create user accounts", "User Management"},
                new String[]{"USER_MANAGE", "Activate, deactivate, and reassign roles for user accounts", "User Management"},
                new String[]{"ROLE_VIEW", "View roles and permissions", "Role Management"},
                new String[]{"ROLE_MANAGE", "Create roles and assign permissions", "Role Management"},
                new String[]{"AUDIT_VIEW", "View audit logs and login history", "Security"},
                new String[]{"EMPLOYEE_VIEW", "View employee profiles and the org directory", "Employee Management"},
                new String[]{"EMPLOYEE_CREATE", "Onboard new employees", "Employee Management"},
                new String[]{"EMPLOYEE_MANAGE", "Edit employee profiles and change employment status", "Employee Management"},
                new String[]{"ORG_VIEW", "View departments, designations, and teams", "Organization"},
                new String[]{"ORG_MANAGE", "Create and edit departments, designations, and teams", "Organization"},
                new String[]{"ATTENDANCE_VIEW", "View live and historical attendance", "Attendance"},
                new String[]{"ATTENDANCE_MANAGE", "Resolve unmapped punches and manage corrections", "Attendance"},
                new String[]{"DEVICE_MANAGE", "View and rename biometric devices", "Attendance"},
                new String[]{"LEAVE_APPLY", "Apply for leave on behalf of employees and view leave balances", "Leave"},
                new String[]{"LEAVE_VIEW", "View all leave requests and the team leave calendar", "Leave"},
                new String[]{"LEAVE_APPROVE", "Approve or reject leave requests", "Leave"},
                new String[]{"LEAVE_MANAGE", "Manage leave types and the holiday calendar", "Leave"},
                new String[]{"RECRUITMENT_VIEW", "View job openings, candidates, and interviews", "Recruitment"},
                new String[]{"RECRUITMENT_MANAGE", "Manage job openings, candidate pipeline, and interviews", "Recruitment"},
                new String[]{"INTERVIEW_DECISION", "Submit ratings and a decision for interview rounds assigned to you", "Recruitment"},
                new String[]{"PERFORMANCE_VIEW", "View goals and performance reviews", "Performance"},
                new String[]{"PERFORMANCE_MANAGE", "Set goals and conduct performance reviews", "Performance"},
                new String[]{"SALARY_VIEW", "View salary structures, employee salary details, payroll runs, and the payroll dashboard", "Payroll"},
                new String[]{"SALARY_MANAGE", "Define salary structures and create, process, or cancel payroll runs", "Payroll"},
                new String[]{"REPORTS_VIEW", "View executive, attendance, leave, and recruitment reports", "Reports"},
                new String[]{"MONITORING_VIEW", "View monitored devices and employee activity sessions", "Monitoring"},
                new String[]{"MONITORING_MANAGE", "Enroll/decommission monitored devices, rotate agent tokens, and push directives", "Monitoring"},
                new String[]{"SOFTWARE_VIEW", "View software catalog, versions, and deployment history", "Software"},
                new String[]{"SOFTWARE_DEPLOY", "Create and queue software deployments to managed devices", "Software"},
                new String[]{"SOFTWARE_MANAGE", "Create, edit, and control software packages and installer versions", "Software"}
        );

        for (String[] p : permissions) {
            if (permissionRepository.findByCode(p[0]).isEmpty()) {
                Permission permission = new Permission();
                permission.setCode(p[0]);
                permission.setDescription(p[1]);
                permission.setModule(p[2]);
                permissionRepository.save(permission);
            }
        }
    }

    private Role seedRole(String name, String description, Set<Permission> permissions) {
        return roleRepository.findByName(name)
                .map(existing -> syncSystemRolePermissions(existing, permissions))
                .orElseGet(() -> {
                    Role role = new Role();
                    role.setName(name);
                    role.setLabel(name);
                    role.setDescription(description);
                    role.setSystemDefined(true);
                    role.setPermissions(permissions);
                    Role saved = roleRepository.save(role);
                    log.info("Seeded system role '{}' with {} permission(s)", name, permissions.size());
                    return saved;
                });
    }

    /**
     * System roles are code-defined, not admin-defined - so unlike a
     * custom role (where we'd never silently change what an admin
     * configured), it's correct to union in any permission codes this
     * version of the seeder expects but an older run didn't have yet.
     * This is what makes upgrading from Phase 0 -> Phase 1 (or any later
     * phase that adds permissions) work without a manual migration step.
     */
    private Role syncSystemRolePermissions(Role role, Set<Permission> expectedPermissions) {
        if (!role.isSystemDefined()) {
            return role;
        }
        Set<Permission> merged = new HashSet<>(role.getPermissions());
        int before = merged.size();
        merged.addAll(expectedPermissions);
        if (merged.size() != before) {
            role.setPermissions(merged);
            role = roleRepository.save(role);
            log.info("Synced {} newly-available permission(s) onto system role '{}'", merged.size() - before, role.getName());
        }
        return role;
    }

    private void seedSuperAdminUser(Role superAdminRole) {
        if (userRepository.existsByUsername(adminUsername)) {
            return;
        }

        User admin = new User();
        admin.setUsername(adminUsername);
        admin.setEmail(adminEmail);
        admin.setFullName("System Administrator");
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        admin.setActive(true);
        admin.setMustChangePassword(true);
        admin.setRoles(new HashSet<>(Set.of(superAdminRole)));
        userRepository.save(admin);

        log.warn("==================================================================");
        log.warn("Seeded default super admin account - CHANGE THIS PASSWORD IMMEDIATELY");
        log.warn("  username: {}", adminUsername);
        log.warn("  password: {}", adminPassword);
        log.warn("Override via APP_SEED_ADMIN_USERNAME / APP_SEED_ADMIN_PASSWORD env vars before deploying anywhere real.");
        log.warn("==================================================================");
    }

    private Set<Permission> allPermissions() {
        return new HashSet<>(permissionRepository.findAllByDeletedFalse());
    }

    private Set<Permission> permissionsByCode(String... codes) {
        Set<Permission> result = new HashSet<>();
        for (String code : codes) {
            permissionRepository.findByCode(code).ifPresent(result::add);
        }
        return result;
    }
}
