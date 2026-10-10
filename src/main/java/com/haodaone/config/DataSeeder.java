
package com.haodaone.config;

import com.haodaone.leave.entity.LeaveType;
import com.haodaone.leave.repository.LeaveTypeRepository;
import com.haodaone.user.entity.Permission;
import com.haodaone.user.entity.Role;
import com.haodaone.user.entity.User;
import com.haodaone.user.entity.PermissionScope;
import com.haodaone.user.entity.RolePermissionScope;
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
        Role superAdmin =         seedRole("SUPER_ADMIN", "Full platform access", allPermissions());
        seedRole("HR_ADMIN", "HR administration - full platform HR management short of user/role administration",
                permissionsByCode(hrManagerPermissionCodes().toArray(String[]::new)));
        // Workspace administration remains separate from HR, payroll, monitoring, and IT access.
        seedRole("COMPANY_ADMIN", "Company-level administrator (tenant-scoped)",
                permissionsByCode(companyAdminPermissionCodes().toArray(String[]::new)));
        seedRole("MANAGER", "Team lead - visibility into their reports, leave approval, and performance management for their team",
                permissionsByCode(managerPermissionCodes().toArray(String[]::new)));
        Role employee = seedRole("EMPLOYEE", "Baseline self-service access", permissionsByCode(
            "SELF_PROFILE_VIEW", "SELF_ATTENDANCE_VIEW", "SELF_ATTENDANCE_CHECKIN", "SELF_ATTENDANCE_CHECKOUT",
            "SELF_LEAVE_VIEW", "SELF_LEAVE_APPLY", "SELF_DOCUMENT_VIEW", "SELF_PAYSLIP_VIEW", "SELF_ASSET_VIEW"));
        Role hrExecutive = seedRole("HR_EXECUTIVE", "HR operations without role administration, payroll management, or IT access",
                permissionsByCode("EMPLOYEE_VIEW", "EMPLOYEE_CREATE", "EMPLOYEE_MANAGE", "ORG_VIEW",
                        "ATTENDANCE_VIEW", "LEAVE_VIEW", "LEAVE_APPROVE", "LEAVE_MANAGE",
                        "RECRUITMENT_VIEW", "RECRUITMENT_MANAGE", "PERFORMANCE_VIEW", "PERFORMANCE_MANAGE",
                        "SELF_PROFILE_VIEW", "SELF_ATTENDANCE_VIEW", "SELF_ATTENDANCE_CHECKIN", "SELF_ATTENDANCE_CHECKOUT",
                        "SELF_LEAVE_VIEW", "SELF_LEAVE_APPLY", "SELF_DOCUMENT_VIEW", "SELF_PAYSLIP_VIEW", "SELF_ASSET_VIEW"));
        Role hrCoordinator = seedRole("HR_COORDINATOR", "Basic HR coordination without approval, role administration, or IT access",
                permissionsByCode("EMPLOYEE_VIEW", "ORG_VIEW", "ATTENDANCE_VIEW", "LEAVE_VIEW", "RECRUITMENT_VIEW",
                        "SELF_PROFILE_VIEW", "SELF_ATTENDANCE_VIEW", "SELF_ATTENDANCE_CHECKIN", "SELF_ATTENDANCE_CHECKOUT",
                        "SELF_LEAVE_VIEW", "SELF_LEAVE_APPLY", "SELF_DOCUMENT_VIEW", "SELF_PAYSLIP_VIEW", "SELF_ASSET_VIEW"));
        Role departmentManager = seedRole("DEPARTMENT_MANAGER", "Department-scoped people and workflow management",
                permissionsByCode("EMPLOYEE_VIEW", "ATTENDANCE_VIEW", "LEAVE_VIEW", "LEAVE_APPROVE",
                        "PERFORMANCE_VIEW", "PERFORMANCE_MANAGE",
                        "SELF_PROFILE_VIEW", "SELF_ATTENDANCE_VIEW", "SELF_ATTENDANCE_CHECKIN", "SELF_ATTENDANCE_CHECKOUT",
                        "SELF_LEAVE_VIEW", "SELF_LEAVE_APPLY", "SELF_DOCUMENT_VIEW", "SELF_PAYSLIP_VIEW", "SELF_ASSET_VIEW"));
        Role itAdministrator = seedRole("IT_ADMINISTRATOR", "IT operations for managed devices, monitoring, and software",
                permissionsByCode(itAdministratorPermissionCodes().toArray(String[]::new)));

        syncRoleScopes(superAdmin, PermissionScope.ORGANIZATION);
        syncRoleScopes(employee, PermissionScope.SELF);
        syncRoleScopes(roleRepository.findByName("MANAGER").orElseThrow(), PermissionScope.TEAM);
        syncRoleScopes(roleRepository.findByName("HR_ADMIN").orElseThrow(), PermissionScope.ORGANIZATION);
        Role companyAdmin = roleRepository.findByName("COMPANY_ADMIN").orElseThrow();
        syncRoleScopes(companyAdmin, PermissionScope.ORGANIZATION);
        syncRoleScopes(hrExecutive, PermissionScope.ORGANIZATION);
        syncRoleScopes(hrCoordinator, PermissionScope.ORGANIZATION);
        syncRoleScopes(departmentManager, PermissionScope.DEPARTMENT);
        syncRoleScopes(itAdministrator, PermissionScope.ORGANIZATION);

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
                new String[]{"USER_PERMISSION_GRANT", "Grant and revoke user-specific permissions within authorized scopes", "User Management"},
                new String[]{"ROLE_ASSIGN", "Assign roles to user accounts", "Role Management"},
                new String[]{"ROLE_VIEW", "View roles and permissions", "Role Management"},
                new String[]{"ROLE_MANAGE", "Create roles and assign permissions", "Role Management"},
                new String[]{"AUDIT_VIEW", "View audit logs and login history", "Security"},
                new String[]{"EMPLOYEE_VIEW", "View employee profiles and the org directory", "Employee Management"},
                new String[]{"EMPLOYEE_CREATE", "Onboard new employees", "Employee Management"},
                new String[]{"EMPLOYEE_IMPORT", "Import employee records in bulk for the organization", "Organization"},
                new String[]{"EMPLOYEE_MANAGE", "Edit employee profiles and change employment status", "Employee Management"},
                new String[]{"ORG_VIEW", "View departments, designations, and teams", "Organization"},
                new String[]{"ORG_MANAGE", "Create and edit departments, designations, and teams", "Organization"},
                new String[]{"REQUIREMENT_VIEW", "View business requirements", "Requirements"},
                new String[]{"REQUIREMENT_MANAGE", "Create and manage business requirements", "Requirements"},
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
                new String[]{"MONITORING_MANAGE", "Manage monitored-device records and agent operations", "Monitoring"},
                new String[]{"REMOTE_SUPPORT_MANAGE", "Start remote support and remote desktop operations", "IT Management"},
                new String[]{"IT_MANAGEMENT_ACCESS", "Access IT Management tools for devices and software", "IT Management"},
                new String[]{"IT_DEVICE_VIEW", "View IT device inventory and details", "IT Management"},
                new String[]{"IT_DEVICE_ENROLL", "Enroll and revoke IT device enrollments", "IT Management"},
                new String[]{"IT_DEVICE_MAPPING_MANAGE", "Assign IT devices to employees", "IT Management"},
                new String[]{"SOFTWARE_VIEW", "View software catalog, versions, and deployment history", "Software"},
                new String[]{"SOFTWARE_DEPLOY", "Create and queue software deployments to managed devices", "Software"},
                new String[]{"SOFTWARE_MANAGE", "Create, edit, and control software packages and installer versions", "Software"}
        );

            permissions = new java.util.ArrayList<>(permissions);
            permissions.addAll(List.of(
                new String[]{"SELF_PROFILE_VIEW", "View your own employee profile", "Self Service"},
                new String[]{"SELF_ATTENDANCE_VIEW", "View your own attendance", "Self Service"},
                new String[]{"SELF_ATTENDANCE_CHECKIN", "Check in for yourself", "Self Service"},
                new String[]{"SELF_ATTENDANCE_CHECKOUT", "Check out for yourself", "Self Service"},
                new String[]{"SELF_LEAVE_VIEW", "View your own leave", "Self Service"},
                new String[]{"SELF_LEAVE_APPLY", "Apply for your own leave", "Self Service"},
                new String[]{"SELF_DOCUMENT_VIEW", "View your own documents", "Self Service"},
                new String[]{"SELF_PAYSLIP_VIEW", "View your own payslips", "Self Service"},
                new String[]{"SELF_ASSET_VIEW", "View your own assets", "Self Service"}
            ));

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
                .map(existing -> {
                    // Reserved role names may predate the system-defined flag.
                    // Reconcile them with the canonical role policy on startup.
                    existing.setSystemDefined(true);
                    existing.setLabel(roleLabel(name));
                    existing.setDescription(description);
                    return syncSystemRolePermissions(existing, permissions);
                })
                .orElseGet(() -> {
                    Role role = new Role();
                    role.setName(name);
                    role.setLabel(roleLabel(name));
                    role.setDescription(description);
                    role.setSystemDefined(true);
                    role.setPermissions(permissions);
                    Role saved = roleRepository.save(role);
                    log.info("Seeded system role '{}' with {} permission(s)", name, permissions.size());
                    return saved;
                });
    }

    private String roleLabel(String name) {
        return switch (name) {
            case "COMPANY_ADMIN" -> "Organization Administrator";
            case "HR_ADMIN" -> "HR Manager";
            case "MANAGER" -> "Team Lead";
            case "EMPLOYEE" -> "Employee";
            case "SUPER_ADMIN" -> "Platform Administrator";
            case "HR_EXECUTIVE" -> "HR Executive";
            case "HR_COORDINATOR" -> "HR Coordinator";
            case "DEPARTMENT_MANAGER" -> "Department Manager";
            case "IT_ADMINISTRATOR" -> "IT Administrator";
            default -> name;
        };
    }

    private Role syncSystemRolePermissions(Role role, Set<Permission> expectedPermissions) {
        if (!role.isSystemDefined()) {
            return role;
        }
        Set<String> currentCodes = role.getPermissions().stream().map(Permission::getCode)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> expectedCodes = expectedPermissions.stream().map(Permission::getCode)
                .collect(java.util.stream.Collectors.toSet());
        if (!currentCodes.equals(expectedCodes)) {
            role.setPermissions(new HashSet<>(expectedPermissions));
            role = roleRepository.save(role);
            log.info("Synchronized permissions for system role '{}'", role.getName());
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

    private void syncRoleScopes(Role role, PermissionScope defaultScope) {
        if (!role.isSystemDefined()) {
            return;
        }
        Set<String> permissionCodes = role.getPermissions().stream()
                .map(Permission::getCode)
                .collect(java.util.stream.Collectors.toSet());
        role.getPermissionScopes().removeIf(scope -> !permissionCodes.contains(scope.getPermission().getCode()));
        for (Permission permission : role.getPermissions()) {
            RolePermissionScope scope = role.getPermissionScopes().stream()
                    .filter(existing -> permission.getCode().equals(existing.getPermission().getCode()))
                    .findFirst()
                    .orElseGet(() -> {
                        RolePermissionScope created = new RolePermissionScope();
                        created.setRole(role);
                        created.setPermission(permission);
                        role.getPermissionScopes().add(created);
                        return created;
                    });
            scope.setScope(role.getName().equals("COMPANY_ADMIN")
                    ? companyAdminScope(permission.getCode())
                    : defaultScope);
        }
        roleRepository.save(role);
    }

    private Set<Permission> permissionsByCode(String... codes) {
        Set<Permission> result = new HashSet<>();
        for (String code : codes) {
            permissionRepository.findByCode(code).ifPresent(result::add);
        }
        return result;
    }

    static Set<String> companyAdminPermissionCodes() {
        return Set.of("USER_VIEW", "USER_CREATE", "USER_MANAGE", "USER_PERMISSION_GRANT",
                "ROLE_VIEW", "ROLE_ASSIGN", "ROLE_MANAGE", "ORG_VIEW", "ORG_MANAGE",
                "SELF_PROFILE_VIEW", "SELF_ATTENDANCE_VIEW", "SELF_ATTENDANCE_CHECKIN", "SELF_ATTENDANCE_CHECKOUT",
                "SELF_LEAVE_VIEW", "SELF_LEAVE_APPLY", "SELF_DOCUMENT_VIEW", "SELF_PAYSLIP_VIEW", "SELF_ASSET_VIEW");
    }

    static PermissionScope companyAdminScope(String permissionCode) {
        return switch (permissionCode) {
            case "SELF_PROFILE_VIEW", "SELF_ATTENDANCE_VIEW", "SELF_ATTENDANCE_CHECKIN", "SELF_ATTENDANCE_CHECKOUT",
                    "SELF_LEAVE_VIEW", "SELF_LEAVE_APPLY", "SELF_DOCUMENT_VIEW", "SELF_PAYSLIP_VIEW", "SELF_ASSET_VIEW" ->
                    PermissionScope.SELF;
            default -> PermissionScope.ORGANIZATION;
        };
    }

    static Set<String> managerPermissionCodes() {
        return Set.of("EMPLOYEE_VIEW", "ATTENDANCE_VIEW", "LEAVE_APPLY", "LEAVE_VIEW", "LEAVE_APPROVE",
                "INTERVIEW_DECISION", "PERFORMANCE_VIEW", "PERFORMANCE_MANAGE",
                "SELF_PROFILE_VIEW", "SELF_ATTENDANCE_VIEW", "SELF_ATTENDANCE_CHECKIN", "SELF_ATTENDANCE_CHECKOUT",
                "SELF_LEAVE_VIEW", "SELF_LEAVE_APPLY", "SELF_DOCUMENT_VIEW", "SELF_PAYSLIP_VIEW", "SELF_ASSET_VIEW");
    }

    static Set<String> hrManagerPermissionCodes() {
        return Set.of("EMPLOYEE_VIEW", "EMPLOYEE_CREATE", "EMPLOYEE_MANAGE", "ORG_VIEW",
                "ATTENDANCE_VIEW", "ATTENDANCE_MANAGE",
                "LEAVE_APPLY", "LEAVE_VIEW", "LEAVE_APPROVE", "LEAVE_MANAGE",
                "RECRUITMENT_VIEW", "RECRUITMENT_MANAGE", "PERFORMANCE_VIEW", "PERFORMANCE_MANAGE",
                "SALARY_VIEW", "SALARY_MANAGE", "REPORTS_VIEW",
                "SELF_PROFILE_VIEW", "SELF_ATTENDANCE_VIEW", "SELF_ATTENDANCE_CHECKIN", "SELF_ATTENDANCE_CHECKOUT",
                "SELF_LEAVE_VIEW", "SELF_LEAVE_APPLY", "SELF_DOCUMENT_VIEW", "SELF_PAYSLIP_VIEW", "SELF_ASSET_VIEW");
    }

    static Set<String> itAdministratorPermissionCodes() {
        return Set.of("IT_MANAGEMENT_ACCESS", "IT_DEVICE_VIEW", "IT_DEVICE_ENROLL",
                "IT_DEVICE_MAPPING_MANAGE", "MONITORING_VIEW", "MONITORING_MANAGE", "REMOTE_SUPPORT_MANAGE",
                "DEVICE_MANAGE", "SOFTWARE_VIEW", "SOFTWARE_DEPLOY", "SOFTWARE_MANAGE");
    }
}
