package com.haodaone.user.controller;

import com.haodaone.user.dto.CreateRoleRequest;
import com.haodaone.user.dto.RoleDTO;
import com.haodaone.user.service.RoleService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ROLE_VIEW')")
    public List<RoleDTO> listAll() {
        return roleService.listAll();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public ResponseEntity<RoleDTO> create(@Valid @RequestBody CreateRoleRequest request) {
        return ResponseEntity.status(201).body(roleService.create(request));
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public RoleDTO updatePermissions(@PathVariable Long id, @RequestBody Set<String> permissionCodes) {
        return roleService.updatePermissions(id, permissionCodes);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
