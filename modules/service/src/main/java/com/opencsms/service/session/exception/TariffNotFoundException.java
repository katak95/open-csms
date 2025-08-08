package com.opencsms.service.session.exception;

/**
 * Exception thrown when a tariff is not found.
 */
public class TariffNotFoundException extends RuntimeException {

    public TariffNotFoundException(String message) {
        super(message);
    }

    public TariffNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}