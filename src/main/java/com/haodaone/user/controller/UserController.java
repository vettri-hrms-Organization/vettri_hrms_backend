package com.haodaone.user.controller;

import com.haodaone.user.dto.CreateUserRequest;
import com.haodaone.user.dto.UserDTO;
import com.haodaone.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * Admin-only user account management. This is deliberately separate from
 * the (Phase 1) Employee module - this controller manages *login accounts*
 * (username/password/roles), not HR profile data.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USER_VIEW')")
    public List<UserDTO> listAll() {
        return userService.listAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('USER_VIEW')")
    public UserDTO getById(@PathVariable Long id) {
        return userService.getById(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER_CREATE') and (@companySecurity.isSuperAdmin() or @companySecurity.isCompanyAdmin())")
    public ResponseEntity<UserDTO> create(@Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity.status(201).body(userService.create(request));
    }

    @PatchMapping("/{id}/activate")
    @PreAuthorize("hasAuthority('USER_MANAGE') and (@companySecurity.canManageUser(#id) or @companySecurity.isSuperAdmin())")
    public UserDTO activate(@PathVariable Long id) {
        return userService.setActive(id, true);
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasAuthority('USER_MANAGE') and (@companySecurity.canManageUser(#id) or @companySecurity.isSuperAdmin())")
    public UserDTO deactivate(@PathVariable Long id) {
        return userService.setActive(id, false);
    }

    @PutMapping("/{id}/roles")
    @PreAuthorize("hasAuthority('USER_MANAGE') and (@companySecurity.canManageUser(#id) or @companySecurity.isSuperAdmin())")
    public UserDTO assignRoles(@PathVariable Long id, @RequestBody Set<String> roleNames) {
        return userService.assignRoles(id, roleNames);
    }
}
