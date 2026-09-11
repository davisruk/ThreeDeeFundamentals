package online.davisfamily.warehouse.sim.dsp.analysis.runtime;

import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.p2p.P2pAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.bag.P2pBagCorrelationAssignmentRegistry;
import online.davisfamily.warehouse.sim.dsp.p2p.bag.P2pBagCorrelationRequirementCatalog;

/**
 * Simulation-thread-owned source of the full-day P2P admission snapshot.
 * Rebuilds the snapshot only when the active correlation-set identity changes.
 */
final class DspFullDayP2pAdmissionSnapshotSource {
    private static final String P2P_CELL_ID = "dsp-p2p";

    private final int idlePrlCount;
    private final P2pBagCorrelationAssignmentRegistry assignmentRegistry;
    private final Set<String> admissibleKnownCorrelations;
    private Set<String> lastActiveCorrelations;
    private P2pAdmissionSnapshot snapshot;

    DspFullDayP2pAdmissionSnapshotSource(
            int idlePrlCount,
            P2pBagCorrelationRequirementCatalog requirementCatalog,
            P2pBagCorrelationAssignmentRegistry assignmentRegistry) {
        if (idlePrlCount < 0) {
            throw new IllegalArgumentException("idlePrlCount must be >= 0");
        }
        if (requirementCatalog == null) {
            throw new IllegalArgumentException("requirementCatalog must not be null");
        }
        if (assignmentRegistry == null) {
            throw new IllegalArgumentException("assignmentRegistry must not be null");
        }
        this.idlePrlCount = idlePrlCount;
        this.assignmentRegistry = assignmentRegistry;
        this.admissibleKnownCorrelations = requirementCatalog.correlationIds();
    }

    P2pAdmissionSnapshot snapshot() {
        Set<String> activeCorrelations = assignmentRegistry.correlationIdsSnapshot();
        if (snapshot == null || activeCorrelations != lastActiveCorrelations) {
            snapshot = new P2pAdmissionSnapshot(
                    P2P_CELL_ID,
                    idlePrlCount,
                    activeCorrelations,
                    admissibleKnownCorrelations,
                    true);
            lastActiveCorrelations = activeCorrelations;
        }
        return snapshot;
    }
}
