package com.haodaone.user.dto;

import com.haodaone.user.entity.User;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class UserDTO {
    private Long id;
    private String username;
    private String email;
    private String fullName;
    private boolean active;
    private boolean mustChangePassword;
    private LocalDateTime lastLoginAt;
    private List<String> roles;
    private Set<String> permissions;
    private Long employeeId;

    public static UserDTO from(User user) {
        UserDTO dto = new UserDTO();
        dto.id = user.getId();
        dto.username = user.getUsername();
        dto.email = user.getEmail();
        dto.fullName = user.getFullName();
        dto.active = user.isActive();
        dto.mustChangePassword = user.isMustChangePassword();
        dto.lastLoginAt = user.getLastLoginAt();
        dto.roles = user.getRoles().stream()
                .map(com.haodaone.user.entity.Role::getName)
                .collect(Collectors.toList());

        dto.permissions = user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(com.haodaone.user.entity.Permission::getCode)
                .collect(Collectors.toSet());
        return dto;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return fullName;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public List<String> getRoles() {
        return roles;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    /** Null for logins with no linked Employee record (see Employee.user javadoc) - not every account is an employee. */
    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }
}
