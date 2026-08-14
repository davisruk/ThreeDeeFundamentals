package online.davisfamily.warehouse.sim.dsp.thirdparty;

import java.util.function.Supplier;

import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionResolver;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;

public class ThirdPartyStationAdmissionResolver implements StationAdmissionResolver {
    private final StationAdmissionResolver fallbackResolver;
    private final ThirdPartyVisitFactory visitFactory;
    private final Supplier<ThirdPartyAreaSnapshot> areaSnapshotSupplier;
    private final ThirdPartyStationAdmissionAdapter adapter = new ThirdPartyStationAdmissionAdapter();

    public ThirdPartyStationAdmissionResolver(
            StationAdmissionResolver fallbackResolver,
            ThirdPartyVisitFactory visitFactory,
            Supplier<ThirdPartyAreaSnapshot> areaSnapshotSupplier) {
        if (fallbackResolver == null) {
            throw new IllegalArgumentException("fallbackResolver must not be null");
        }
        if (visitFactory == null) {
            throw new IllegalArgumentException("visitFactory must not be null");
        }
        if (areaSnapshotSupplier == null) {
            throw new IllegalArgumentException("areaSnapshotSupplier must not be null");
        }
        this.fallbackResolver = fallbackResolver;
        this.visitFactory = visitFactory;
        this.areaSnapshotSupplier = areaSnapshotSupplier;
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
        if (stationType != StationType.THIRD_PARTY) {
            return fallbackResolver.admissionFor(stationType, candidate, snapshot);
        }

        ThirdPartyAreaSnapshot areaSnapshot = areaSnapshotSupplier.get();
        if (areaSnapshot == null) {
            throw new IllegalStateException("areaSnapshotSupplier returned null");
        }
        return adapter.admissionFor(visitFactory.create(candidate.order()), areaSnapshot);
    }
}
