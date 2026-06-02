package com.xmlhelpline.schemalightener;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
     * Find a generated output file by file name.
     *
     * @param fileName output file name
     * @return output file path when a matching file exists
     */
    public Optional<Path> findOutputFile(String fileName) {
        Objects.requireNonNull(fileName, "fileName must not be null");
        for (Path outputFile : outputFiles) {
            if (outputFile.getFileName().toString().equals(fileName)) {
                return Optional.of(outputFile);
            }
        }
        return Optional.empty();
    }

    /**
     * Find a generated output file by file name, throwing when it is not present.
     *
     * @param fileName output file name
     * @return matching output file path
     */
    public Path requireOutputFile(String fileName) {
        return findOutputFile(fileName)
                .orElseThrow(() -> new SchemaLightenerException(
                        "Expected output file named " + fileName + " in " + outputFiles));
    }

    /**
     * Return the only generated output file when exactly one file was produced.
     *
     * @return the single output file, or empty when zero or multiple files were produced
     */
    public Optional<Path> singleOutputFile() {
        if (outputFiles.size() == 1) {
            return Optional.of(outputFiles.get(0));
        }
        return Optional.empty();
    }

    /**
     * Return the only generated output file, throwing when zero or multiple files were produced.
     *
     * @return the single output file
     */
    public Path requireSingleOutputFile() {
        return singleOutputFile()
                .orElseThrow(() -> new SchemaLightenerException(
                        "Expected exactly one output file but found " + outputFiles.size() + ": " + outputFiles));
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
