package com.haodaone.security;

import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.monitoring.repository.MonitoredDeviceRepository;
import com.haodaone.user.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Helper bean used from @PreAuthorize SpEL expressions to centralize
 * platform-vs-company-level authorization checks.
 */
@Component("companySecurity")
public class CompanySecurity {

    private final EmployeeRepository employeeRepository;
    private final MonitoredDeviceRepository monitoredDeviceRepository;
    private final UserRepository userRepository;
    private final AuthorizationService authorizationService;

    @Autowired
    public CompanySecurity(EmployeeRepository employeeRepository,
                           MonitoredDeviceRepository monitoredDeviceRepository,
                           UserRepository userRepository,
                           AuthorizationService authorizationService) {
        this.employeeRepository = employeeRepository;
        this.monitoredDeviceRepository = monitoredDeviceRepository;
        this.userRepository = userRepository;
        this.authorizationService = authorizationService;
    }

    public CompanySecurity(EmployeeRepository employeeRepository,
                           MonitoredDeviceRepository monitoredDeviceRepository,
                           UserRepository userRepository) {
        this(employeeRepository, monitoredDeviceRepository, userRepository, null);
    }

    private Authentication auth() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private Set<String> currentRoles() {
        Authentication a = auth();
        if (a == null) return Set.of();
        return a.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }

    /** True when the current login is the platform-level super admin. */
    public boolean isSuperAdmin() {
        return currentRoles().contains("ROLE_SUPER_ADMIN") || currentRoles().contains("SUPER_ADMIN");
    }

    private Optional<com.haodaone.user.entity.User> currentUser() {
        Authentication a = auth();
        if (a == null) return Optional.empty();
        String username = a.getName();
        return userRepository.findByUsernameAndDeletedFalse(username);
    }

    private Optional<Long> currentCompanyId() {
        return currentUser().map(u -> u.getCompany() == null ? null : u.getCompany().getId()).map(Optional::ofNullable).orElse(Optional.empty());
    }

    public boolean isCompanyAdmin() {
        return currentRoles().contains("ROLE_COMPANY_ADMIN") || currentRoles().contains("COMPANY_ADMIN");
    }

    public boolean isHrAdmin() {
        return currentRoles().contains("ROLE_HR_ADMIN") || currentRoles().contains("HR_ADMIN");
    }

    /** True if the caller can create employees for their current tenant/company. */
    public boolean canCreateEmployee() {
        if (isSuperAdmin()) return true;
        if (!currentCompanyId().isPresent()) return false;
        return isCompanyAdmin() || isHrAdmin();
    }

    /** True if the caller can perform organization-level bulk employee import. */
    public boolean canImportEmployees() {
        if (isSuperAdmin()) return true;
        if (!currentCompanyId().isPresent()) return false;
        return currentRoles().contains("EMPLOYEE_IMPORT");
    }

    /** True if caller can manage the given employee id (company scope or super admin). */
    public boolean canManageEmployee(Long employeeId) {
        if (employeeId == null) return false;
        if (isSuperAdmin()) return true;
        if (!(isCompanyAdmin() || isHrAdmin())) return false;
        Optional<Long> myCompany = currentCompanyId();
        if (myCompany.isEmpty()) return false;
        return employeeRepository.findById(employeeId)
            .map(emp -> emp.getCompany() != null
                && myCompany.get().equals(emp.getCompany().getId())
                && (emp.getUser() == null || emp.getUser().getRoles().stream()
                .noneMatch(role -> "SUPER_ADMIN".equals(role.getName())
                    || "ROLE_SUPER_ADMIN".equals(role.getName()))))
                .orElse(false);
    }

    public boolean canManageDevice(Long deviceId) {
        if (deviceId == null) return false;
        if (isSuperAdmin()) return true;
        Optional<Long> myCompany = currentCompanyId();
        if (myCompany.isEmpty()) return false;
        return monitoredDeviceRepository.findByIdAndCompany_IdAndDeletedFalse(deviceId, myCompany.get())
                .filter(device -> device.getEmployee() == null
                        ? authorizationService != null && authorizationService.hasOrganizationScope("MONITORING_MANAGE")
                        : authorizationService != null && authorizationService.isAllowed("MONITORING_MANAGE", "EMPLOYEE", device.getEmployee().getId()))
                .isPresent();
    }

    /** True if caller can view the given device (same-company or super admin). */
    public boolean canViewDevice(Long deviceId) {
        if (deviceId == null) return false;
        if (isSuperAdmin()) return true;
        Optional<Long> myCompany = currentCompanyId();
        if (myCompany.isEmpty()) return false;
        return monitoredDeviceRepository.findByIdAndCompany_IdAndDeletedFalse(deviceId, myCompany.get())
                .filter(device -> device.getEmployee() == null
                        ? authorizationService != null && authorizationService.hasOrganizationScope("MONITORING_VIEW")
                        : authorizationService != null && authorizationService.isAllowed("MONITORING_VIEW", "EMPLOYEE", device.getEmployee().getId()))
                .isPresent();
    }

    /** True if caller can manage users (role assignments / activation) for the given user id. */
    public boolean canManageUser(Long userId) {
        if (userId == null) return false;
        if (isSuperAdmin()) return true;
        // company admins may manage users belonging to their company
        if (!isCompanyAdmin()) return false;
        Optional<Long> myCompany = currentCompanyId();
        if (myCompany.isEmpty()) return false;
        return userRepository.findById(userId)
                .map(u -> u.getCompany() != null && myCompany.get().equals(u.getCompany().getId()))
                .orElse(false);
    }
}
