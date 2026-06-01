package com.xmlhelpline.schemalightener;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.SchemaFactory;
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
