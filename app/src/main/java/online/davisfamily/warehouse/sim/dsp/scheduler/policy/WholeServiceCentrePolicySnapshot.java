package online.davisfamily.warehouse.sim.dsp.scheduler.policy;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;

/** Detached release permission and the bounded set of lines available for acquisition. */
public record WholeServiceCentrePolicySnapshot(
        WholeServiceCentreReleaseSnapshot releases,
        Optional<String> eligibleServiceCentreId,
        List<P2pLineId> availableUnleasedLineIds) {

    public WholeServiceCentrePolicySnapshot {
        if (releases == null || eligibleServiceCentreId == null
                || availableUnleasedLineIds == null
                || availableUnleasedLineIds.stream().anyMatch(id -> id == null)) {
            throw new IllegalArgumentException("whole-service-centre metadata must not be null");
        }
        if (eligibleServiceCentreId.isPresent()
                && !eligibleServiceCentreId.equals(releases.releaseServiceCentreId())) {
            throw new IllegalArgumentException("eligible centre must equal the release centre");
        }
        if (new LinkedHashSet<>(availableUnleasedLineIds).size()
                != availableUnleasedLineIds.size()) {
            throw new IllegalArgumentException("available line IDs must be distinct");
        }
        if (!releases.committedP2pToteCounts().isEmpty()
                && !releases.committedP2pToteCounts().values().iterator().next().keySet()
                        .containsAll(availableUnleasedLineIds)) {
            throw new IllegalArgumentException("available lines must belong to the release counts");
        }
        availableUnleasedLineIds = List.copyOf(availableUnleasedLineIds);
    }
}
