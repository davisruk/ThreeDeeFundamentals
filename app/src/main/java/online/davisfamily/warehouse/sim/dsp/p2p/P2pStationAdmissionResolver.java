package online.davisfamily.warehouse.sim.dsp.p2p;

import java.util.Optional;
import java.util.function.Supplier;

import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionResolver;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationCapacity;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;

public class P2pStationAdmissionResolver implements StationAdmissionResolver {
    private final StationAdmissionResolver fallbackResolver;
    private final P2pAdmission p2pAdmission;
    private final Supplier<P2pAdmissionSnapshot> p2pSnapshotSupplier;
    private final StationCapacity p2pCapacity;
    private final Supplier<StationSnapshot> p2pStationSnapshotSupplier;
    private final Optional<String> selectedTargetId;

    public P2pStationAdmissionResolver(
            StationAdmissionResolver fallbackResolver,
            P2pAdmission p2pAdmission,
            Supplier<P2pAdmissionSnapshot> p2pSnapshotSupplier,
            StationCapacity p2pCapacity,
            Supplier<StationSnapshot> p2pStationSnapshotSupplier) {
        this(
                fallbackResolver,
                p2pAdmission,
                p2pSnapshotSupplier,
                p2pCapacity,
                p2pStationSnapshotSupplier,
                Optional.empty());
    }

    public P2pStationAdmissionResolver(
            StationAdmissionResolver fallbackResolver,
            P2pAdmission p2pAdmission,
            Supplier<P2pAdmissionSnapshot> p2pSnapshotSupplier,
            StationCapacity p2pCapacity,
            Supplier<StationSnapshot> p2pStationSnapshotSupplier,
            String targetId) {
        this(
                fallbackResolver,
                p2pAdmission,
                p2pSnapshotSupplier,
                p2pCapacity,
                p2pStationSnapshotSupplier,
                Optional.of(requireTargetId(targetId)));
    }

    private P2pStationAdmissionResolver(
            StationAdmissionResolver fallbackResolver,
            P2pAdmission p2pAdmission,
            Supplier<P2pAdmissionSnapshot> p2pSnapshotSupplier,
            StationCapacity p2pCapacity,
            Supplier<StationSnapshot> p2pStationSnapshotSupplier,
            Optional<String> selectedTargetId) {
        if (fallbackResolver == null) {
            throw new IllegalArgumentException("fallbackResolver must not be null");
        }
        if (p2pAdmission == null) {
            throw new IllegalArgumentException("p2pAdmission must not be null");
        }
        if (p2pSnapshotSupplier == null) {
            throw new IllegalArgumentException("p2pSnapshotSupplier must not be null");
        }
        if (p2pCapacity == null) {
            throw new IllegalArgumentException("p2pCapacity must not be null");
        }
        if (p2pStationSnapshotSupplier == null) {
            throw new IllegalArgumentException("p2pStationSnapshotSupplier must not be null");
        }
        this.fallbackResolver = fallbackResolver;
        this.p2pAdmission = p2pAdmission;
        this.p2pSnapshotSupplier = p2pSnapshotSupplier;
        this.p2pCapacity = p2pCapacity;
        this.p2pStationSnapshotSupplier = p2pStationSnapshotSupplier;
        this.selectedTargetId = selectedTargetId;
    }

    @Override
    public StationAdmissionResolver forEvaluation(WarehouseSchedulerSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }
        StationAdmissionResolver scopedFallback = fallbackResolver.forEvaluation(snapshot);
        if (scopedFallback == null) {
            throw new IllegalStateException(
                    "fallbackResolver.forEvaluation returned null");
        }
        return new EvaluationScopedResolver(scopedFallback, snapshot);
    }

    @Override
    public StationAdmissionSnapshot admissionFor(
            StationType stationType,
            DspSchedulerOrderState candidate,
            WarehouseSchedulerSnapshot snapshot) {
        validateAdmissionInputs(stationType, candidate, snapshot);
        if (stationType != StationType.P2P) {
            return fallbackResolver.admissionFor(stationType, candidate, snapshot);
        }

        return newP2pAdapter().admissionFor(candidate.order());
    }

    private P2pCapacityStationAdapter newP2pAdapter() {
        P2pAdmissionSnapshot p2pSnapshot = p2pSnapshotSupplier.get();
        StationSnapshot stationSnapshot = p2pStationSnapshotSupplier.get();
        return selectedTargetId
                .map(targetId -> new P2pCapacityStationAdapter(
                        p2pAdmission,
                        p2pSnapshot,
                        p2pCapacity,
                        stationSnapshot,
                        targetId))
                .orElseGet(() -> new P2pCapacityStationAdapter(
                        p2pAdmission,
                        p2pSnapshot,
                        p2pCapacity,
                        stationSnapshot));
    }

    private final class EvaluationScopedResolver implements StationAdmissionResolver {
        private final StationAdmissionResolver scopedFallback;
        private final WarehouseSchedulerSnapshot evaluationSnapshot;
        private P2pCapacityStationAdapter p2pAdapter;

        private EvaluationScopedResolver(
                StationAdmissionResolver scopedFallback,
                WarehouseSchedulerSnapshot evaluationSnapshot) {
            this.scopedFallback = scopedFallback;
            this.evaluationSnapshot = evaluationSnapshot;
        }

        @Override
        public StationAdmissionSnapshot admissionFor(
                StationType stationType,
                DspSchedulerOrderState candidate,
                WarehouseSchedulerSnapshot snapshot) {
            validateAdmissionInputs(stationType, candidate, snapshot);
            if (snapshot != evaluationSnapshot) {
                throw new IllegalArgumentException(
                        "evaluation-scoped resolver received a different snapshot");
            }
            if (stationType != StationType.P2P) {
                return scopedFallback.admissionFor(stationType, candidate, snapshot);
            }
            if (p2pAdapter == null) {
                p2pAdapter = newP2pAdapter();
            }
            return p2pAdapter.admissionFor(candidate.order());
        }
    }

    private static void validateAdmissionInputs(
            StationType stationType,
            DspSchedulerOrderState candidate,
            WarehouseSchedulerSnapshot snapshot) {
        if (stationType == null) {
            throw new IllegalArgumentException("stationType must not be null");
        }
        if (candidate == null) {
            throw new IllegalArgumentException("candidate must not be null");
        }
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }
    }

    private static String requireTargetId(String targetId) {
        if (targetId == null || targetId.isBlank()) {
            throw new IllegalArgumentException("targetId must not be blank");
        }
        return targetId.trim();
    }
}
