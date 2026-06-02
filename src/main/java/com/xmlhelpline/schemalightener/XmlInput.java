package com.xmlhelpline.schemalightener;

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Reusable XML input for the in-memory SchemaLightener API.
 */
public final class XmlInput {
    private static final URI MEMORY_BASE_URI = URI.create("memory:/schemalightener/");

    private final String xml;
    private final Path path;
    private final URI systemId;

    private XmlInput(String xml, Path path, URI systemId) {
        this.xml = xml;
        this.path = path;
        this.systemId = systemId.normalize();
    }

    /**
     * Create reusable XML input from a string.
     * Most callers can use {@link #fromString(String, String)} with a logical path instead.
     *
     * @param xml XML document content
     * @param systemId absolute system ID used for advanced relative URI resolution
     * @return XML input
     */
    public static XmlInput fromString(String xml, URI systemId) {
        Objects.requireNonNull(xml, "xml must not be null");
        return new XmlInput(xml, null, requireAbsoluteSystemId(systemId));
    }

    /**
     * Create reusable XML input from a string with a logical in-memory path.
     * The logical path is used only to resolve relative XML Schema includes/imports
     * between in-memory documents. It must be relative, such as {@code schemas/root.xsd}.
     *
     * @param xml XML document content
     * @param logicalPath relative logical path used for in-memory URI resolution
     * @return XML input
     */
    public static XmlInput fromString(String xml, String logicalPath) {
        return fromString(xml, logicalPathSystemId(logicalPath));
    }

    /**
     * Create reusable XML input from a reader.
     * Most callers can use {@link #fromReader(Reader, String)} with a logical path instead.
     *
     * @param reader XML document reader
     * @param systemId absolute system ID used for advanced relative URI resolution
     * @return XML input
     */
    public static XmlInput fromReader(Reader reader, URI systemId) {
        Objects.requireNonNull(reader, "reader must not be null");
        try {
            return fromString(readAll(reader), systemId);
        } catch (IOException e) {
            throw new SchemaLightenerException("Unable to read XML input from reader", e);
        }
    }

    /**
     * Create reusable XML input from a reader with a logical in-memory path.
     * The logical path is used only to resolve relative XML Schema includes/imports
     * between in-memory documents. It must be relative, such as {@code schemas/root.xsd}.
     *
     * @param reader XML document reader
     * @param logicalPath relative logical path used for in-memory URI resolution
     * @return XML input
     */
    public static XmlInput fromReader(Reader reader, String logicalPath) {
        return fromReader(reader, logicalPathSystemId(logicalPath));
    }

    /**
     * Create reusable XML input from a file path.
     *
     * @param path path to a readable XML file
     * @return XML input
     */
    public static XmlInput fromPath(Path path) {
        Objects.requireNonNull(path, "path must not be null");
        Path normalizedPath = path.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalizedPath) || !Files.isReadable(normalizedPath)) {
            throw new SchemaLightenerException("path is not a readable file: " + normalizedPath);
        }
        return new XmlInput(null, normalizedPath, normalizedPath.toUri());
    }

    /**
     * Get the system ID used for relative URI resolution.
     *
     * @return absolute system ID used for relative URI resolution
     */
    public URI getSystemId() {
        return systemId;
    }

    Source toSource() {
        if (xml != null) {
            StreamSource source = new StreamSource(new StringReader(xml));
            source.setSystemId(systemId.toASCIIString());
            return source;
        }
        return new StreamSource(path.toFile());
    }

    private static URI requireAbsoluteSystemId(URI systemId) {
        Objects.requireNonNull(systemId, "systemId must not be null");
        if (!systemId.isAbsolute()) {
            throw new SchemaLightenerException("systemId must be absolute: " + systemId);
        }
        return systemId;
    }

    private static URI logicalPathSystemId(String logicalPath) {
        Objects.requireNonNull(logicalPath, "logicalPath must not be null");
        String normalizedPath = logicalPath.replace('\\', '/');
        if (normalizedPath.trim().isEmpty()) {
            throw new SchemaLightenerException("logicalPath must not be empty");
        }
        if (normalizedPath.startsWith("/")) {
            throw new SchemaLightenerException("logicalPath must be relative: " + logicalPath);
        }
        if (normalizedPath.indexOf(':') >= 0) {
            throw new SchemaLightenerException("logicalPath must be relative; use the URI overload for absolute system IDs");
        }

        try {
            URI logicalUri = new URI(null, null, normalizedPath, null);
            URI systemId = MEMORY_BASE_URI.resolve(logicalUri).normalize();
            if (!systemId.toASCIIString().startsWith(MEMORY_BASE_URI.toASCIIString())) {
                throw new SchemaLightenerException("logicalPath must stay within the in-memory document set: " + logicalPath);
            }
            return systemId;
        } catch (URISyntaxException e) {
            throw new SchemaLightenerException("logicalPath is not a valid relative path: " + logicalPath, e);
        }
    }

    private static String readAll(Reader reader) throws IOException {
        StringBuilder content = new StringBuilder();
        char[] buffer = new char[4096];
        int read;
        while ((read = reader.read(buffer)) != -1) {
            content.append(buffer, 0, read);
        }
        return content.toString();
    }
}
