package com.haodaone.user.security;

import com.haodaone.user.entity.Permission;
import com.haodaone.user.entity.PermissionScope;

import java.util.Locale;
import java.util.Set;

public final class PermissionMetadataRegistry {

    private static final Set<String> ORGANIZATION_ONLY = Set.of(
            "USER_VIEW", "USER_CREATE", "USER_MANAGE", "USER_PERMISSION_GRANT",
            "ROLE_ASSIGN", "ROLE_VIEW", "ROLE_MANAGE", "AUDIT_VIEW",
            "EMPLOYEE_IMPORT", "ORG_VIEW", "ORG_MANAGE", "REQUIREMENT_VIEW", "REQUIREMENT_MANAGE",
            "ATTENDANCE_MANAGE", "DEVICE_MANAGE", "LEAVE_MANAGE", "IT_MANAGEMENT_ACCESS",
            "SOFTWARE_VIEW", "SOFTWARE_DEPLOY", "SOFTWARE_MANAGE",
            "IT_DEVICE_VIEW", "IT_DEVICE_ENROLL", "IT_DEVICE_MAPPING_MANAGE", "REMOTE_SUPPORT_MANAGE");

    private static final Set<String> NON_DELEGABLE = Set.of(
            "USER_PERMISSION_GRANT", "ROLE_ASSIGN", "ROLE_MANAGE",
            "MONITORING_MANAGE", "REMOTE_SUPPORT_MANAGE", "SOFTWARE_DEPLOY", "SOFTWARE_MANAGE");

    private static final Set<String> CRITICAL = Set.of(
            "ROLE_ASSIGN", "ROLE_MANAGE", "USER_PERMISSION_GRANT", "SALARY_MANAGE",
            "MONITORING_MANAGE", "REMOTE_SUPPORT_MANAGE", "SOFTWARE_DEPLOY", "SOFTWARE_MANAGE",
            "IT_DEVICE_ENROLL", "IT_DEVICE_MAPPING_MANAGE");

    private static final Set<String> HIGH = Set.of(
            "USER_MANAGE", "USER_CREATE", "AUDIT_VIEW", "EMPLOYEE_MANAGE", "EMPLOYEE_IMPORT",
            "ATTENDANCE_MANAGE", "DEVICE_MANAGE", "LEAVE_APPROVE", "LEAVE_MANAGE",
            "SALARY_VIEW", "REPORTS_VIEW", "MONITORING_VIEW", "IT_MANAGEMENT_ACCESS",
            "IT_DEVICE_VIEW");

    private PermissionMetadataRegistry() {
    }

    public record Metadata(
            String displayName,
            String risk,
            String requiredScope,
            boolean delegable,
            boolean platformOnly,
            boolean readOnly) {
    }

    public static Metadata forPermission(Permission permission) {
        String code = permission.getCode();
        boolean readOnly = code.endsWith("_VIEW") || code.equals("IT_MANAGEMENT_ACCESS");
        String risk = CRITICAL.contains(code) ? "CRITICAL"
                : HIGH.contains(code) ? "HIGH"
                : readOnly ? "LOW" : "MEDIUM";
        String requiredScope = ORGANIZATION_ONLY.contains(code) ? PermissionScope.ORGANIZATION.name()
                : code.startsWith("SELF_") ? PermissionScope.SELF.name()
                : "SCOPED";
        return new Metadata(
                displayName(code),
                risk,
                requiredScope,
                !NON_DELEGABLE.contains(code),
                false,
                readOnly);
    }

    public static boolean requiresOrganizationScope(String permissionCode) {
        return ORGANIZATION_ONLY.contains(permissionCode);
    }

    public static boolean isDelegable(String permissionCode) {
        return !NON_DELEGABLE.contains(permissionCode);
    }

    public static boolean isPlatformOnly(String permissionCode) {
        return "SUPER_ADMIN".equals(permissionCode);
    }

    private static String displayName(String code) {
        String normalized = code.toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder result = new StringBuilder(normalized.length());
        boolean capitalizeNext = true;
        for (char character : normalized.toCharArray()) {
            result.append(capitalizeNext ? Character.toUpperCase(character) : character);
            capitalizeNext = Character.isWhitespace(character);
        }
        return result.toString();
    }
}
