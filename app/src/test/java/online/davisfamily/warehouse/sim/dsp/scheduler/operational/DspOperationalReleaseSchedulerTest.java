package online.davisfamily.warehouse.sim.dsp.scheduler.operational;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StartLocation;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseAvailability;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseCandidate;
import online.davisfamily.warehouse.sim.dsp.osr.release.ReleasePhysicalToteFromOsrCommand;
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspOrderStatus;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationCapacity;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationSnapshot;

class DspOperationalReleaseSchedulerTest {

    private final DspOperationalReleaseScheduler scheduler =
            new DspOperationalReleaseScheduler();

    @Test
    void shouldEmitPhysicalCommandForDependencyReadyCandidate() {
        DspOperationalReleaseCandidate candidate = candidate(
                "tote-1",
                logicalState(
                        "order-1", 1, OrderType.FULL_PACK, "sc-1", 999,
                        DspOrderLineType.FULL_PACK, p2pRoute()),
                7,
                OsrProcessingReleaseAvailability.AVAILABLE,
                Optional.empty());
        DspOperationalReleaseSnapshot snapshot = snapshot(
                List.of(candidate),
                Map.of(StationType.P2P, openAdmission(StationType.P2P, "p2p-1")),
                Set.of());

        DspOperationalReleaseDecision decision = scheduler.evaluate(snapshot)
                .releaseDecision()
                .orElseThrow();
        ReleasePhysicalToteFromOsrCommand command = decision.command();

        assertSame(candidate, decision.candidate());
        assertEquals(new PhysicalToteId("tote-1"), command.physicalToteId());
        assertEquals(candidate.logicalOrderState().order().orderSheetKey(), command.orderSheetKey());
        assertEquals("sc-1", command.serviceCentreId());
        assertEquals("p2p-1", command.releaseTargetId());
    }

    @Test
    void shouldEmitExactSelectedRouteEntryTarget() {
        RouteRequirements route = new RouteRequirements(
                true, false, false, true, false, StartLocation.OSR);
        DspOperationalReleaseCandidate candidate = candidate(
                "tote-1",
                logicalState(
                        "order-1", 1, OrderType.FULL_PACK, "sc-1", 999,
                        DspOrderLineType.FULL_PACK, route),
                1,
                OsrProcessingReleaseAvailability.AVAILABLE,
                Optional.empty());
        DspOperationalReleaseSnapshot snapshot = snapshot(
                List.of(candidate),
                Map.of(
                        StationType.THIRD_PARTY,
                        openAdmission(StationType.THIRD_PARTY, "third-party-lane-2"),
                        StationType.P2P,
                        openAdmission(StationType.P2P, "p2p-ignored")),
                Set.of());

        DspOperationalReleaseDecision decision = scheduler.evaluate(snapshot)
                .releaseDecision()
                .orElseThrow();

        assertEquals(StationType.THIRD_PARTY, decision.routeEntry().stationType());
        assertEquals("third-party-lane-2", decision.routeEntry().targetId());
        assertEquals("third-party-lane-2", decision.command().releaseTargetId());
    }

    @Test
    void shouldReleaseFullPackWhileAssociatedDependencyIsBlocked() {
        DspOperationalReleaseCandidate associated = candidate(
                "associated-tote",
                logicalState(
                        "associated-order", 1, OrderType.ASSOCIATED, "sc-1", 999,
                        DspOrderLineType.ADAPTED, p2pRoute()),
                1,
                OsrProcessingReleaseAvailability.AVAILABLE,
                Optional.empty());
        DspOperationalReleaseCandidate fullPack = candidate(
                "full-pack-tote",
                logicalState(
                        "full-pack-order", 1, OrderType.FULL_PACK, "sc-1", 999,
                        DspOrderLineType.FULL_PACK, p2pRoute()),
                2,
                OsrProcessingReleaseAvailability.AVAILABLE,
                Optional.empty());

        DspOperationalReleaseEvaluation evaluation = scheduler.evaluate(snapshot(
                List.of(associated, fullPack),
                Map.of(StationType.P2P, openAdmission(StationType.P2P, "p2p-1")),
                Set.of()));

        assertEquals(
                new PhysicalToteId("full-pack-tote"),
                evaluation.releaseDecision().orElseThrow().command().physicalToteId());
        assertEquals(1, evaluation.blockedCandidates().size());
        assertEquals(
                new PhysicalToteId("associated-tote"),
                evaluation.blockedCandidates().get(0).physicalToteId());
        assertEquals(
                OperationalReleaseBlockType.ADAPTED_DEPENDENCY,
                evaluation.blockedCandidates().get(0).blocks().get(0).type());
    }

    @Test
    void shouldKeepBlockedRepeatedSheetCandidateObservable() {
        DspSchedulerOrderState sharedLogicalState = logicalState(
                "order-1", 1, OrderType.FULL_PACK, "sc-1", 999,
                DspOrderLineType.FULL_PACK, p2pRoute());
        DspOperationalReleaseCandidate active = candidate(
                "active-tote",
                sharedLogicalState,
                1,
                OsrProcessingReleaseAvailability.AVAILABLE,
                Optional.empty());
        DspOperationalReleaseCandidate waiting = candidate(
                "waiting-tote",
                sharedLogicalState,
                2,
                OsrProcessingReleaseAvailability.BLOCKED_BY_ACTIVE_SHEET_ASSIGNMENT,
                Optional.of(new PhysicalToteId("active-tote")));

        DspOperationalReleaseEvaluation evaluation = scheduler.evaluate(snapshot(
                List.of(active, waiting),
                Map.of(StationType.P2P, openAdmission(StationType.P2P, "p2p-1")),
                Set.of()));

        assertEquals(
                new PhysicalToteId("active-tote"),
                evaluation.releaseDecision().orElseThrow().command().physicalToteId());
        OperationalBlockedCandidate blocked = evaluation.blockedCandidates().get(0);
        assertEquals(new PhysicalToteId("waiting-tote"), blocked.physicalToteId());
        assertEquals(sharedLogicalState.order().orderSheetKey(), blocked.orderSheetKey());
        assertEquals(OperationalReleaseBlockType.ACTIVE_SHEET_ASSIGNMENT,
                blocked.blocks().get(0).type());
    }

    @Test
    void shouldChooseLowerPriorityCentreWhenHigherCentreHasNoEligibleCandidate() {
        DspOperationalReleaseCandidate blockedHighPriority = candidate(
                "high-tote",
                logicalState(
                        "high-order", 1, OrderType.ASSOCIATED, "sc-high", 999,
                        DspOrderLineType.ADAPTED, p2pRoute()),
                1,
                OsrProcessingReleaseAvailability.AVAILABLE,
                Optional.empty());
        DspOperationalReleaseCandidate readyLowPriority = candidate(
                "low-tote",
                logicalState(
                        "low-order", 1, OrderType.FULL_PACK, "sc-low", 990,
                        DspOrderLineType.FULL_PACK, p2pRoute()),
                2,
                OsrProcessingReleaseAvailability.AVAILABLE,
                Optional.empty());

        DspOperationalReleaseEvaluation evaluation = scheduler.evaluate(snapshot(
                List.of(blockedHighPriority, readyLowPriority),
                Map.of(StationType.P2P, openAdmission(StationType.P2P, "p2p-1")),
                Set.of()));

        assertEquals(
                new PhysicalToteId("low-tote"),
                evaluation.releaseDecision().orElseThrow().command().physicalToteId());
        assertEquals(new PhysicalToteId("high-tote"),
                evaluation.blockedCandidates().get(0).physicalToteId());
    }

    @Test
    void shouldNotUseLegacyOrderTypePriority() {
        DspOperationalReleaseCandidate associated = candidate(
                "associated-tote",
                logicalState(
                        "associated-order", 1, OrderType.ASSOCIATED, "sc-1", 999,
                        DspOrderLineType.ADAPTED, p2pRoute()),
                2,
                OsrProcessingReleaseAvailability.AVAILABLE,
                Optional.empty());
        DspOperationalReleaseCandidate fullPack = candidate(
                "full-pack-tote",
                logicalState(
                        "full-pack-order", 1, OrderType.FULL_PACK, "sc-1", 999,
                        DspOrderLineType.FULL_PACK, p2pRoute()),
                1,
                OsrProcessingReleaseAvailability.AVAILABLE,
                Optional.empty());
        DspOrderItem adaptedLine = associated.logicalOrderState().order().items().get(0);
        PreparedLineKey preparedKey = PreparedLineKey.forDispatchLine(
                associated.logicalOrderState().order(), adaptedLine);

        DspOperationalReleaseEvaluation evaluation = scheduler.evaluate(snapshot(
                List.of(associated, fullPack),
                Map.of(StationType.P2P, openAdmission(StationType.P2P, "p2p-1")),
                Set.of(preparedKey)));

        assertEquals(
                new PhysicalToteId("full-pack-tote"),
                evaluation.releaseDecision().orElseThrow().command().physicalToteId());
        assertTrue(evaluation.blockedCandidates().isEmpty());
    }

    @Test
    void shouldReturnNothingWhenSnapshotHasNoCandidates() {
        DspOperationalReleaseEvaluation evaluation = scheduler.evaluate(
                new DspOperationalReleaseSnapshot(List.of(), List.of(), Map.of(), Set.of()));

        assertTrue(evaluation.releaseDecision().isEmpty());
        assertTrue(evaluation.blockedCandidates().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> scheduler.evaluate(null));
    }

    @Test
    void shouldReturnTypedBlocksWhenEveryCandidateIsBlocked() {
        DspOperationalReleaseCandidate dependencyBlocked = candidate(
                "dependency-tote",
                logicalState(
                        "associated-order", 1, OrderType.ASSOCIATED, "sc-1", 999,
                        DspOrderLineType.ADAPTED, p2pRoute()),
                1,
                OsrProcessingReleaseAvailability.AVAILABLE,
                Optional.empty());
        DspOperationalReleaseCandidate lifecycleBlocked = candidate(
                "lifecycle-tote",
                logicalState(
                        "full-pack-order", 1, OrderType.FULL_PACK, "sc-1", 999,
                        DspOrderLineType.FULL_PACK, p2pRoute()),
                2,
                OsrProcessingReleaseAvailability.BLOCKED_BY_ACTIVE_SHEET_ASSIGNMENT,
                Optional.of(new PhysicalToteId("active-tote")));

        DspOperationalReleaseEvaluation evaluation = scheduler.evaluate(snapshot(
                List.of(dependencyBlocked, lifecycleBlocked),
                Map.of(StationType.P2P, openAdmission(StationType.P2P, "p2p-1")),
                Set.of()));

        assertTrue(evaluation.releaseDecision().isEmpty());
        assertEquals(List.of(
                OperationalReleaseBlockType.ADAPTED_DEPENDENCY,
                OperationalReleaseBlockType.ACTIVE_SHEET_ASSIGNMENT),
                evaluation.blockedCandidates().stream()
                        .map(blocked -> blocked.blocks().get(0).type())
                        .toList());
        assertThrows(UnsupportedOperationException.class,
                () -> evaluation.blockedCandidates().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> evaluation.blockedCandidates().get(0).blocks().clear());
    }

    @Test
    void shouldEvaluateWithoutMutatingSnapshotState() {
        DspOperationalReleaseCandidate candidate = candidate(
                "tote-1",
                logicalState(
                        "order-1", 1, OrderType.FULL_PACK, "sc-1", 999,
                        DspOrderLineType.FULL_PACK, p2pRoute()),
                1,
                OsrProcessingReleaseAvailability.AVAILABLE,
                Optional.empty());
        StationAdmissionSnapshot admission = openAdmission(StationType.P2P, "p2p-1");
        DspOperationalReleaseSnapshot snapshot = snapshot(
                List.of(candidate), Map.of(StationType.P2P, admission), Set.of());

        DspOperationalReleaseEvaluation first = scheduler.evaluate(snapshot);
        DspOperationalReleaseEvaluation second = scheduler.evaluate(snapshot);

        assertEquals(first, second);
        assertEquals(List.of(candidate), snapshot.candidates());
        assertSame(admission, snapshot.stationAdmissions().get(StationType.P2P));
        assertEquals(DspOrderStatus.WAITING, candidate.logicalOrderState().status());
        assertFalse(snapshot.candidates().isEmpty());
    }

    private static DspOperationalReleaseSnapshot snapshot(
            List<DspOperationalReleaseCandidate> candidates,
            Map<StationType, StationAdmissionSnapshot> stationAdmissions,
            Set<PreparedLineKey> preparedLineKeys) {
        List<ServiceCentrePharmacyGroup> groups = candidates.stream()
                .map(candidate -> candidate.physicalCandidate().serviceCentreId())
                .distinct()
                .map(serviceCentreId -> new ServiceCentrePharmacyGroup(
                        serviceCentreId, "pharmacy-1", 0, 1))
                .toList();
        OperationalRouteEntrySelector routeEntrySelector = new OperationalRouteEntrySelector();
        List<OperationalCandidateRouteAdmission> routeAdmissions = candidates.stream()
                .map(candidate -> routeEntrySelector.firstStation(
                        candidate.logicalOrderState().routeRequirements())
                        .map(stationAdmissions::get)
                        .map(admission -> new OperationalCandidateRouteAdmission(
                                candidate.physicalCandidate().physicalToteId(), admission)))
                .flatMap(Optional::stream)
                .toList();
        return new DspOperationalReleaseSnapshot(
                candidates,
                groups,
                stationAdmissions,
                preparedLineKeys,
                routeAdmissions);
    }

    private static DspOperationalReleaseCandidate candidate(
            String physicalToteId,
            DspSchedulerOrderState logicalState,
            long sourceSequenceNumber,
            OsrProcessingReleaseAvailability availability,
            Optional<PhysicalToteId> blockingPhysicalToteId) {
        OsrProcessingReleaseCandidate physicalCandidate = new OsrProcessingReleaseCandidate(
                new PhysicalToteId(physicalToteId),
                logicalState.order().orderSheetKey(),
                logicalState.order().orderType(),
                logicalState.order().serviceCentreId(),
                sourceSequenceNumber,
                availability,
                blockingPhysicalToteId);
        return new DspOperationalReleaseCandidate(
                physicalCandidate, logicalState, List.of("pharmacy-1"));
    }

    private static DspSchedulerOrderState logicalState(
            String orderId,
            int sheetNumber,
            OrderType orderType,
            String serviceCentreId,
            int priority,
            DspOrderLineType lineType,
            RouteRequirements routeRequirements) {
        DspOrderItem item = new DspOrderItem(
                "line-" + orderId,
                "product-" + orderId,
                1,
                "pharmacy-1",
                "patient-" + orderId,
                "prescription-" + orderId,
                lineType,
                "reference-" + orderId,
                1,
                1);
        NotionalToteOrder order = new NotionalToteOrder(
                orderId,
                "notional-" + orderId,
                serviceCentreId,
                sheetNumber,
                orderType,
                List.of(item),
                priority,
                1);
        return new DspSchedulerOrderState(
                order, routeRequirements, DspOrderStatus.WAITING);
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

    private static RouteRequirements p2pRoute() {
        return new RouteRequirements(
                false, false, false, true, false, StartLocation.OSR);
    }
}
