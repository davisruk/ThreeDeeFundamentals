package online.davisfamily.warehouse.sim.dsp.adapting;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;

public class AdaptedLineStore {
    private final AdaptingStorageLayout layout;

    public AdaptedLineStore() {
        this(new AdaptingStorageLayout(AdaptingStorageConfig.defaults(), new AdaptingStorageMap()));
    }

    public AdaptedLineStore(AdaptingStorageLayout layout) {
        if (layout == null) {
            throw new IllegalArgumentException("layout must not be null");
        }
        this.layout = layout;
    }

    public void bindStorageMap(AdaptingStorageMap storageMap) {
        layout.bindStorageMap(storageMap);
    }

    public void stage(
            DspOrderItem line,
            OrderSheetKey sourceOrderSheetKey,
            String sourceServiceCentreId) {
        layout.stage(line, sourceOrderSheetKey, sourceServiceCentreId);
    }

    public void stage(AdaptedLineRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("record must not be null");
        }
        layout.stage(record);
    }

    public boolean contains(PreparedLineKey key) {
        return layout.contains(key);
    }

    public Optional<AdaptedLineRecord> take(PreparedLineKey key) {
        return Optional.ofNullable(layout.take(key));
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
        return layout.takeAll(requestedKeys);
    }

    public AdaptedLineStoreSnapshot snapshot() {
        return layout.snapshot();
    }
}
