package com.xmlhelpline.schemalightener;

/**
 * Runtime exception thrown when a SchemaLightener transformation cannot be completed.
 */
public class SchemaLightenerException extends RuntimeException {
    /**
     * Create an exception with a message.
     *
     * @param message exception message
     */
    public SchemaLightenerException(String message) {
        super(message);
    }

    /**
     * Create an exception with a message and cause.
     *
     * @param message exception message
     * @param cause root cause
     */
    public SchemaLightenerException(String message, Throwable cause) {
        super(message, cause);
    }
}
