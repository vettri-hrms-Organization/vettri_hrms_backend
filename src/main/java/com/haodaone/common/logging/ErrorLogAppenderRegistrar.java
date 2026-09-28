package com.haodaone.common.logging;

import ch.qos.logback.classic.LoggerContext;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Registers the database appender after Spring has created the database writer. */
@Component
public class ErrorLogAppenderRegistrar {

    private static final String APPENDER_NAME = "ERROR_LOG_DATABASE_APPENDER";

    private final ErrorLogService errorLogService;
    private ErrorLogAppender appender;
    private LoggerContext loggerContext;

    public ErrorLogAppenderRegistrar(ErrorLogService errorLogService) {
        this.errorLogService = errorLogService;
    }

    @PostConstruct
    public void register() {
        loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
        appender = new ErrorLogAppender(errorLogService);
        appender.setContext(loggerContext);
        appender.setName(APPENDER_NAME);
        appender.start();
        loggerContext.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).addAppender(appender);
    }

    @PreDestroy
    public void unregister() {
        if (loggerContext != null && appender != null) {
            loggerContext.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME).detachAppender(APPENDER_NAME);
            appender.stop();
        }
    }
}
