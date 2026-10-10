package com.haodaone.user.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.company.entity.Company;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.security.AuthorizationService;
import com.haodaone.tenant.TenantContext;
import com.haodaone.user.dto.CreateUserPermissionGrantRequest;
import com.haodaone.user.dto.UserPermissionGrantDTO;
import com.haodaone.user.entity.Permission;
import com.haodaone.user.entity.PermissionScope;
import com.haodaone.user.entity.User;
import com.haodaone.user.entity.UserPermissionGrant;
import com.haodaone.user.repository.PermissionRepository;
import com.haodaone.user.repository.UserPermissionGrantRepository;
import com.haodaone.user.repository.UserRepository;
import com.haodaone.user.security.PermissionMetadataRegistry;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class UserPermissionGrantService {

    private final UserPermissionGrantRepository grantRepository;
    private final UserRepository userRepository;
    private final PermissionRepository permissionRepository;
    private final CompanyRepository companyRepository;
    private final AuthorizationService authorizationService;
    private final AuditLogService auditLogService;

    public UserPermissionGrantService(UserPermissionGrantRepository grantRepository,
                                      UserRepository userRepository,
                                      PermissionRepository permissionRepository,
                                      CompanyRepository companyRepository,
                                      AuthorizationService authorizationService,
                                      AuditLogService auditLogService) {
        this.grantRepository = grantRepository;
        this.userRepository = userRepository;
        this.permissionRepository = permissionRepository;
        this.companyRepository = companyRepository;
        this.authorizationService = authorizationService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public List<UserPermissionGrantDTO> listForUser(Long userId) {
        Long companyId = requiredTenant();
        requireGrantManager();
        requireTenantUser(userId, companyId);
        return grantRepository.findAllByCompany_IdAndUser_IdAndDeletedFalseOrderByGrantedAtDesc(companyId, userId)
                .stream().map(UserPermissionGrantDTO::from).toList();
    }

    @Transactional
    public UserPermissionGrantDTO grant(Long userId, CreateUserPermissionGrantRequest request) {
        Long companyId = requiredTenant();
        User grantor = currentActor(companyId);
        if (grantor.getId().equals(userId)) {
            throw new AccessDeniedException("Users cannot grant additional permissions to themselves.");
        }
        User recipient = requireTenantUser(userId, companyId);
        if (!recipient.isActive() || !"ACTIVE".equalsIgnoreCase(recipient.getAccountStatus())) {
            throw new BadRequestException("Permission grants can only be assigned to active users.");
        }
        String permissionCode = request.getPermissionCode().trim().toUpperCase(Locale.ROOT);
        PermissionScope scope = request.getScope();
        requireGrantManager();
        if (scope == null) {
            throw new BadRequestException("A permission scope is required.");
        }
        if (scope == PermissionScope.CUSTOM) {
            throw new BadRequestException("CUSTOM scope cannot be granted until custom targets are supported.");
        }
        if (PermissionMetadataRegistry.requiresOrganizationScope(permissionCode)
                && scope != PermissionScope.ORGANIZATION) {
            throw new BadRequestException(permissionCode + " requires Company scope.");
        }
        Permission permission = permissionRepository.findByCode(permissionCode)
                .filter(candidate -> !candidate.isDeleted())
                .orElseThrow(() -> new BadRequestException("Unknown permission code: " + permissionCode));
        if (!authorizationService.canDelegatePermission(permissionCode, scope, recipient.getId())) {
            throw new AccessDeniedException("You are not authorized to grant this permission at the requested scope.");
        }

        if (grantRepository.existsByCompany_IdAndUser_IdAndPermission_CodeAndRevokedAtIsNullAndDeletedFalse(
                companyId, userId, permissionCode)) {
            throw new BadRequestException("An active grant already exists for this permission.");
        }
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new BadRequestException("Company context is invalid."));

        UserPermissionGrant grant = new UserPermissionGrant();
        grant.setCompany(company);
        grant.setUser(recipient);
        grant.setPermission(permission);
        grant.setScope(scope);
        grant.setGrantedBy(grantor);
        grant.setGrantedAt(LocalDateTime.now());
        try {
            UserPermissionGrant saved = grantRepository.saveAndFlush(grant);
            auditLogService.log("UserPermissionGrant", saved.getId(), "GRANT",
                    "Granted " + permissionCode + " with " + scope + " scope to user " + recipient.getId());
            return UserPermissionGrantDTO.from(saved);
        } catch (DataIntegrityViolationException exception) {
            throw new BadRequestException("An active grant already exists for this permission.");
        }
    }

    @Transactional
    public UserPermissionGrantDTO revoke(Long userId, Long grantId) {
        Long companyId = requiredTenant();
        User grantor = currentActor(companyId);
        requireGrantManager();
        requireTenantUser(userId, companyId);
        UserPermissionGrant grant = grantRepository
                .findByIdAndCompany_IdAndUser_IdAndDeletedFalse(grantId, companyId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Permission grant not found: " + grantId));
        if (!grant.isActive()) {
            throw new BadRequestException("Permission grant has already been revoked.");
        }
        grant.setRevokedBy(grantor);
        grant.setRevokedAt(LocalDateTime.now());
        UserPermissionGrant saved = grantRepository.save(grant);
        auditLogService.log("UserPermissionGrant", saved.getId(), "REVOKE",
                "Revoked " + grant.getPermission().getCode() + " grant from user " + userId);
        return UserPermissionGrantDTO.from(saved);
    }

    private void requireGrantManager() {
        if (!authorizationService.canManageUserPermissionGrants()) {
            throw new AccessDeniedException("You are not authorized to manage user permission grants.");
        }
    }

    private User requireTenantUser(Long userId, Long companyId) {
        return userRepository.findByIdAndCompanyIdAndDeletedFalse(userId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    private User currentActor(Long companyId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Authentication is required.");
        }
        User actor = userRepository.findByUsernameAndDeletedFalse(authentication.getName())
                .or(() -> userRepository.findByEmailIgnoreCaseAndDeletedFalse(authentication.getName()))
                .orElseThrow(() -> new AccessDeniedException("Authenticated user was not found."));
        if (actor.getCompany() == null || !companyId.equals(actor.getCompany().getId()) || !actor.isActive()
                || !"ACTIVE".equalsIgnoreCase(actor.getAccountStatus())) {
            throw new AccessDeniedException("Grantor must be active and belong to the current company.");
        }
        return actor;
    }

    private Long requiredTenant() {
        Long companyId = TenantContext.getCurrentTenant();
        if (companyId == null) {
            throw new AccessDeniedException("Company context is required.");
        }
        return companyId;
    }
}
