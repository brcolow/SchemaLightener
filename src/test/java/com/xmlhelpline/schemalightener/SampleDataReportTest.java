package com.xmlhelpline.schemalightener;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SampleDataReportTest {
    private static final String SAMPLE_REPORT_PROPERTY = "schemalightener.sampleReport";
    private static final String XSD_NS = XMLConstants.W3C_XML_SCHEMA_NS_URI;
    private static final String XSI_NS = XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI;
    private static final DecimalFormat PERCENT_FORMAT =
            new DecimalFormat("0.0", DecimalFormatSymbols.getInstance(Locale.ROOT));

    private final DocumentBuilderFactory documentBuilderFactory = newDocumentBuilderFactory();
    private final SchemaLightener schemaLightener = new SchemaLightener();

    @Test
    void generateSampleDataReport() throws Exception {
        Assumptions.assumeTrue(
                Boolean.getBoolean(SAMPLE_REPORT_PROPERTY),
                "sample data report disabled; pass -D" + SAMPLE_REPORT_PROPERTY + "=true to generate it");

        Path sampleDataDirectory = Paths.get("SampleData").toAbsolutePath().normalize();
        Path reportDirectory = Paths.get("target", "schemalightener-sample-report")
                .toAbsolutePath()
                .normalize();
        Path generatedDirectory = reportDirectory.resolve("generated");
        deleteRecursively(reportDirectory);
        Files.createDirectories(generatedDirectory);

        List<SamplePair> samplePairs = discoverSamplePairs(sampleDataDirectory);
        List<ReportRow> rows = new ArrayList<ReportRow>();
        int index = 0;
        for (SamplePair samplePair : samplePairs) {
            index++;
            rows.add(runSample(index, samplePair, generatedDirectory));
        }

        Files.write(reportDirectory.resolve("index.html"), renderHtml(rows).getBytes(StandardCharsets.UTF_8));

        assertTrue(Files.isRegularFile(reportDirectory.resolve("index.html")),
                "Expected report artifact to be generated");
    }

    private ReportRow runSample(
            int index,
            SamplePair samplePair,
            Path generatedDirectory) {
        Path outputDirectory = generatedDirectory.resolve(String.format(
                Locale.ROOT,
                "%03d-%s",
                index,
                safeFileName(samplePair.instance.getFileName().toString())));
        Counts originalCounts = countSchemaClosure(samplePair.schema);
        Counts lightenedCounts = Counts.empty();
        Status status = Status.PASS;
        String message = "";
        List<Path> outputFiles = Collections.emptyList();

        String sourceValidation = validate(samplePair.schema, samplePair.instance);
        if (!"OK".equals(sourceValidation)) {
            status = Status.SOURCE_INVALID;
            message = sourceValidation;
            return new ReportRow(index, samplePair, status, originalCounts, lightenedCounts, message);
        }

        try {
            TransformationResult result = schemaLightener.lightenSchema(samplePair.schema, samplePair.instance, outputDirectory);
            outputFiles = result.getOutputFiles();
            lightenedCounts = countFiles(outputFiles);
            Path rootOutput = findRootOutput(result, samplePair.schema);
            String generatedValidation = validate(rootOutput, samplePair.instance);
            if (!"OK".equals(generatedValidation)) {
                status = Status.GENERATED_INVALID;
                message = generatedValidation;
            }
        } catch (Exception e) {
            status = Status.TRANSFORM_FAILED;
            message = describe(e);
        }

        return new ReportRow(index, samplePair, status, originalCounts, lightenedCounts, message);
    }

    private List<SamplePair> discoverSamplePairs(Path sampleDataDirectory) throws Exception {
        List<SamplePair> samplePairs = new ArrayList<SamplePair>();
        try (Stream<Path> paths = Files.walk(sampleDataDirectory)) {
            List<Path> xmlFiles = paths
                    .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".xml"))
                    .sorted()
                    .collect(Collectors.toList());
            for (Path xmlFile : xmlFiles) {
                List<Path> schemaLocations = schemaLocationsFor(xmlFile);
                if (!schemaLocations.isEmpty()) {
                    samplePairs.add(new SamplePair(xmlFile, schemaLocations.get(0)));
                }
            }
        }
        return samplePairs;
    }

    private List<Path> schemaLocationsFor(Path xmlFile) throws Exception {
        Document document = parse(xmlFile);
        String schemaLocation = document.getDocumentElement().getAttributeNS(XSI_NS, "schemaLocation");
        if (schemaLocation.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String[] parts = schemaLocation.trim().split("\\s+");
        List<Path> schemas = new ArrayList<Path>();
        for (int i = 1; i < parts.length; i += 2) {
            Path schema = xmlFile.getParent().resolve(parts[i]).normalize();
            if (Files.isRegularFile(schema)) {
                schemas.add(schema.toAbsolutePath().normalize());
            }
        }
        return schemas;
    }

    private Counts countSchemaClosure(Path rootSchema) {
        Set<Path> visited = new LinkedHashSet<Path>();
        collectSchemaClosure(rootSchema.toAbsolutePath().normalize(), visited);
        return countFiles(new ArrayList<Path>(visited));
    }

    private void collectSchemaClosure(Path schema, Set<Path> visited) {
        Path normalizedSchema = schema.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalizedSchema) || !visited.add(normalizedSchema)) {
            return;
        }

        try {
            Document document = parse(normalizedSchema);
            Element root = document.getDocumentElement();
            NodeList children = root.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node node = children.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE
                        && XSD_NS.equals(node.getNamespaceURI())
                        && ("include".equals(node.getLocalName()) || "import".equals(node.getLocalName()))) {
                    Element element = (Element) node;
                    String schemaLocation = element.getAttribute("schemaLocation");
                    if (schemaLocation != null
                            && !schemaLocation.trim().isEmpty()
                            && !isAbsoluteUri(schemaLocation)) {
                        collectSchemaClosure(normalizedSchema.getParent().resolve(schemaLocation).normalize(), visited);
                    }
                }
            }
        } catch (Exception ignored) {
            // Counting is best-effort; validation captures parseability and schema issues in the report row.
        }
    }

    private Counts countFiles(List<Path> schemaFiles) {
        Counts total = Counts.empty();
        Set<Path> seen = new HashSet<Path>();
        for (Path schemaFile : schemaFiles) {
            Path normalized = schemaFile.toAbsolutePath().normalize();
            if (!seen.add(normalized) || !Files.isRegularFile(normalized)) {
                continue;
            }
            try {
                total = total.plus(countTopLevelDeclarations(normalized));
            } catch (Exception ignored) {
                // The report still records validation/transform failures; declaration counts are supplemental.
            }
        }
        return total;
    }

    private Counts countTopLevelDeclarations(Path schema) throws Exception {
        Document document = parse(schema);
        Element root = document.getDocumentElement();
        int elements = 0;
        int simpleTypes = 0;
        int complexTypes = 0;
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && XSD_NS.equals(node.getNamespaceURI())) {
                if ("element".equals(node.getLocalName())) {
                    elements++;
                } else if ("simpleType".equals(node.getLocalName())) {
                    simpleTypes++;
                } else if ("complexType".equals(node.getLocalName())) {
                    complexTypes++;
                }
            }
        }
        return new Counts(elements, simpleTypes, complexTypes);
    }

    private String validate(Path schemaPath, Path instancePath) {
        try {
            Schema schema = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI)
                    .newSchema(schemaPath.toFile());
            schema.newValidator().validate(new StreamSource(instancePath.toFile()));
            return "OK";
        } catch (Exception e) {
            return describe(e);
        }
    }

    private Path findRootOutput(TransformationResult result, Path schema) {
        String schemaFileName = schema.getFileName().toString();
        for (Path outputFile : result.getOutputFiles()) {
            if (outputFile.getFileName().toString().equals(schemaFileName)) {
                return outputFile;
            }
        }
        if (result.getOutputFiles().size() == 1) {
            return result.getOutputFiles().get(0);
        }
        throw new SchemaLightenerException("Unable to find generated root schema for "
                + schema + " in " + result.getOutputFiles());
    }

    private Document parse(Path path) throws Exception {
        DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
        return documentBuilder.parse(path.toFile());
    }

    private static DocumentBuilderFactory newDocumentBuilderFactory() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        return factory;
    }

    private String renderHtml(List<ReportRow> rows) {
        Summary summary = summarize(rows);
        StringBuilder html = new StringBuilder();
        html.append("<!doctype html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n");
        html.append("<title>SchemaLightener Sample Data Report</title>\n");
        html.append("<style>");
        html.append("body{font-family:Arial,sans-serif;margin:24px;color:#1f2933;background:#f8fafc}");
        html.append("h1{margin-bottom:4px} .meta{color:#52606d;margin-top:0}");
        html.append(".cards{display:flex;gap:12px;flex-wrap:wrap;margin:18px 0}");
        html.append(".card{background:#fff;border:1px solid #d9e2ec;border-radius:6px;padding:12px 14px;min-width:140px}");
        html.append(".card strong{display:block;font-size:24px;margin-bottom:2px}");
        html.append("table{width:100%;border-collapse:collapse;background:#fff;border:1px solid #d9e2ec}");
        html.append("th,td{padding:7px 8px;border-bottom:1px solid #e4e7eb;text-align:left;vertical-align:top}");
        html.append("th{background:#eef2f7;font-size:12px;text-transform:uppercase;letter-spacing:.03em}");
        html.append("tr.pass td.status{color:#0f7b45;font-weight:bold}");
        html.append("tr.generated_invalid td.status{color:#b7791f;font-weight:bold}");
        html.append("tr.source_invalid td.status,tr.transform_failed td.status{color:#c42d2d;font-weight:bold}");
        html.append(".number{text-align:right;font-variant-numeric:tabular-nums}.path{font-family:Consolas,monospace;font-size:12px}");
        html.append(".message{max-width:420px}.small{font-size:12px;color:#52606d}");
        html.append("</style>\n</head>\n<body>\n");
        html.append("<h1>SchemaLightener Sample Data Report</h1>\n");
        html.append("<p class=\"meta\">Generated ").append(escapeHtml(Instant.now().toString())).append("</p>\n");
        html.append("<div class=\"cards\">");
        card(html, "Sample pairs", String.valueOf(summary.total));
        card(html, "Valid lightened schemas", String.valueOf(summary.passed));
        card(html, "Generated schema failures", String.valueOf(summary.generatedInvalid));
        card(html, "Average element reduction", formatPercent(summary.averageElementReduction));
        card(html, "Average simpleType reduction", formatPercent(summary.averageSimpleTypeReduction));
        card(html, "Average complexType reduction", formatPercent(summary.averageComplexTypeReduction));
        html.append("</div>\n");
        html.append("<p class=\"small\">Rows marked as generated schema failures had valid source schema/instance pairs, ");
        html.append("but the lightened schema did not validate the same instance.</p>\n");
        html.append("<table>\n<thead><tr>");
        html.append("<th>#</th><th>Status</th><th>XML instance</th><th>Source schema</th>");
        html.append("<th class=\"number\">Elements</th><th class=\"number\">Element reduction</th>");
        html.append("<th class=\"number\">simpleTypes</th><th class=\"number\">simpleType reduction</th>");
        html.append("<th class=\"number\">complexTypes</th><th class=\"number\">complexType reduction</th>");
        html.append("<th>Message</th></tr></thead>\n<tbody>\n");
        for (ReportRow row : rows) {
            html.append("<tr class=\"").append(row.status.cssClass).append("\">");
            html.append("<td class=\"number\">").append(row.index).append("</td>");
            html.append("<td class=\"status\">").append(escapeHtml(row.status.label)).append("</td>");
            html.append("<td class=\"path\">").append(escapeHtml(relative(row.samplePair.instance))).append("</td>");
            html.append("<td class=\"path\">").append(escapeHtml(relative(row.samplePair.schema))).append("</td>");
            countsCell(html, row.originalCounts.elements, row.lightenedCounts.elements);
            percentCell(html, reduction(row.originalCounts.elements, row.lightenedCounts.elements));
            countsCell(html, row.originalCounts.simpleTypes, row.lightenedCounts.simpleTypes);
            percentCell(html, reduction(row.originalCounts.simpleTypes, row.lightenedCounts.simpleTypes));
            countsCell(html, row.originalCounts.complexTypes, row.lightenedCounts.complexTypes);
            percentCell(html, reduction(row.originalCounts.complexTypes, row.lightenedCounts.complexTypes));
            html.append("<td class=\"message\">").append(escapeHtml(row.message)).append("</td>");
            html.append("</tr>\n");
        }
        html.append("</tbody>\n</table>\n</body>\n</html>\n");
        return html.toString();
    }

    private Summary summarize(List<ReportRow> rows) {
        Summary summary = new Summary();
        summary.total = rows.size();
        double elementTotal = 0.0;
        double simpleTypeTotal = 0.0;
        double complexTypeTotal = 0.0;
        int reductionRows = 0;
        for (ReportRow row : rows) {
            if (row.status == Status.PASS) {
                summary.passed++;
            } else if (row.status == Status.GENERATED_INVALID) {
                summary.generatedInvalid++;
            } else if (row.status == Status.SOURCE_INVALID) {
                summary.sourceInvalid++;
            } else if (row.status == Status.TRANSFORM_FAILED) {
                summary.transformFailed++;
            }
            if (row.status != Status.SOURCE_INVALID && row.status != Status.TRANSFORM_FAILED) {
                elementTotal += reduction(row.originalCounts.elements, row.lightenedCounts.elements);
                simpleTypeTotal += reduction(row.originalCounts.simpleTypes, row.lightenedCounts.simpleTypes);
                complexTypeTotal += reduction(row.originalCounts.complexTypes, row.lightenedCounts.complexTypes);
                reductionRows++;
            }
        }
        if (reductionRows > 0) {
            summary.averageElementReduction = elementTotal / reductionRows;
            summary.averageSimpleTypeReduction = simpleTypeTotal / reductionRows;
            summary.averageComplexTypeReduction = complexTypeTotal / reductionRows;
        }
        return summary;
    }

    private static void card(StringBuilder html, String label, String value) {
        html.append("<div class=\"card\"><strong>")
                .append(escapeHtml(value))
                .append("</strong>")
                .append(escapeHtml(label))
                .append("</div>");
    }

    private static void countsCell(StringBuilder html, int original, int lightened) {
        html.append("<td class=\"number\">")
                .append(original)
                .append(" -> ")
                .append(lightened)
                .append("</td>");
    }

    private static void percentCell(StringBuilder html, double value) {
        html.append("<td class=\"number\">").append(formatPercent(value)).append("</td>");
    }

    private static String relative(Path path) {
        Path base = Paths.get("").toAbsolutePath().normalize();
        Path absolute = path.toAbsolutePath().normalize();
        if (absolute.startsWith(base)) {
            return base.relativize(absolute).toString();
        }
        return absolute.toString();
    }

    private static double reduction(int original, int lightened) {
        if (original <= 0) {
            return 0.0;
        }
        return (original - lightened) * 100.0 / original;
    }

    private static String formatPercent(double value) {
        return PERCENT_FORMAT.format(value) + "%";
    }

    private static String safeFileName(String value) {
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static boolean isAbsoluteUri(String value) {
        return value.matches("^[A-Za-z][A-Za-z0-9+.-]*:.*");
    }

    private static String describe(Exception e) {
        String message = e.getMessage();
        Throwable cause = e.getCause();
        if ((message == null || message.trim().isEmpty()) && cause != null) {
            message = cause.getMessage();
        }
        if (message == null || message.trim().isEmpty()) {
            message = e.getClass().getName();
        }
        return e.getClass().getSimpleName() + ": " + message;
    }

    private static String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static void deleteRecursively(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            List<Path> orderedPaths = paths
                    .sorted(Comparator.reverseOrder())
                    .collect(Collectors.toList());
            for (Path path : orderedPaths) {
                Files.deleteIfExists(path);
            }
        }
    }

    private enum Status {
        PASS("Pass", "pass"),
        SOURCE_INVALID("Source invalid", "source_invalid"),
        TRANSFORM_FAILED("Transform failed", "transform_failed"),
        GENERATED_INVALID("Generated invalid", "generated_invalid");

        private final String label;
        private final String cssClass;

        Status(String label, String cssClass) {
            this.label = label;
            this.cssClass = cssClass;
        }
    }

    private static final class SamplePair {
        private final Path instance;
        private final Path schema;

        private SamplePair(Path instance, Path schema) {
            this.instance = instance;
            this.schema = schema;
        }
    }

    private static final class Counts {
        private final int elements;
        private final int simpleTypes;
        private final int complexTypes;

        private Counts(int elements, int simpleTypes, int complexTypes) {
            this.elements = elements;
            this.simpleTypes = simpleTypes;
            this.complexTypes = complexTypes;
        }

        private static Counts empty() {
            return new Counts(0, 0, 0);
        }

        private Counts plus(Counts other) {
            return new Counts(
                    elements + other.elements,
                    simpleTypes + other.simpleTypes,
                    complexTypes + other.complexTypes);
        }
    }

    private static final class ReportRow {
        private final int index;
        private final SamplePair samplePair;
        private final Status status;
        private final Counts originalCounts;
        private final Counts lightenedCounts;
        private final String message;

        private ReportRow(
                int index,
                SamplePair samplePair,
                Status status,
                Counts originalCounts,
                Counts lightenedCounts,
                String message) {
            this.index = index;
            this.samplePair = samplePair;
            this.status = status;
            this.originalCounts = originalCounts;
            this.lightenedCounts = lightenedCounts;
            this.message = message;
        }
    }

    private static final class Summary {
        private int total;
        private int passed;
        private int generatedInvalid;
        private int sourceInvalid;
        private int transformFailed;
        private double averageElementReduction;
        private double averageSimpleTypeReduction;
        private double averageComplexTypeReduction;
    }
}
