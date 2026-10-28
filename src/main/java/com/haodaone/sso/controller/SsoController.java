package com.haodaone.sso.controller;

import com.haodaone.auth.dto.LoginResponse;
import com.haodaone.common.exception.BadRequestException;
import com.haodaone.sso.dto.SsoDTO;
import com.haodaone.sso.service.SsoPolicyService;
import com.haodaone.sso.service.SsoProperties;
import com.haodaone.sso.service.SsoService;
import com.haodaone.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

/**
 * Public: GET providers, GET {provider}/start, GET {provider}/callback, POST exchange.
 * Admin (COMPANY_ADMIN / SUPER_ADMIN / USER_MANAGE with organisation scope): GET/PUT settings.
 */
@RestController
@RequestMapping("/api/auth/sso")
public class SsoController {

    private static final String ADMIN_RULE =
            "hasRole('COMPANY_ADMIN') or hasRole('SUPER_ADMIN') or @authorizationService.hasOrganizationScope('USER_MANAGE')";

    private final SsoService ssoService;
    private final SsoPolicyService policyService;
    private final SsoProperties properties;

    public SsoController(SsoService ssoService, SsoPolicyService policyService, SsoProperties properties) {
        this.ssoService = ssoService;
        this.policyService = policyService;
        this.properties = properties;
    }

    @GetMapping("/providers")
    public SsoDTO.ProvidersResponse providers() {
        return ssoService.providers();
    }

    @GetMapping("/{provider}/start")
    public ResponseEntity<Void> start(@PathVariable String provider, HttpServletRequest request, HttpServletResponse response) {
        try {
            String location = ssoService.start(provider, request, response);
            return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, location)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store").build();
        } catch (BadRequestException ex) {
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(properties.frontendLoginUrl() + "?sso_error=unavailable")).build();
        }
    }

    @GetMapping("/{provider}/callback")
    public ResponseEntity<Void> callback(@PathVariable String provider,
                                         @RequestParam(required = false) String code,
                                         @RequestParam(required = false) String state,
                                         @RequestParam(required = false) String error,
                                         HttpServletRequest request, HttpServletResponse response) {
        SsoService.Redirect redirect = ssoService.callback(provider, code, state, error, request, response);
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, redirect.url())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Referrer-Policy", "no-referrer").build();
    }

    @PostMapping("/exchange")
    public ResponseEntity<LoginResponse> exchange(@Valid @RequestBody SsoDTO.ExchangeRequest body, HttpServletRequest request) {
        return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "no-store").body(ssoService.exchange(body.code(), request));
    }

    @GetMapping("/settings")
    @PreAuthorize(ADMIN_RULE)
    public SsoDTO.Settings settings() {
        return policyService.get(companyId());
    }

    @PutMapping("/settings")
    @PreAuthorize(ADMIN_RULE)
    public SsoDTO.Settings updateSettings(@RequestBody SsoDTO.SettingsRequest request) {
        return policyService.update(companyId(), request);
    }

    private static Long companyId() {
        Long companyId = TenantContext.getCurrentTenant();
        if (companyId == null) throw new BadRequestException("Select a company first");
        return companyId;
    }
}
