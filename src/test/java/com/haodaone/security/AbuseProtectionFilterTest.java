package com.haodaone.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AbuseProtectionFilterTest {

    private final AbuseProtectionFilter filter =
            new AbuseProtectionFilter(Clock.fixed(Instant.parse("2026-10-28T10:00:00Z"), ZoneOffset.UTC));

    private int post(String path, String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }

    @Test
    void forgotPasswordIsLimitedPerIp() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertEquals(200, post("/api/auth/forgot-password", "203.0.113.7"));
        }
        assertEquals(429, post("/api/auth/forgot-password", "203.0.113.7"));
        assertEquals(200, post("/api/auth/forgot-password", "203.0.113.8"));
    }

    @Test
    void unlistedPathsAndLoopbackAreNotLimited() throws Exception {
        for (int i = 0; i < 50; i++) {
            assertEquals(200, post("/api/employees", "203.0.113.9"));
            assertEquals(200, post("/api/auth/login", "127.0.0.1"));
        }
    }

    @Test
    void largeUploadsAreRejectedExceptSoftwareInstallers() throws Exception {
        MockHttpServletRequest resume = new MockHttpServletRequest("POST", "/api/careers/apply");
        resume.setContentType("multipart/form-data; boundary=x");
        resume.setContent(new byte[(int) AbuseProtectionFilter.MAX_UPLOAD_BYTES + 1]);
        MockHttpServletResponse resumeResponse = new MockHttpServletResponse();
        filter.doFilter(resume, resumeResponse, new MockFilterChain());
        assertEquals(413, resumeResponse.getStatus());

        MockHttpServletRequest installer = new MockHttpServletRequest("POST", "/api/software/packages/1/versions");
        installer.setContentType("multipart/form-data; boundary=x");
        installer.setContent(new byte[(int) AbuseProtectionFilter.MAX_UPLOAD_BYTES + 1]);
        MockHttpServletResponse installerResponse = new MockHttpServletResponse();
        filter.doFilter(installer, installerResponse, new MockFilterChain());
        assertEquals(200, installerResponse.getStatus());
    }
}
