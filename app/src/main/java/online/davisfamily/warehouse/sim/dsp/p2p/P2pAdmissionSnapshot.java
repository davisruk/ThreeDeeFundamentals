package online.davisfamily.warehouse.sim.dsp.p2p;

import java.util.Set;

public record P2pAdmissionSnapshot(
        String p2pCellId,
        int idlePrlCount,
        Set<String> activeBagCorrelations,
        Set<String> admissibleKnownCorrelations,
        boolean pcrAvailableForNewRelease) {

    public P2pAdmissionSnapshot {
        if (p2pCellId == null || p2pCellId.isBlank()) {
            throw new IllegalArgumentException("p2pCellId must not be blank");
        }
        if (idlePrlCount < 0) {
            throw new IllegalArgumentException("idlePrlCount must be >= 0");
        }
        if (activeBagCorrelations == null) {
            throw new IllegalArgumentException("activeBagCorrelations must not be null");
        }
        if (admissibleKnownCorrelations == null) {
            throw new IllegalArgumentException("admissibleKnownCorrelations must not be null");
        }
        activeBagCorrelations = Set.copyOf(activeBagCorrelations);
        admissibleKnownCorrelations = Set.copyOf(admissibleKnownCorrelations);
    }
}
