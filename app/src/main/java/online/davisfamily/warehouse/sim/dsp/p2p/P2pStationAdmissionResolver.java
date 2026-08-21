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
    public StationAdmissionSnapshot admissionFor(
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
        if (stationType != StationType.P2P) {
            return fallbackResolver.admissionFor(stationType, candidate, snapshot);
        }

        P2pCapacityStationAdapter adapter = selectedTargetId
                .map(targetId -> new P2pCapacityStationAdapter(
                        p2pAdmission,
                        p2pSnapshotSupplier.get(),
                        p2pCapacity,
                        p2pStationSnapshotSupplier.get(),
                        targetId))
                .orElseGet(() -> new P2pCapacityStationAdapter(
                        p2pAdmission,
                        p2pSnapshotSupplier.get(),
                        p2pCapacity,
                        p2pStationSnapshotSupplier.get()));
        return adapter.admissionFor(candidate.order());
    }

    private static String requireTargetId(String targetId) {
        if (targetId == null || targetId.isBlank()) {
            throw new IllegalArgumentException("targetId must not be blank");
        }
        return targetId.trim();
    }
}
