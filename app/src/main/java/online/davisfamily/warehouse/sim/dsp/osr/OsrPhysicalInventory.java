package online.davisfamily.warehouse.sim.dsp.osr;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

public final class OsrPhysicalInventory {
    private final OsrInventoryConfig config;
    private final Map<PhysicalToteId, InboundToteManifest> storedTotes = new LinkedHashMap<>();
    private final Set<PhysicalToteId> seenPhysicalToteIds = new LinkedHashSet<>();
    private final List<InboundToteManifest> departedTotes = new ArrayList<>();
    private OsrInventorySnapshot currentSnapshot;

    public OsrPhysicalInventory(OsrInventoryConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        this.config = config;
        this.currentSnapshot = new OsrInventorySnapshot(config.capacity(), List.of(), List.of());
    }

    public void store(InboundToteManifest manifest) {
        if (manifest == null) {
            throw new IllegalArgumentException("manifest must not be null");
        }
        storeAll(List.of(manifest));
    }

    public void storeAll(List<InboundToteManifest> manifests) {
        if (manifests == null) {
            throw new IllegalArgumentException("manifests must not be null");
        }

        Set<PhysicalToteId> candidateIds = new LinkedHashSet<>();
        for (InboundToteManifest manifest : manifests) {
            if (manifest == null) {
                throw new IllegalArgumentException("manifests must not contain null");
            }
            PhysicalToteId physicalToteId = manifest.physicalToteId();
            if (seenPhysicalToteIds.contains(physicalToteId)) {
                throw new IllegalStateException(
                        "Physical tote has already entered OSR inventory: " + physicalToteId.value());
            }
            if (!candidateIds.add(physicalToteId)) {
                throw new IllegalArgumentException(
                        "Duplicate physical tote ID in store batch: " + physicalToteId.value());
            }
        }
        if (manifests.size() > config.capacity() - storedTotes.size()) {
            throw new IllegalStateException(
                    "OSR capacity exceeded: capacity=" + config.capacity()
                            + ", occupancy=" + storedTotes.size()
                            + ", candidates=" + manifests.size());
        }

        if (manifests.isEmpty()) {
            return;
        }

        List<InboundToteManifest> nextStoredTotes = new ArrayList<>(storedTotes.values());
        nextStoredTotes.addAll(manifests);
        OsrInventorySnapshot nextSnapshot = new OsrInventorySnapshot(
                config.capacity(), nextStoredTotes, departedTotes);

        for (InboundToteManifest manifest : manifests) {
            storedTotes.put(manifest.physicalToteId(), manifest);
            seenPhysicalToteIds.add(manifest.physicalToteId());
        }
        currentSnapshot = nextSnapshot;
    }

    public InboundToteManifest recordDeparture(PhysicalToteId physicalToteId) {
        if (physicalToteId == null) {
            throw new IllegalArgumentException("physicalToteId must not be null");
        }
        InboundToteManifest manifest = storedTotes.get(physicalToteId);
        if (manifest == null) {
            throw new IllegalStateException(
                    "Physical tote is not currently stored in OSR: " + physicalToteId.value());
        }

        List<InboundToteManifest> nextStoredTotes = new ArrayList<>(storedTotes.values());
        nextStoredTotes.remove(manifest);
        List<InboundToteManifest> nextDepartedTotes = new ArrayList<>(departedTotes);
        nextDepartedTotes.add(manifest);
        OsrInventorySnapshot nextSnapshot = new OsrInventorySnapshot(
                config.capacity(), nextStoredTotes, nextDepartedTotes);

        storedTotes.remove(physicalToteId);
        departedTotes.add(manifest);
        currentSnapshot = nextSnapshot;
        return manifest;
    }

    public OsrInventorySnapshot snapshot() {
        return currentSnapshot;
    }
}
