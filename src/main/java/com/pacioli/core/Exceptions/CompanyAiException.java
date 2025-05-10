package com.pacioli.core.Exceptions;

/**
 * Exception thrown when there is an error communicating with the Company AI service
 */
public class CompanyAiException extends RuntimeException {

    /**
     * Constructs a new CompanyAiException with the specified detail message.
     *
     * @param message the detail message
     */
    public CompanyAiException(String message) {
        super(message);
    }

    /**
     * Constructs a new CompanyAiException with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause
     */
    public CompanyAiException(String message, Throwable cause) {
        super(message, cause);
    }
}
