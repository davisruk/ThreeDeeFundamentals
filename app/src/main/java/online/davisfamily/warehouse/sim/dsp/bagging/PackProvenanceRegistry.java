package online.davisfamily.warehouse.sim.dsp.bagging;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class PackProvenanceRegistry {
    private final Map<String, PackSourceProvenance> provenanceByPackId = new LinkedHashMap<>();

    public void register(String packId, PackSourceProvenance provenance) {
        String normalizedPackId = requireTrimmedPackId(packId);
        if (provenance == null) {
            throw new IllegalArgumentException("provenance must not be null");
        }

        PackSourceProvenance existing = provenanceByPackId.putIfAbsent(normalizedPackId, provenance);
        if (existing != null && !existing.equals(provenance)) {
            throw new IllegalArgumentException(
                    "Conflicting source provenance is already registered for pack ID: " + normalizedPackId);
        }
    }

    public Optional<PackSourceProvenance> find(String packId) {
        return Optional.ofNullable(provenanceByPackId.get(requireTrimmedPackId(packId)));
    }

    public PackProvenanceSnapshot snapshot() {
        return new PackProvenanceSnapshot(provenanceByPackId);
    }

    private static String requireTrimmedPackId(String packId) {
        if (packId == null || packId.isBlank()) {
            throw new IllegalArgumentException("packId must not be blank");
        }
        return packId.trim();
    }
}
