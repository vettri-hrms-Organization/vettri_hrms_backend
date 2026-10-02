package com.haodaone.user.controller;

import com.haodaone.user.dto.CreateUserPermissionGrantRequest;
import com.haodaone.user.dto.UserPermissionGrantDTO;
import com.haodaone.user.service.UserPermissionGrantService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users/{userId}/permission-grants")
public class UserPermissionGrantController {

    private final UserPermissionGrantService grantService;

    public UserPermissionGrantController(UserPermissionGrantService grantService) {
        this.grantService = grantService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USER_PERMISSION_GRANT')")
    public List<UserPermissionGrantDTO> list(@PathVariable Long userId) {
        return grantService.listForUser(userId);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('USER_PERMISSION_GRANT')")
    public ResponseEntity<UserPermissionGrantDTO> grant(
            @PathVariable Long userId,
            @Valid @RequestBody CreateUserPermissionGrantRequest request) {
        return ResponseEntity.status(201).body(grantService.grant(userId, request));
    }

    @DeleteMapping("/{grantId}")
    @PreAuthorize("hasAuthority('USER_PERMISSION_GRANT')")
    public UserPermissionGrantDTO revoke(@PathVariable Long userId, @PathVariable Long grantId) {
        return grantService.revoke(userId, grantId);
    }
}
