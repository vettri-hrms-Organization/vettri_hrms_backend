package com.haodaone.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT authentication filter for normal user authentication.
 *
 * Agent endpoints under /api/agent/** use a separate per-device
 * AgentTokenAuthenticationFilter, so this filter must skip them.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log =
            LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String TOKEN_QUERY_PARAM = "token";

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            CustomUserDetailsService userDetailsService) {

        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    /**
     * Agent requests use a device-specific bearer token, not a user JWT.
     * Therefore JwtAuthenticationFilter must completely skip /api/agent/**.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        return path.startsWith("/api/agent/")
                || path.startsWith("/agent/")
                || isPublicPath(path);
    }

    private boolean isPublicPath(String path) {
        return path.equals("/api/auth/login")
                || path.equals("/api/auth/refresh")
                || path.equals("/api/auth/logout")
                || path.startsWith("/api/auth/invitation/")
                || path.startsWith("/api/auth/activate/")
                || path.startsWith("/api/careers/")
                || path.equals("/actuator/health")
                || path.startsWith("/iclock/");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader(AUTH_HEADER);
        String token;

        /*
         * Normal authentication:
         * Authorization: Bearer <JWT>
         */
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            token = authHeader.substring(BEARER_PREFIX.length());
        } else {
            /*
             * Special case for SSE endpoint where EventSource cannot
             * send custom Authorization headers.
             */
            token = request.getParameter(TOKEN_QUERY_PARAM);
        }

        /*
         * No token -> let the rest of Spring Security handle
         * authentication/authorization.
         */
        if (token == null || token.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String username = jwtService.extractUsername(token);

            if (username != null
                    && SecurityContextHolder.getContext().getAuthentication() == null) {

                UserDetails userDetails =
                        userDetailsService.loadUserByUsername(username);

                boolean validToken =
                        jwtService.isTokenValid(token, username);

                boolean enabled =
                        userDetails.isEnabled();

                boolean notLocked =
                        userDetails.isAccountNonLocked();

                if (validToken && enabled && notLocked) {

                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(
                                    userDetails,
                                    null,
                                    userDetails.getAuthorities()
                            );

                    authToken.setDetails(
                            new WebAuthenticationDetailsSource()
                                    .buildDetails(request)
                    );

                    SecurityContextHolder
                            .getContext()
                            .setAuthentication(authToken);
                }
            }

        } catch (Exception ex) {

            /*
             * Invalid JWT must not crash the request.
             * Leave the request unauthenticated and let Spring Security
             * decide whether the endpoint requires authentication.
             */
            // Avoid logging raw exception messages to prevent any accidental leakage of token fragments
            log.debug("JWT validation failed for request {}: {}", request.getRequestURI(), ex.getClass().getSimpleName());

            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}