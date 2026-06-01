package com.xmlhelpline.schemalightener;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Java API for the SchemaLightener XSLT tools.
 */
public final class SchemaLightener {
    private static final String SAXON_TRANSFORMER_FACTORY = "net.sf.saxon.TransformerFactoryImpl";
    private static final String XSLT_RESOURCE_BASE = "/com/xmlhelpline/schemalightener/xslt/";

    /**
     * Flatten an XML Schema into the fewest possible schema files.
     *
     * @param schema source XML Schema
     * @param outputDirectory destination directory
     * @return transform result with output files
     */
    public TransformationResult flattenSchema(Path schema, Path outputDirectory) {
        Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        return transform(TransformationOperation.FLATTEN_SCHEMA, "SchemaFlattener.xslt", schema, outputDirectory, parameters);
    }

    /**
     * Lighten an XML Schema using an XML instance as the subset indicator.
     *
     * @param schema source XML Schema
     * @param instance XML instance describing the desired subset
     * @param outputDirectory destination directory
     * @return transform result with output files
     */
    public TransformationResult lightenSchema(Path schema, Path instance, Path outputDirectory) {
        Path normalizedInstance = requireReadableFile(instance, "instance");
        Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        parameters.put("instanceFilePathAndName", normalizedInstance.toUri().toASCIIString());
        return transform(TransformationOperation.LIGHTEN_SCHEMA, "SchemaLightener.xslt", schema, outputDirectory, parameters);
    }

    /**
     * Flatten a WSDL and its schema dependencies.
     *
     * @param wsdl source WSDL
     * @param outputDirectory destination directory
     * @return transform result with output files
     */
    public TransformationResult flattenWsdl(Path wsdl, Path outputDirectory) {
        Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        return transform(TransformationOperation.FLATTEN_WSDL, "WSDLFlattener.xslt", wsdl, outputDirectory, parameters);
    }

    private TransformationResult transform(
            TransformationOperation operation,
            String stylesheetName,
            Path source,
            Path outputDirectory,
            Map<String, Object> parameters) {
        Path normalizedSource = requireReadableFile(source, "source");
        Path normalizedOutputDirectory = normalizeOutputDirectory(outputDirectory);
        try {
            Files.createDirectories(normalizedOutputDirectory);

            Transformer transformer = newTransformer(stylesheetName);
            String resultBasePath = ensureTrailingSlash(normalizedOutputDirectory.toUri().toASCIIString());
            transformer.setParameter("resultBasePath", resultBasePath);
            transformer.setParameter("sourcePathAndFileName", normalizedSource.toUri().toASCIIString());
            for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                transformer.setParameter(entry.getKey(), entry.getValue());
            }

            StringWriter primaryResult = new StringWriter();
            try (InputStream sourceStream = Files.newInputStream(normalizedSource)) {
                Source sourceDocument = new StreamSource(sourceStream);
                sourceDocument.setSystemId(normalizedSource.toUri().toASCIIString());
                Result result = new StreamResult(primaryResult);
                transformer.transform(sourceDocument, result);
            }

            return new TransformationResult(
                    operation,
                    normalizedSource,
                    normalizedOutputDirectory,
                    collectOutputFiles(normalizedOutputDirectory),
                    primaryResult.toString());
        } catch (IOException e) {
            throw new SchemaLightenerException("Unable to run " + operation + " for " + normalizedSource, e);
        } catch (TransformerException e) {
            throw new SchemaLightenerException("Unable to transform " + normalizedSource + " with " + stylesheetName, e);
        }
    }

    private Transformer newTransformer(String stylesheetName) throws IOException, TransformerException {
        URL stylesheet = SchemaLightener.class.getResource(XSLT_RESOURCE_BASE + stylesheetName);
        if (stylesheet == null) {
            throw new SchemaLightenerException("Cannot find stylesheet resource: " + stylesheetName);
        }

        try (InputStream stylesheetStream = stylesheet.openStream()) {
            Source stylesheetSource = new StreamSource(stylesheetStream);
            stylesheetSource.setSystemId(stylesheet.toExternalForm());
            ClassLoader classLoader = SchemaLightener.class.getClassLoader();
            TransformerFactory factory = TransformerFactory.newInstance(SAXON_TRANSFORMER_FACTORY, classLoader);
            return factory.newTransformer(stylesheetSource);
        }
    }

    private static Path requireReadableFile(Path path, String label) {
        if (path == null) {
            throw new SchemaLightenerException(label + " path must not be null");
        }
        Path normalized = path.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized) || !Files.isReadable(normalized)) {
            throw new SchemaLightenerException(label + " path is not a readable file: " + normalized);
        }
        return normalized;
    }

    private static Path normalizeOutputDirectory(Path outputDirectory) {
        if (outputDirectory == null) {
            throw new SchemaLightenerException("outputDirectory path must not be null");
        }
        return outputDirectory.toAbsolutePath().normalize();
    }

    private static String ensureTrailingSlash(String uri) {
        return uri.endsWith("/") ? uri : uri + "/";
    }

    private static List<Path> collectOutputFiles(Path outputDirectory) throws IOException {
        if (!Files.exists(outputDirectory)) {
            return Collections.emptyList();
        }
        try (Stream<Path> paths = Files.walk(outputDirectory)) {
            return paths
                    .filter(Files::isRegularFile)
                    .map(path -> path.toAbsolutePath().normalize())
                    .sorted()
                    .collect(Collectors.toList());
        }
    }
}
