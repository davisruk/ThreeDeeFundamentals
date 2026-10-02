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
    private final AdaptingOrderPreparationCatalog orderPreparationCatalog;
    private AdaptingStorageMap storageMap;
    private final Map<PreparedLineKey, AdaptedLineRecord> stagedRecords = new LinkedHashMap<>();
    private final Map<String, BinCursor> cursorsByPharmacy = new LinkedHashMap<>();
    private final Map<OrderGroupKey, OrderBinGroup> groupsByOrder = new LinkedHashMap<>();
    private final Set<OrderGroupKey> collectedGroups = new LinkedHashSet<>();
    private final Map<PreparedLineKey, SheetBin> binsByPreparedLine = new LinkedHashMap<>();
    private long mutationVersion;
    private long binSnapshotVersion = -1;
    private int occupiedStrictBinCount;
    private List<AdaptingBinSnapshot> cachedBinSnapshots = List.of();

    public AdaptingStorageLayout(AdaptingStorageConfig config, AdaptingStorageMap storageMap) {
        this(config, storageMap, null, null, false);
    }

    public AdaptingStorageLayout(
            AdaptingStorageConfig config,
            AdaptingStorageMap storageMap,
            AdaptingTargetSheetCatalog targetSheetCatalog,
            AdaptingOrderPreparationCatalog orderPreparationCatalog) {
        this(config, storageMap, targetSheetCatalog, orderPreparationCatalog, true);
    }

    private AdaptingStorageLayout(
            AdaptingStorageConfig config,
            AdaptingStorageMap storageMap,
            AdaptingTargetSheetCatalog targetSheetCatalog,
            AdaptingOrderPreparationCatalog orderPreparationCatalog,
            boolean strict) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        if (storageMap == null) {
            throw new IllegalArgumentException("storageMap must not be null");
        }
        if (strict && (targetSheetCatalog == null || orderPreparationCatalog == null)) {
            throw new IllegalArgumentException("Both catalogs are required in strict storage");
        }
        this.config = config;
        this.storageMap = storageMap;
        this.targetSheetCatalog = targetSheetCatalog;
        this.orderPreparationCatalog = orderPreparationCatalog;
    }

    public boolean strictStorage() {
        return targetSheetCatalog != null;
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
                String orderId = key.targetOrderId();
                if (!orderPreparationCatalog.requireStoreId(orderId).equals(line.pharmacyId())) {
                    throw new IllegalStateException("Prepared line store changed: " + key);
                }
                OrderSheetKey targetSheet = targetSheetCatalog.requireTargetSheet(key);
                if (!targetSheet.equals(orderPreparationCatalog.requireTargetSheet(key))) {
                    throw new IllegalStateException("Prepared line target changed: " + key);
                }
                OrderGroupKey groupKey = new OrderGroupKey(line.pharmacyId(), orderId);
                if (collectedGroups.contains(groupKey)) {
                    throw new IllegalStateException("Late STORE after order collection: " + groupKey);
                }
            }
        }

        for (int index = 0; index < lines.size(); index++) {
            if (targetSheetCatalog == null) {
                stageLegacy(lines.get(index), sourceOrderSheetKey, sourceServiceCentreId);
            } else {
                stageStrict(lines.get(index), sourceOrderSheetKey, sourceServiceCentreId);
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
            String sourceServiceCentreId) {
        OrderGroupKey groupKey = new OrderGroupKey(line.pharmacyId(), line.referenceOrderId());
        OrderBinGroup group = groupsByOrder.get(groupKey);
        if (group == null) {
            group = new OrderBinGroup();
            groupsByOrder.put(groupKey, group);
        }
        SheetBin bin = group.bins.isEmpty() ? null : group.bins.getLast();
        if (bin == null || bin.acceptedKeyCount == config.linesPerBin()) {
            bin = new SheetBin(new AdaptingBinId(
                    groupKey.storeId(), groupKey.referenceOrderId(), group.bins.size() + 1));
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
        if (strictStorage()) {
            throw new IllegalStateException("Strict storage requires whole-order collection");
        }
        if (key == null) {
            throw new IllegalArgumentException("key must not be null");
        }
        return stagedRecords.remove(key);
    }

    public List<AdaptedLineRecord> takeAll(List<PreparedLineKey> keys) {
        if (strictStorage()) {
            throw new IllegalStateException("Strict storage requires whole-order collection");
        }
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

    public AdaptingPreparedOrderGroup prepareOrderGroup(String storeId, String referenceOrderId) {
        requireStrictStorage();
        OrderGroupKey groupKey = groupKey(storeId, referenceOrderId);
        validateCatalogStore(groupKey);
        if (collectedGroups.contains(groupKey)) {
            return new AdaptingPreparedOrderGroup(groupKey.storeId(), groupKey.referenceOrderId(),
                    mutationVersion, List.of(), false);
        }
        OrderBinGroup group = groupsByOrder.get(groupKey);
        List<PreparedLineKey> expectedKeys = orderPreparationCatalog.requiredKeysFor(referenceOrderId);
        if (expectedKeys.isEmpty() || group == null || group.activeRecordCount != expectedKeys.size()) {
            throw new IllegalStateException("Incomplete staged order group: " + groupKey);
        }
        List<AdaptedLineRecord> records = new ArrayList<>(group.activeRecordCount);
        Set<PreparedLineKey> actualKeys = new LinkedHashSet<>();
        for (SheetBin bin : group.bins) {
            for (AdaptedLineRecord record : bin.stagedRecords.values()) {
                records.add(record);
                actualKeys.add(record.key());
            }
        }
        if (!actualKeys.equals(new LinkedHashSet<>(expectedKeys))) {
            throw new IllegalStateException("Missing expected staged keys for order group: " + groupKey);
        }
        return new AdaptingPreparedOrderGroup(groupKey.storeId(), groupKey.referenceOrderId(),
                mutationVersion, records, true);
    }

    public List<AdaptedLineRecord> commitOrderGroup(AdaptingPreparedOrderGroup decision) {
        requireStrictStorage();
        if (decision == null) {
            throw new IllegalArgumentException("decision must not be null");
        }
        if (decision.mutationVersion() != mutationVersion) {
            throw new IllegalStateException("Stale order-group collection decision");
        }
        AdaptingPreparedOrderGroup current = prepareOrderGroup(decision.storeId(), decision.referenceOrderId());
        if (!current.equals(decision)) {
            throw new IllegalStateException("Order-group collection decision no longer matches storage");
        }
        if (!decision.firstCollection()) {
            return List.of();
        }
        OrderGroupKey groupKey = groupKey(decision.storeId(), decision.referenceOrderId());
        OrderBinGroup group = groupsByOrder.remove(groupKey);
        for (SheetBin bin : group.bins) {
            if (!bin.stagedRecords.isEmpty()) {
                occupiedStrictBinCount--;
            }
        }
        for (AdaptedLineRecord record : decision.records()) {
            stagedRecords.remove(record.key());
            binsByPreparedLine.remove(record.key());
        }
        collectedGroups.add(groupKey);
        mutationVersion++;
        return decision.records();
    }

    private void requireStrictStorage() {
        if (!strictStorage()) {
            throw new IllegalStateException("Order-group collection requires strict storage");
        }
    }

    private OrderGroupKey groupKey(String storeId, String referenceOrderId) {
        if (storeId == null || storeId.isBlank() || referenceOrderId == null || referenceOrderId.isBlank()) {
            throw new IllegalArgumentException("storeId and referenceOrderId must not be blank");
        }
        return new OrderGroupKey(storeId.trim(), referenceOrderId.trim());
    }

    private void validateCatalogStore(OrderGroupKey key) {
        if (!orderPreparationCatalog.requireStoreId(key.referenceOrderId()).equals(key.storeId())) {
            throw new IllegalStateException("Wrong store for order group: " + key);
        }
    }

    public List<AdaptingBinSnapshot> binSnapshots() {
        if (targetSheetCatalog == null) {
            throw new IllegalStateException("Order-owned bin inspection requires strict storage");
        }
        if (binSnapshotVersion == mutationVersion) {
            return cachedBinSnapshots;
        }
        List<AdaptingBinSnapshot> snapshots = new ArrayList<>();
        for (OrderBinGroup group : groupsByOrder.values()) {
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

    private record OrderGroupKey(String storeId, String referenceOrderId) {}

    private static final class OrderBinGroup {
        private final List<SheetBin> bins = new ArrayList<>();
        private int activeRecordCount;
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
