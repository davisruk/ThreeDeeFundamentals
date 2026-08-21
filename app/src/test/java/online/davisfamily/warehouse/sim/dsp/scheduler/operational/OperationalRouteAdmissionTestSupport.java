package online.davisfamily.warehouse.sim.dsp.scheduler.operational;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StartLocation;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseAvailability;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseCandidate;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseRequest;
import online.davisfamily.warehouse.sim.dsp.osr.release.route.OperationalRouteEntryQueue;
import online.davisfamily.warehouse.sim.dsp.osr.release.route.OperationalRouteTargetDefinition;
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspOrderStatus;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationCapacity;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;

final class OperationalRouteAdmissionTestSupport {
    private OperationalRouteAdmissionTestSupport() {
    }

    static DspOperationalReleaseCandidate candidate(
            String physicalToteId,
            RouteRequirements routeRequirements) {
        String orderId = "order-" + physicalToteId;
        DspOrderItem item = new DspOrderItem(
                "line-" + physicalToteId,
                "product-1",
                1,
                "pharmacy-1",
                "patient-1",
                "prescription-1",
                DspOrderLineType.FULL_PACK,
                orderId,
                1,
                1);
        NotionalToteOrder order = new NotionalToteOrder(
                orderId,
                "notional-" + physicalToteId,
                "sc-1",
                1,
                OrderType.FULL_PACK,
                List.of(item),
                999,
                1);
        DspSchedulerOrderState logicalState = new DspSchedulerOrderState(
                order,
                routeRequirements,
                DspOrderStatus.WAITING);
        OsrProcessingReleaseCandidate physicalCandidate = new OsrProcessingReleaseCandidate(
                new PhysicalToteId(physicalToteId),
                order.orderSheetKey(),
                order.orderType(),
                order.serviceCentreId(),
                1,
                OsrProcessingReleaseAvailability.AVAILABLE,
                Optional.empty());
        return new DspOperationalReleaseCandidate(
                physicalCandidate,
                logicalState,
                List.of("pharmacy-1"));
    }

    static RouteRequirements route(StationType stationType) {
        return new RouteRequirements(
                stationType == StationType.THIRD_PARTY,
                stationType == StationType.ADAPTING,
                false,
                stationType == StationType.P2P,
                false,
                StartLocation.OSR);
    }

    static RouteRequirements noRoute() {
        return new RouteRequirements(false, false, false, false, false, StartLocation.OSR);
    }

    static StationAdmissionSnapshot openAdmission(
            StationType stationType,
            String targetId) {
        return new StationAdmissionSnapshot(
                stationType,
                new StationCapacity(1, 1),
                new StationSnapshot(stationType, 0, 0),
                true,
                "",
                Optional.ofNullable(targetId));
    }

    static StationAdmissionSnapshot closedAdmission(
            StationType stationType,
            String targetId,
            String reason) {
        return new StationAdmissionSnapshot(
                stationType,
                new StationCapacity(1, 1),
                new StationSnapshot(stationType, 0, 0),
                false,
                reason,
                Optional.ofNullable(targetId));
    }

    static OperationalRouteEntryQueue queue(
            StationType stationType,
            String targetId,
            int capacity) {
        return new OperationalRouteEntryQueue(new OperationalRouteTargetDefinition(
                stationType,
                targetId,
                capacity));
    }

    static WarehouseSchedulerSnapshot logicalSnapshot(
            List<DspOperationalReleaseCandidate> candidates) {
        return new WarehouseSchedulerSnapshot(
                candidates.stream()
                        .map(DspOperationalReleaseCandidate::logicalOrderState)
                        .toList(),
                Map.of(),
                Set.of(),
                Optional.empty());
    }

    static DspOperationalReleaseSnapshot operationalSnapshot(
            List<DspOperationalReleaseCandidate> candidates,
            List<OperationalCandidateRouteAdmission> routeAdmissions) {
        return new DspOperationalReleaseSnapshot(
                candidates,
                List.of(new ServiceCentrePharmacyGroup(
                        "sc-1", "pharmacy-1", 0, 1)),
                Map.of(),
                Set.of(),
                routeAdmissions);
    }

    static OsrProcessingReleaseRequest request(DspOperationalReleaseCandidate candidate) {
        NotionalToteOrder order = candidate.logicalOrderState().order();
        return new OsrProcessingReleaseRequest(
                new InboundToteManifest(
                        candidate.physicalCandidate().physicalToteId(),
                        order.orderSheetKey(),
                        order.orderType(),
                        order.serviceCentreId(),
                        order.items(),
                        candidate.physicalCandidate().sourceSequenceNumber()),
                Duration.ZERO);
    }
}
