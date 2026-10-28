package com.haodaone.sso.service;

import com.haodaone.audit.service.AuditLogService;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.common.exception.ResourceNotFoundException;
import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.sso.dto.SsoDTO;
import com.haodaone.sso.entity.CompanySsoSetting;
import com.haodaone.sso.repository.CompanySsoSettingRepository;
import com.haodaone.user.entity.User;
import com.haodaone.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Company SSO policy: SSO-only enforcement (with break-glass admin), auto-provisioning, trusting IdP MFA. */
@Service
public class SsoPolicyService {

    private static final Pattern DOMAIN = Pattern.compile("^(?=.{3,253}$)([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$");
    private static final Set<String> PUBLIC_DOMAINS = Set.of("gmail.com", "googlemail.com", "outlook.com", "hotmail.com",
            "live.com", "yahoo.com", "yahoo.co.in", "icloud.com", "rediffmail.com", "proton.me", "protonmail.com");

    private final CompanySsoSettingRepository settings;
    private final CompanyRepository companies;
    private final UserRepository users;
    private final SsoProperties properties;
    private final AuditLogService auditLogService;

    public SsoPolicyService(CompanySsoSettingRepository settings, CompanyRepository companies, UserRepository users,
                            SsoProperties properties, AuditLogService auditLogService) {
        this.settings = settings;
        this.companies = companies;
        this.users = users;
        this.properties = properties;
        this.auditLogService = auditLogService;
    }

    /** True when the user's company is SSO-only and the user is not the break-glass admin (or a super admin). */
    @Transactional(readOnly = true)
    public boolean isPasswordLoginBlocked(User user) {
        if (user == null || user.getCompany() == null || hasRole(user, "SUPER_ADMIN")) return false;
        Optional<CompanySsoSetting> setting = settings.findByCompany_IdAndDeletedFalse(user.getCompany().getId());
        if (setting.isEmpty() || !setting.get().isSsoOnly()) return false;
        Long breakGlass = setting.get().getBreakGlassUserId();
        if (breakGlass != null) return !breakGlass.equals(user.getId());
        return !hasRole(user, "COMPANY_ADMIN");
    }

    @Transactional(readOnly = true)
    public boolean trustsSsoMfa(User user) {
        if (user == null || user.getCompany() == null) return false;
        return settings.findByCompany_IdAndDeletedFalse(user.getCompany().getId())
                .map(CompanySsoSetting::isTrustSsoMfa).orElse(false);
    }

    /** The single company that auto-provisions users of this verified email domain, if any. */
    @Transactional(readOnly = true)
    public Optional<CompanySsoSetting> autoProvisionCompanyFor(String email) {
        if (email == null || !email.contains("@")) return Optional.empty();
        String domain = email.substring(email.lastIndexOf('@') + 1).toLowerCase(Locale.ROOT);
        List<CompanySsoSetting> matches = settings.findAllByAutoProvisionEnabledTrueAndDeletedFalse().stream()
                .filter(s -> domains(s.getAutoProvisionDomains()).contains(domain))
                .filter(s -> s.getCompany() != null && !s.getCompany().isDeleted())
                .toList();
        return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
    }

    @Transactional(readOnly = true)
    public SsoDTO.Settings get(Long companyId) {
        return toDto(settings.findByCompany_IdAndDeletedFalse(companyId).orElse(null));
    }

    @Transactional
    public SsoDTO.Settings update(Long companyId, SsoDTO.SettingsRequest request) {
        CompanySsoSetting setting = settings.findByCompany_IdAndDeletedFalse(companyId).orElseGet(() -> {
            CompanySsoSetting created = new CompanySsoSetting();
            created.setCompany(companies.findById(companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Company not found")));
            return created;
        });
        boolean ssoOnly = request.ssoOnly() != null ? request.ssoOnly() : setting.isSsoOnly();
        Long breakGlass = request.breakGlassUserId();
        if (breakGlass != null) {
            User admin = users.findByIdAndCompanyIdAndDeletedFalse(breakGlass, companyId)
                    .orElseThrow(() -> new BadRequestException("Break-glass user must belong to your company"));
            if (!hasRole(admin, "COMPANY_ADMIN")) {
                throw new BadRequestException("The break-glass user must be a company admin");
            }
        }
        if (ssoOnly && configuredProviders().isEmpty()) {
            throw new BadRequestException("No SSO provider is configured on this server, so password sign-in cannot be turned off");
        }
        boolean autoProvision = request.autoProvisionEnabled() != null ? request.autoProvisionEnabled() : setting.isAutoProvisionEnabled();
        List<String> domainList = request.autoProvisionDomains() != null
                ? cleanDomains(request.autoProvisionDomains()) : new ArrayList<>(domains(setting.getAutoProvisionDomains()));
        if (autoProvision && domainList.isEmpty()) {
            throw new BadRequestException("Add at least one company email domain for auto-provisioning");
        }
        if (autoProvision) {
            for (CompanySsoSetting other : settings.findAllByAutoProvisionEnabledTrueAndDeletedFalse()) {
                if (other.getCompany() != null && companyId.equals(other.getCompany().getId())) continue;
                for (String domain : domainList) {
                    if (domains(other.getAutoProvisionDomains()).contains(domain)) {
                        throw new BadRequestException("The domain " + domain + " is already used for auto-provisioning by another workspace");
                    }
                }
            }
        }
        setting.setSsoOnly(ssoOnly);
        setting.setBreakGlassUserId(breakGlass);
        setting.setAutoProvisionEnabled(autoProvision);
        setting.setAutoProvisionDomains(domainList.isEmpty() ? null : String.join(",", domainList));
        if (request.trustSsoMfa() != null) setting.setTrustSsoMfa(request.trustSsoMfa());
        CompanySsoSetting saved = settings.save(setting);
        auditLogService.log("CompanySsoSetting", saved.getId(), "UPDATE",
                "SSO settings: ssoOnly=" + saved.isSsoOnly() + ", autoProvision=" + saved.isAutoProvisionEnabled()
                        + ", trustSsoMfa=" + saved.isTrustSsoMfa() + ", breakGlassUserId=" + saved.getBreakGlassUserId());
        return toDto(saved);
    }

    public List<SsoDTO.Provider> configuredProviders() {
        List<SsoDTO.Provider> providers = new ArrayList<>();
        for (SsoProvider provider : SsoProvider.values()) {
            if (properties.isConfigured(provider)) providers.add(new SsoDTO.Provider(provider.pathName(), provider.displayName()));
        }
        return providers;
    }

    private SsoDTO.Settings toDto(CompanySsoSetting s) {
        if (s == null) return new SsoDTO.Settings(false, null, false, List.of(), false, configuredProviders());
        return new SsoDTO.Settings(s.isSsoOnly(), s.getBreakGlassUserId(), s.isAutoProvisionEnabled(),
                new ArrayList<>(domains(s.getAutoProvisionDomains())), s.isTrustSsoMfa(), configuredProviders());
    }

    static List<String> cleanDomains(List<String> raw) {
        Set<String> result = new LinkedHashSet<>();
        for (String value : raw) {
            if (value == null || value.isBlank()) continue;
            String domain = value.trim().toLowerCase(Locale.ROOT).replaceFirst("^@", "");
            if (!DOMAIN.matcher(domain).matches()) throw new BadRequestException("Invalid domain: " + value);
            if (PUBLIC_DOMAINS.contains(domain)) {
                throw new BadRequestException(domain + " is a public email provider and cannot be used for auto-provisioning");
            }
            result.add(domain);
        }
        if (result.size() > 20) throw new BadRequestException("At most 20 domains are allowed");
        return new ArrayList<>(result);
    }

    private static Set<String> domains(String csv) {
        if (csv == null || csv.isBlank()) return Set.of();
        Set<String> result = new LinkedHashSet<>();
        Arrays.stream(csv.split(",")).map(String::trim).filter(d -> !d.isEmpty())
                .forEach(d -> result.add(d.toLowerCase(Locale.ROOT)));
        return result;
    }

    private static boolean hasRole(User user, String role) {
        return user.getRoles() != null && user.getRoles().stream()
                .anyMatch(r -> role.equals(r.getName()) || ("ROLE_" + role).equals(r.getName()));
    }
}
