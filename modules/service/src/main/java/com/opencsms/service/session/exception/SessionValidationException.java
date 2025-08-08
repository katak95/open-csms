package com.opencsms.service.session.exception;

/**
 * Exception thrown when session validation fails.
 */
public class SessionValidationException extends RuntimeException {

    public SessionValidationException(String message) {
        super(message);
    }

    public SessionValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}