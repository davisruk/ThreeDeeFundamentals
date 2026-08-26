package online.davisfamily.warehouse.sim.dsp.scheduler.operational;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.av02.ReleasePhysicalToteFromAv02Command;
import online.davisfamily.warehouse.sim.dsp.av02.Av02OperationalPhysicalToteCandidate;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRole;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StartLocation;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseAvailability;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseCandidate;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteIdentity;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteSource;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineActivitySnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineDefinition;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineLeaseCatalogSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineLeaseSnapshot;
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspOrderStatus;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationCapacity;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationSnapshot;

class DspAv02OperationalReleaseTest {

    private final DspOperationalReleaseScheduler scheduler =
            new DspOperationalReleaseScheduler();

    @Test
    void shouldEmitAv02CommandForDirectP2pCandidateWithExactAssignment() {
        DspOperationalReleaseCandidate candidate = av02Candidate(
                "av02-000001",
                "empty-order",
                "sc-1",
                1,
                999,
                p2pRoute());
        OperationalRouteDestination destination =
                new OperationalRouteDestination(StationType.P2P, "p2p-1");
        StationAdmissionSnapshot admission = openAdmission(StationType.P2P, "p2p-1");

        DspOperationalReleaseDecision decision = scheduler.evaluate(stickySnapshot(
                List.of(candidate),
                Map.of(StationType.P2P, admission),
                destination)).releaseDecision().orElseThrow();

        ReleasePhysicalToteFromAv02Command command = assertInstanceOf(
                ReleasePhysicalToteFromAv02Command.class, decision.command());
        assertEquals(OperationalPhysicalToteSource.AV02, command.source());
        assertEquals(new PhysicalToteId("av02-000001"), command.physicalToteId());
        assertEquals(StationType.P2P, decision.routeEntry().stationType());
        assertEquals("p2p-1", decision.routeEntry().targetId());
        assertEquals(destination, command.proposedP2pAssignment().orElseThrow().destination());
    }

    @Test
    void shouldRouteAv02CandidateToThirdPartyBeforeP2p() {
        DspOperationalReleaseCandidate candidate = av02Candidate(
                "av02-000002",
                "third-party-order",
                "sc-1",
                2,
                999,
                new RouteRequirements(true, false, false, true, false, StartLocation.AV02));

        DspOperationalReleaseDecision decision = scheduler.evaluate(snapshot(
                List.of(candidate),
                Map.of(StationType.THIRD_PARTY,
                        openAdmission(StationType.THIRD_PARTY, "third-party-1"))))
                .releaseDecision().orElseThrow();

        assertInstanceOf(ReleasePhysicalToteFromAv02Command.class, decision.command());
        assertEquals(StationType.THIRD_PARTY, decision.routeEntry().stationType());
        assertEquals("third-party-1", decision.routeEntry().targetId());
        assertTrue(decision.command().proposedP2pAssignment().isEmpty());
    }

    @Test
    void shouldRouteAv02CandidateToAdaptingBeforeP2p() {
        DspOperationalReleaseCandidate candidate = av02Candidate(
                "av02-000003",
                "adapting-order",
                "sc-1",
                3,
                999,
                new RouteRequirements(false, true, false, true, false, StartLocation.AV02));

        DspOperationalReleaseDecision decision = scheduler.evaluate(snapshot(
                List.of(candidate),
                Map.of(StationType.ADAPTING,
                        openAdmission(StationType.ADAPTING, "adapting-1"))))
                .releaseDecision().orElseThrow();

        assertInstanceOf(ReleasePhysicalToteFromAv02Command.class, decision.command());
        assertEquals(StationType.ADAPTING, decision.routeEntry().stationType());
        assertEquals("adapting-1", decision.routeEntry().targetId());
    }

    @Test
    void shouldBlockAv02CandidateWhenFirstRouteTargetIsFull() {
        DspOperationalReleaseCandidate candidate = av02Candidate(
                "av02-000004",
                "blocked-order",
                "sc-1",
                4,
                999,
                new RouteRequirements(true, false, false, true, false, StartLocation.AV02));

        DspOperationalReleaseEvaluation evaluation = scheduler.evaluate(snapshot(
                List.of(candidate),
                Map.of(StationType.THIRD_PARTY,
                        closedAdmission(StationType.THIRD_PARTY, "third-party-1"))));

        assertTrue(evaluation.releaseDecision().isEmpty());
        assertEquals(OperationalReleaseBlockType.STATION_ADMISSION,
                evaluation.blockedCandidates().getFirst().blocks().getFirst().type());
    }

    @Test
    void shouldApplyAdaptedDependencyReadinessToAv02Candidate() {
        DspOperationalReleaseCandidate candidate = av02Candidate(
                "av02-000006",
                "adapted-dependent-order",
                "sc-1",
                6,
                999,
                DspOrderLineType.ADAPTED,
                p2pRoute());

        DspOperationalReleaseEvaluation evaluation = scheduler.evaluate(snapshot(
                List.of(candidate),
                Map.of(StationType.P2P, openAdmission(StationType.P2P, "p2p-1"))));

        assertTrue(evaluation.releaseDecision().isEmpty());
        assertEquals(OperationalReleaseBlockType.ADAPTED_DEPENDENCY,
                evaluation.blockedCandidates().getFirst().blocks().getFirst().type());
    }

    @Test
    void shouldUseSourceSequenceRatherThanOrderTypeForMixedCandidates() {
        DspOperationalReleaseCandidate osrCandidate = osrCandidate(
                "osr-000001",
                "full-pack-order",
                "sc-1",
                2,
                999,
                osrP2pRoute());
        DspOperationalReleaseCandidate av02Candidate = av02Candidate(
                "av02-000005",
                "empty-order",
                "sc-1",
                1,
                999,
                p2pRoute());
        StationAdmissionSnapshot admission = openAdmission(StationType.P2P, "p2p-1");

        DspOperationalReleaseDecision decision = scheduler.evaluate(snapshot(
                List.of(osrCandidate, av02Candidate),
                Map.of(StationType.P2P, admission))).releaseDecision().orElseThrow();

        assertEquals(new PhysicalToteId("av02-000005"), decision.command().physicalToteId());
        assertInstanceOf(ReleasePhysicalToteFromAv02Command.class, decision.command());
    }

    private static DspOperationalReleaseCandidate av02Candidate(
            String physicalToteId,
            String orderId,
            String serviceCentreId,
            long sourceSequenceNumber,
            int orderPriority,
            RouteRequirements routeRequirements) {
        return av02Candidate(
                physicalToteId,
                orderId,
                serviceCentreId,
                sourceSequenceNumber,
                orderPriority,
                DspOrderLineType.FULL_PACK,
                routeRequirements);
    }

    private static DspOperationalReleaseCandidate av02Candidate(
            String physicalToteId,
            String orderId,
            String serviceCentreId,
            long sourceSequenceNumber,
            int orderPriority,
            DspOrderLineType lineType,
            RouteRequirements routeRequirements) {
        NotionalToteOrder order = order(
                orderId,
                serviceCentreId,
                OrderType.EMPTY,
                orderPriority,
                sourceSequenceNumber,
                lineType);
        OperationalPhysicalToteIdentity identity = new OperationalPhysicalToteIdentity(
                OperationalPhysicalToteSource.AV02,
                new PhysicalToteId(physicalToteId),
                order.orderSheetKey(),
                OrderType.EMPTY,
                serviceCentreId,
                PhysicalToteRole.PRE_P2P,
                sourceSequenceNumber);
        return new DspOperationalReleaseCandidate(
                new Av02OperationalPhysicalToteCandidate(identity),
                new DspSchedulerOrderState(order, routeRequirements, DspOrderStatus.WAITING),
                List.of("pharmacy-1"));
    }

    private static DspOperationalReleaseCandidate osrCandidate(
            String physicalToteId,
            String orderId,
            String serviceCentreId,
            long sourceSequenceNumber,
            int orderPriority,
            RouteRequirements routeRequirements) {
        NotionalToteOrder order = order(
                orderId,
                serviceCentreId,
                OrderType.FULL_PACK,
                orderPriority,
                sourceSequenceNumber);
        return new DspOperationalReleaseCandidate(
                new OsrProcessingReleaseCandidate(
                        new PhysicalToteId(physicalToteId),
                        order.orderSheetKey(),
                        OrderType.FULL_PACK,
                        serviceCentreId,
                        sourceSequenceNumber,
                        OsrProcessingReleaseAvailability.AVAILABLE,
                        Optional.empty()),
                new DspSchedulerOrderState(order, routeRequirements, DspOrderStatus.WAITING),
                List.of("pharmacy-1"));
    }

    private static NotionalToteOrder order(
            String orderId,
            String serviceCentreId,
            OrderType orderType,
            int orderPriority,
            long sequenceNumber) {
        return order(
                orderId,
                serviceCentreId,
                orderType,
                orderPriority,
                sequenceNumber,
                DspOrderLineType.FULL_PACK);
    }

    private static NotionalToteOrder order(
            String orderId,
            String serviceCentreId,
            OrderType orderType,
            int orderPriority,
            long sequenceNumber,
            DspOrderLineType lineType) {
        DspOrderItem item = new DspOrderItem(
                "line-" + orderId,
                "product-" + orderId,
                1,
                "pharmacy-1",
                "patient-" + orderId,
                "prescription-" + orderId,
                lineType,
                orderId,
                1,
                1);
        return new NotionalToteOrder(
                orderId,
                "notional-" + orderId,
                serviceCentreId,
                1,
                orderType,
                List.of(item),
                orderPriority,
                sequenceNumber);
    }

    private static RouteRequirements p2pRoute() {
        return new RouteRequirements(false, false, false, true, false, StartLocation.AV02);
    }

    private static RouteRequirements osrP2pRoute() {
        return new RouteRequirements(false, false, false, true, false, StartLocation.OSR);
    }

    private static DspOperationalReleaseSnapshot snapshot(
            List<DspOperationalReleaseCandidate> candidates,
            Map<StationType, StationAdmissionSnapshot> stationAdmissions) {
        OperationalRouteEntrySelector selector = new OperationalRouteEntrySelector();
        List<OperationalCandidateRouteAdmission> routeAdmissions = candidates.stream()
                .map(candidate -> selector.firstStation(
                                candidate.logicalOrderState().routeRequirements())
                        .map(stationAdmissions::get)
                        .map(admission -> new OperationalCandidateRouteAdmission(
                                candidate.physicalCandidate().physicalToteId(), admission))
                        .orElseThrow())
                .toList();
        return new DspOperationalReleaseSnapshot(
                candidates,
                groups(candidates),
                stationAdmissions,
                Set.of(),
                routeAdmissions);
    }

    private static DspOperationalReleaseSnapshot stickySnapshot(
            List<DspOperationalReleaseCandidate> candidates,
            Map<StationType, StationAdmissionSnapshot> stationAdmissions,
            OperationalRouteDestination destination) {
        DspOperationalReleaseSnapshot base = snapshot(candidates, stationAdmissions);
        P2pLineDefinition definition = new P2pLineDefinition(
                new P2pLineId("line-1"), destination);
        P2pLineLeaseSnapshot lease = new P2pLineLeaseSnapshot(
                definition,
                Optional.of("sc-1"),
                P2pLineActivitySnapshot.idle(),
                List.of());
        return new DspOperationalReleaseSnapshot(
                base.candidates(),
                base.pharmacyGroups(),
                base.stationAdmissions(),
                base.preparedLineKeys(),
                base.routeAdmissions(),
                new P2pLineLeaseCatalogSnapshot(List.of(lease)),
                Map.of(destination, true));
    }

    private static List<ServiceCentrePharmacyGroup> groups(
            List<DspOperationalReleaseCandidate> candidates) {
        List<ServiceCentrePharmacyGroup> groups = new ArrayList<>();
        Map<String, Integer> nextIndex = new LinkedHashMap<>();
        Set<String> seen = new LinkedHashSet<>();
        for (DspOperationalReleaseCandidate candidate : candidates) {
            String serviceCentreId = candidate.physicalCandidate().serviceCentreId();
            for (String pharmacyId : candidate.pharmacyIds()) {
                String key = serviceCentreId + "\u0000" + pharmacyId;
                if (seen.add(key)) {
                    int groupIndex = nextIndex.getOrDefault(serviceCentreId, 0);
                    groups.add(new ServiceCentrePharmacyGroup(
                            serviceCentreId,
                            pharmacyId,
                            groupIndex,
                            candidate.physicalCandidate().sourceSequenceNumber()));
                    nextIndex.put(serviceCentreId, groupIndex + 1);
                }
            }
        }
        return List.copyOf(groups);
    }

    private static StationAdmissionSnapshot openAdmission(
            StationType stationType,
            String targetId) {
        return new StationAdmissionSnapshot(
                stationType,
                new StationCapacity(1, 1),
                new StationSnapshot(stationType, 0, 0),
                true,
                "",
                Optional.of(targetId));
    }

    private static StationAdmissionSnapshot closedAdmission(
            StationType stationType,
            String targetId) {
        return new StationAdmissionSnapshot(
                stationType,
                new StationCapacity(1, 0),
                new StationSnapshot(stationType, 1, 0),
                true,
                "",
                Optional.of(targetId));
    }
}
