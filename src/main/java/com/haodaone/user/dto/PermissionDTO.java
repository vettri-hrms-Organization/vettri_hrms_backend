package com.haodaone.user.dto;

import com.haodaone.user.entity.Permission;
import com.haodaone.user.security.PermissionMetadataRegistry;

public class PermissionDTO {
    private Long id;
    private String code;
    private String description;
    private String module;
    private String displayName;
    private String risk;
    private String requiredScope;
    private boolean delegable;
    private boolean platformOnly;
    private boolean readOnly;

    public static PermissionDTO from(Permission p) {
        PermissionDTO dto = new PermissionDTO();
        dto.id = p.getId();
        dto.code = p.getCode();
        dto.description = p.getDescription();
        dto.module = p.getModule();
        PermissionMetadataRegistry.Metadata metadata = PermissionMetadataRegistry.forPermission(p);
        dto.displayName = metadata.displayName();
        dto.risk = metadata.risk();
        dto.requiredScope = metadata.requiredScope();
        dto.delegable = metadata.delegable();
        dto.platformOnly = metadata.platformOnly();
        dto.readOnly = metadata.readOnly();
        return dto;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public String getModule() {
        return module;
    }

    public String getDisplayName() { return displayName; }
    public String getRisk() { return risk; }
    public String getRequiredScope() { return requiredScope; }
    public boolean isDelegable() { return delegable; }
    public boolean isPlatformOnly() { return platformOnly; }
    public boolean isReadOnly() { return readOnly; }
}
