package com.haodaone.common.logging;

import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Records exceptions thrown by application beans outside an HTTP request.
 * Request scoped failures are recorded by GlobalExceptionHandler, where the
 * final HTTP status is known.
 */
@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class ErrorLoggingAspect {

    private final ErrorLogService errorLogService;

    public ErrorLoggingAspect(ErrorLogService errorLogService) {
        this.errorLogService = errorLogService;
    }

    @Around("execution(* com.haodaone..*(..)) && !within(com.haodaone.common.logging..*)")
    public Object captureException(ProceedingJoinPoint joinPoint) throws Throwable {
        try {
            return joinPoint.proceed();
        } catch (Throwable throwable) {
            HttpServletRequest request = currentRequest();
            if (request == null) {
                errorLogService.capture(throwable, null, 500, joinPoint.getSignature().toShortString());
            }
            throw throwable;
        }
    }

    private HttpServletRequest currentRequest() {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return servletAttributes.getRequest();
        }
        return null;
    }
}
