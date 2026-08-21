package online.davisfamily.warehouse.sim.dsp.scheduler.operational;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.route.OperationalRouteEntryQueue;
import online.davisfamily.warehouse.sim.dsp.osr.release.route.OperationalRouteEntryQueueSnapshot;
import online.davisfamily.warehouse.sim.dsp.osr.release.route.OperationalRouteTargetRegistry;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionResolver;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;

public final class OperationalCandidateRouteAdmissionFactory {
    private final OperationalRouteEntrySelector routeEntrySelector;
    private final StationAdmissionResolver stationAdmissionResolver;
    private final OperationalRouteTargetRegistry routeTargetRegistry;

    public OperationalCandidateRouteAdmissionFactory(
            OperationalRouteEntrySelector routeEntrySelector,
            StationAdmissionResolver stationAdmissionResolver,
            OperationalRouteTargetRegistry routeTargetRegistry) {
        if (routeEntrySelector == null) {
            throw new IllegalArgumentException("routeEntrySelector must not be null");
        }
        if (stationAdmissionResolver == null) {
            throw new IllegalArgumentException("stationAdmissionResolver must not be null");
        }
        if (routeTargetRegistry == null) {
            throw new IllegalArgumentException("routeTargetRegistry must not be null");
        }
        this.routeEntrySelector = routeEntrySelector;
        this.stationAdmissionResolver = stationAdmissionResolver;
        this.routeTargetRegistry = routeTargetRegistry;
    }

    public List<OperationalCandidateRouteAdmission> create(
            List<DspOperationalReleaseCandidate> candidates,
            WarehouseSchedulerSnapshot logicalSnapshot) {
        if (candidates == null) {
            throw new IllegalArgumentException("candidates must not be null");
        }
        if (logicalSnapshot == null) {
            throw new IllegalArgumentException("logicalSnapshot must not be null");
        }

        List<OperationalCandidateRouteAdmission> admissions = new ArrayList<>();
        for (DspOperationalReleaseCandidate candidate : candidates) {
            if (candidate == null) {
                throw new IllegalArgumentException("candidates must not contain null");
            }
            Optional<StationType> routeEntryStation = routeEntrySelector.firstStation(
                    candidate.logicalOrderState().routeRequirements());
            if (routeEntryStation.isEmpty()) {
                continue;
            }

            StationType stationType = routeEntryStation.orElseThrow();
            StationAdmissionSnapshot stationAdmission = stationAdmissionResolver.admissionFor(
                    stationType,
                    candidate.logicalOrderState(),
                    logicalSnapshot);
            if (stationAdmission == null) {
                continue;
            }
            if (stationAdmission.stationType() != stationType) {
                throw new IllegalArgumentException(
                        "Resolved station admission must match the candidate route-entry station");
            }

            StationAdmissionSnapshot effectiveAdmission = effectiveAdmission(
                    stationAdmission,
                    stationType);
            admissions.add(new OperationalCandidateRouteAdmission(
                    candidate.physicalCandidate().physicalToteId(),
                    effectiveAdmission));
        }
        return List.copyOf(admissions);
    }

    private StationAdmissionSnapshot effectiveAdmission(
            StationAdmissionSnapshot stationAdmission,
            StationType stationType) {
        if (!stationAdmission.canAccept()) {
            String blockedReason = stationAdmission.blockedReason().isBlank()
                    ? "Route-entry station " + stationType + " cannot accept the candidate"
                    : stationAdmission.blockedReason();
            return closed(stationAdmission, blockedReason);
        }

        Optional<String> selectedTargetId = stationAdmission.selectedTargetId();
        if (selectedTargetId.isEmpty()) {
            return closed(
                    stationAdmission,
                    "Route-entry station " + stationType + " has no selected target");
        }

        String targetId = selectedTargetId.orElseThrow();
        Optional<OperationalRouteEntryQueue> selectedQueue = routeTargetRegistry.find(targetId);
        if (selectedQueue.isEmpty()) {
            return closed(
                    stationAdmission,
                    "Unknown operational route target " + targetId
                            + " for station " + stationType);
        }

        OperationalRouteEntryQueueSnapshot queueSnapshot = selectedQueue.orElseThrow().snapshot();
        if (queueSnapshot.stationType() != stationType) {
            return closed(
                    stationAdmission,
                    "Operational route target " + targetId + " belongs to station "
                            + queueSnapshot.stationType() + " instead of " + stationType);
        }
        if (!queueSnapshot.canAccept()) {
            return closed(
                    stationAdmission,
                    "Operational route target " + targetId + " has no waiting capacity");
        }
        return stationAdmission;
    }

    private static StationAdmissionSnapshot closed(
            StationAdmissionSnapshot admission,
            String blockedReason) {
        return new StationAdmissionSnapshot(
                admission.stationType(),
                admission.capacity(),
                admission.snapshot(),
                false,
                blockedReason,
                Optional.empty());
    }
}
