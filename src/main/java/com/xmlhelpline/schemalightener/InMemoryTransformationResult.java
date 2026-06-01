package com.xmlhelpline.schemalightener;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
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

    public TransformationOperation getOperation() {
        return operation;
    }

    public URI getSourceSystemId() {
        return sourceSystemId;
    }

    /**
     * The primary XSLT result. The bundled stylesheets mostly write files using xsl:result-document,
     * so this value is often empty.
     */
    public String getPrimaryResult() {
        return primaryResult;
    }

    /**
     * Result documents keyed by the URI supplied to xsl:result-document.
     */
    public Map<URI, String> getResultDocuments() {
        return resultDocuments;
    }

    public Optional<String> findResultDocument(String fileName) {
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
}
