package com.haodaone.sso.service;

import java.util.Locale;
import java.util.Optional;

/** Supported OpenID Connect identity providers. */
public enum SsoProvider {
    GOOGLE("Google"),
    MICROSOFT("Microsoft");

    private final String displayName;

    SsoProvider(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public String pathName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Optional<SsoProvider> fromPath(String value) {
        if (value == null) return Optional.empty();
        for (SsoProvider provider : values()) {
            if (provider.pathName().equalsIgnoreCase(value.trim())) return Optional.of(provider);
        }
        return Optional.empty();
    }
}
