package com.xmlhelpline.schemalightener;

/**
 * Supported SchemaLightener transformation operations.
 */
public enum TransformationOperation {
    /**
     * Flatten an XML Schema into the fewest possible schema files.
     */
    FLATTEN_SCHEMA,

    /**
     * Lighten an XML Schema using an XML instance as the subset indicator.
     */
    LIGHTEN_SCHEMA,

    /**
     * Flatten an XML Schema and then lighten the flattened result using an XML instance.
     */
    FLATTEN_AND_LIGHTEN_SCHEMA,

    /**
     * Flatten a WSDL and its schema dependencies.
     */
    FLATTEN_WSDL
}
