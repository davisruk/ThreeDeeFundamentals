package online.davisfamily.warehouse.testing.scheduler;

import java.util.LinkedHashSet;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.p2p.P2pAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationSnapshot;
import online.davisfamily.warehouse.sim.totebag.assignment.PrlState;
import online.davisfamily.warehouse.sim.totebag.control.ToteToBagFlowController;

public class DebugP2pAdmissionSnapshotFactory {
    private final String p2pCellId;
    private final ToteToBagFlowController flowController;

    public DebugP2pAdmissionSnapshotFactory(String p2pCellId, ToteToBagFlowController flowController) {
        if (p2pCellId == null || p2pCellId.isBlank()) {
            throw new IllegalArgumentException("p2pCellId must not be blank");
        }
        if (flowController == null) {
            throw new IllegalArgumentException("flowController must not be null");
        }
        this.p2pCellId = p2pCellId.trim();
        this.flowController = flowController;
    }

    public P2pAdmissionSnapshot snapshot() {
        int idlePrlCount = 0;
        Set<String> activeBagCorrelations = new LinkedHashSet<>();
        for (var prl : flowController.getPrlsById().values()) {
            var assignment = prl.getAssignment();
            if (assignment.getState() == PrlState.IDLE) {
                idlePrlCount++;
                continue;
            }
            String correlationId = assignment.getCorrelationId();
            if (correlationId != null && !correlationId.isBlank()) {
                activeBagCorrelations.add(correlationId);
            }
        }
        return new P2pAdmissionSnapshot(
                p2pCellId,
                idlePrlCount,
                activeBagCorrelations,
                activeBagCorrelations,
                true);
    }

    public StationSnapshot stationSnapshot() {
        return new StationSnapshot(StationType.P2P, 0, 0);
    }
}
