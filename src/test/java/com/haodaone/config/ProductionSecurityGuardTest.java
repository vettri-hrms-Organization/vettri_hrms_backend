package com.haodaone.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductionSecurityGuardTest {

    private static final String VALID_TEST_SECRET = "test-only-jwt-secret-with-at-least-32-bytes";
    private static final String VALID_TEST_PASSWORD = "test-only-admin-password";

    @Test
    void localConfigurationDoesNotRequireProductionSecrets() {
        assertDoesNotThrow(() -> new ProductionSecurityGuard("", "local", "", "").validate());
    }

    @Test
    void productionRequiresNonDefaultJwtSecret() {
        assertThrows(IllegalStateException.class, () -> new ProductionSecurityGuard(
                "", "production", "local-dev-only-secret-change-before-deploying-anywhere-real-1234567890",
                VALID_TEST_PASSWORD).validate());
    }

    @Test
    void productionRequiresJwtSecretAtLeast32Utf8Bytes() {
        assertThrows(IllegalStateException.class, () -> new ProductionSecurityGuard(
                "", "production", "too-short", VALID_TEST_PASSWORD).validate());
    }

    @Test
    void productionRequiresNonDefaultSeedPassword() {
        assertThrows(IllegalStateException.class, () -> new ProductionSecurityGuard(
                "", "production", VALID_TEST_SECRET, "ChangeMe123!").validate());
    }

    @Test
    void remoteDatabaseIsTreatedAsProductionEvenWithDefaultEnvironment() {
        assertThrows(IllegalStateException.class, () -> new ProductionSecurityGuard(
                "", "local", "local-dev-only-secret-change-before-deploying-anywhere-real-1234567890",
                VALID_TEST_PASSWORD, "jdbc:postgresql://db.example.com:5432/vettri").validate());
    }

    @Test
    void localDatabaseKeepsDevelopmentDefaults() {
        assertDoesNotThrow(() -> new ProductionSecurityGuard(
                "", "local", "", "", "jdbc:postgresql://localhost:5432/vettri").validate());
    }

    @Test
    void productionAcceptsConfiguredSecrets() {
        assertDoesNotThrow(() -> new ProductionSecurityGuard(
                "", "production", VALID_TEST_SECRET, VALID_TEST_PASSWORD).validate());
    }
}
