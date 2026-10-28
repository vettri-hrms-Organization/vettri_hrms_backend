package com.haodaone.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Cheap protection for the endpoints anyone on the internet can call:
 * a per-IP request budget on login, signup, password reset, public job
 * applications and checkout, and a body-size cap on uploads other than
 * software installers (which legitimately need the large multipart limit).
 *
 * Counters are in memory, which is enough for a single instance; put a shared
 * limiter (e.g. at the proxy) in front if the backend is ever scaled out.
 */
public class AbuseProtectionFilter extends OncePerRequestFilter {

    static final long MAX_UPLOAD_BYTES = 25L * 1024 * 1024;
    private static final long WINDOW_MILLIS = 60_000;

    private record Rule(String path, int perMinute) {}

    private static final List<Rule> RULES = List.of(
            new Rule("/api/auth/login", 20),
            new Rule("/api/auth/mfa/verify", 10),
            new Rule("/api/auth/mfa/email/send", 5),
            new Rule("/api/auth/sso/exchange", 20),
            new Rule("/api/auth/register", 5),
            new Rule("/api/auth/forgot-password", 5),
            new Rule("/api/auth/reset-password", 10),
            new Rule("/api/auth/invitation/activate", 10),
            new Rule("/api/auth/activate", 10),
            new Rule("/api/auth/refresh", 120),
            new Rule("/api/careers/apply", 5),
            new Rule("/api/billing/create-order", 10),
            new Rule("/api/billing/verify", 10)
    );

    private record Window(long startedAt, AtomicInteger count) {}

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;

    public AbuseProtectionFilter() {
        this(Clock.systemUTC());
    }

    AbuseProtectionFilter(Clock clock) {
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();

        String contentType = request.getContentType();
        if (contentType != null && contentType.toLowerCase().startsWith("multipart/")
                && !path.startsWith("/api/software/")
                && request.getContentLengthLong() > MAX_UPLOAD_BYTES) {
            reject(response, path, 413, "PAYLOAD_TOO_LARGE", "The file is too large. Upload a file under 25 MB.");
            return;
        }

        // Loopback is only ever local development and tests, never a real visitor.
        if ("POST".equalsIgnoreCase(request.getMethod()) && !isLoopback(request.getRemoteAddr())) {
            for (Rule rule : RULES) {
                if (path.equals(rule.path()) && !allow(rule, request.getRemoteAddr())) {
                    response.setHeader("Retry-After", "60");
                    reject(response, path, 429, "TOO_MANY_REQUESTS", "Too many attempts. Please wait a minute and try again.");
                    return;
                }
            }
        }
        chain.doFilter(request, response);
    }

    private boolean allow(Rule rule, String ip) {
        long now = clock.millis();
        if (windows.size() > 50_000) {
            windows.entrySet().removeIf(entry -> now - entry.getValue().startedAt() >= WINDOW_MILLIS);
        }
        Window window = windows.compute(rule.path() + "|" + ip, (key, current) ->
                current == null || now - current.startedAt() >= WINDOW_MILLIS ? new Window(now, new AtomicInteger()) : current);
        return window.count().incrementAndGet() <= rule.perMinute();
    }

    private static boolean isLoopback(String ip) {
        return "127.0.0.1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip);
    }

    private static void reject(HttpServletResponse response, String path, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"status\":" + status + ",\"error\":\"" + code + "\",\"code\":\"" + code
                + "\",\"message\":\"" + message + "\",\"path\":\"" + path.replace("\"", "") + "\"}");
    }
}
