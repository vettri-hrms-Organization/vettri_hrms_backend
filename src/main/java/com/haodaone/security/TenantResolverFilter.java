package com.haodaone.security;

import com.haodaone.company.repository.CompanyRepository;
import com.haodaone.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;

/**
 * After authentication is established, resolve the current tenant from
 * the authenticated principal and populate TenantContext for the request.
 */
public class TenantResolverFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(TenantResolverFilter.class);
    private static final String COMPANY_HEADER = "X-Company-Id";
    private final CompanyRepository companyRepository;
    private final org.springframework.beans.factory.ObjectProvider<com.haodaone.supportaccess.SupportAccessService> supportAccess;

    public TenantResolverFilter(CompanyRepository companyRepository) {
        this(companyRepository, null);
    }

    public TenantResolverFilter(CompanyRepository companyRepository,
                                org.springframework.beans.factory.ObjectProvider<com.haodaone.supportaccess.SupportAccessService> supportAccess) {
        this.companyRepository = companyRepository;
        this.supportAccess = supportAccess;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Always run; if no authenticated principal, leave TenantContext empty
        return false;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null) {
                Object principal = auth.getPrincipal();
                if (principal instanceof com.haodaone.security.CustomUserPrincipal) {
                    com.haodaone.security.CustomUserPrincipal p = (com.haodaone.security.CustomUserPrincipal) principal;
                    Long companyId = p.getCompanyId();
                    String requestedCompanyId = request.getHeader(COMPANY_HEADER);
                    if (requestedCompanyId != null && !requestedCompanyId.isBlank()) {
                        if (!isSuperAdmin(auth)) {
                            writeError(response, HttpServletResponse.SC_FORBIDDEN, "Company selection is only available to SUPER_ADMIN");
                            return;
                        }
                        try {
                            companyId = Long.valueOf(requestedCompanyId);
                        } catch (NumberFormatException ex) {
                            writeError(response, HttpServletResponse.SC_BAD_REQUEST, "Invalid company selection");
                            return;
                        }
                        if (!companyRepository.existsById(companyId)) {
                            writeError(response, HttpServletResponse.SC_BAD_REQUEST, "Selected company does not exist");
                            return;
                        }
                        // Platform staff only enter a company the company itself has opened to support
                        // (or a Vettri demo workspace); read-only grants also refuse writes.
                        com.haodaone.supportaccess.SupportAccessService gate = supportAccess == null ? null : supportAccess.getIfAvailable();
                        String refusal = gate == null ? null : gate.refusalFor(companyId, request.getMethod());
                        if (refusal != null) {
                            writeError(response, HttpServletResponse.SC_FORBIDDEN, refusal);
                            return;
                        }
                    }
                    if (companyId != null) {
                        TenantContext.setCurrentTenant(companyId);
                    }
                } else if (principal instanceof com.haodaone.monitoring.entity.MonitoredDevice) {
                    com.haodaone.monitoring.entity.MonitoredDevice device = (com.haodaone.monitoring.entity.MonitoredDevice) principal;
                    if (device.getCompany() != null) {
                        TenantContext.setCurrentTenant(device.getCompany().getId());
                    }
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private boolean isSuperAdmin(Authentication auth) {
        Set<String> authorities = auth.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .collect(java.util.stream.Collectors.toSet());
        return authorities.contains("ROLE_SUPER_ADMIN") || authorities.contains("SUPER_ADMIN");
    }

    private void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
