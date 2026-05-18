package online.davisfamily.warehouse.sim.dsp.adapting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;

public class AdaptedLineStore {
    private final Map<PreparedLineKey, AdaptedLineRecord> stagedLines = new LinkedHashMap<>();

    public void stage(DspOrderItem line) {
        stage(AdaptedLineRecord.fromPreparedLine(line));
    }

    public void stage(AdaptedLineRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("record must not be null");
        }
        stagedLines.put(record.key(), record);
    }

    public boolean contains(PreparedLineKey key) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        return stagedLines.containsKey(key);
    }

    public Optional<AdaptedLineRecord> take(PreparedLineKey key) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        return Optional.ofNullable(stagedLines.remove(key));
    }

    public List<AdaptedLineRecord> takeAll(Iterable<PreparedLineKey> keys) {
        if (keys == null) {
            throw new IllegalArgumentException("keys must not be null");
        }

        List<PreparedLineKey> requestedKeys = new ArrayList<>();
        for (PreparedLineKey key : keys) {
            if (key == null) {
                throw new IllegalArgumentException("keys must not contain null");
            }
            requestedKeys.add(key);
        }

        List<PreparedLineKey> missingKeys = requestedKeys.stream()
                .filter(key -> !stagedLines.containsKey(key))
                .toList();
        if (!missingKeys.isEmpty()) {
            throw new IllegalStateException("Missing staged adapted lines for keys: " + missingKeys);
        }

        List<AdaptedLineRecord> records = new ArrayList<>(requestedKeys.size());
        for (PreparedLineKey key : requestedKeys) {
            records.add(stagedLines.remove(key));
        }
        return List.copyOf(records);
    }

    public AdaptedLineStoreSnapshot snapshot() {
        return new AdaptedLineStoreSnapshot(stagedLines.size(), stagedLines.keySet());
    }
}
