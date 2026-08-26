package online.davisfamily.warehouse.sim.dsp.osr.release;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class OperationalPhysicalToteReleaseTargetRegistry {
    private final Map<String, OperationalPhysicalToteReleaseTarget> targetsById;

    public OperationalPhysicalToteReleaseTargetRegistry(
            List<OperationalPhysicalToteReleaseTarget> targets) {
        if (targets == null) {
            throw new IllegalArgumentException("targets must not be null");
        }
        Map<String, OperationalPhysicalToteReleaseTarget> configuredTargets =
                new LinkedHashMap<>();
        for (OperationalPhysicalToteReleaseTarget target : targets) {
            if (target == null) {
                throw new IllegalArgumentException("targets must not contain null");
            }
            String targetId = requireTargetId(target.targetId());
            if (configuredTargets.putIfAbsent(targetId, target) != null) {
                throw new IllegalArgumentException("Duplicate release target ID: " + targetId);
            }
        }
        targetsById = Collections.unmodifiableMap(configuredTargets);
    }

    public Optional<OperationalPhysicalToteReleaseTarget> find(String targetId) {
        return Optional.ofNullable(targetsById.get(requireTargetId(targetId)));
    }

    private static String requireTargetId(String targetId) {
        if (targetId == null || targetId.isBlank()) {
            throw new IllegalArgumentException("targetId must not be blank");
        }
        return targetId.trim();
    }
}
