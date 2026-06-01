package com.xmlhelpline.schemalightener;

/**
 * Runtime exception thrown when a SchemaLightener transformation cannot be completed.
 */
public class SchemaLightenerException extends RuntimeException {
    public SchemaLightenerException(String message) {
        super(message);
    }

    public SchemaLightenerException(String message, Throwable cause) {
        super(message, cause);
    }
}
