package com.haodaone.config;

import com.haodaone.user.entity.PermissionScope;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataSeederPermissionPolicyTest {

    @Test
    void organizationAdministratorGetsWorkspaceAdministrationButNotFunctionalOrItAccess() {
        var permissions = DataSeeder.companyAdminPermissionCodes();

        assertTrue(permissions.contains("USER_PERMISSION_GRANT"));
        assertTrue(permissions.contains("ROLE_MANAGE"));
        assertTrue(permissions.contains("ORG_MANAGE"));
        assertFalse(permissions.contains("MONITORING_VIEW"));
        assertFalse(permissions.contains("MONITORING_MANAGE"));
        assertFalse(permissions.contains("IT_MANAGEMENT_ACCESS"));
        assertFalse(permissions.contains("SOFTWARE_VIEW"));
        assertFalse(permissions.contains("SOFTWARE_DEPLOY"));
        assertFalse(permissions.contains("SOFTWARE_MANAGE"));
        assertFalse(permissions.contains("DEVICE_MANAGE"));
        assertFalse(permissions.contains("EMPLOYEE_MANAGE"));
        assertFalse(permissions.contains("SALARY_MANAGE"));
        assertEquals(PermissionScope.SELF, DataSeeder.companyAdminScope("SELF_PAYSLIP_VIEW"));
        assertEquals(PermissionScope.ORGANIZATION, DataSeeder.companyAdminScope("ORG_MANAGE"));
    }

    @Test
    void hrManagerDoesNotReceiveMonitoringByDefault() {
        assertFalse(DataSeeder.hrManagerPermissionCodes().contains("MONITORING_VIEW"));
        assertFalse(DataSeeder.hrManagerPermissionCodes().contains("IT_MANAGEMENT_ACCESS"));
    }

    @Test
    void itAdministratorGetsItManagementWithoutRemoteSupportOrBiometricDeviceAdministration() {
        var permissions = DataSeeder.itAdministratorPermissionCodes();

        assertTrue(permissions.contains("IT_MANAGEMENT_ACCESS"));
        assertTrue(permissions.contains("MONITORING_VIEW"));
        assertTrue(permissions.contains("SOFTWARE_VIEW"));
        assertTrue(permissions.contains("SOFTWARE_MANAGE"));
        assertFalse(permissions.contains("DEVICE_MANAGE"));
        assertFalse(permissions.contains("REMOTE_SUPPORT"));
    }
}
