package online.davisfamily.warehouse.sim.dsp.scheduler.operational;

import java.util.List;
import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionSnapshot;

public final class OperationalRouteEntryAdmissionPolicy {
    private final OperationalRouteEntrySelector routeEntrySelector;
    private final OperationalStationAdmissionResolver stationAdmissionResolver;

    public OperationalRouteEntryAdmissionPolicy() {
        this(
                new OperationalRouteEntrySelector(),
                new SnapshotOperationalStationAdmissionResolver());
    }

    public OperationalRouteEntryAdmissionPolicy(
            OperationalRouteEntrySelector routeEntrySelector,
            OperationalStationAdmissionResolver stationAdmissionResolver) {
        if (routeEntrySelector == null) {
            throw new IllegalArgumentException("routeEntrySelector must not be null");
        }
        if (stationAdmissionResolver == null) {
            throw new IllegalArgumentException("stationAdmissionResolver must not be null");
        }
        this.routeEntrySelector = routeEntrySelector;
        this.stationAdmissionResolver = stationAdmissionResolver;
    }

    public OperationalRouteEntryEvaluation evaluate(
            DspOperationalReleaseCandidate candidate,
            DspOperationalReleaseSnapshot snapshot) {
        if (candidate == null) {
            throw new IllegalArgumentException("candidate must not be null");
        }
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }

        Optional<StationType> routeEntryStation = routeEntrySelector.firstStation(
                candidate.logicalOrderState().routeRequirements());
        if (routeEntryStation.isEmpty()) {
            return blocked(
                    OperationalReleaseBlockType.ROUTE_ENTRY,
                    "No route-entry station is configured for physical tote "
                            + candidate.physicalCandidate().physicalToteId().value());
        }

        StationType stationType = routeEntryStation.orElseThrow();
        StationAdmissionSnapshot admission = stationAdmissionResolver.admissionFor(
                stationType, candidate, snapshot);
        if (admission == null) {
            return blocked(
                    OperationalReleaseBlockType.STATION_ADMISSION,
                    "No admission snapshot is available for route-entry station " + stationType);
        }
        if (admission.stationType() != stationType) {
            throw new IllegalArgumentException(
                    "Resolved admission station type must match requested route-entry station");
        }
        if (!admission.canAccept()) {
            String reason = admission.blockedReason().isBlank()
                    ? "Route-entry station " + stationType + " cannot accept the candidate"
                    : admission.blockedReason();
            return blocked(OperationalReleaseBlockType.STATION_ADMISSION, reason);
        }

        Optional<String> selectedTargetId = admission.selectedTargetId();
        if (selectedTargetId.isEmpty()) {
            return blocked(
                    OperationalReleaseBlockType.TARGET_SELECTION,
                    "Route-entry station " + stationType + " has no selected target");
        }
        return new OperationalRouteEntryEvaluation(
                Optional.of(new OperationalRouteEntry(
                        stationType, selectedTargetId.orElseThrow())),
                List.of());
    }

    private static OperationalRouteEntryEvaluation blocked(
            OperationalReleaseBlockType type,
            String reason) {
        return new OperationalRouteEntryEvaluation(
                Optional.empty(),
                List.of(new OperationalReleaseBlock(type, reason)));
    }
}
