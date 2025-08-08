package com.opencsms.service.user;

/**
 * Exception thrown for role service errors.
 */
public class RoleServiceException extends RuntimeException {

    public RoleServiceException(String message) {
        super(message);
    }

    public RoleServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}