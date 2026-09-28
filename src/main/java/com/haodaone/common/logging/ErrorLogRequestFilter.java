package com.haodaone.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Catches failures raised before Spring MVC can invoke GlobalExceptionHandler. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ErrorLogRequestFilter extends OncePerRequestFilter {

    private final ErrorLogService errorLogService;

    public ErrorLogRequestFilter(ErrorLogService errorLogService) {
        this.errorLogService = errorLogService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } catch (Throwable throwable) {
            errorLogService.capture(throwable, request, response.getStatus() >= 400 ? response.getStatus() : 500,
                    "servlet-filter");
            rethrow(throwable);
        } finally {
            ErrorLogCaptureContext.clear();
        }
    }

    private void rethrow(Throwable throwable) throws ServletException, IOException {
        if (throwable instanceof ServletException servletException) throw servletException;
        if (throwable instanceof IOException ioException) throw ioException;
        if (throwable instanceof RuntimeException runtimeException) throw runtimeException;
        if (throwable instanceof Error error) throw error;
        throw new ServletException(throwable);
    }
}
