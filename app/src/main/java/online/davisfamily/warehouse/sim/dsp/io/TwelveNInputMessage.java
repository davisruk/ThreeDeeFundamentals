package online.davisfamily.warehouse.sim.dsp.io;

import java.nio.file.Path;

/** An individually parsed 12N message together with its source identity. */
public record TwelveNInputMessage(
        Path path,
        int sourceMessageEncounterIndex,
        TwelveNMessageJson message) {

    public TwelveNInputMessage {
        if (path == null) {
            throw new IllegalArgumentException("path must not be null");
        }
        if (sourceMessageEncounterIndex < 0) {
            throw new IllegalArgumentException("sourceMessageEncounterIndex must be >= 0");
        }
        if (message == null) {
            throw new IllegalArgumentException("message must not be null");
        }
    }

    /** Compatibility terminology for callers that refer to this as the encounter index. */
    public int encounterIndex() {
        return sourceMessageEncounterIndex;
    }
}
