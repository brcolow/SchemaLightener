package com.xmlhelpline.schemalightener;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result metadata for a completed SchemaLightener transformation.
 */
public final class TransformationResult {
    private final TransformationOperation operation;
    private final Path source;
    private final Path outputDirectory;
    private final List<Path> outputFiles;
    private final String primaryResult;

    TransformationResult(
            TransformationOperation operation,
            Path source,
            Path outputDirectory,
            List<Path> outputFiles,
            String primaryResult) {
        this.operation = operation;
        this.source = source;
        this.outputDirectory = outputDirectory;
        this.outputFiles = Collections.unmodifiableList(new ArrayList<Path>(outputFiles));
        this.primaryResult = primaryResult;
    }

    /**
     * Get the transformation operation that produced this result.
     *
     * @return transformation operation that produced this result
     */
    public TransformationOperation getOperation() {
        return operation;
    }

    /**
     * Get the source file supplied to the transformation.
     *
     * @return source file supplied to the transformation
     */
    public Path getSource() {
        return source;
    }

    /**
     * Get the output directory that received generated files.
     *
     * @return output directory that received generated files
     */
    public Path getOutputDirectory() {
        return outputDirectory;
    }

    /**
     * Get the generated output files.
     *
     * @return generated output files
     */
    public List<Path> getOutputFiles() {
        return outputFiles;
    }

    /**
     * The primary XSLT result. The bundled stylesheets mostly write files using xsl:result-document,
     * so this value is often empty.
     *
     * @return primary transformation result
     */
    public String getPrimaryResult() {
        return primaryResult;
    }
}
