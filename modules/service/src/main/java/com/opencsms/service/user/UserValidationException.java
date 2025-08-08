package com.opencsms.service.user;

/**
 * Exception thrown for user validation errors.
 */
public class UserValidationException extends RuntimeException {

    public UserValidationException(String message) {
        super(message);
    }

    public UserValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}