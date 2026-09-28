package com.haodaone.common.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

/** Logback bridge that persists every ERROR event emitted by the application. */
public class ErrorLogAppender extends AppenderBase<ILoggingEvent> {

    private final ErrorLogService errorLogService;

    public ErrorLogAppender(ErrorLogService errorLogService) {
        this.errorLogService = errorLogService;
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (event.getLevel().isGreaterOrEqual(Level.ERROR)) {
            errorLogService.captureLogEvent(event);
        }
    }
}
