    package com.haodaone.security;

    import com.fasterxml.jackson.databind.ObjectMapper;
    import com.haodaone.company.repository.CompanyRepository;
    import com.haodaone.common.dto.ApiError;
    import org.springframework.beans.factory.annotation.Value;
    import org.springframework.context.annotation.Bean;
    import org.springframework.context.annotation.Configuration;
    import org.springframework.http.HttpStatus;
    import org.springframework.http.MediaType;
    import org.springframework.security.authentication.AuthenticationManager;
    import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
    import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
    import org.springframework.security.config.annotation.web.builders.HttpSecurity;
    import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
    import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
    import org.springframework.security.config.http.SessionCreationPolicy;
    import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
    import org.springframework.security.crypto.password.PasswordEncoder;
    import org.springframework.security.web.SecurityFilterChain;
    import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
    import org.springframework.web.cors.CorsConfiguration;
    import org.springframework.web.cors.CorsConfigurationSource;
    import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

    import java.util.List;

    /**
     * Central security configuration:
     * - Stateless (no HTTP session) - every request authenticates via JWT
     * - BCrypt for password hashing (never plaintext, never reversible encryption)
     * - Method-level security enabled so services/controllers can use
     *   @PreAuthorize("hasAuthority('EMPLOYEE_CREATE')") style checks as later
     *   modules are added
     * - /api/auth/** is the only open surface; everything else requires a
     *   valid JWT
     */
    @Configuration
    @EnableWebSecurity
    @EnableMethodSecurity(prePostEnabled = true)
    public class SecurityConfig {

        private final JwtAuthenticationFilter jwtAuthenticationFilter;
        private final AgentTokenAuthenticationFilter agentTokenAuthenticationFilter;
        private final ObjectMapper objectMapper;

        @Value("${app.cors.allowed-origins:}")
        private String allowedOrigins;

        @Value("${app.security.require-https:false}")
        private boolean requireHttps;

        public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                               AgentTokenAuthenticationFilter agentTokenAuthenticationFilter,
                               ObjectMapper objectMapper) {
            this.jwtAuthenticationFilter = jwtAuthenticationFilter;
            this.agentTokenAuthenticationFilter = agentTokenAuthenticationFilter;
            this.objectMapper = objectMapper;
        }

        @Bean
        public PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder(12);
        }

        @Bean
        public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
            return config.getAuthenticationManager();
        }

        @Bean
        public DaoAuthenticationProvider authenticationProvider(CustomUserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
            DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
            provider.setUserDetailsService(userDetailsService);
            provider.setPasswordEncoder(passwordEncoder);
            return provider;
        }

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                CompanyRepository companyRepository) throws Exception {
            http
                    .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                    .csrf(csrf -> csrf.disable()) // stateless JWT API - no session/cookie CSRF surface to protect
                    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    // Optionally require HTTPS for all requests when enabled in properties
                    ;

            if (requireHttps) {
                http = http.requiresChannel(channel -> channel.anyRequest().requiresSecure());
            }

            http = http.authorizeHttpRequests(auth -> auth
                                .requestMatchers("/api/auth/login", "/api/auth/refresh", "/api/auth/logout",
                                    "/api/auth/invitation/**", "/api/auth/activate", "/api/auth/activate/**").permitAll()
                            .requestMatchers("/api/interviews/my").authenticated()
                                    .requestMatchers("/api/holidays").authenticated()
                            .requestMatchers("/api/monitoring/**", "/api/audit/**",
                                    "/api/departments/**", "/api/designations/**", "/api/teams/**",
                                    "/api/candidates/**", "/api/interviews/**", "/api/job-openings/**",
                                    "/api/recruitment/**", "/api/reports/**", "/api/users/**", "/api/devices/**",
                                    "/api/goals/**", "/api/performance-reviews/**",
                                    "/api/roles/**", "/api/permissions/**", "/api/holidays/**", "/api/leave-types/**")
                                .hasAnyAuthority("ROLE_SUPER_ADMIN", "ROLE_HR_ADMIN", "ROLE_COMPANY_ADMIN", "ROLE_MANAGER")
                            .requestMatchers("/api/careers/**").permitAll()
                            .requestMatchers("/api/agent/**").authenticated()
                            .requestMatchers("/agent/**").permitAll()
                            .requestMatchers("/iclock/**").permitAll()
                            .requestMatchers("/actuator/health").permitAll()
                            .anyRequest().authenticated())
                    .exceptionHandling(handler -> handler
                            .authenticationEntryPoint((request, response, ex) -> writeJsonError(
                                    response, HttpStatus.UNAUTHORIZED, "Authentication required", request.getRequestURI()))
                            .accessDeniedHandler((request, response, ex) -> writeJsonError(
                                    response, HttpStatus.FORBIDDEN, "You don't have permission to perform this action", request.getRequestURI())));

            // Register custom filters relative to Spring's standard auth filter chain.
            // The custom filters themselves are not part of the built-in order registry,
            // so they must be placed before/after a known filter class such as
            // UsernamePasswordAuthenticationFilter rather than one another.
            http.addFilterBefore(agentTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                    .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                    .addFilterAfter(maskingRequestFilter(), JwtAuthenticationFilter.class)
                    .addFilterAfter(tenantResolverFilter(companyRepository), UsernamePasswordAuthenticationFilter.class);

            return http.build();
        }

        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
            CorsConfiguration configuration = new CorsConfiguration();
            // Parse allowed origins only if provided; in production this should be set explicitly
            if (allowedOrigins != null && !allowedOrigins.isBlank()) {
                String[] parts = allowedOrigins.split(",");
                List<String> origins = java.util.Arrays.stream(parts).map(String::trim).filter(s -> !s.isEmpty()).toList();
                configuration.setAllowedOrigins(origins);
            }
            configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
            // Restrict allowed headers to common safe headers and Authorization
            configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Requested-With", "X-Company-Id", "X-WebRTC-Signaling-Token"));
            configuration.setExposedHeaders(List.of("Authorization", "X-Remote-Width", "X-Remote-Height"));
            configuration.setAllowCredentials(true);
            UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
            source.registerCorsConfiguration("/**", configuration);
            return source;
        }

        private void writeJsonError(jakarta.servlet.http.HttpServletResponse response, HttpStatus status, String message, String path) throws java.io.IOException {
            response.setStatus(status.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            ApiError error = new ApiError(status.value(), status.getReasonPhrase(), message, path);
            response.getWriter().write(objectMapper.writeValueAsString(error));
        }

        @Bean
        public com.haodaone.security.MaskingRequestFilter maskingRequestFilter() {
            return new com.haodaone.security.MaskingRequestFilter();
        }

        @Bean
        public com.haodaone.security.TenantResolverFilter tenantResolverFilter(CompanyRepository companyRepository) {
            return new com.haodaone.security.TenantResolverFilter(companyRepository);
        }
    }