package com.haodaone.common.exception;

/** Thrown when a requested entity doesn't exist (or is soft-deleted). Maps to HTTP 404. */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
