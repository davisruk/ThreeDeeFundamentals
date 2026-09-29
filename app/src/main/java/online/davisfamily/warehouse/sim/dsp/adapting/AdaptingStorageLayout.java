package online.davisfamily.warehouse.sim.dsp.adapting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;

public class AdaptingStorageLayout {
    private final AdaptingStorageConfig config;
    private final AdaptingTargetSheetCatalog targetSheetCatalog;
    private AdaptingStorageMap storageMap;
    private final Map<PreparedLineKey, AdaptedLineRecord> stagedRecords = new LinkedHashMap<>();
    private final Map<String, BinCursor> cursorsByPharmacy = new LinkedHashMap<>();
    private final Map<OrderSheetKey, SheetBinGroup> groupsByTargetSheet = new LinkedHashMap<>();
    private final Map<PreparedLineKey, SheetBin> binsByPreparedLine = new LinkedHashMap<>();
    private long mutationVersion;
    private long binSnapshotVersion = -1;
    private int occupiedStrictBinCount;
    private List<AdaptingBinSnapshot> cachedBinSnapshots = List.of();

    public AdaptingStorageLayout(AdaptingStorageConfig config, AdaptingStorageMap storageMap) {
        this(config, storageMap, null, false);
    }

    public AdaptingStorageLayout(
            AdaptingStorageConfig config,
            AdaptingStorageMap storageMap,
            AdaptingTargetSheetCatalog targetSheetCatalog) {
        this(config, storageMap, targetSheetCatalog, true);
    }

    private AdaptingStorageLayout(
            AdaptingStorageConfig config,
            AdaptingStorageMap storageMap,
            AdaptingTargetSheetCatalog targetSheetCatalog,
            boolean strict) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        if (storageMap == null) {
            throw new IllegalArgumentException("storageMap must not be null");
        }
        if (strict && targetSheetCatalog == null) {
            throw new IllegalArgumentException("targetSheetCatalog must not be null in strict storage");
        }
        this.config = config;
        this.storageMap = storageMap;
        this.targetSheetCatalog = targetSheetCatalog;
    }

    public void bindStorageMap(AdaptingStorageMap storageMap) {
        if (storageMap == null) {
            throw new IllegalArgumentException("storageMap must not be null");
        }
        this.storageMap = storageMap;
    }

    public AdaptedLineRecord stage(
            DspOrderItem line,
            OrderSheetKey sourceOrderSheetKey,
            String sourceServiceCentreId) {
        if (line == null) {
            throw new IllegalArgumentException("line must not be null");
        }
        if (targetSheetCatalog != null) {
            stageAll(List.of(line), sourceOrderSheetKey, sourceServiceCentreId);
            return stagedRecords.get(PreparedLineKey.forPreparedLine(line));
        }
        return stageLegacy(line, sourceOrderSheetKey, sourceServiceCentreId);
    }

    public void stageAll(
            List<DspOrderItem> lines,
            OrderSheetKey sourceOrderSheetKey,
            String sourceServiceCentreId) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("lines must not be empty");
        }
        if (sourceOrderSheetKey == null) {
            throw new IllegalArgumentException("sourceOrderSheetKey must not be null");
        }
        if (sourceServiceCentreId == null || sourceServiceCentreId.isBlank()) {
            throw new IllegalArgumentException("sourceServiceCentreId must not be blank");
        }

        Set<PreparedLineKey> visitKeys = new LinkedHashSet<>();
        Map<OrderSheetKey, String> visitPharmacies = new LinkedHashMap<>();
        List<OrderSheetKey> targetSheets = new ArrayList<>(lines.size());
        for (DspOrderItem line : lines) {
            if (line == null || line.lineType() != DspOrderLineType.ADAPTED) {
                throw new IllegalArgumentException("Every staged line must be ADAPTED");
            }
            PreparedLineKey key = PreparedLineKey.forPreparedLine(line);
            if (!visitKeys.add(key) || stagedRecords.containsKey(key)) {
                throw new IllegalStateException("Duplicate staged adapted line: " + key);
            }
            if (targetSheetCatalog == null) {
                storageMap.preferredBenchFor(line.pharmacyId());
            } else {
                OrderSheetKey targetSheet = targetSheetCatalog.requireTargetSheet(key);
                String priorPharmacy = visitPharmacies.putIfAbsent(targetSheet, line.pharmacyId());
                if (priorPharmacy != null && !priorPharmacy.equals(line.pharmacyId())) {
                    throw new IllegalStateException("Target sheet has lines from different pharmacies: " + targetSheet);
                }
                SheetBinGroup group = groupsByTargetSheet.get(targetSheet);
                if (group != null && !group.storeId.equals(line.pharmacyId())) {
                    throw new IllegalStateException("Target sheet store changed: " + targetSheet);
                }
                targetSheets.add(targetSheet);
            }
        }

        for (int index = 0; index < lines.size(); index++) {
            if (targetSheetCatalog == null) {
                stageLegacy(lines.get(index), sourceOrderSheetKey, sourceServiceCentreId);
            } else {
                stageStrict(lines.get(index), sourceOrderSheetKey, sourceServiceCentreId,
                        targetSheets.get(index));
            }
        }
        if (targetSheetCatalog != null) {
            mutationVersion++;
        }
    }

    private AdaptedLineRecord stageLegacy(
            DspOrderItem line,
            OrderSheetKey sourceOrderSheetKey,
            String sourceServiceCentreId) {
        AdaptingBenchId benchId = storageMap.preferredBenchFor(line.pharmacyId());
        BinCursor cursor = cursorsByPharmacy.computeIfAbsent(
                line.pharmacyId(),
                pharmacyId -> new BinCursor(pharmacyId, benchId));
        if (!cursor.benchId.equals(benchId)) {
            cursor = new BinCursor(line.pharmacyId(), benchId);
            cursorsByPharmacy.put(line.pharmacyId(), cursor);
        }

        AdaptingStorageLocation location = cursor.currentLocation();
        AdaptedLineRecord record = AdaptedLineRecord.fromPreparedLine(
                line, sourceOrderSheetKey, sourceServiceCentreId, location);
        stagedRecords.put(record.key(), record);
        cursor.advance(config);
        return record;
    }

    private void stageStrict(
            DspOrderItem line,
            OrderSheetKey sourceOrderSheetKey,
            String sourceServiceCentreId,
            OrderSheetKey targetSheet) {
        SheetBinGroup group = groupsByTargetSheet.get(targetSheet);
        if (group == null) {
            group = new SheetBinGroup(line.pharmacyId());
            groupsByTargetSheet.put(targetSheet, group);
        }
        SheetBin bin = group.bins.isEmpty() ? null : group.bins.getLast();
        if (bin == null || bin.acceptedKeyCount == config.linesPerBin()) {
            bin = new SheetBin(new AdaptingBinId(
                    group.storeId, targetSheet, group.bins.size() + 1));
            group.bins.add(bin);
        }
        if (bin.stagedRecords.isEmpty()) {
            occupiedStrictBinCount++;
        }
        AdaptedLineRecord record = AdaptedLineRecord.fromPreparedLineWithoutLocation(
                line, sourceOrderSheetKey, sourceServiceCentreId);
        stagedRecords.put(record.key(), record);
        bin.stagedRecords.put(record.key(), record);
        binsByPreparedLine.put(record.key(), bin);
        bin.acceptedKeyCount++;
        group.activeRecordCount++;
    }

    public void stage(AdaptedLineRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("record must not be null");
        }
        if (targetSheetCatalog != null) {
            throw new IllegalStateException("Direct adapted record staging requires legacy storage");
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
        AdaptedLineRecord record = stagedRecords.remove(key);
        if (record != null && targetSheetCatalog != null) {
            SheetBin bin = binsByPreparedLine.remove(key);
            bin.stagedRecords.remove(key);
            if (bin.stagedRecords.isEmpty()) {
                occupiedStrictBinCount--;
            }
            OrderSheetKey targetSheet = targetSheetCatalog.requireTargetSheet(key);
            SheetBinGroup group = groupsByTargetSheet.get(targetSheet);
            group.activeRecordCount--;
            if (group.activeRecordCount == 0) {
                groupsByTargetSheet.remove(targetSheet);
            }
            mutationVersion++;
        }
        return record;
    }

    public List<AdaptedLineRecord> takeAll(List<PreparedLineKey> keys) {
        if (keys == null) {
            throw new IllegalArgumentException("keys must not be null");
        }
        Set<PreparedLineKey> seen = new LinkedHashSet<>();
        List<PreparedLineKey> missingKeys = new ArrayList<>();
        for (PreparedLineKey key : keys) {
            if (key == null) {
                throw new IllegalArgumentException("keys must not contain null");
            }
            if (!seen.add(key)) {
                throw new IllegalStateException("Duplicate staged adapted line request: " + key);
            }
            if (!stagedRecords.containsKey(key)) {
                missingKeys.add(key);
            }
        }
        if (!missingKeys.isEmpty()) {
            throw new IllegalStateException("Missing staged adapted lines for keys: " + missingKeys);
        }

        List<AdaptedLineRecord> records = new ArrayList<>(keys.size());
        for (PreparedLineKey key : keys) {
            records.add(take(key));
        }
        return List.copyOf(records);
    }

    public List<AdaptingBinSnapshot> binSnapshots() {
        if (targetSheetCatalog == null) {
            throw new IllegalStateException("Sheet-owned bin inspection requires strict storage");
        }
        if (binSnapshotVersion == mutationVersion) {
            return cachedBinSnapshots;
        }
        List<AdaptingBinSnapshot> snapshots = new ArrayList<>();
        for (SheetBinGroup group : groupsByTargetSheet.values()) {
            for (int index = 0; index < group.bins.size(); index++) {
                SheetBin bin = group.bins.get(index);
                Optional<AdaptingBinId> next = index + 1 < group.bins.size()
                        ? Optional.of(group.bins.get(index + 1).id)
                        : Optional.empty();
                snapshots.add(new AdaptingBinSnapshot(
                        bin.id, next, List.copyOf(bin.stagedRecords.values())));
            }
        }
        cachedBinSnapshots = List.copyOf(snapshots);
        binSnapshotVersion = mutationVersion;
        return cachedBinSnapshots;
    }

    public AdaptedLineStoreSnapshot snapshot() {
        if (targetSheetCatalog != null) {
            return new AdaptedLineStoreSnapshot(
                    stagedRecords.size(), stagedRecords.keySet(), Map.of(), 0, 0,
                    occupiedStrictBinCount);
        }

        Map<AdaptingBenchId, Integer> stagedLineCountByBench = new LinkedHashMap<>();
        Set<String> rackKeys = new LinkedHashSet<>();
        Set<String> shelfKeys = new LinkedHashSet<>();
        Set<AdaptingStorageLocation> binKeys = new LinkedHashSet<>();

        for (AdaptedLineRecord record : stagedRecords.values()) {
            AdaptingStorageLocation location = record.location().orElseThrow(
                    () -> new IllegalStateException("Legacy storage record has no location"));
            stagedLineCountByBench.merge(location.benchId(), 1, Integer::sum);
            rackKeys.add(location.benchId().value() + ":" + location.rackIndex());
            shelfKeys.add(location.benchId().value() + ":" + location.rackIndex() + ":" + location.shelfIndex());
            binKeys.add(location);
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
            advanceBin(config);
        }

        private void advanceBin(AdaptingStorageConfig config) {
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

    private static final class SheetBinGroup {
        private final String storeId;
        private final List<SheetBin> bins = new ArrayList<>();
        private int activeRecordCount;

        private SheetBinGroup(String storeId) {
            this.storeId = storeId;
        }
    }

    private static final class SheetBin {
        private final AdaptingBinId id;
        private final Map<PreparedLineKey, AdaptedLineRecord> stagedRecords = new LinkedHashMap<>();
        private int acceptedKeyCount;

        private SheetBin(AdaptingBinId id) {
            this.id = id;
        }
    }
}
