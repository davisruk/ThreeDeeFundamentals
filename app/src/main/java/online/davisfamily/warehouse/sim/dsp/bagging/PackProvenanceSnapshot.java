package online.davisfamily.warehouse.sim.dsp.bagging;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public record PackProvenanceSnapshot(Map<String, PackSourceProvenance> provenanceByPackId) {

    public PackProvenanceSnapshot {
        if (provenanceByPackId == null) {
            throw new IllegalArgumentException("provenanceByPackId must not be null");
        }

        LinkedHashMap<String, PackSourceProvenance> copy = new LinkedHashMap<>();
        provenanceByPackId.forEach((packId, provenance) -> {
            String normalizedPackId = requireTrimmedPackId(packId);
            if (provenance == null) {
                throw new IllegalArgumentException("provenanceByPackId must not contain null values");
            }
            if (copy.putIfAbsent(normalizedPackId, provenance) != null) {
                throw new IllegalArgumentException("Duplicate pack ID after trimming: " + normalizedPackId);
            }
        });
        provenanceByPackId = Collections.unmodifiableMap(copy);
    }

    public Optional<PackSourceProvenance> find(String packId) {
        return Optional.ofNullable(provenanceByPackId.get(requireTrimmedPackId(packId)));
    }

    private static String requireTrimmedPackId(String packId) {
        if (packId == null || packId.isBlank()) {
            throw new IllegalArgumentException("packId must not be blank");
        }
        return packId.trim();
    }
}
