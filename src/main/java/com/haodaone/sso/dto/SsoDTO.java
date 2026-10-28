package com.haodaone.sso.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class SsoDTO {

    private SsoDTO() {
    }

    public record Provider(String id, String name) {
    }

    public record ProvidersResponse(List<Provider> providers) {
    }

    public record ExchangeRequest(@NotBlank @Size(max = 200) String code) {
    }

    public record Settings(boolean ssoOnly, Long breakGlassUserId, boolean autoProvisionEnabled,
                           List<String> autoProvisionDomains, boolean trustSsoMfa, List<Provider> configuredProviders) {
    }

    public record SettingsRequest(Boolean ssoOnly, Long breakGlassUserId, Boolean autoProvisionEnabled,
                                  List<String> autoProvisionDomains, Boolean trustSsoMfa) {
    }
}
