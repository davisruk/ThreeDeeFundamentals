package online.davisfamily.warehouse.sim.dsp.lifecycle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;

public final class InboundToteManifestCatalog {
    private final List<InboundToteManifest> manifests;
    private final Map<PhysicalToteId, InboundToteManifest> manifestsByPhysicalToteId;
    private final Map<OrderSheetKey, List<InboundToteManifest>> manifestsByOrderSheetKey;

    public InboundToteManifestCatalog(List<InboundToteManifest> manifests) {
        if (manifests == null) {
            throw new IllegalArgumentException("manifests must not be null");
        }

        List<InboundToteManifest> manifestCopy = new ArrayList<>();
        Map<PhysicalToteId, InboundToteManifest> byPhysicalToteId = new LinkedHashMap<>();
        Map<OrderSheetKey, List<InboundToteManifest>> byOrderSheetKey = new LinkedHashMap<>();
        for (InboundToteManifest manifest : manifests) {
            if (manifest == null) {
                throw new IllegalArgumentException("manifests must not contain null");
            }
            if (byPhysicalToteId.putIfAbsent(manifest.physicalToteId(), manifest) != null) {
                throw new IllegalArgumentException("Duplicate physical tote ID in inbound manifest catalog: "
                        + manifest.physicalToteId().value());
            }
            manifestCopy.add(manifest);
            byOrderSheetKey
                    .computeIfAbsent(manifest.orderSheetKey(), ignored -> new ArrayList<>())
                    .add(manifest);
        }

        this.manifests = List.copyOf(manifestCopy);
        this.manifestsByPhysicalToteId = Map.copyOf(byPhysicalToteId);

        Map<OrderSheetKey, List<InboundToteManifest>> immutableByOrderSheetKey = new LinkedHashMap<>();
        byOrderSheetKey.forEach((key, value) -> immutableByOrderSheetKey.put(key, List.copyOf(value)));
        this.manifestsByOrderSheetKey = Map.copyOf(immutableByOrderSheetKey);
    }

    public List<InboundToteManifest> manifests() {
        return manifests;
    }

    public Optional<InboundToteManifest> findByPhysicalToteId(PhysicalToteId toteId) {
        if (toteId == null) {
            throw new IllegalArgumentException("toteId must not be null");
        }
        return Optional.ofNullable(manifestsByPhysicalToteId.get(toteId));
    }

    public List<InboundToteManifest> manifestsFor(OrderSheetKey orderSheetKey) {
        if (orderSheetKey == null) {
            throw new IllegalArgumentException("orderSheetKey must not be null");
        }
        return manifestsByOrderSheetKey.getOrDefault(orderSheetKey, List.of());
    }
}
