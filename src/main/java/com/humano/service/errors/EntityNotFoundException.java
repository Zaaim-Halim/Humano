package com.humano.service.errors;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when an entity cannot be found by its identifier.
 * This exception is mapped to an HTTP 404 Not Found response.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class EntityNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new entity not found exception with the specified detail message.
     *
     * @param message the detail message
     */
    public EntityNotFoundException(String message) {
        super(message);
    }

    /**
     * Constructs a new entity not found exception with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public EntityNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new entity not found exception for a specific entity type and ID.
     *
     * @param entityName the name of the entity
     * @param id the ID that was not found
     * @return the exception
     */
    public static EntityNotFoundException create(String entityName, Object id) {
        return new EntityNotFoundException(entityName + " not found with ID: " + id);
    }
}
