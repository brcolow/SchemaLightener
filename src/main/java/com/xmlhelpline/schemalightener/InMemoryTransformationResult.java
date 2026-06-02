package com.xmlhelpline.schemalightener;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * In-memory transformation result for APIs that do not write directly to a directory.
 */
public final class InMemoryTransformationResult {
    private final TransformationOperation operation;
    private final URI sourceSystemId;
    private final String primaryResult;
    private final Map<URI, String> resultDocuments;

    InMemoryTransformationResult(
            TransformationOperation operation,
            URI sourceSystemId,
            String primaryResult,
            Map<URI, String> resultDocuments) {
        this.operation = operation;
        this.sourceSystemId = sourceSystemId;
        this.primaryResult = primaryResult;
        this.resultDocuments = Collections.unmodifiableMap(new LinkedHashMap<URI, String>(resultDocuments));
    }

    /**
     * Get the transformation operation that produced this result.
     *
     * @return transformation operation that produced this result
     */
    public TransformationOperation getOperation() {
        return operation;
    }

    /**
     * Get the system ID of the source document supplied to the transformation.
     *
     * @return system ID of the source document supplied to the transformation
     */
    public URI getSourceSystemId() {
        return sourceSystemId;
    }

    /**
     * The primary XSLT result. The bundled stylesheets mostly write files using xsl:result-document,
     * so this value is often empty.
     *
     * @return primary transformation result
     */
    public String getPrimaryResult() {
        return primaryResult;
    }

    /**
     * Result documents keyed by the URI supplied to xsl:result-document.
     *
     * @return generated result documents
     */
    public Map<URI, String> getResultDocuments() {
        return resultDocuments;
    }

    /**
     * Find a generated result document by its trailing file name.
     *
     * @param fileName result document file name
     * @return generated XML when a matching result document exists
     */
    public Optional<String> findResultDocument(String fileName) {
        Objects.requireNonNull(fileName, "fileName must not be null");
        for (Map.Entry<URI, String> entry : resultDocuments.entrySet()) {
            String path = entry.getKey().getPath();
            if (path != null && path.endsWith("/" + fileName)) {
                return Optional.of(entry.getValue());
            }
            String uri = entry.getKey().toASCIIString();
            if (uri.endsWith("/" + fileName) || uri.endsWith(fileName)) {
                return Optional.of(entry.getValue());
            }
        }
        return Optional.empty();
    }

    /**
     * Find a generated result document by its trailing file name, throwing when it is not present.
     *
     * @param fileName result document file name
     * @return generated XML for the matching result document
     */
    public String requireResultDocument(String fileName) {
        return findResultDocument(fileName)
                .orElseThrow(() -> new SchemaLightenerException(
                        "Expected result document named " + fileName + " in " + resultDocuments.keySet()));
    }

    /**
     * Return the only generated result document when exactly one document was produced.
     *
     * @return the single result document XML, or empty when zero or multiple documents were produced
     */
    public Optional<String> singleResultDocument() {
        if (resultDocuments.size() == 1) {
            return Optional.of(resultDocuments.values().iterator().next());
        }
        return Optional.empty();
    }

    /**
     * Return the only generated result document, throwing when zero or multiple documents were produced.
     *
     * @return the single result document XML
     */
    public String requireSingleResultDocument() {
        return singleResultDocument()
                .orElseThrow(() -> new SchemaLightenerException(
                        "Expected exactly one result document but found "
                                + resultDocuments.size() + ": " + resultDocuments.keySet()));
    }
}
