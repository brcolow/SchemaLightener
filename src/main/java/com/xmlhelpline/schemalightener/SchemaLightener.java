package com.xmlhelpline.schemalightener;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.URIResolver;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Java API for the SchemaLightener XSLT tools.
 */
public final class SchemaLightener {
    private static final String SAXON_TRANSFORMER_FACTORY = "net.sf.saxon.TransformerFactoryImpl";
    private static final String SAXON_OUTPUT_URI_RESOLVER = "net.sf.saxon.lib.OutputURIResolver";
    private static final String SAXON_OUTPUT_URI_RESOLVER_FEATURE = "http://saxon.sf.net/feature/outputURIResolver";
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
     * Flatten an XML Schema supplied as a string and return generated documents in memory.
     *
     * @param schemaXml source XML Schema content
     * @return in-memory transform result
     */
    public InMemoryTransformationResult flattenSchema(String schemaXml) {
        return flattenSchema(schemaXml, URI.create("memory:/schemalightener/schema.xsd"));
    }

    /**
     * Flatten an XML Schema supplied as a string and return generated documents in memory.
     *
     * @param schemaXml source XML Schema content
     * @param systemId base URI used for resolving relative includes/imports
     * @param supportingDocuments optional in-memory documents available to document() lookups
     * @return in-memory transform result
     */
    public InMemoryTransformationResult flattenSchema(String schemaXml, URI systemId, XmlInput... supportingDocuments) {
        return flattenSchema(XmlInput.fromString(schemaXml, systemId), supportingDocuments);
    }

    /**
     * Flatten an XML Schema supplied by a reader and return generated documents in memory.
     *
     * @param schemaReader source XML Schema reader
     * @param systemId base URI used for resolving relative includes/imports
     * @param supportingDocuments optional in-memory documents available to document() lookups
     * @return in-memory transform result
     */
    public InMemoryTransformationResult flattenSchema(Reader schemaReader, URI systemId, XmlInput... supportingDocuments) {
        return flattenSchema(XmlInput.fromReader(schemaReader, systemId), supportingDocuments);
    }

    /**
     * Flatten an XML Schema and return generated documents in memory.
     *
     * @param schema source XML Schema
     * @param supportingDocuments optional in-memory documents available to document() lookups
     * @return in-memory transform result
     */
    public InMemoryTransformationResult flattenSchema(XmlInput schema, XmlInput... supportingDocuments) {
        Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        return transformToMemory(
                TransformationOperation.FLATTEN_SCHEMA,
                "SchemaFlattener.xslt",
                schema,
                parameters,
                supportingDocuments);
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
     * Lighten an XML Schema supplied as strings and return generated documents in memory.
     *
     * @param schemaXml source XML Schema content
     * @param instanceXml XML instance describing the desired subset
     * @return in-memory transform result
     */
    public InMemoryTransformationResult lightenSchema(String schemaXml, String instanceXml) {
        return lightenSchema(
                schemaXml,
                URI.create("memory:/schemalightener/schema.xsd"),
                instanceXml,
                URI.create("memory:/schemalightener/instance.xml"));
    }

    /**
     * Lighten an XML Schema supplied as strings and return generated documents in memory.
     *
     * @param schemaXml source XML Schema content
     * @param schemaSystemId base URI used for resolving schema includes/imports
     * @param instanceXml XML instance describing the desired subset
     * @param instanceSystemId URI used for resolving the instance document
     * @param supportingDocuments optional in-memory documents available to document() lookups
     * @return in-memory transform result
     */
    public InMemoryTransformationResult lightenSchema(
            String schemaXml,
            URI schemaSystemId,
            String instanceXml,
            URI instanceSystemId,
            XmlInput... supportingDocuments) {
        return lightenSchema(
                XmlInput.fromString(schemaXml, schemaSystemId),
                XmlInput.fromString(instanceXml, instanceSystemId),
                supportingDocuments);
    }

    /**
     * Lighten an XML Schema supplied by readers and return generated documents in memory.
     *
     * @param schemaReader source XML Schema reader
     * @param schemaSystemId base URI used for resolving schema includes/imports
     * @param instanceReader XML instance reader
     * @param instanceSystemId URI used for resolving the instance document
     * @param supportingDocuments optional in-memory documents available to document() lookups
     * @return in-memory transform result
     */
    public InMemoryTransformationResult lightenSchema(
            Reader schemaReader,
            URI schemaSystemId,
            Reader instanceReader,
            URI instanceSystemId,
            XmlInput... supportingDocuments) {
        return lightenSchema(
                XmlInput.fromReader(schemaReader, schemaSystemId),
                XmlInput.fromReader(instanceReader, instanceSystemId),
                supportingDocuments);
    }

    /**
     * Lighten an XML Schema and return generated documents in memory.
     *
     * @param schema source XML Schema
     * @param instance XML instance describing the desired subset
     * @param supportingDocuments optional in-memory documents available to document() lookups
     * @return in-memory transform result
     */
    public InMemoryTransformationResult lightenSchema(XmlInput schema, XmlInput instance, XmlInput... supportingDocuments) {
        Objects.requireNonNull(instance, "instance must not be null");
        Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        parameters.put("instanceFilePathAndName", instance.getSystemId().toASCIIString());
        XmlInput[] allSupportingDocuments = prepend(instance, supportingDocuments);
        return transformToMemory(
                TransformationOperation.LIGHTEN_SCHEMA,
                "SchemaLightener.xslt",
                schema,
                parameters,
                allSupportingDocuments);
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

    /**
     * Flatten a WSDL supplied as a string and return generated documents in memory.
     *
     * @param wsdlXml source WSDL content
     * @return in-memory transform result
     */
    public InMemoryTransformationResult flattenWsdl(String wsdlXml) {
        return flattenWsdl(wsdlXml, URI.create("memory:/schemalightener/service.wsdl"));
    }

    /**
     * Flatten a WSDL supplied as a string and return generated documents in memory.
     *
     * @param wsdlXml source WSDL content
     * @param systemId base URI used for resolving relative imports
     * @param supportingDocuments optional in-memory documents available to document() lookups
     * @return in-memory transform result
     */
    public InMemoryTransformationResult flattenWsdl(String wsdlXml, URI systemId, XmlInput... supportingDocuments) {
        return flattenWsdl(XmlInput.fromString(wsdlXml, systemId), supportingDocuments);
    }

    /**
     * Flatten a WSDL supplied by a reader and return generated documents in memory.
     *
     * @param wsdlReader source WSDL reader
     * @param systemId base URI used for resolving relative imports
     * @param supportingDocuments optional in-memory documents available to document() lookups
     * @return in-memory transform result
     */
    public InMemoryTransformationResult flattenWsdl(Reader wsdlReader, URI systemId, XmlInput... supportingDocuments) {
        return flattenWsdl(XmlInput.fromReader(wsdlReader, systemId), supportingDocuments);
    }

    /**
     * Flatten a WSDL and return generated documents in memory.
     *
     * @param wsdl source WSDL
     * @param supportingDocuments optional in-memory documents available to document() lookups
     * @return in-memory transform result
     */
    public InMemoryTransformationResult flattenWsdl(XmlInput wsdl, XmlInput... supportingDocuments) {
        Map<String, Object> parameters = new LinkedHashMap<String, Object>();
        return transformToMemory(
                TransformationOperation.FLATTEN_WSDL,
                "WSDLFlattener.xslt",
                wsdl,
                parameters,
                supportingDocuments);
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
        return newTransformer(newSaxonTransformerFactory(), stylesheetName);
    }

    private Transformer newTransformer(TransformerFactory factory, String stylesheetName) throws IOException, TransformerException {
        URL stylesheet = SchemaLightener.class.getResource(XSLT_RESOURCE_BASE + stylesheetName);
        if (stylesheet == null) {
            throw new SchemaLightenerException("Cannot find stylesheet resource: " + stylesheetName);
        }

        try (InputStream stylesheetStream = stylesheet.openStream()) {
            Source stylesheetSource = new StreamSource(stylesheetStream);
            stylesheetSource.setSystemId(stylesheet.toExternalForm());
            return factory.newTransformer(stylesheetSource);
        }
    }

    private InMemoryTransformationResult transformToMemory(
            TransformationOperation operation,
            String stylesheetName,
            XmlInput source,
            Map<String, Object> parameters,
            XmlInput... supportingDocuments) {
        Objects.requireNonNull(source, "source must not be null");
        try {
            Map<String, XmlInput> resolvableInputs = new LinkedHashMap<String, XmlInput>();
            registerResolvableInput(resolvableInputs, source);
            if (supportingDocuments != null) {
                for (XmlInput supportingDocument : supportingDocuments) {
                    registerResolvableInput(resolvableInputs, supportingDocument);
                }
            }
            URIResolver uriResolver = memoryAwareResolver(resolvableInputs);
            String resultBasePath = "memory:/schemalightener/results/" + UUID.randomUUID() + "/";
            Map<URI, StringWriter> resultWriters = new LinkedHashMap<URI, StringWriter>();

            TransformerFactory factory = newSaxonTransformerFactory();
            factory.setURIResolver(uriResolver);
            setOutputUriResolver(factory, resultWriters);

            Transformer transformer = newTransformer(factory, stylesheetName);
            transformer.setURIResolver(uriResolver);
            transformer.setParameter("resultBasePath", resultBasePath);
            transformer.setParameter("sourcePathAndFileName", source.getSystemId().toASCIIString());
            for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                transformer.setParameter(entry.getKey(), String.valueOf(entry.getValue()));
            }

            StringWriter primaryResult = new StringWriter();
            StreamResult result = new StreamResult(primaryResult);
            result.setSystemId(resultBasePath);
            transformer.transform(source.toSource(), result);

            Map<URI, String> resultDocuments = new LinkedHashMap<URI, String>();
            for (Map.Entry<URI, StringWriter> entry : resultWriters.entrySet()) {
                resultDocuments.put(entry.getKey(), entry.getValue().toString());
            }
            return new InMemoryTransformationResult(
                    operation,
                    source.getSystemId(),
                    primaryResult.toString(),
                    resultDocuments);
        } catch (IOException e) {
            throw new SchemaLightenerException("Unable to read stylesheet resource: " + stylesheetName, e);
        } catch (TransformerException e) {
            throw new SchemaLightenerException("Unable to transform " + source.getSystemId() + " with " + stylesheetName, e);
        }
    }

    private static TransformerFactory newSaxonTransformerFactory() {
        ClassLoader classLoader = SchemaLightener.class.getClassLoader();
        try {
            return TransformerFactory.newInstance(SAXON_TRANSFORMER_FACTORY, classLoader);
        } catch (TransformerFactoryConfigurationError e) {
            throw new SchemaLightenerException("Saxon HE is required at runtime to run SchemaLightener transformations", e);
        }
    }

    private static void setOutputUriResolver(
            TransformerFactory factory,
            Map<URI, StringWriter> resultWriters) throws TransformerException {
        try {
            ClassLoader classLoader = factory.getClass().getClassLoader();
            if (classLoader == null) {
                classLoader = SchemaLightener.class.getClassLoader();
            }
            Class<?> resolverType = Class.forName(SAXON_OUTPUT_URI_RESOLVER, true, classLoader);
            Object resolver = Proxy.newProxyInstance(
                    classLoader,
                    new Class<?>[] { resolverType },
                    outputUriResolverHandler(resultWriters));
            factory.setAttribute(SAXON_OUTPUT_URI_RESOLVER_FEATURE, resolver);
        } catch (ClassNotFoundException e) {
            throw new SchemaLightenerException("Saxon HE output URI resolver is not available at runtime", e);
        } catch (IllegalArgumentException e) {
            throw new TransformerException("Unable to configure Saxon output URI resolver", e);
        }
    }

    private static InvocationHandler outputUriResolverHandler(Map<URI, StringWriter> resultWriters) {
        return (proxy, method, args) -> {
            String methodName = method.getName();
            if ("newInstance".equals(methodName)) {
                return proxy;
            }
            if ("resolve".equals(methodName)) {
                String href = (String) args[0];
                String base = (String) args[1];
                URI resultUri = resolveUri(href, base);
                StringWriter writer = new StringWriter();
                resultWriters.put(resultUri, writer);

                StreamResult result = new StreamResult(writer);
                result.setSystemId(resultUri.toASCIIString());
                return result;
            }
            if ("close".equals(methodName)) {
                return null;
            }
            if ("toString".equals(methodName)) {
                return "SchemaLightenerOutputURIResolver";
            }
            if ("hashCode".equals(methodName)) {
                return System.identityHashCode(proxy);
            }
            if ("equals".equals(methodName)) {
                return proxy == args[0];
            }
            throw new UnsupportedOperationException("Unsupported OutputURIResolver method: " + methodName);
        };
    }

    private static URIResolver memoryAwareResolver(Map<String, XmlInput> resolvableInputs) {
        return (href, base) -> {
            URI resolvedUri = resolveUri(href, base);
            XmlInput input = resolvableInputs.get(resolvedUri.toASCIIString());
            return input == null ? null : input.toSource();
        };
    }

    private static URI resolveUri(String href, String base) {
        URI hrefUri = URI.create(href);
        if (!hrefUri.isAbsolute() && base != null && !base.isEmpty()) {
            hrefUri = URI.create(base).resolve(hrefUri);
        }
        return hrefUri.normalize();
    }

    private static void registerResolvableInput(Map<String, XmlInput> inputs, XmlInput input) {
        if (input != null) {
            inputs.put(input.getSystemId().normalize().toASCIIString(), input);
        }
    }

    private static XmlInput[] prepend(XmlInput first, XmlInput[] rest) {
        int restLength = rest == null ? 0 : rest.length;
        XmlInput[] values = new XmlInput[restLength + 1];
        values[0] = first;
        if (restLength > 0) {
            System.arraycopy(rest, 0, values, 1, restLength);
        }
        return values;
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
