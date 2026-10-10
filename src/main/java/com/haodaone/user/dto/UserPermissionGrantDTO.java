package com.haodaone.user.dto;

import com.haodaone.user.entity.PermissionScope;
import com.haodaone.user.entity.UserPermissionGrant;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public class UserPermissionGrantDTO {

    private Long id;
    private String permissionCode;
    private String permissionDescription;
    private PermissionScope scope;
    private Long grantedByUserId;
    private String grantedByName;
    private LocalDateTime grantedAt;
    private OffsetDateTime expiresAt;
    private Long revokedByUserId;
    private String revokedByName;
    private LocalDateTime revokedAt;
    private boolean active;

    public static UserPermissionGrantDTO from(UserPermissionGrant grant) {
        UserPermissionGrantDTO dto = new UserPermissionGrantDTO();
        dto.id = grant.getId();
        dto.permissionCode = grant.getPermission().getCode();
        dto.permissionDescription = grant.getPermission().getDescription();
        dto.scope = grant.getScope();
        dto.grantedByUserId = grant.getGrantedBy().getId();
        dto.grantedByName = grant.getGrantedBy().getFullName();
        dto.grantedAt = grant.getGrantedAt();
        dto.expiresAt = grant.getExpiresAt() == null ? null : grant.getExpiresAt().atOffset(ZoneOffset.UTC);
        dto.revokedByUserId = grant.getRevokedBy() == null ? null : grant.getRevokedBy().getId();
        dto.revokedByName = grant.getRevokedBy() == null ? null : grant.getRevokedBy().getFullName();
        dto.revokedAt = grant.getRevokedAt();
        dto.active = grant.isActive();
        return dto;
    }

    public Long getId() { return id; }
    public String getPermissionCode() { return permissionCode; }
    public String getPermissionDescription() { return permissionDescription; }
    public PermissionScope getScope() { return scope; }
    public Long getGrantedByUserId() { return grantedByUserId; }
    public String getGrantedByName() { return grantedByName; }
    public LocalDateTime getGrantedAt() { return grantedAt; }
    public OffsetDateTime getExpiresAt() { return expiresAt; }
    public Long getRevokedByUserId() { return revokedByUserId; }
    public String getRevokedByName() { return revokedByName; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
    public boolean isActive() { return active; }
}
