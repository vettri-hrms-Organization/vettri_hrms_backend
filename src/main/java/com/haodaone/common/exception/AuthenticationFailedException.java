package com.haodaone.common.exception;

/** Thrown for authentication failures - wrong credentials, expired or malformed tokens. Maps to HTTP 401. */
public class AuthenticationFailedException extends RuntimeException {
    public AuthenticationFailedException(String message) {
        super(message);
    }
}
