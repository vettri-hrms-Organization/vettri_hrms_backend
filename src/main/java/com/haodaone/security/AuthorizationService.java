package com.haodaone.security;

import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.leave.repository.LeaveRequestRepository;
import com.haodaone.attendance.repository.WfhRequestRepository;
import com.haodaone.tenant.TenantContext;
import com.haodaone.user.entity.PermissionScope;
import com.haodaone.user.entity.RolePermissionScope;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;
import java.util.HashSet;
import java.util.Optional;
import java.util.stream.Collectors;

@Service("authorizationService")
public class AuthorizationService {
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final WfhRequestRepository wfhRequestRepository;

    public AuthorizationService(UserRepository userRepository, EmployeeRepository employeeRepository,
                               LeaveRequestRepository leaveRequestRepository,
                               WfhRequestRepository wfhRequestRepository) {
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.wfhRequestRepository = wfhRequestRepository;
    }

    @Transactional(readOnly = true)
    public boolean isAllowed(String permissionCode, String resourceType, Long resourceId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || !isAccountActive(authentication)) {
            return false;
        }

        Long tenantId = TenantContext.getCurrentTenant();
        User user = currentUser(authentication);
        if (user == null || user.getCompany() == null || tenantId == null || !tenantId.equals(user.getCompany().getId())) {
            return isSuperAdmin(authentication) && tenantId != null;
        }
        if (!hasAuthority(authentication, permissionCode)) {
            return false;
        }

        Set<PermissionScope> scopes = user.getRoles().stream()
                .flatMap(role -> role.getPermissionScopes().stream())
                .filter(scope -> permissionCode.equals(scope.getPermission().getCode()))
                .filter(this::currentlyValid)
                .map(RolePermissionScope::getScope)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(PermissionScope.class)));
        if (scopes.isEmpty()) {
            scopes = EnumSet.of(PermissionScope.ORGANIZATION);
        }
        if (resourceId == null || resourceType == null) {
            return true;
        }
        if (!"EMPLOYEE".equalsIgnoreCase(resourceType)) {
            return scopes.contains(PermissionScope.ORGANIZATION);
        }

        Employee target = employeeRepository.findByIdAndCompany_IdAndDeletedFalse(resourceId, tenantId).orElse(null);
        if (target == null) return false;
        Employee current = employeeRepository.findByUser_IdAndDeletedFalse(user.getId()).orElse(null);
        if (current == null) return false;

        if (scopes.contains(PermissionScope.ORGANIZATION)) return true;
        if (scopes.contains(PermissionScope.SELF) && current.getId().equals(target.getId())) return true;
        if (scopes.contains(PermissionScope.TEAM)
                && target.getReportingManager() != null
                && current.getId().equals(target.getReportingManager().getId())) return true;
        return scopes.contains(PermissionScope.DEPARTMENT)
                && current.getDepartment() != null
                && target.getDepartment() != null
                && current.getDepartment().getId().equals(target.getDepartment().getId());
    }

    @Transactional(readOnly = true)
    public Set<PermissionScope> getScopes(String permissionCode) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !isAccountActive(authentication)) return Set.of();
        User user = currentUser(authentication);
        if (user == null) return Set.of();
        Set<PermissionScope> scopes = user.getRoles().stream()
                .flatMap(role -> role.getPermissionScopes().stream())
                .filter(scope -> permissionCode.equals(scope.getPermission().getCode()))
                .filter(this::currentlyValid)
                .map(RolePermissionScope::getScope)
                .collect(Collectors.toSet());
        return scopes.isEmpty() && hasAuthority(authentication, permissionCode)
                ? Set.of(PermissionScope.ORGANIZATION) : scopes;
    }

    /**
     * Returns permitted employee IDs for list queries. An empty Optional means
     * organization scope; a present empty set means the user has no visible
     * employees. The caller must still include its company predicate.
     */
    @Transactional(readOnly = true)
    public Optional<Set<Long>> resolveEmployeeIds(String permissionCode) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long companyId = TenantContext.getCurrentTenant();
        if (authentication == null || companyId == null || !isAccountActive(authentication)) return Optional.of(Set.of());
        User user = currentUser(authentication);
        if (user == null || user.getCompany() == null || !companyId.equals(user.getCompany().getId())) {
            return isSuperAdmin(authentication) ? Optional.empty() : Optional.of(Set.of());
        }
        Set<PermissionScope> scopes = getScopes(permissionCode);
        if (scopes.contains(PermissionScope.ORGANIZATION)) return Optional.empty();
        Employee current = employeeRepository.findByUser_IdAndDeletedFalse(user.getId()).orElse(null);
        if (current == null) return Optional.of(Set.of());
        Set<Long> ids = new HashSet<>();
        if (scopes.contains(PermissionScope.SELF)) ids.add(current.getId());
        if (scopes.contains(PermissionScope.TEAM)) ids.addAll(employeeRepository.findIdsByCompanyAndReportingManager(companyId, current.getId()));
        if (scopes.contains(PermissionScope.DEPARTMENT) && current.getDepartment() != null) {
            ids.addAll(employeeRepository.findIdsByCompanyAndDepartment(companyId, current.getDepartment().getId()));
        }
        return Optional.of(ids);
    }

    public Optional<Set<Long>> resolveEmployeeIdsForAny(String... permissionCodes) {
        Optional<Set<Long>> resolved = Optional.of(Set.of());
        for (String permissionCode : permissionCodes) {
            if (hasAuthority(SecurityContextHolder.getContext().getAuthentication(), permissionCode)) {
                Optional<Set<Long>> current = resolveEmployeeIds(permissionCode);
                if (current.isEmpty()) return Optional.empty();
                Set<Long> merged = new HashSet<>(resolved.orElse(Set.of()));
                merged.addAll(current.orElse(Set.of()));
                resolved = Optional.of(merged);
            }
        }
        return resolved;
    }

    public boolean hasOrganizationScope(String permissionCode) {
        return getScopes(permissionCode).contains(PermissionScope.ORGANIZATION);
    }

    @Transactional(readOnly = true)
    public boolean isAllowedLeaveRequest(String permissionCode, Long leaveRequestId) {
        Long tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) return false;
        return leaveRequestRepository.findByIdAndCompany_Id(leaveRequestId, tenantId)
                .map(request -> isAllowed(permissionCode, "EMPLOYEE", request.getEmployee().getId()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean isAllowedWfhRequest(Long requestId) {
        Long tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) return false;
        return wfhRequestRepository.findByIdAndCompany_IdAndDeletedFalse(requestId, tenantId)
                .map(request -> isAllowed("LEAVE_APPROVE", "EMPLOYEE", request.getEmployee().getId()))
                .orElse(false);
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsernameAndDeletedFalse(authentication.getName()).orElse(null);
    }

    private boolean isAccountActive(Authentication authentication) {
        User user = currentUser(authentication);
        return user != null && user.isActive() && "ACTIVE".equalsIgnoreCase(user.getAccountStatus())
                && (user.getLockedUntil() == null || user.getLockedUntil().isBefore(LocalDateTime.now()));
    }

    private boolean hasAuthority(Authentication authentication, String code) {
        return authentication != null && authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(code::equals);
    }

    private boolean isSuperAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_SUPER_ADMIN"::equals);
    }

    private boolean currentlyValid(RolePermissionScope scope) {
        LocalDateTime now = LocalDateTime.now();
        return (scope.getValidFrom() == null || !now.isBefore(scope.getValidFrom()))
                && (scope.getValidUntil() == null || now.isBefore(scope.getValidUntil()));
    }
}
