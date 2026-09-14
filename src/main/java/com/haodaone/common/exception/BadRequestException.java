package com.haodaone.common.exception;

/** Thrown for invalid input or a business rule violation (e.g. duplicate username). Maps to HTTP 400. */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
