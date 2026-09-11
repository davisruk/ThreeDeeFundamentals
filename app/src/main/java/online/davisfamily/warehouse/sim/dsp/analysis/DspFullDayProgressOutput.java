package online.davisfamily.warehouse.sim.dsp.analysis;

import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;

/** Calling-thread progress sink that mirrors flushed blocks to console and optional UTF-8 file. */
final class DspFullDayProgressOutput implements AutoCloseable {
    private final PrintStream console;
    private final Writer fileWriter;
    private boolean closed;

    static DspFullDayProgressOutput open(
            PrintStream console,
            Optional<Path> progressLogPath,
            boolean overwrite) throws IOException {
        if (console == null || progressLogPath == null) {
            throw new IllegalArgumentException("progress output values must not be null");
        }
        if (progressLogPath.isEmpty()) {
            return new DspFullDayProgressOutput(console, Optional.empty());
        }
        Path path = progressLogPath.orElseThrow();
        Path parent = path.toAbsolutePath().getParent();
        if (parent == null) {
            throw new IOException("progress log has no parent directory: " + path);
        }
        Files.createDirectories(parent);
        Writer writer = overwrite
                ? Files.newBufferedWriter(
                        path,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE)
                : Files.newBufferedWriter(
                        path,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE);
        return new DspFullDayProgressOutput(console, Optional.of(writer));
    }

    /** Test seam for deterministic write, flush, and close failure injection. */
    DspFullDayProgressOutput(PrintStream console, Optional<Writer> fileWriter) {
        if (console == null || fileWriter == null) {
            throw new IllegalArgumentException("progress output values must not be null");
        }
        this.console = console;
        this.fileWriter = fileWriter.orElse(null);
    }

    void print(String milestone, List<String> lines) throws IOException {
        if (closed) {
            throw new IOException("progress output is closed");
        }
        if (milestone == null || milestone.isBlank()) {
            throw new IllegalArgumentException("milestone must not be blank");
        }
        if (lines == null || lines.stream().anyMatch(line -> line == null)) {
            throw new IllegalArgumentException("progress lines must not be null");
        }
        String lineSeparator = System.lineSeparator();
        String header = "[dsp-full-day:" + milestone + "]";
        console.print(header + lineSeparator);
        for (String line : lines) {
            console.print(line + lineSeparator);
        }
        console.flush();
        if (fileWriter != null) {
            fileWriter.write(header);
            fileWriter.write(lineSeparator);
            for (String line : lines) {
                fileWriter.write(line);
                fileWriter.write(lineSeparator);
            }
            fileWriter.flush();
        }
    }

    @Override
    public void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        if (fileWriter != null) {
            IOException failure = null;
            try {
                fileWriter.flush();
            } catch (IOException exception) {
                failure = exception;
            }
            try {
                fileWriter.close();
            } catch (IOException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }
}
