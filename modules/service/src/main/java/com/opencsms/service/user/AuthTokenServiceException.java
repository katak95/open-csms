package com.opencsms.service.user;

/**
 * Exception thrown for auth token service errors.
 */
public class AuthTokenServiceException extends RuntimeException {

    public AuthTokenServiceException(String message) {
        super(message);
    }

    public AuthTokenServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}