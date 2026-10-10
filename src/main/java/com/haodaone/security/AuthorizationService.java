package com.haodaone.security;

import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.leave.repository.LeaveRequestRepository;
import com.haodaone.attendance.repository.WfhRequestRepository;
import com.haodaone.tenant.TenantContext;
import com.haodaone.user.entity.PermissionScope;
import com.haodaone.user.entity.RolePermissionScope;
import com.haodaone.user.entity.User;
import com.haodaone.user.entity.UserPermissionGrant;
import com.haodaone.user.repository.UserPermissionGrantRepository;
import com.haodaone.user.repository.UserRepository;
import com.haodaone.user.security.PermissionMetadataRegistry;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service("authorizationService")
public class AuthorizationService {
    private final UserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final LeaveRequestRepository leaveRequestRepository;
    private final WfhRequestRepository wfhRequestRepository;
    private final UserPermissionGrantRepository permissionGrantRepository;

    public AuthorizationService(UserRepository userRepository, EmployeeRepository employeeRepository,
                               LeaveRequestRepository leaveRequestRepository,
                               WfhRequestRepository wfhRequestRepository,
                               UserPermissionGrantRepository permissionGrantRepository) {
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.leaveRequestRepository = leaveRequestRepository;
        this.wfhRequestRepository = wfhRequestRepository;
        this.permissionGrantRepository = permissionGrantRepository;
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

        Set<PermissionScope> scopes = getScopesForUser(user, permissionCode, tenantId);
        if (scopes.isEmpty()) {
            return false;
        }
        if (resourceId == null || resourceType == null) {
            return scopes.contains(PermissionScope.ORGANIZATION);
        }
        if (!"EMPLOYEE".equalsIgnoreCase(resourceType)) {
            return scopes.contains(PermissionScope.ORGANIZATION);
        }

        Employee target = employeeRepository.findByIdAndCompany_IdAndDeletedFalse(resourceId, tenantId).orElse(null);
        if (target == null) return false;
        if (scopes.contains(PermissionScope.ORGANIZATION)) return true;

        Employee current = employeeRepository.findByUser_IdAndDeletedFalse(user.getId()).orElse(null);
        if (current == null) return false;

        if (scopes.contains(PermissionScope.SELF) && current.getId().equals(target.getId())) return true;
        if (scopes.contains(PermissionScope.TEAM)
                && current.getCompany() != null
                && target.getCompany() != null
                && Objects.equals(current.getCompany().getId(), target.getCompany().getId())
                && current.getTeam() != null
                && target.getTeam() != null
                && current.getTeam().getId().equals(target.getTeam().getId())) return true;
        return scopes.contains(PermissionScope.DEPARTMENT)
                && current.getCompany() != null
                && tenantId.equals(current.getCompany().getId())
                && target.getCompany() != null
                && tenantId.equals(target.getCompany().getId())
                && current.getDepartment() != null
                && target.getDepartment() != null
                && current.getDepartment().getId().equals(target.getDepartment().getId());
    }

    @Transactional(readOnly = true)
    public boolean canAccessOwnAttendance() {
        return resolveCurrentEmployee() != null;
    }

    @Transactional(readOnly = true)
    public Set<PermissionScope> getScopes(String permissionCode) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || !isAccountActive(authentication)) return Set.of();
        User user = currentUser(authentication);
        Long tenantId = TenantContext.getCurrentTenant();
        if (user == null || user.getCompany() == null || tenantId == null
                || !tenantId.equals(user.getCompany().getId())) return Set.of();
        return getScopesForUser(user, permissionCode, tenantId);
    }

    private Set<PermissionScope> getScopesForUser(User user, String permissionCode, Long tenantId) {
        Set<PermissionScope> scopes = user.getRoles().stream()
                .filter(role -> !role.isDeleted())
                .flatMap(role -> role.getPermissionScopes().stream())
                .filter(scope -> scope.getRole() != null
                        && scope.getRole().getPermissions().stream()
                                .anyMatch(permission -> scope.getPermission() != null
                                        && permission.getCode().equals(scope.getPermission().getCode())
                                        && !permission.isDeleted()))
                .filter(scope -> permissionCode.equals(scope.getPermission().getCode()))
                .filter(scope -> !scope.getPermission().isDeleted())
                .filter(this::currentlyValid)
                .map(RolePermissionScope::getScope)
                .filter(scope -> scope != null && scope != PermissionScope.CUSTOM)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(PermissionScope.class)));
        permissionGrantRepository.findAllByCompany_IdAndUser_IdAndRevokedAtIsNullAndDeletedFalse(
                        tenantId, user.getId()).stream()
                .filter(UserPermissionGrant::isActive)
                .filter(grant -> grant.getCompany() != null && tenantId.equals(grant.getCompany().getId()))
                .filter(grant -> grant.getUser() != null && user.getId().equals(grant.getUser().getId()))
                .filter(grant -> grant.getPermission() != null && !grant.getPermission().isDeleted())
                .filter(grant -> permissionCode.equals(grant.getPermission().getCode()))
                .map(UserPermissionGrant::getScope)
                .filter(scope -> scope != null && scope != PermissionScope.CUSTOM)
                .forEach(scopes::add);
        return scopes;
    }

    public boolean canManageUserPermissionGrants() {
        return hasAuthority(SecurityContextHolder.getContext().getAuthentication(), "USER_PERMISSION_GRANT")
                && getScopes("USER_PERMISSION_GRANT").contains(PermissionScope.ORGANIZATION);
    }

    @Transactional(readOnly = true)
    public boolean canDelegatePermission(String permissionCode, PermissionScope requestedScope, Long recipientUserId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (requestedScope == null || requestedScope == PermissionScope.CUSTOM
                || !PermissionMetadataRegistry.isDelegable(permissionCode)
                || PermissionMetadataRegistry.isPlatformOnly(permissionCode)
                || authentication == null || !authentication.isAuthenticated() || !isAccountActive(authentication)
                || !hasAuthority(authentication, "USER_PERMISSION_GRANT")
                || !canManageUserPermissionGrants()) return false;

        Long tenantId = TenantContext.getCurrentTenant();
        User grantor = currentUser(authentication);
        if (tenantId == null || grantor == null || grantor.getCompany() == null
                || !tenantId.equals(grantor.getCompany().getId())) return false;
        User recipient = userRepository.findByIdAndCompanyIdAndDeletedFalse(recipientUserId, tenantId).orElse(null);
        if (recipient == null || !recipient.isActive()
                || !"ACTIVE".equalsIgnoreCase(recipient.getAccountStatus())
                || recipient.getId().equals(grantor.getId())) return false;

        Set<PermissionScope> grantorScopes = getScopesForUser(grantor, permissionCode, tenantId);
        if (grantorScopes.isEmpty()) return false;
        Employee grantorEmployee = employeeRepository.findByUser_IdAndDeletedFalse(grantor.getId()).orElse(null);
        Employee recipientEmployee = employeeRepository.findByUser_IdAndDeletedFalse(recipient.getId()).orElse(null);
        if (grantorScopes.contains(PermissionScope.ORGANIZATION)) return true;
        if (!isEmployeeInTenant(grantorEmployee, tenantId)
                || !isEmployeeInTenant(recipientEmployee, tenantId)) return false;
        return requestedScopeIsWithinGrantorScope(
                requestedScope, grantorScopes, grantorEmployee, recipientEmployee, tenantId);
    }

    @Transactional(readOnly = true)
    public boolean canAssignPermissionToRole(String permissionCode, PermissionScope requestedScope) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long tenantId = TenantContext.getCurrentTenant();
        if (requestedScope == null || requestedScope == PermissionScope.CUSTOM
                || authentication == null || !authentication.isAuthenticated() || !isAccountActive(authentication)
                || tenantId == null) {
            return false;
        }
        if (isSuperAdmin(authentication)) return true;
        if (!PermissionMetadataRegistry.isDelegable(permissionCode)
                || PermissionMetadataRegistry.isPlatformOnly(permissionCode)
                || !hasOrganizationScope("ROLE_MANAGE")) {
            return false;
        }
        if (PermissionMetadataRegistry.requiresOrganizationScope(permissionCode)
                && requestedScope != PermissionScope.ORGANIZATION) {
            return false;
        }
        Set<PermissionScope> actorScopes = getScopes(permissionCode);
        if (requestedScope == PermissionScope.SELF) return !actorScopes.isEmpty();
        return actorScopes.contains(PermissionScope.ORGANIZATION);
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
        if (current == null || current.getCompany() == null || !companyId.equals(current.getCompany().getId())) {
            return Optional.of(Set.of());
        }
        Set<Long> ids = new HashSet<>();
        if (scopes.contains(PermissionScope.SELF)) ids.add(current.getId());
        if (scopes.contains(PermissionScope.TEAM) && current.getTeam() != null) {
            ids.addAll(employeeRepository.findIdsByCompanyAndTeam(companyId, current.getTeam().getId()));
        }
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

    @Transactional(readOnly = true)
    public boolean hasOrganizationScope(String permissionCode) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && isAccountActive(authentication)
                && isSuperAdmin(authentication)) {
            return TenantContext.getCurrentTenant() != null;
        }
        return authentication != null && authentication.isAuthenticated() && isAccountActive(authentication)
                && hasAuthority(authentication, permissionCode)
                && getScopes(permissionCode).contains(PermissionScope.ORGANIZATION);
    }

    @Transactional(readOnly = true)
    public boolean canManageOfficeLocations() {
        return hasOrganizationScope("ORG_MANAGE") || hasOrganizationScope("ATTENDANCE_MANAGE");
    }

    private boolean requestedScopeIsWithinGrantorScope(PermissionScope requestedScope,
                                                       Set<PermissionScope> grantorScopes,
                                                       Employee grantor,
                                                       Employee recipient,
                                                       Long tenantId) {
        if (requestedScope == PermissionScope.SELF) {
            return employeeIsWithinScope(grantorScopes, grantor, recipient);
        }
        if (requestedScope == PermissionScope.TEAM) {
            if (recipient.getTeam() == null) return false;
            if (grantorScopes.contains(PermissionScope.TEAM) && grantor.getTeam() != null
                    && grantor.getTeam().getId().equals(recipient.getTeam().getId())) return true;
            Set<Long> teamMembers = new HashSet<>(employeeRepository.findIdsByCompanyAndTeam(
                    tenantId, recipient.getTeam().getId()));
            return !teamMembers.isEmpty() && teamMembers.stream()
                    .map(id -> employeeRepository.findByIdAndCompany_IdAndDeletedFalse(id, tenantId).orElse(null))
                    .allMatch(member -> member != null && employeeIsWithinScope(grantorScopes, grantor, member));
        }
        if (requestedScope == PermissionScope.DEPARTMENT) {
            if (recipient.getDepartment() == null) return false;
            if (grantorScopes.contains(PermissionScope.DEPARTMENT) && grantor.getDepartment() != null
                    && grantor.getDepartment().getId().equals(recipient.getDepartment().getId())) return true;
            Set<Long> departmentMembers = new HashSet<>(employeeRepository.findIdsByCompanyAndDepartment(
                    tenantId, recipient.getDepartment().getId()));
            return !departmentMembers.isEmpty() && departmentMembers.stream()
                    .map(id -> employeeRepository.findByIdAndCompany_IdAndDeletedFalse(id, tenantId).orElse(null))
                    .allMatch(member -> member != null && employeeIsWithinScope(grantorScopes, grantor, member));
        }
        return false;
    }

    private boolean employeeIsWithinScope(Set<PermissionScope> scopes, Employee grantor, Employee target) {
        if (scopes.contains(PermissionScope.ORGANIZATION)) return true;
        if (scopes.contains(PermissionScope.SELF) && grantor.getId().equals(target.getId())) return true;
        if (scopes.contains(PermissionScope.TEAM) && grantor.getTeam() != null && target.getTeam() != null
                && grantor.getTeam().getId().equals(target.getTeam().getId())) return true;
        return scopes.contains(PermissionScope.DEPARTMENT)
                && grantor.getDepartment() != null && target.getDepartment() != null
                && grantor.getDepartment().getId().equals(target.getDepartment().getId());
    }

    private boolean isEmployeeInTenant(Employee employee, Long tenantId) {
        return employee != null && !employee.isDeleted() && employee.getCompany() != null
                && tenantId.equals(employee.getCompany().getId());
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
        String identifier = authentication.getName();
        return userRepository.findByUsernameAndDeletedFalse(identifier)
                .or(() -> userRepository.findByEmailIgnoreCaseAndDeletedFalse(identifier))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public Employee resolveCurrentEmployee() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || !isAccountActive(authentication)) {
            return null;
        }
        User user = currentUser(authentication);
        if (user == null || user.getCompany() == null) {
            return null;
        }
        Long tenantId = TenantContext.getCurrentTenant();
        Employee current = employeeRepository.findByUser_IdAndDeletedFalse(user.getId()).orElse(null);
        if (current == null || current.getCompany() == null || current.isDeleted()) {
            return null;
        }
        if (tenantId != null && !tenantId.equals(current.getCompany().getId())) {
            return null;
        }
        if (current.getStatus() != null && !"Active".equalsIgnoreCase(current.getStatus())) {
            return null;
        }
        return current;
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
