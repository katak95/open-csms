package com.opencsms.service.user;

/**
 * Exception thrown when an auth token is not found.
 */
public class AuthTokenNotFoundException extends RuntimeException {

    public AuthTokenNotFoundException(String message) {
        super(message);
    }

    public AuthTokenNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}