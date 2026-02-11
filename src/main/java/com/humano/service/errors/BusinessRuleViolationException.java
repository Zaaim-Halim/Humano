package com.humano.service.errors;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a business rule is violated.
 * This exception is mapped to an HTTP 400 Bad Request response.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class BusinessRuleViolationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String errorCode;

    /**
     * Constructs a new business rule violation exception with the specified detail message.
     *
     * @param message the detail message
     */
    public BusinessRuleViolationException(String message) {
        super(message);
        this.errorCode = "BUSINESS_RULE_VIOLATION";
    }

    /**
     * Constructs a new business rule violation exception with the specified error code and message.
     *
     * @param errorCode a machine-readable error code
     * @param message the detail message
     */
    public BusinessRuleViolationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * Constructs a new business rule violation exception with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public BusinessRuleViolationException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "BUSINESS_RULE_VIOLATION";
    }

    /**
     * Returns the error code associated with this exception.
     *
     * @return the error code
     */
    public String getErrorCode() {
        return errorCode;
    }
}
