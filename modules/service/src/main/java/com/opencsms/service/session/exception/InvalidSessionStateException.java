package com.opencsms.service.session.exception;

/**
 * Exception thrown when trying to perform invalid state transitions.
 */
public class InvalidSessionStateException extends RuntimeException {

    public InvalidSessionStateException(String message) {
        super(message);
    }

    public InvalidSessionStateException(String message, Throwable cause) {
        super(message, cause);
    }
}