package com.haodaone.common.logging;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Keeps one exception from being written more than once while it moves through
 * the controller, service, repository, and exception handler layers.
 */
public final class ErrorLogCaptureContext {

    private static final ThreadLocal<Set<Throwable>> CAPTURED =
            ThreadLocal.withInitial(() -> Collections.newSetFromMap(new IdentityHashMap<>()));

    private ErrorLogCaptureContext() {
    }

    public static boolean markIfNew(Throwable throwable) {
        return CAPTURED.get().add(throwable);
    }

    public static void clear() {
        CAPTURED.remove();
    }
}
