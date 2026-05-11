package com.channel.integration.exception;

/**
 * Base exception for all Integration Framework failures.
 * Allows callers to catch framework-level errors with a single catch clause.
 */
public class IntegrationException extends RuntimeException {

    private final String serviceId;
    private final String errorCode;

    public IntegrationException(String serviceId, String errorCode, String message) {
        super(message);
        this.serviceId = serviceId;
        this.errorCode = errorCode;
    }

    public IntegrationException(String serviceId, String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.serviceId = serviceId;
        this.errorCode = errorCode;
    }

    public String getServiceId() { return serviceId; }
    public String getErrorCode() { return errorCode; }
}
