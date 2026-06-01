package com.xmlhelpline.schemalightener;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import java.io.StringReader;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaLightenerTest {
    private final SchemaLightener schemaLightener = new SchemaLightener();

    @TempDir
    Path outputDirectory;

    @Test
    void flattenSchemaMergesIncludedSchemaContent() throws Exception {
        Path sourceSchema = fixture("flatten/root.xsd");

        TransformationResult result = schemaLightener.flattenSchema(sourceSchema, outputDirectory);

        assertEquals(TransformationOperation.FLATTEN_SCHEMA, result.getOperation());
        Path flattenedSchema = findOutputFile(result, "root.xsd");
        String flattenedXml = read(flattenedSchema);
        assertTrue(flattenedXml.contains("OrderType"));
        assertTrue(flattenedXml.contains("LineType"));
        assertFalse(flattenedXml.contains("schemaLocation=\"common.xsd\""));
    }

    @Test
    void flattenSchemaCanUseStringInputsAndReturnResultDocumentsInMemory() throws Exception {
        URI rootUri = URI.create("memory:/schemas/root.xsd");
        URI commonUri = URI.create("memory:/schemas/common.xsd");

        InMemoryTransformationResult result = schemaLightener.flattenSchema(
                read(fixture("flatten/root.xsd")),
                rootUri,
                XmlInput.fromString(read(fixture("flatten/common.xsd")), commonUri));

        assertEquals(TransformationOperation.FLATTEN_SCHEMA, result.getOperation());
        String flattenedXml = result.findResultDocument("root.xsd")
                .orElseThrow(() -> new AssertionError("Missing root.xsd result: " + result.getResultDocuments().keySet()));
        assertTrue(flattenedXml.contains("OrderType"));
        assertTrue(flattenedXml.contains("LineType"));
        assertFalse(flattenedXml.contains("schemaLocation=\"common.xsd\""));
    }

    @Test
    void flattenSchemaCanResolveImportsFromInMemoryDocuments() throws Exception {
        URI rootUri = URI.create("memory:/schemas/root.xsd");
        URI commonUri = URI.create("memory:/schemas/common.xsd");

        InMemoryTransformationResult result = schemaLightener.flattenSchema(
                read(fixture("flatten-import/root.xsd")),
                rootUri,
                XmlInput.fromString(read(fixture("flatten-import/common.xsd")), commonUri));

        assertEquals(TransformationOperation.FLATTEN_SCHEMA, result.getOperation());
        String rootXml = result.findResultDocument("root.xsd")
                .orElseThrow(() -> new AssertionError("Missing root.xsd result: " + result.getResultDocuments().keySet()));
        String commonXml = result.findResultDocument("common.xsd")
                .orElseThrow(() -> new AssertionError("Missing common.xsd result: " + result.getResultDocuments().keySet()));
        assertTrue(rootXml.contains("namespace=\"urn:test:common\""));
        assertTrue(rootXml.contains("schemaLocation=\"../urn_test_common/common.xsd\""));
        assertTrue(commonXml.contains("CommonType"));
        assertTrue(commonXml.contains("urn:test:common"));
    }

    @Test
    void lightenSchemaRemovesUnusedGlobalComponents() throws Exception {
        Path sourceSchema = fixture("lighten/catalog.xsd");
        Path instance = fixture("lighten/order.xml");

        TransformationResult result = schemaLightener.lightenSchema(sourceSchema, instance, outputDirectory);

        assertEquals(TransformationOperation.LIGHTEN_SCHEMA, result.getOperation());
        Path lightenedSchema = findOutputFile(result, "catalog.xsd");
        String lightenedXml = read(lightenedSchema);
        assertTrue(lightenedXml.contains("name=\"order\""));
        assertTrue(lightenedXml.contains("name=\"OrderType\""));
        assertFalse(lightenedXml.contains("name=\"invoice\""));
        assertFalse(lightenedXml.contains("name=\"InvoiceType\""));

        SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                .newSchema(lightenedSchema.toFile())
                .newValidator()
                .validate(new StreamSource(instance.toFile()));
    }

    @Test
    void lightenSchemaCanUseReadersAndReturnResultDocumentsInMemory() throws Exception {
        String schemaXml = read(fixture("lighten/catalog.xsd"));
        String instanceXml = read(fixture("lighten/order.xml"));

        InMemoryTransformationResult result = schemaLightener.lightenSchema(
                new StringReader(schemaXml),
                URI.create("memory:/schemas/catalog.xsd"),
                new StringReader(instanceXml),
                URI.create("memory:/instances/order.xml"));

        assertEquals(TransformationOperation.LIGHTEN_SCHEMA, result.getOperation());
        String lightenedXml = result.findResultDocument("catalog.xsd")
                .orElseThrow(() -> new AssertionError("Missing catalog.xsd result: " + result.getResultDocuments().keySet()));
        assertTrue(lightenedXml.contains("name=\"order\""));
        assertTrue(lightenedXml.contains("name=\"OrderType\""));
        assertFalse(lightenedXml.contains("name=\"invoice\""));
        assertFalse(lightenedXml.contains("name=\"InvoiceType\""));

        SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                .newSchema(new StreamSource(new StringReader(lightenedXml)))
                .newValidator()
                .validate(new StreamSource(new StringReader(instanceXml)));
    }

    @Test
    void flattenWsdlMergesSchemaDependencies() throws Exception {
        Path sourceWsdl = fixture("wsdl/service.wsdl");

        TransformationResult result = schemaLightener.flattenWsdl(sourceWsdl, outputDirectory);

        assertEquals(TransformationOperation.FLATTEN_WSDL, result.getOperation());
        Path flattenedWsdl = findOutputFile(result, "service.wsdl");
        String flattenedXml = read(flattenedWsdl);
        assertTrue(flattenedXml.contains("SubmitOrderRequest"));
        assertTrue(flattenedXml.contains("OrderType"));
        assertFalse(flattenedXml.contains("schemaLocation=\"types.xsd\""));
    }

    private static Path fixture(String name) throws Exception {
        URL resource = SchemaLightenerTest.class.getResource("/com/xmlhelpline/schemalightener/fixtures/" + name);
        assertNotNull(resource, "Missing test fixture: " + name);
        return Paths.get(resource.toURI());
    }

    private static Path findOutputFile(TransformationResult result, String fileName) {
        for (Path outputFile : result.getOutputFiles()) {
            if (outputFile.getFileName().toString().equals(fileName)) {
                return outputFile;
            }
        }
        throw new AssertionError("Expected output file named " + fileName + " in " + result.getOutputFiles());
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
