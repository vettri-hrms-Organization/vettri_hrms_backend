package com.haodaone.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class MaskingRequestFilterTest {

    @Test
    public void masksAuthorizationHeader() throws ServletException, IOException {
        MaskingRequestFilter filter = new MaskingRequestFilter();
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.addHeader("Authorization", "Bearer abcdefghijklmnopqrstuvwxyz1234567890");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = (request, response) -> {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            String header = httpRequest.getHeader("Authorization");
            assertNotNull(header);
            assertTrue(header.startsWith("Bearer "), "Should preserve Bearer prefix");
            assertFalse(header.contains("abcdefghijklmnopqrstuvwxyz1234567890"), "Token body must be masked");
        };

        filter.doFilter(req, resp, chain);
    }
}
