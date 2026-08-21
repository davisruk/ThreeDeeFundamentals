package online.davisfamily.warehouse.sim.dsp.scheduler.operational;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteTargetAdmissionCatalog;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteTargetAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionResolver;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;

public final class OperationalCandidateRouteAdmissionFactory {
    private final OperationalRouteEntrySelector routeEntrySelector;
    private final StationAdmissionResolver stationAdmissionResolver;
    private final OperationalRouteTargetAdmissionCatalog routeTargetAdmissionCatalog;

    public OperationalCandidateRouteAdmissionFactory(
            OperationalRouteEntrySelector routeEntrySelector,
            StationAdmissionResolver stationAdmissionResolver,
            OperationalRouteTargetAdmissionCatalog routeTargetAdmissionCatalog) {
        if (routeEntrySelector == null) {
            throw new IllegalArgumentException("routeEntrySelector must not be null");
        }
        if (stationAdmissionResolver == null) {
            throw new IllegalArgumentException("stationAdmissionResolver must not be null");
        }
        if (routeTargetAdmissionCatalog == null) {
            throw new IllegalArgumentException("routeTargetAdmissionCatalog must not be null");
        }
        this.routeEntrySelector = routeEntrySelector;
        this.stationAdmissionResolver = stationAdmissionResolver;
        this.routeTargetAdmissionCatalog = routeTargetAdmissionCatalog;
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

        Map<String, OperationalRouteTargetAdmissionSnapshot> targetAdmissionsById =
                snapshotAdmissionsByTargetId();
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
                    stationType,
                    targetAdmissionsById);
            admissions.add(new OperationalCandidateRouteAdmission(
                    candidate.physicalCandidate().physicalToteId(),
                    effectiveAdmission));
        }
        return List.copyOf(admissions);
    }

    private StationAdmissionSnapshot effectiveAdmission(
            StationAdmissionSnapshot stationAdmission,
            StationType stationType,
            Map<String, OperationalRouteTargetAdmissionSnapshot> targetAdmissionsById) {
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
        OperationalRouteTargetAdmissionSnapshot targetAdmission =
                targetAdmissionsById.get(targetId);
        if (targetAdmission == null) {
            return closed(
                    stationAdmission,
                    "Unknown operational route admission target " + targetId
                            + " for station " + stationType);
        }

        if (targetAdmission.stationType() != stationType) {
            return closed(
                    stationAdmission,
                    "Operational route admission target " + targetId + " belongs to station "
                            + targetAdmission.stationType() + " instead of " + stationType);
        }
        if (!targetAdmission.canAccept()) {
            return closed(
                    stationAdmission,
                    "Operational route admission target " + targetId
                            + " has no waiting capacity");
        }
        return stationAdmission;
    }

    private Map<String, OperationalRouteTargetAdmissionSnapshot> snapshotAdmissionsByTargetId() {
        List<OperationalRouteTargetAdmissionSnapshot> targetAdmissions =
                routeTargetAdmissionCatalog.snapshotAdmissions();
        if (targetAdmissions == null) {
            throw new IllegalStateException("snapshotAdmissions returned null");
        }

        Map<String, OperationalRouteTargetAdmissionSnapshot> admissionsByTargetId =
                new LinkedHashMap<>();
        for (OperationalRouteTargetAdmissionSnapshot targetAdmission : targetAdmissions) {
            if (targetAdmission == null) {
                throw new IllegalStateException("snapshotAdmissions must not contain null");
            }
            if (admissionsByTargetId.putIfAbsent(
                    targetAdmission.targetId(), targetAdmission) != null) {
                throw new IllegalStateException(
                        "Duplicate operational route admission target ID: "
                                + targetAdmission.targetId());
            }
        }
        return Map.copyOf(admissionsByTargetId);
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
