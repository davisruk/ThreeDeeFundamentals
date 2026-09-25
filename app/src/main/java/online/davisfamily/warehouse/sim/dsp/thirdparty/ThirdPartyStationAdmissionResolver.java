package online.davisfamily.warehouse.sim.dsp.thirdparty;

import java.util.function.Supplier;

import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionResolver;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;

public class ThirdPartyStationAdmissionResolver implements StationAdmissionResolver {
    private final StationAdmissionResolver fallbackResolver;
    private final ThirdPartyVisitPlanSource planSource;
    private final Supplier<ThirdPartyAreaSnapshot> areaSnapshotSupplier;
    private final ThirdPartyStationAdmissionAdapter adapter;

    public ThirdPartyStationAdmissionResolver(
            StationAdmissionResolver fallbackResolver,
            ThirdPartyVisitFactory visitFactory,
            Supplier<ThirdPartyAreaSnapshot> areaSnapshotSupplier) {
        this(
                fallbackResolver,
                requireVisitFactory(visitFactory)::planFor,
                areaSnapshotSupplier,
                new ThirdPartyStationAdmissionAdapter());
    }

    public ThirdPartyStationAdmissionResolver(
            StationAdmissionResolver fallbackResolver,
            ThirdPartyVisitFactory visitFactory,
            Supplier<ThirdPartyAreaSnapshot> areaSnapshotSupplier,
            String targetId) {
        this(
                fallbackResolver,
                requireVisitFactory(visitFactory)::planFor,
                areaSnapshotSupplier,
                new ThirdPartyStationAdmissionAdapter(targetId));
    }

    public ThirdPartyStationAdmissionResolver(
            StationAdmissionResolver fallbackResolver,
            ThirdPartyVisitPlanSource planSource,
            Supplier<ThirdPartyAreaSnapshot> areaSnapshotSupplier,
            String targetId) {
        this(fallbackResolver, planSource, areaSnapshotSupplier, new ThirdPartyStationAdmissionAdapter(targetId));
    }

    private ThirdPartyStationAdmissionResolver(
            StationAdmissionResolver fallbackResolver,
            ThirdPartyVisitPlanSource planSource,
            Supplier<ThirdPartyAreaSnapshot> areaSnapshotSupplier,
            ThirdPartyStationAdmissionAdapter adapter) {
        if (fallbackResolver == null) {
            throw new IllegalArgumentException("fallbackResolver must not be null");
        }
        if (planSource == null) {
            throw new IllegalArgumentException("planSource must not be null");
        }
        if (areaSnapshotSupplier == null) {
            throw new IllegalArgumentException("areaSnapshotSupplier must not be null");
        }
        this.fallbackResolver = fallbackResolver;
        this.planSource = planSource;
        this.areaSnapshotSupplier = areaSnapshotSupplier;
        this.adapter = adapter;
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
        return adapter.admissionFor(planSource.planFor(candidate.order()), areaSnapshot);
    }

    private static ThirdPartyVisitFactory requireVisitFactory(ThirdPartyVisitFactory visitFactory) {
        if (visitFactory == null) {
            throw new IllegalArgumentException("visitFactory must not be null");
        }
        return visitFactory;
    }
}
