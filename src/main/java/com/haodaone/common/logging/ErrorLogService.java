package com.haodaone.common.logging;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.haodaone.tenant.TenantContext;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxy;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;

/**
 * Persists application errors in a separate transaction. All callers are
 * deliberately best effort: a failure while writing an error row must never
 * replace or hide the original application failure.
 */
@Service
public class ErrorLogService {

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public ErrorLogService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        if (jdbcTemplate.getDataSource() == null) {
            throw new IllegalStateException("A data source is required for database error logging");
        }
        this.transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(jdbcTemplate.getDataSource()));
        this.transactionTemplate.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
    }

    public void capture(Throwable throwable, HttpServletRequest request, int statusCode, String source) {
        if (throwable == null || !ErrorLogCaptureContext.markIfNew(throwable)) {
            return;
        }

        try {
            HttpServletRequest currentRequest = request != null ? request : currentRequest();
            persist(() -> insert(
                    throwable.getClass().getName(), safeMessage(throwable), stackTrace(throwable),
                    currentRequest, statusCode, source));
        } catch (Throwable loggingFailure) {
            // Do not call log.error here. The database appender would recurse.
            System.err.println("Could not persist application error log: " + loggingFailure.getMessage());
        }
    }

    /**
     * Receives ERROR events from every SLF4J/Logback logger in the application.
     * This covers exceptions that are caught and logged inside a service.
     */
    public void captureLogEvent(ILoggingEvent event) {
        if (event == null) {
            return;
        }

        Throwable throwable = extractThrowable(event.getThrowableProxy());
        if (throwable != null) {
            capture(throwable, null, 500, event.getLoggerName());
            return;
        }

        try {
            persist(() -> insert(
                    "LOG_EVENT", safeText(event.getFormattedMessage()), callerStackTrace(event),
                    currentRequest(), 500, event.getLoggerName()));
        } catch (Throwable loggingFailure) {
            System.err.println("Could not persist application log event: " + loggingFailure.getMessage());
        }
    }

    private void persist(Runnable insertOperation) {
        transactionTemplate.executeWithoutResult(status -> insertOperation.run());
    }

    private void insert(String errorType, String errorMessage, String stackTrace,
                        HttpServletRequest request, int statusCode, String source) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication != null && authentication.isAuthenticated()
                ? safeText(authentication.getName()) : "anonymous";
        Long companyId = TenantContext.getCurrentTenant();

        jdbcTemplate.update("""
                INSERT INTO error_logs
                    (error_type, error_message, stack_trace, request_uri, request_method,
                     username, ip_address, company_id, http_status, source, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                safeText(errorType),
                safeText(errorMessage),
                safeText(stackTrace),
                request == null ? null : safeText(request.getRequestURI()),
                request == null ? null : safeText(request.getMethod()),
                username,
                request == null ? null : safeText(request.getRemoteAddr()),
                companyId,
                statusCode,
                safeText(source),
                LocalDateTime.now()
        );
    }

    private HttpServletRequest currentRequest() {
        var attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes servletAttributes) {
            return servletAttributes.getRequest();
        }
        return null;
    }

    private Throwable extractThrowable(IThrowableProxy proxy) {
        return proxy instanceof ThrowableProxy throwableProxy ? throwableProxy.getThrowable() : null;
    }

    private String safeMessage(Throwable throwable) {
        return safeText(throwable.getMessage() == null ? throwable.toString() : throwable.getMessage());
    }

    private String stackTrace(Throwable throwable) {
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    private String callerStackTrace(ILoggingEvent event) {
        StringBuilder stack = new StringBuilder();
        if (event.getCallerData() != null) {
            for (StackTraceElement element : event.getCallerData()) {
                stack.append("at ").append(element).append(System.lineSeparator());
            }
        }
        return stack.length() == 0 ? "No stack trace was attached to this log event." : stack.toString();
    }

    private String safeText(String value) {
        return value == null ? null : value;
    }
}
