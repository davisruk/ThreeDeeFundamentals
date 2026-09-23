package online.davisfamily.warehouse.sim.dsp.analysis.runtime;

import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Supplier;

import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.bag.P2pBagCorrelationAssignmentSnapshot;
import online.davisfamily.warehouse.sim.totebag.plan.ToteToBagWorkPlanProvider;

/** Provides only the bag correlations currently assigned to one P2P line. */
final class AssignedLineWorkPlanProvider implements ToteToBagWorkPlanProvider {
    private final P2pLineId lineId;
    private final ToteToBagWorkPlanProvider allWork;
    private final Supplier<P2pBagCorrelationAssignmentSnapshot> assignmentSnapshotSupplier;

    AssignedLineWorkPlanProvider(
            P2pLineId lineId,
            ToteToBagWorkPlanProvider allWork,
            Supplier<P2pBagCorrelationAssignmentSnapshot> assignmentSnapshotSupplier) {
        if (lineId == null) {
            throw new IllegalArgumentException("lineId must not be null");
        }
        if (allWork == null) {
            throw new IllegalArgumentException("allWork must not be null");
        }
        if (assignmentSnapshotSupplier == null) {
            throw new IllegalArgumentException(
                    "assignmentSnapshotSupplier must not be null");
        }
        this.lineId = lineId;
        this.allWork = allWork;
        this.assignmentSnapshotSupplier = assignmentSnapshotSupplier;
    }

    @Override
    public OptionalInt expectedPackCount(String correlationId) {
        P2pBagCorrelationAssignmentSnapshot snapshot = assignmentSnapshot();
        if (snapshot.lineFor(correlationId).filter(lineId::equals).isEmpty()) {
            return OptionalInt.empty();
        }
        return allWork.expectedPackCount(correlationId);
    }

    @Override
    public Set<String> expectedCorrelationIds() {
        return assignmentSnapshot().correlationIdsFor(lineId);
    }

    private P2pBagCorrelationAssignmentSnapshot assignmentSnapshot() {
        P2pBagCorrelationAssignmentSnapshot snapshot = assignmentSnapshotSupplier.get();
        if (snapshot == null) {
            throw new IllegalStateException("assignmentSnapshotSupplier returned null");
        }
        return snapshot;
    }
}
