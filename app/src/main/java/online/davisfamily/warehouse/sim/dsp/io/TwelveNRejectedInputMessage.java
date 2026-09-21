package online.davisfamily.warehouse.sim.dsp.io;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;

/** Immutable diagnostics for one 12N document rejected during recoverable loading. */
public record TwelveNRejectedInputMessage(
        Path path,
        int sourceMessageEncounterIndex,
        String diagnostic,
        String exceptionClassName,
        List<String> stackTraceLines) {

    public TwelveNRejectedInputMessage {
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        if (sourceMessageEncounterIndex < 0) {
            throw new IllegalArgumentException("sourceMessageEncounterIndex must be >= 0");
        }
        if (diagnostic == null || diagnostic.isBlank()) {
            throw new IllegalArgumentException("diagnostic must not be blank");
        }
        diagnostic = diagnostic.trim();
        if (exceptionClassName == null || exceptionClassName.isBlank()) {
            throw new IllegalArgumentException("exceptionClassName must not be blank");
        }
        exceptionClassName = exceptionClassName.trim();
        if (stackTraceLines == null || stackTraceLines.stream().anyMatch(line -> line == null)) {
            throw new IllegalArgumentException("stackTraceLines must not be null or contain null");
        }
        stackTraceLines = List.copyOf(stackTraceLines);
    }

    /** Compatibility terminology for callers that refer to this as the encounter index. */
    public int encounterIndex() {
        return sourceMessageEncounterIndex;
    }

    static TwelveNRejectedInputMessage fromException(
            Path path,
            int sourceMessageEncounterIndex,
            RuntimeException exception) {
        if (exception == null) {
            throw new IllegalArgumentException("exception must not be null");
        }
        String diagnostic = exception.getMessage();
        if (diagnostic == null || diagnostic.isBlank()) {
            diagnostic = exception.toString();
        }

        StringWriter writer = new StringWriter();
        exception.printStackTrace(new PrintWriter(writer));
        List<String> stackTraceLines = writer.toString().lines().toList();
        return new TwelveNRejectedInputMessage(
                path,
                sourceMessageEncounterIndex,
                diagnostic,
                exception.getClass().getName(),
                stackTraceLines);
    }
}
