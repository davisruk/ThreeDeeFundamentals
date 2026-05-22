package online.davisfamily.warehouse.sim.dsp.adapting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;

public class AdaptingStorageLayout {
    private final AdaptingStorageConfig config;
    private AdaptingStorageMap storageMap;
    private final Map<PreparedLineKey, AdaptedLineRecord> stagedRecords = new LinkedHashMap<>();
    private final Map<String, BinCursor> cursorsByPharmacy = new LinkedHashMap<>();

    public AdaptingStorageLayout(AdaptingStorageConfig config, AdaptingStorageMap storageMap) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        if (storageMap == null) {
            throw new IllegalArgumentException("storageMap must not be null");
        }
        this.config = config;
        this.storageMap = storageMap;
    }

    public void bindStorageMap(AdaptingStorageMap storageMap) {
        if (storageMap == null) {
            throw new IllegalArgumentException("storageMap must not be null");
        }
        this.storageMap = storageMap;
    }

    public AdaptedLineRecord stage(DspOrderItem line) {
        if (line == null) {
            throw new IllegalArgumentException("line must not be null");
        }
        AdaptingBenchId benchId = storageMap.preferredBenchFor(line.pharmacyId());
        BinCursor cursor = cursorsByPharmacy.computeIfAbsent(
                line.pharmacyId(),
                pharmacyId -> new BinCursor(pharmacyId, benchId));
        if (!cursor.benchId.equals(benchId)) {
            cursor = new BinCursor(line.pharmacyId(), benchId);
            cursorsByPharmacy.put(line.pharmacyId(), cursor);
        }

        AdaptingStorageLocation location = cursor.currentLocation();
        AdaptedLineRecord record = AdaptedLineRecord.fromPreparedLine(line, location);
        stagedRecords.put(record.key(), record);
        cursor.advance(config);
        return record;
    }

    public void stage(AdaptedLineRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("record must not be null");
        }
        stagedRecords.put(record.key(), record);
    }

    public boolean contains(PreparedLineKey key) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        return stagedRecords.containsKey(key);
    }

    public AdaptedLineRecord take(PreparedLineKey key) {
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        return stagedRecords.remove(key);
    }

    public List<AdaptedLineRecord> takeAll(List<PreparedLineKey> keys) {
        if (keys == null) {
            throw new IllegalArgumentException("keys must not be null");
        }
        List<PreparedLineKey> missingKeys = keys.stream()
                .filter(key -> !stagedRecords.containsKey(key))
                .toList();
        if (!missingKeys.isEmpty()) {
            throw new IllegalStateException("Missing staged adapted lines for keys: " + missingKeys);
        }

        List<AdaptedLineRecord> records = new ArrayList<>(keys.size());
        for (PreparedLineKey key : keys) {
            records.add(stagedRecords.remove(key));
        }
        return List.copyOf(records);
    }

    public AdaptedLineStoreSnapshot snapshot() {
        Map<AdaptingBenchId, Integer> stagedLineCountByBench = new LinkedHashMap<>();
        Set<String> rackKeys = new LinkedHashSet<>();
        Set<String> shelfKeys = new LinkedHashSet<>();
        Set<String> binKeys = new LinkedHashSet<>();

        for (AdaptedLineRecord record : stagedRecords.values()) {
            AdaptingStorageLocation location = record.location();
            stagedLineCountByBench.merge(location.benchId(), 1, Integer::sum);
            rackKeys.add(location.benchId().value() + ":" + location.rackIndex());
            shelfKeys.add(location.benchId().value() + ":" + location.rackIndex() + ":" + location.shelfIndex());
            binKeys.add(location.benchId().value() + ":" + location.rackIndex() + ":" + location.shelfIndex() + ":" + location.binIndex());
        }

        return new AdaptedLineStoreSnapshot(
                stagedRecords.size(),
                stagedRecords.keySet(),
                stagedLineCountByBench,
                rackKeys.size(),
                shelfKeys.size(),
                binKeys.size());
    }

    private static final class BinCursor {
        private final String pharmacyId;
        private final AdaptingBenchId benchId;
        private int rackIndex;
        private int shelfIndex;
        private int binIndex;
        private int lineCountInBin;

        private BinCursor(String pharmacyId, AdaptingBenchId benchId) {
            this.pharmacyId = pharmacyId;
            this.benchId = benchId;
        }

        private AdaptingStorageLocation currentLocation() {
            return new AdaptingStorageLocation(pharmacyId, benchId, rackIndex, shelfIndex, binIndex);
        }

        private void advance(AdaptingStorageConfig config) {
            lineCountInBin++;
            if (lineCountInBin < config.linesPerBin()) {
                return;
            }

            lineCountInBin = 0;
            binIndex++;
            if (binIndex < config.binsPerShelf()) {
                return;
            }

            binIndex = 0;
            shelfIndex++;
            if (shelfIndex < config.shelvesPerRack()) {
                return;
            }

            shelfIndex = 0;
            rackIndex++;
        }
    }
}
