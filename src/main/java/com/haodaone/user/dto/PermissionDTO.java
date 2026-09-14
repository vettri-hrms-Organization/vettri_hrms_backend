package com.haodaone.user.dto;

import com.haodaone.user.entity.Permission;

public class PermissionDTO {
    private Long id;
    private String code;
    private String description;
    private String module;

    public static PermissionDTO from(Permission p) {
        PermissionDTO dto = new PermissionDTO();
        dto.id = p.getId();
        dto.code = p.getCode();
        dto.description = p.getDescription();
        dto.module = p.getModule();
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
}
