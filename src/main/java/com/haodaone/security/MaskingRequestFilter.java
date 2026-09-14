package com.haodaone.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * A defensive filter that masks Authorization and agent token headers so
 * that downstream code that logs headers/requests doesn't accidentally
 * persist sensitive tokens. It wraps the HttpServletRequest and returns
 * a masked value for sensitive headers.
 */
public class MaskingRequestFilter extends OncePerRequestFilter {

    private static final List<String> SENSITIVE_HEADERS = List.of("authorization", "x-agent-token", "x-refresh-token");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        HttpServletRequest wrapped = new HttpServletRequestWrapper(request) {
            @Override
            public String getHeader(String name) {
                if (name == null) return null;
                for (String s : SENSITIVE_HEADERS) {
                    if (s.equalsIgnoreCase(name)) {
                        String original = super.getHeader(name);
                        return original == null ? null : mask(original);
                    }
                }
                return super.getHeader(name);
            }

            @Override
            public Enumeration<String> getHeaders(String name) {
                if (name == null) return super.getHeaders(name);
                for (String s : SENSITIVE_HEADERS) {
                    if (s.equalsIgnoreCase(name)) {
                        String v = super.getHeader(name);
                        if (v == null) return Collections.emptyEnumeration();
                        return Collections.enumeration(List.of(mask(v)));
                    }
                }
                return super.getHeaders(name);
            }

            @Override
            public Enumeration<String> getHeaderNames() {
                List<String> names = Collections.list(super.getHeaderNames());
                return Collections.enumeration(names);
            }

            private String mask(String v) {
                if (v == null) return null;
                if (v.length() <= 8) return "****";
                // keep prefix (e.g., 'Bearer ') if present but mask token body
                if (v.toLowerCase().startsWith("bearer ")) {
                    String prefix = v.substring(0, 7);
                    String token = v.substring(7);
                    int keep = Math.min(6, token.length());
                    return prefix + token.substring(0, keep) + "..." + token.substring(token.length()-2);
                }
                int keep = Math.min(6, v.length());
                return v.substring(0, keep) + "..." + v.substring(v.length()-2);
            }
        };

        filterChain.doFilter(wrapped, response);
    }
}
