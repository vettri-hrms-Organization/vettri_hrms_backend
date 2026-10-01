package com.haodaone.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.Locale;

@Configuration
public class ProductionSecurityGuard {

    private final String activeProfiles;
    private final String appEnvironment;
    private final String jwtSecret;
    private final String seedAdminPassword;

    public ProductionSecurityGuard(
            @Value("${spring.profiles.active:}") String activeProfiles,
            @Value("${app.environment:local}") String appEnvironment,
            @Value("${app.jwt.secret:}") String jwtSecret,
            @Value("${app.seed.admin-password:}") String seedAdminPassword) {
        this.activeProfiles = activeProfiles;
        this.appEnvironment = appEnvironment;
        this.jwtSecret = jwtSecret;
        this.seedAdminPassword = seedAdminPassword;
    }

    @PostConstruct
    public void validate() {
        String effectiveEnvironment = (appEnvironment == null ? "" : appEnvironment).toLowerCase(Locale.ROOT);
        String profiles = (activeProfiles == null ? "" : activeProfiles).toLowerCase(Locale.ROOT);
        boolean prodLike = effectiveEnvironment.contains("prod")
                || effectiveEnvironment.contains("production")
                || profiles.contains("prod")
                || profiles.contains("production");

        if (!prodLike) {
            return;
        }

        if (jwtSecret == null || jwtSecret.isBlank() || "local-dev-only-secret-change-before-deploying-anywhere-real-1234567890".equals(jwtSecret)) {
            throw new IllegalStateException("Production startup requires a unique APP/JWT secret; the default development JWT secret is not allowed.");
        }
        if (seedAdminPassword == null || seedAdminPassword.isBlank() || "ChangeMe123!".equals(seedAdminPassword)) {
            throw new IllegalStateException("Production startup requires a unique APP_SEED_ADMIN_PASSWORD; the development seed password is not allowed.");
        }
    }
}
