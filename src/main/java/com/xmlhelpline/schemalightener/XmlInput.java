package com.xmlhelpline.schemalightener;

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Reusable XML input for the in-memory SchemaLightener API.
 */
public final class XmlInput {
    private final String xml;
    private final Path path;
    private final URI systemId;

    private XmlInput(String xml, Path path, URI systemId) {
        this.xml = xml;
        this.path = path;
        this.systemId = systemId.normalize();
    }

    public static XmlInput fromString(String xml, URI systemId) {
        Objects.requireNonNull(xml, "xml must not be null");
        return new XmlInput(xml, null, requireAbsoluteSystemId(systemId));
    }

    public static XmlInput fromReader(Reader reader, URI systemId) {
        Objects.requireNonNull(reader, "reader must not be null");
        try {
            return fromString(readAll(reader), systemId);
        } catch (IOException e) {
            throw new SchemaLightenerException("Unable to read XML input from reader", e);
        }
    }

    public static XmlInput fromPath(Path path) {
        Objects.requireNonNull(path, "path must not be null");
        Path normalizedPath = path.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalizedPath) || !Files.isReadable(normalizedPath)) {
            throw new SchemaLightenerException("path is not a readable file: " + normalizedPath);
        }
        return new XmlInput(null, normalizedPath, normalizedPath.toUri());
    }

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
