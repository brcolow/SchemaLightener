package com.xmlhelpline.schemalightener;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
import java.io.StringReader;
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
        Path flattenedSchema = result.requireSingleOutputFile();
        assertEquals(flattenedSchema, result.requireOutputFile("root.xsd"));
        String flattenedXml = read(flattenedSchema);
        assertTrue(flattenedXml.contains("OrderType"));
        assertTrue(flattenedXml.contains("LineType"));
        assertFalse(flattenedXml.contains("schemaLocation=\"common.xsd\""));
    }

    @Test
    void flattenSchemaCanUseStringInputsAndReturnResultDocumentsInMemory() throws Exception {
        InMemoryTransformationResult result = schemaLightener.flattenSchema(
                XmlInput.fromString(read(fixture("flatten/root.xsd")), "root.xsd"),
                XmlInput.fromString(read(fixture("flatten/common.xsd")), "common.xsd"));

        assertEquals(TransformationOperation.FLATTEN_SCHEMA, result.getOperation());
        String flattenedXml = result.requireSingleResultDocument();
        assertEquals(flattenedXml, result.requireResultDocument("root.xsd"));
        assertTrue(flattenedXml.contains("OrderType"));
        assertTrue(flattenedXml.contains("LineType"));
        assertFalse(flattenedXml.contains("schemaLocation=\"common.xsd\""));
    }

    @Test
    void flattenSchemaCanResolveImportsFromInMemoryDocuments() throws Exception {
        InMemoryTransformationResult result = schemaLightener.flattenSchema(
                XmlInput.fromString(read(fixture("flatten-import/root.xsd")), "root.xsd"),
                XmlInput.fromString(read(fixture("flatten-import/common.xsd")), "common.xsd"));

        assertEquals(TransformationOperation.FLATTEN_SCHEMA, result.getOperation());
        String rootXml = result.requireResultDocument("root.xsd");
        String commonXml = result.requireResultDocument("common.xsd");
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
        Path lightenedSchema = result.requireOutputFile("catalog.xsd");
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
                XmlInput.fromReader(new StringReader(schemaXml), "catalog.xsd"),
                XmlInput.fromReader(new StringReader(instanceXml), "order.xml"));

        assertEquals(TransformationOperation.LIGHTEN_SCHEMA, result.getOperation());
        String lightenedXml = result.requireResultDocument("catalog.xsd");
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
    void lightenSchemaRemovesEmptyModelGroupsAndWhitespaceOnlyLines() throws Exception {
        Path sourceSchema = fixture("lighten-pruned/envelope.xsd");
        Path instance = fixture("lighten-pruned/envelope.xml");

        TransformationResult result = schemaLightener.lightenSchema(sourceSchema, instance, outputDirectory);

        Path lightenedSchema = result.requireOutputFile("envelope.xsd");
        String lightenedXml = read(lightenedSchema);
        assertTrue(lightenedXml.contains("KeepRequestType"));
        assertFalse(lightenedXml.contains("DropRequestType"));
        assertFalse(lightenedXml.contains("UnusedValue"));
        assertFalse(lightenedXml.matches("(?s).*<xsd:sequence[^>]*>\\s*</xsd:sequence>.*"));
        assertFalse(lightenedXml.matches("(?m).*^\\s+$.*"));

        SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                .newSchema(lightenedSchema.toFile())
                .newValidator()
                .validate(new StreamSource(instance.toFile()));
    }

    @Test
    void flattenAndLightenSchemaFlattensIncludesBeforeLightening() throws Exception {
        Path sourceSchema = fixture("flatten-lighten/root.xsd");
        Path instance = fixture("flatten-lighten/order.xml");

        TransformationResult result = schemaLightener.flattenAndLightenSchema(sourceSchema, instance, outputDirectory);

        assertEquals(TransformationOperation.FLATTEN_AND_LIGHTEN_SCHEMA, result.getOperation());
        assertEquals(sourceSchema.toAbsolutePath().normalize(), result.getSource());
        Path lightenedSchema = result.requireSingleOutputFile();
        String lightenedXml = read(lightenedSchema);
        assertTrue(lightenedXml.contains("name=\"order\""));
        assertTrue(lightenedXml.contains("name=\"OrderType\""));
        assertFalse(lightenedXml.contains("schemaLocation=\"common.xsd\""));
        assertFalse(lightenedXml.contains("schemaLocation=\"unused.xsd\""));
        assertFalse(lightenedXml.contains("UnusedType"));

        SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                .newSchema(lightenedSchema.toFile())
                .newValidator()
                .validate(new StreamSource(instance.toFile()));
    }

    @Test
    void flattenAndLightenSchemaCanUseDefaultStringInputs() throws Exception {
        String schemaXml = read(fixture("lighten/catalog.xsd"));
        String instanceXml = read(fixture("lighten/order.xml"));

        InMemoryTransformationResult result = schemaLightener.flattenAndLightenSchema(schemaXml, instanceXml);

        assertEquals(TransformationOperation.FLATTEN_AND_LIGHTEN_SCHEMA, result.getOperation());
        String lightenedXml = result.requireSingleResultDocument();
        assertEquals(lightenedXml, result.requireResultDocument("schema.xsd"));
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
    void flattenAndLightenSchemaCanUseReadersAndReturnResultDocumentsInMemory() throws Exception {
        InMemoryTransformationResult result = schemaLightener.flattenAndLightenSchema(
                XmlInput.fromReader(new StringReader(read(fixture("flatten-lighten/root.xsd"))), "root.xsd"),
                XmlInput.fromReader(new StringReader(read(fixture("flatten-lighten/order.xml"))), "order.xml"),
                XmlInput.fromString(read(fixture("flatten-lighten/common.xsd")), "common.xsd"),
                XmlInput.fromString(read(fixture("flatten-lighten/unused.xsd")), "unused.xsd"));

        assertEquals(TransformationOperation.FLATTEN_AND_LIGHTEN_SCHEMA, result.getOperation());
        String lightenedXml = result.requireSingleResultDocument();
        assertEquals(lightenedXml, result.requireResultDocument("root.xsd"));
        assertTrue(lightenedXml.contains("name=\"order\""));
        assertTrue(lightenedXml.contains("name=\"OrderType\""));
        assertFalse(lightenedXml.contains("schemaLocation=\"common.xsd\""));
        assertFalse(lightenedXml.contains("schemaLocation=\"unused.xsd\""));
        assertFalse(lightenedXml.contains("UnusedType"));

        SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                .newSchema(new StreamSource(new StringReader(lightenedXml)))
                .newValidator()
                .validate(new StreamSource(new StringReader(read(fixture("flatten-lighten/order.xml")))));
    }

    @Test
    void sampleDataAssessmentStatusRequestCanBeLightened() throws Exception {
        Path sourceSchema = sampleData("hr-xml/HR-XML-2_5/StandAlone/AssessmentStatusRequest.xsd");
        Path instance = sampleData("hr-xml/HR-XML-2_5/instances/AssessmentStatusRequest.xml");

        TransformationResult result = schemaLightener.lightenSchema(sourceSchema, instance, outputDirectory);

        assertEquals(TransformationOperation.LIGHTEN_SCHEMA, result.getOperation());
        Path lightenedSchema = result.requireOutputFile("AssessmentStatusRequest.xsd");
        String lightenedXml = read(lightenedSchema);
        assertTrue(lightenedXml.contains("AssessmentStatusRequest"));
        assertTrue(lightenedXml.contains("ClientId"));

        SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                .newSchema(lightenedSchema.toFile())
                .newValidator()
                .validate(new StreamSource(instance.toFile()));
    }

    @Test
    void flattenWsdlMergesSchemaDependencies() throws Exception {
        Path sourceWsdl = fixture("wsdl/service.wsdl");

        TransformationResult result = schemaLightener.flattenWsdl(sourceWsdl, outputDirectory);

        assertEquals(TransformationOperation.FLATTEN_WSDL, result.getOperation());
        Path flattenedWsdl = result.requireOutputFile("service.wsdl");
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

    private static Path sampleData(String name) {
        Path path = Paths.get("SampleData").resolve(name).toAbsolutePath().normalize();
        assertTrue(Files.isRegularFile(path), "Missing sample data file: " + path);
        return path;
    }

    private static String read(Path path) throws Exception {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }
}
