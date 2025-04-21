package com.salesmsg.compliance.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a requested resource cannot be found.
 * This exception is automatically mapped to a 404 Not Found HTTP response.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

    /**
     * Create a new ResourceNotFoundException with a message.
     *
     * @param message The error message
     */
    public ResourceNotFoundException(String message) {
        super(message);
    }

    /**
     * Create a new ResourceNotFoundException with a message and cause.
     *
     * @param message The error message
     * @param cause The cause of the exception
     */
    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Create a new ResourceNotFoundException for a resource of a specific type and ID.
     *
     * @param resourceType The type of resource
     * @param resourceId The ID of the resource
     * @return A new ResourceNotFoundException
     */
    public static ResourceNotFoundException forResource(String resourceType, String resourceId) {
        return new ResourceNotFoundException(resourceType + " not found with ID: " + resourceId);
    }
}
