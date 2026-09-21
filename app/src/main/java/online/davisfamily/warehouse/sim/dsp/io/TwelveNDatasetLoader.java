package online.davisfamily.warehouse.sim.dsp.io;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class TwelveNDatasetLoader {

    public List<TwelveNMessageJson> load(List<Path> paths) {
        if (paths == null) {
            throw new IllegalArgumentException("paths must not be null");
        }
        List<TwelveNMessageJson> messages = new ArrayList<>();
        for (Path path : paths) {
            if (path == null) {
                throw new IllegalArgumentException("paths must not contain null");
            }
            messages.add(JsonLoaderSupport.read(path, TwelveNMessageJson.class));
        }
        return List.copyOf(messages);
    }

    public TwelveNLoadResult loadRecovering(List<Path> paths) {
        if (paths == null) {
            throw new IllegalArgumentException("paths must not be null");
        }

        List<TwelveNInputMessage> messages = new ArrayList<>();
        List<TwelveNRejectedInputMessage> rejectedMessages = new ArrayList<>();
        for (int sourceMessageEncounterIndex = 0;
                sourceMessageEncounterIndex < paths.size();
                sourceMessageEncounterIndex++) {
            Path path = paths.get(sourceMessageEncounterIndex);
            if (path == null) {
                throw new IllegalArgumentException("paths must not contain null");
            }

            // Reading is intentionally outside the recovery boundary: an unreadable file is an
            // environmental input failure, whereas parsing and structural mapping belong to the
            // individual-document recovery boundary.
            String json = JsonLoaderSupport.readText(path);
            try {
                TwelveNMessageJson message = JsonLoaderSupport.readString(json, TwelveNMessageJson.class);
                validateRecoverableDocument(message);
                messages.add(new TwelveNInputMessage(
                        path,
                        sourceMessageEncounterIndex,
                        message));
            } catch (RuntimeException exception) {
                rejectedMessages.add(TwelveNRejectedInputMessage.fromException(
                        path,
                        sourceMessageEncounterIndex,
                        exception));
            }
        }
        return new TwelveNLoadResult(messages, rejectedMessages);
    }

    private static void validateRecoverableDocument(TwelveNMessageJson message) {
        TwelveNLineMappingSupport.validateMessage(message);
        TwelveNMessageKind messageKind = new TwelveNMessageKindMapper()
                .map(message.toteIdentifier().payload());
        if (messageKind == TwelveNMessageKind.MANUAL_PREPARATION) {
            TwelveNLineMappingSupport.validateOrderLineCount(message.orderDetail());
            return;
        }

        // Mapping with a neutral sequence validates the complete per-document 12N shape. It
        // does not perform any cross-document grouping or product-master validation.
        new TwelveNOrderMapper(new TwelveNMessageKindMapper()).map(message, 0L);
    }

    public TwelveNMessageJson loadString(String json) {
        return JsonLoaderSupport.readString(json, TwelveNMessageJson.class);
    }

    public List<TwelveNMessageJson> loadStrings(List<String> jsonMessages) {
        if (jsonMessages == null) {
            throw new IllegalArgumentException("jsonMessages must not be null");
        }
        List<TwelveNMessageJson> messages = new ArrayList<>();
        for (String json : jsonMessages) {
            if (json == null) {
                throw new IllegalArgumentException("jsonMessages must not contain null");
            }
            messages.add(loadString(json));
        }
        return List.copyOf(messages);
    }
}
