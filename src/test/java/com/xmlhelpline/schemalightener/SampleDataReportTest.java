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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
        Path sampleOutputDirectory = generatedDirectory.resolve(String.format(
                Locale.ROOT,
                "%03d-%s",
                index,
                safeFileName(samplePair.instance.getFileName().toString())));
        Counts originalCounts = countSchemaClosure(samplePair.schema);

        String sourceValidation = validate(samplePair.schema, samplePair.instance);
        if (!"OK".equals(sourceValidation)) {
            return new ReportRow(
                    index,
                    samplePair,
                    originalCounts,
                    sourceValidation,
                    GeneratedRun.notRun(),
                    GeneratedRun.notRun(),
                    OutputComparison.notCompared("Source schema did not validate the instance"));
        }

        GeneratedRun directRun = runGeneratedPath(
                samplePair,
                sampleOutputDirectory.resolve("direct-lighten"),
                false);
        GeneratedRun flattenLightenRun = runGeneratedPath(
                samplePair,
                sampleOutputDirectory.resolve("flatten-lighten"),
                true);

        return new ReportRow(
                index,
                samplePair,
                originalCounts,
                sourceValidation,
                directRun,
                flattenLightenRun,
                compareOutputs(directRun, flattenLightenRun));
    }

    private GeneratedRun runGeneratedPath(
            SamplePair samplePair,
            Path outputDirectory,
            boolean flattenFirst) {
        try {
            TransformationResult result = flattenFirst
                    ? schemaLightener.flattenAndLightenSchema(samplePair.schema, samplePair.instance, outputDirectory)
                    : schemaLightener.lightenSchema(samplePair.schema, samplePair.instance, outputDirectory);
            List<Path> outputFiles = result.getOutputFiles();
            Counts counts = countFiles(outputFiles);
            Path rootOutput = findRootOutput(result, samplePair.schema);
            String generatedValidation = validate(rootOutput, samplePair.instance);
            return new GeneratedRun(
                    "OK".equals(generatedValidation) ? RunStatus.PASS : RunStatus.GENERATED_INVALID,
                    counts,
                    generatedValidation,
                    "OK".equals(generatedValidation) ? "" : generatedValidation,
                    result.getOutputDirectory(),
                    outputFiles);
        } catch (Exception e) {
            return new GeneratedRun(
                    RunStatus.TRANSFORM_FAILED,
                    Counts.empty(),
                    "Not run",
                    describe(e),
                    outputDirectory.toAbsolutePath().normalize(),
                    Collections.<Path>emptyList());
        }
    }

    private OutputComparison compareOutputs(GeneratedRun directRun, GeneratedRun flattenLightenRun) {
        if (directRun.status == RunStatus.NOT_RUN || flattenLightenRun.status == RunStatus.NOT_RUN) {
            return OutputComparison.notCompared("One or both paths were not run");
        }
        if (directRun.status == RunStatus.TRANSFORM_FAILED || flattenLightenRun.status == RunStatus.TRANSFORM_FAILED) {
            return OutputComparison.notCompared("One or both paths failed before comparable output was available");
        }

        try {
            Map<String, String> directOutputs = outputContentsByRelativePath(directRun);
            Map<String, String> flattenLightenOutputs = outputContentsByRelativePath(flattenLightenRun);
            List<String> onlyDirect = new ArrayList<String>();
            List<String> onlyFlattenLighten = new ArrayList<String>();
            List<String> changed = new ArrayList<String>();

            Set<String> allNames = new LinkedHashSet<String>();
            allNames.addAll(directOutputs.keySet());
            allNames.addAll(flattenLightenOutputs.keySet());
            for (String name : allNames) {
                if (!flattenLightenOutputs.containsKey(name)) {
                    onlyDirect.add(name);
                } else if (!directOutputs.containsKey(name)) {
                    onlyFlattenLighten.add(name);
                } else if (!directOutputs.get(name).equals(flattenLightenOutputs.get(name))) {
                    changed.add(name);
                }
            }

            if (onlyDirect.isEmpty() && onlyFlattenLighten.isEmpty() && changed.isEmpty()) {
                return new OutputComparison(
                        false,
                        "Same generated output (" + directOutputs.size() + " file(s))");
            }

            List<String> details = new ArrayList<String>();
            details.add("direct " + directOutputs.size()
                    + " file(s), flatten+lighten " + flattenLightenOutputs.size() + " file(s)");
            appendDetail(details, "only direct", onlyDirect);
            appendDetail(details, "only flatten+lighten", onlyFlattenLighten);
            appendDetail(details, "changed", changed);
            return new OutputComparison(true, "Different output: " + String.join("; ", details));
        } catch (IOException e) {
            return new OutputComparison(true, "Unable to compare generated output: " + describe(e));
        }
    }

    private Map<String, String> outputContentsByRelativePath(GeneratedRun run) throws IOException {
        Map<String, String> outputs = new LinkedHashMap<String, String>();
        for (Path outputFile : run.outputFiles) {
            String relativePath = relativeOutputName(run.outputDirectory, outputFile);
            String content = new String(Files.readAllBytes(outputFile), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n")
                    .replace("\r", "\n");
            outputs.put(relativePath, content);
        }
        return outputs;
    }

    private static String relativeOutputName(Path outputDirectory, Path outputFile) {
        Path normalizedDirectory = outputDirectory.toAbsolutePath().normalize();
        Path normalizedFile = outputFile.toAbsolutePath().normalize();
        if (normalizedFile.startsWith(normalizedDirectory)) {
            return normalizedDirectory.relativize(normalizedFile).toString();
        }
        return normalizedFile.getFileName().toString();
    }

    private static void appendDetail(List<String> details, String label, List<String> values) {
        if (!values.isEmpty()) {
            details.add(label + ": " + preview(values));
        }
    }

    private static String preview(List<String> values) {
        int limit = Math.min(values.size(), 4);
        String preview = String.join(", ", values.subList(0, limit));
        if (values.size() > limit) {
            preview += ", +" + (values.size() - limit) + " more";
        }
        return preview;
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
        html.append("tr.diverged td.status{color:#7a4f01;font-weight:bold}");
        html.append("tr.generated_invalid td.status{color:#b7791f;font-weight:bold}");
        html.append("tr.source_invalid td.status,tr.transform_failed td.status{color:#c42d2d;font-weight:bold}");
        html.append(".run_pass{color:#0f7b45;font-weight:bold}.run_generated_invalid,.comparison_diverged{color:#b7791f;font-weight:bold}");
        html.append(".run_transform_failed{color:#c42d2d;font-weight:bold}.run_not_run{color:#52606d}");
        html.append(".number{text-align:right;font-variant-numeric:tabular-nums}.path{font-family:Consolas,monospace;font-size:12px}");
        html.append(".metric{white-space:nowrap}.message{max-width:420px}.comparison{max-width:360px}.small{font-size:12px;color:#52606d}");
        html.append("</style>\n</head>\n<body>\n");
        html.append("<h1>SchemaLightener Sample Data Report</h1>\n");
        html.append("<p class=\"meta\">Generated ").append(escapeHtml(Instant.now().toString())).append("</p>\n");
        html.append("<div class=\"cards\">");
        card(html, "Sample pairs", String.valueOf(summary.total));
        card(html, "Direct valid schemas", String.valueOf(summary.directPassed));
        card(html, "Flatten+lighten valid schemas", String.valueOf(summary.flattenLightenPassed));
        card(html, "Validation differences", String.valueOf(summary.validationDifferences));
        card(html, "Output differences", String.valueOf(summary.outputDifferences));
        card(html, "Generated schema failures", String.valueOf(summary.generatedInvalid));
        card(html, "Direct average element reduction", formatPercent(summary.directAverageElementReduction));
        card(html, "Flatten+lighten average element reduction", formatPercent(summary.flattenLightenAverageElementReduction));
        card(html, "Direct average simpleType reduction", formatPercent(summary.directAverageSimpleTypeReduction));
        card(html, "Flatten+lighten average simpleType reduction", formatPercent(summary.flattenLightenAverageSimpleTypeReduction));
        card(html, "Direct average complexType reduction", formatPercent(summary.directAverageComplexTypeReduction));
        card(html, "Flatten+lighten average complexType reduction", formatPercent(summary.flattenLightenAverageComplexTypeReduction));
        html.append("</div>\n");
        html.append("<p class=\"small\">Each sample runs direct lightenSchema(...) and composed flattenAndLightenSchema(...). ");
        html.append("Validation differences compare whether each generated root schema validates the same instance; ");
        html.append("output differences compare generated file sets and normalized file content.</p>\n");
        html.append("<table>\n<thead><tr>");
        html.append("<th>#</th><th>Status</th><th>XML instance</th><th>Source schema</th>");
        html.append("<th class=\"number\">Source counts</th>");
        html.append("<th>Direct validation</th><th class=\"number\">Direct output counts</th><th class=\"number\">Direct reduction</th>");
        html.append("<th>Flatten+lighten validation</th><th class=\"number\">Flatten+lighten output counts</th>");
        html.append("<th class=\"number\">Flatten+lighten reduction</th><th>Output comparison</th>");
        html.append("<th>Message</th></tr></thead>\n<tbody>\n");
        for (ReportRow row : rows) {
            html.append("<tr class=\"").append(row.status.cssClass).append("\">");
            html.append("<td class=\"number\">").append(row.index).append("</td>");
            html.append("<td class=\"status\">").append(escapeHtml(row.status.label)).append("</td>");
            html.append("<td class=\"path\">").append(escapeHtml(relative(row.samplePair.instance))).append("</td>");
            html.append("<td class=\"path\">").append(escapeHtml(relative(row.samplePair.schema))).append("</td>");
            countsCell(html, row.originalCounts);
            validationCell(html, row.directRun);
            generatedCountsCell(html, row.directRun);
            generatedReductionCell(html, row.originalCounts, row.directRun);
            validationCell(html, row.flattenLightenRun);
            generatedCountsCell(html, row.flattenLightenRun);
            generatedReductionCell(html, row.originalCounts, row.flattenLightenRun);
            outputComparisonCell(html, row.outputComparison);
            html.append("<td class=\"message\">").append(escapeHtml(row.message())).append("</td>");
            html.append("</tr>\n");
        }
        html.append("</tbody>\n</table>\n</body>\n</html>\n");
        return html.toString();
    }

    private Summary summarize(List<ReportRow> rows) {
        Summary summary = new Summary();
        summary.total = rows.size();
        double directElementTotal = 0.0;
        double directSimpleTypeTotal = 0.0;
        double directComplexTypeTotal = 0.0;
        int directReductionRows = 0;
        double flattenLightenElementTotal = 0.0;
        double flattenLightenSimpleTypeTotal = 0.0;
        double flattenLightenComplexTypeTotal = 0.0;
        int flattenLightenReductionRows = 0;
        for (ReportRow row : rows) {
            if (row.status == Status.PASS) {
                summary.passed++;
            } else if (row.status == Status.DIVERGED) {
                summary.diverged++;
            } else if (row.status == Status.GENERATED_INVALID) {
                summary.generatedInvalid++;
            } else if (row.status == Status.SOURCE_INVALID) {
                summary.sourceInvalid++;
            } else if (row.status == Status.TRANSFORM_FAILED) {
                summary.transformFailed++;
            }
            if (row.directRun.status == RunStatus.PASS) {
                summary.directPassed++;
            }
            if (row.flattenLightenRun.status == RunStatus.PASS) {
                summary.flattenLightenPassed++;
            }
            if (row.validationDiverged()) {
                summary.validationDifferences++;
            }
            if (row.outputComparison.diverged) {
                summary.outputDifferences++;
            }
            if (row.directRun.hasOutput()) {
                directElementTotal += reduction(row.originalCounts.elements, row.directRun.counts.elements);
                directSimpleTypeTotal += reduction(row.originalCounts.simpleTypes, row.directRun.counts.simpleTypes);
                directComplexTypeTotal += reduction(row.originalCounts.complexTypes, row.directRun.counts.complexTypes);
                directReductionRows++;
            }
            if (row.flattenLightenRun.hasOutput()) {
                flattenLightenElementTotal += reduction(row.originalCounts.elements, row.flattenLightenRun.counts.elements);
                flattenLightenSimpleTypeTotal += reduction(row.originalCounts.simpleTypes, row.flattenLightenRun.counts.simpleTypes);
                flattenLightenComplexTypeTotal += reduction(row.originalCounts.complexTypes, row.flattenLightenRun.counts.complexTypes);
                flattenLightenReductionRows++;
            }
        }
        if (directReductionRows > 0) {
            summary.directAverageElementReduction = directElementTotal / directReductionRows;
            summary.directAverageSimpleTypeReduction = directSimpleTypeTotal / directReductionRows;
            summary.directAverageComplexTypeReduction = directComplexTypeTotal / directReductionRows;
        }
        if (flattenLightenReductionRows > 0) {
            summary.flattenLightenAverageElementReduction = flattenLightenElementTotal / flattenLightenReductionRows;
            summary.flattenLightenAverageSimpleTypeReduction = flattenLightenSimpleTypeTotal / flattenLightenReductionRows;
            summary.flattenLightenAverageComplexTypeReduction = flattenLightenComplexTypeTotal / flattenLightenReductionRows;
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

    private static void validationCell(StringBuilder html, GeneratedRun run) {
        html.append("<td class=\"")
                .append(run.status.cssClass)
                .append("\">")
                .append(escapeHtml(run.status.label))
                .append("</td>");
    }

    private static void countsCell(StringBuilder html, Counts counts) {
        html.append("<td class=\"number\">");
        metric(html, "E", String.valueOf(counts.elements));
        metric(html, "ST", String.valueOf(counts.simpleTypes));
        metric(html, "CT", String.valueOf(counts.complexTypes));
        html.append("</td>");
    }

    private static void generatedCountsCell(StringBuilder html, GeneratedRun run) {
        if (!run.hasOutput()) {
            notApplicableCell(html);
            return;
        }
        countsCell(html, run.counts);
    }

    private static void generatedReductionCell(StringBuilder html, Counts original, GeneratedRun run) {
        if (!run.hasOutput()) {
            notApplicableCell(html);
            return;
        }
        reductionCell(html, original, run.counts);
    }

    private static void reductionCell(StringBuilder html, Counts original, Counts generated) {
        html.append("<td class=\"number\">");
        metric(html, "E", formatPercent(reduction(original.elements, generated.elements)));
        metric(html, "ST", formatPercent(reduction(original.simpleTypes, generated.simpleTypes)));
        metric(html, "CT", formatPercent(reduction(original.complexTypes, generated.complexTypes)));
        html.append("</td>");
    }

    private static void notApplicableCell(StringBuilder html) {
        html.append("<td class=\"number small\">n/a</td>");
    }

    private static void outputComparisonCell(StringBuilder html, OutputComparison comparison) {
        html.append("<td class=\"comparison");
        if (comparison.diverged) {
            html.append(" comparison_diverged");
        }
        html.append("\">").append(escapeHtml(comparison.message)).append("</td>");
    }

    private static void metric(StringBuilder html, String label, String value) {
        html.append("<div class=\"metric\">")
                .append(escapeHtml(label))
                .append(": ")
                .append(escapeHtml(value))
                .append("</div>");
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
        DIVERGED("Diverged", "diverged"),
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

    private enum RunStatus {
        PASS("Valid", "run_pass"),
        GENERATED_INVALID("Invalid", "run_generated_invalid"),
        TRANSFORM_FAILED("Failed", "run_transform_failed"),
        NOT_RUN("Not run", "run_not_run");

        private final String label;
        private final String cssClass;

        RunStatus(String label, String cssClass) {
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
        private final String sourceValidation;
        private final GeneratedRun directRun;
        private final GeneratedRun flattenLightenRun;
        private final OutputComparison outputComparison;

        private ReportRow(
                int index,
                SamplePair samplePair,
                Counts originalCounts,
                String sourceValidation,
                GeneratedRun directRun,
                GeneratedRun flattenLightenRun,
                OutputComparison outputComparison) {
            this.index = index;
            this.samplePair = samplePair;
            this.originalCounts = originalCounts;
            this.sourceValidation = sourceValidation;
            this.directRun = directRun;
            this.flattenLightenRun = flattenLightenRun;
            this.outputComparison = outputComparison;
            this.status = determineStatus();
        }

        private Status determineStatus() {
            if (!"OK".equals(sourceValidation)) {
                return Status.SOURCE_INVALID;
            }
            if (directRun.status == RunStatus.TRANSFORM_FAILED
                    || flattenLightenRun.status == RunStatus.TRANSFORM_FAILED) {
                return Status.TRANSFORM_FAILED;
            }
            if (directRun.status == RunStatus.GENERATED_INVALID
                    || flattenLightenRun.status == RunStatus.GENERATED_INVALID) {
                return Status.GENERATED_INVALID;
            }
            if (validationDiverged() || outputComparison.diverged) {
                return Status.DIVERGED;
            }
            return Status.PASS;
        }

        private boolean validationDiverged() {
            if (!"OK".equals(sourceValidation)) {
                return false;
            }
            if (directRun.status != flattenLightenRun.status) {
                return true;
            }
            return directRun.status == RunStatus.GENERATED_INVALID
                    && !directRun.validation.equals(flattenLightenRun.validation);
        }

        private String message() {
            List<String> messages = new ArrayList<String>();
            if (!"OK".equals(sourceValidation)) {
                messages.add("Source: " + sourceValidation);
            }
            if (!directRun.message.isEmpty()) {
                messages.add("Direct: " + directRun.message);
            }
            if (!flattenLightenRun.message.isEmpty()) {
                messages.add("Flatten+lighten: " + flattenLightenRun.message);
            }
            return String.join(" | ", messages);
        }
    }

    private static final class GeneratedRun {
        private final RunStatus status;
        private final Counts counts;
        private final String validation;
        private final String message;
        private final Path outputDirectory;
        private final List<Path> outputFiles;

        private GeneratedRun(
                RunStatus status,
                Counts counts,
                String validation,
                String message,
                Path outputDirectory,
                List<Path> outputFiles) {
            this.status = status;
            this.counts = counts;
            this.validation = validation;
            this.message = message;
            this.outputDirectory = outputDirectory;
            this.outputFiles = Collections.unmodifiableList(new ArrayList<Path>(outputFiles));
        }

        private static GeneratedRun notRun() {
            return new GeneratedRun(
                    RunStatus.NOT_RUN,
                    Counts.empty(),
                    "Not run",
                    "",
                    Paths.get("").toAbsolutePath().normalize(),
                    Collections.<Path>emptyList());
        }

        private boolean hasOutput() {
            return status != RunStatus.NOT_RUN
                    && status != RunStatus.TRANSFORM_FAILED
                    && !outputFiles.isEmpty();
        }
    }

    private static final class OutputComparison {
        private final boolean diverged;
        private final String message;

        private OutputComparison(boolean diverged, String message) {
            this.diverged = diverged;
            this.message = message;
        }

        private static OutputComparison notCompared(String message) {
            return new OutputComparison(false, "Not compared: " + message);
        }
    }

    private static final class Summary {
        private int total;
        private int passed;
        private int diverged;
        private int directPassed;
        private int flattenLightenPassed;
        private int validationDifferences;
        private int outputDifferences;
        private int generatedInvalid;
        private int sourceInvalid;
        private int transformFailed;
        private double directAverageElementReduction;
        private double directAverageSimpleTypeReduction;
        private double directAverageComplexTypeReduction;
        private double flattenLightenAverageElementReduction;
        private double flattenLightenAverageSimpleTypeReduction;
        private double flattenLightenAverageComplexTypeReduction;
    }
}
