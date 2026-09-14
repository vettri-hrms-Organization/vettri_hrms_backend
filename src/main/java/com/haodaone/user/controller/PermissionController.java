package com.haodaone.user.controller;

import com.haodaone.user.dto.PermissionDTO;
import com.haodaone.user.repository.PermissionRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Permissions are platform-defined (seeded by DataSeeder, one set per
 * module) rather than created through the UI - this endpoint is read-only
 * so the Settings > Roles screen can list available permissions to attach
 * to a role.
 */
@RestController
@RequestMapping("/api/permissions")
public class PermissionController {

    private final PermissionRepository permissionRepository;

    public PermissionController(PermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_VIEW')")
    public List<PermissionDTO> listAll() {
        return permissionRepository.findAllByDeletedFalse().stream().map(PermissionDTO::from).toList();
    }
}
