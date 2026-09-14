package com.haodaone.security;

import com.haodaone.monitoring.entity.MonitoredDevice;
import com.haodaone.monitoring.repository.MonitoredDeviceRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Runs once per request, scoped to /api/agent/** by SecurityConfig's
 * request matcher: pulls the Bearer token HaodaOne.Agent/Services/
 * ApiClientService.cs attaches (BuildRequest -> Authorization: Bearer
 * {token}, token decrypted from DPAPI on the agent side), hashes it with
 * the same SHA-256 scheme DeviceEnrollmentService used to store it, and -
 * if it matches an active, non-deleted MonitoredDevice - populates the
 * SecurityContext with that device as principal. Mirrors
 * JwtAuthenticationFilter's shape and fail-open-to-anonymous behavior on a
 * missing/invalid token; the actual 401 is still produced by Spring
 * Security's entry point, not this filter.
 */
@Component
public class AgentTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AgentTokenAuthenticationFilter.class);
    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AGENT_PATH_PREFIX = "/api/agent/";

    private final MonitoredDeviceRepository deviceRepository;

    public AgentTokenAuthenticationFilter(MonitoredDeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        // Scoped to agent endpoints only - every other request continues straight
        // through to JwtAuthenticationFilter, unaffected by this filter's existence.
        return !request.getServletPath().startsWith(AGENT_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader(AUTH_HEADER);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String rawToken = authHeader.substring(BEARER_PREFIX.length());

        try {
            Optional<MonitoredDevice> match = deviceRepository.findByAgentTokenHashAndDeletedFalse(hash(rawToken));

            if (match.isPresent() && match.get().isActive()) {
                MonitoredDevice device = match.get();

                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        device, null, List.of(new SimpleGrantedAuthority("AGENT_DEVICE")));
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authToken);
            } else if (match.isPresent()) {
                log.debug("Agent token matched a deactivated device {} - rejecting", match.get().getId());
            }
        } catch (Exception ex) {
            log.debug("Agent token validation failed for request {}: {}", request.getRequestURI(), ex.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
