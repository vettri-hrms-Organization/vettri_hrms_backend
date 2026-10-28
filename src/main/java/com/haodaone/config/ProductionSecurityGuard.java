package com.haodaone.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Configuration
public class ProductionSecurityGuard {

    private final String activeProfiles;
    private final String appEnvironment;
    private final String jwtSecret;
    private final String seedAdminPassword;
    private final String datasourceUrl;

    public ProductionSecurityGuard(String activeProfiles, String appEnvironment, String jwtSecret, String seedAdminPassword) {
        this(activeProfiles, appEnvironment, jwtSecret, seedAdminPassword, "");
    }

    @Autowired
    public ProductionSecurityGuard(
            @Value("${spring.profiles.active:}") String activeProfiles,
            @Value("${app.environment:local}") String appEnvironment,
            @Value("${app.jwt.secret:}") String jwtSecret,
            @Value("${app.seed.admin-password:}") String seedAdminPassword,
            @Value("${spring.datasource.url:}") String datasourceUrl) {
        this.activeProfiles = activeProfiles;
        this.appEnvironment = appEnvironment;
        this.jwtSecret = jwtSecret;
        this.seedAdminPassword = seedAdminPassword;
        this.datasourceUrl = datasourceUrl;
    }

    @PostConstruct
    public void validate() {
        String effectiveEnvironment = (appEnvironment == null ? "" : appEnvironment).toLowerCase(Locale.ROOT);
        String profiles = (activeProfiles == null ? "" : activeProfiles).toLowerCase(Locale.ROOT);
        boolean explicitNonProd = effectiveEnvironment.equals("dev") || effectiveEnvironment.equals("development")
                || effectiveEnvironment.equals("test") || profiles.contains("test");
        // Fail closed: a deployment pointed at a non-local database is treated as production
        // even if nobody remembered to set APP_ENVIRONMENT (the default is "local").
        boolean prodLike = effectiveEnvironment.contains("prod")
                || profiles.contains("prod")
                || (!explicitNonProd && isRemoteDatabase(datasourceUrl));

        if (!prodLike) {
            return;
        }

        if (jwtSecret == null || jwtSecret.isBlank() || "local-dev-only-secret-change-before-deploying-anywhere-real-1234567890".equals(jwtSecret)) {
            throw new IllegalStateException("Production startup requires a unique APP/JWT secret; the default development JWT secret is not allowed.");
        }
        if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("Production startup requires an APP/JWT secret of at least 32 UTF-8 bytes.");
        }
        if (seedAdminPassword == null || seedAdminPassword.isBlank() || "ChangeMe123!".equals(seedAdminPassword)) {
            throw new IllegalStateException("Production startup requires a unique APP_SEED_ADMIN_PASSWORD; the development seed password is not allowed.");
        }
    }

    private static boolean isRemoteDatabase(String url) {
        if (url == null || url.isBlank()) return false;
        String value = url.toLowerCase(Locale.ROOT);
        return !(value.startsWith("jdbc:h2:") || value.contains("//localhost") || value.contains("//127.0.0.1")
                || value.contains("//host.docker.internal") || value.contains("//[::1]"));
    }
}
