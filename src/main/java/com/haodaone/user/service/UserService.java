package com.haodaone.user.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.user.dto.CreateUserRequest;
import com.haodaone.user.dto.UserDTO;
import com.haodaone.user.entity.Role;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.RoleRepository;
import com.haodaone.user.repository.UserRepository;
import com.haodaone.employee.entity.Employee;
import com.haodaone.employee.repository.EmployeeRepository;
import com.haodaone.security.AuthorizationService;
import com.haodaone.user.repository.UserPermissionGrantRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final com.haodaone.company.repository.CompanyRepository companyRepository;
    private final EmployeeRepository employeeRepository;
    private final AuthorizationService authorizationService;
    private final UserPermissionGrantRepository permissionGrantRepository;

    public UserService(UserRepository userRepository, RoleRepository roleRepository,
                        PasswordEncoder passwordEncoder, AuditLogService auditLogService, com.haodaone.company.repository.CompanyRepository companyRepository,
                        EmployeeRepository employeeRepository, AuthorizationService authorizationService,
                        UserPermissionGrantRepository permissionGrantRepository) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
        this.companyRepository = companyRepository;
        this.employeeRepository = employeeRepository;
        this.authorizationService = authorizationService;
        this.permissionGrantRepository = permissionGrantRepository;
    }

    public List<UserDTO> listAll() {
        Long currentTenant = requiredTenant();
        var grantsByUser = permissionGrantRepository
                .findAllByCompany_IdAndRevokedAtIsNullAndDeletedFalse(currentTenant).stream()
                .collect(java.util.stream.Collectors.groupingBy(grant -> grant.getUser().getId()));
        return userRepository.findAllByCompanyIdAndDeletedFalse(currentTenant).stream()
                .map(user -> UserDTO.from(user, grantsByUser.getOrDefault(user.getId(), List.of())))
                .toList();
    }

    public UserDTO getById(Long id) {
        Long companyId = requiredTenant();
        User user = userRepository.findByIdAndCompanyIdAndDeletedFalse(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
        return toDTO(user);
    }

    @Transactional
    public UserDTO create(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BadRequestException("Username '" + request.getUsername() + "' is already taken");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email '" + request.getEmail() + "' is already registered");
        }

        Set<String> requestedRoleNames = request.getRoleNames().isEmpty() ? Set.of("EMPLOYEE") : request.getRoleNames();
        validateRequestedRoles(requestedRoleNames, Set.of());
        Long currentTenant = requiredTenant();
        Set<Role> roles = new HashSet<>();
        for (String roleName : requestedRoleNames) {
            roles.add(findRoleForTenant(roleName, currentTenant)
                    .orElseThrow(() -> new BadRequestException("Unknown role: " + roleName)));
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setFullName(request.getFullName());
        user.setPasswordHash(passwordEncoder.encode(request.getTemporaryPassword()));
        user.setMustChangePassword(true);
        user.setActive(true);
        user.setAccountStatus("ACTIVE");
        user.setRoles(roles);

        // Assign company from context (tenant) if present
        companyRepository.findById(currentTenant).ifPresent(user::setCompany);

        User saved = userRepository.save(user);
        if (requestedRoleNames.contains("EMPLOYEE") && currentTenant != null) {
            employeeRepository.findByEmailIgnoreCaseAndCompany_IdAndDeletedFalse(saved.getEmail(), currentTenant)
                    .filter(employee -> employee.getUser() == null)
                    .ifPresent(employee -> {
                        employee.setUser(saved);
                        employeeRepository.save(employee);
                    });
        }
        auditLogService.log("User", saved.getId(), "CREATE", "Created user '" + saved.getUsername() + "' with roles " + requestedRoleNames);
        return toDTO(saved);
    }

    @Transactional
    public UserDTO setActive(Long id, boolean active) {
        User user = findActiveOrThrow(id);
        boolean wasActive = user.isActive();
        user.setActive(active);
        User saved = userRepository.save(user);
        if (wasActive != active) {
            auditLogService.log("User", saved.getId(), active ? "ACTIVATE" : "DEACTIVATE",
                    "active: " + wasActive + " -> " + active);
        }
        return toDTO(saved);
    }

    @Transactional
    public UserDTO assignRoles(Long id, Set<String> roleNames) {
        User user = findActiveOrThrow(id);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null) {
            userRepository.findByUsernameAndDeletedFalse(authentication.getName())
                    .or(() -> userRepository.findByEmailIgnoreCaseAndDeletedFalse(authentication.getName()))
                    .filter(actor -> actor.getId().equals(user.getId()))
                    .ifPresent(actor -> {
                        throw new AccessDeniedException("Users cannot change their own roles.");
                    });
        }
        Set<String> currentRoleNames = user.getRoles().stream()
                .map(Role::getName)
                .collect(java.util.stream.Collectors.toSet());
        validateRequestedRoles(roleNames, currentRoleNames);
        Set<Role> roles = new HashSet<>();
        Long companyId = requiredTenant();
        for (String roleName : roleNames) {
            roles.add(findRoleForTenant(roleName, companyId)
                    .orElseThrow(() -> new BadRequestException("Unknown role: " + roleName)));
        }
        user.setRoles(roles);
        User saved = userRepository.save(user);
        auditLogService.log("User", saved.getId(), "UPDATE", "Roles set to " + roleNames);
        return toDTO(saved);
    }

    private User findActiveOrThrow(Long id) {
        Long companyId = requiredTenant();
        User user = userRepository.findByIdAndCompanyIdAndDeletedFalse(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
        if (user.isDeleted()) {
            throw new ResourceNotFoundException("User not found: " + id);
        }
        return user;
    }

    private Long requiredTenant() {
        Long tenant = com.haodaone.tenant.TenantContext.getCurrentTenant();
        if (tenant == null) {
            throw new BadRequestException("Company context is required");
        }
        return tenant;
    }

    private UserDTO toDTO(User user) {
        Long companyId = user.getCompany() == null ? null : user.getCompany().getId();
        var grants = companyId == null ? List.<com.haodaone.user.entity.UserPermissionGrant>of()
                : permissionGrantRepository.findAllByCompany_IdAndUser_IdAndRevokedAtIsNullAndDeletedFalse(
                        companyId, user.getId());
        return UserDTO.from(user, grants);
    }

    private void validateRequestedRoles(Set<String> roleNames, Set<String> currentRoleNames) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new AccessDeniedException("You are not authenticated.");
        }

        boolean isSuperAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_SUPER_ADMIN".equals(authority.getAuthority()) || "SUPER_ADMIN".equals(authority.getAuthority()));
        boolean canAssignRoles = authorizationService.hasOrganizationScope("ROLE_ASSIGN")
                || authorizationService.hasOrganizationScope("USER_MANAGE");
        if (!canAssignRoles) {
            throw new AccessDeniedException("You don't have organization-scoped permission to assign roles.");
        }
        if (roleNames == null) {
            throw new BadRequestException("Role names are required.");
        }

        Set<String> requested = roleNames.stream().map(String::trim).filter(s -> !s.isEmpty()).collect(java.util.stream.Collectors.toSet());

        if (requested.contains("SUPER_ADMIN") && !isSuperAdmin && !currentRoleNames.contains("SUPER_ADMIN")) {
            throw new AccessDeniedException("Only a Super Admin can assign the platform Super Admin role.");
        }

    }

    private java.util.Optional<Role> findRoleForTenant(String roleName, Long companyId) {
        return roleRepository.findByNameAndCompany_Id(roleName, companyId)
                .or(() -> roleRepository.findByName(roleName).filter(role -> role.getCompany() == null));
    }
}
