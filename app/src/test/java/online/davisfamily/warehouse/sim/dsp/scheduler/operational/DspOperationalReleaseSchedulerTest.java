package online.davisfamily.warehouse.sim.dsp.scheduler.operational;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDateTime;
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
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteSnapshot;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pBaggingActivitySnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.allocation.DeadlineAwareElasticStickyP2pLineAllocationPolicy;
import online.davisfamily.warehouse.sim.dsp.p2p.allocation.P2pElasticAllocationCalibrationStatus;
import online.davisfamily.warehouse.sim.dsp.p2p.allocation.P2pElasticAllocationSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.allocation.P2pServiceCentreLineDemandSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.allocation.P2pServiceCentreWorkloadSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pInputActivitySnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineActivitySnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineDefinition;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineLeaseCatalogSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineLeaseSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pPackPathActivitySnapshot;
import online.davisfamily.warehouse.sim.dsp.schedule.ServiceCentreDeadlineSnapshot;
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
    void shouldSelectAvailableFallbackLineForDirectP2pAndCarryAssignment() {
        DspOperationalReleaseCandidate candidate = candidate(
                "tote-1",
                logicalState(
                        "order-1", 1, OrderType.FULL_PACK, "sc-1", 999,
                        DspOrderLineType.FULL_PACK, p2pRoute()),
                1,
                OsrProcessingReleaseAvailability.AVAILABLE,
                Optional.empty());
        P2pLineLeaseSnapshot preferred = leasedLine(
                "line-1", "sc-1", Optional.of("pharmacy-1"));
        P2pLineLeaseSnapshot fallback = leasedLine(
                "line-2", "sc-1", Optional.empty());
        Map<OperationalRouteDestination, Boolean> targetAdmissions = new java.util.LinkedHashMap<>();
        targetAdmissions.put(preferred.definition().destination(), false);
        targetAdmissions.put(fallback.definition().destination(), true);

        DspOperationalReleaseDecision decision = scheduler.evaluate(stickySnapshot(
                List.of(candidate),
                Map.of(StationType.P2P, openAdmission(StationType.P2P, "target-line-1")),
                Set.of(),
                new P2pLineLeaseCatalogSnapshot(List.of(preferred, fallback)),
                targetAdmissions)).releaseDecision().orElseThrow();

        assertEquals("target-line-2", decision.routeEntry().targetId());
        assertEquals(fallback.definition().lineId(),
                decision.command().proposedP2pAssignment().orElseThrow().lineId());
        assertFalse(decision.command().proposedP2pAssignment().isEmpty());
    }

    @Test
    void shouldAssignP2pWithoutGatingEarlierRouteEntryOnCurrentP2pCapacity() {
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
        P2pLineLeaseSnapshot line = leasedLine(
                "line-1", "sc-1", Optional.of("pharmacy-1"));

        DspOperationalReleaseDecision decision = scheduler.evaluate(stickySnapshot(
                List.of(candidate),
                Map.of(StationType.THIRD_PARTY,
                        openAdmission(StationType.THIRD_PARTY, "third-party-1")),
                Set.of(),
                new P2pLineLeaseCatalogSnapshot(List.of(line)),
                Map.of(line.definition().destination(), false)))
                .releaseDecision().orElseThrow();

        assertEquals(StationType.THIRD_PARTY, decision.routeEntry().stationType());
        assertEquals("third-party-1", decision.command().releaseTargetId());
        assertEquals(line.definition().destination(),
                decision.command().proposedP2pAssignment().orElseThrow().destination());
    }

    @Test
    void shouldPrioritizeActiveLinePharmacyAffinityWithinServiceCentre() {
        DspOperationalReleaseCandidate firstGroup = candidate(
                "tote-1",
                logicalState(
                        "order-1", 1, OrderType.FULL_PACK, "sc-1", 999,
                        DspOrderLineType.FULL_PACK, p2pRoute()),
                1,
                OsrProcessingReleaseAvailability.AVAILABLE,
                Optional.empty());
        DspOperationalReleaseCandidate affinity = candidateWithPharmacy(
                "tote-2",
                logicalState(
                        "order-2", 1, OrderType.FULL_PACK, "sc-1", 999,
                        DspOrderLineType.FULL_PACK, p2pRoute()),
                2,
                "pharmacy-2");
        P2pLineLeaseSnapshot line = leasedLine(
                "line-1", "sc-1", Optional.of("pharmacy-2"));
        DspOperationalReleaseSnapshot snapshot = stickySnapshot(
                List.of(firstGroup, affinity),
                Map.of(StationType.P2P, openAdmission(StationType.P2P, "target-line-1")),
                Set.of(),
                new P2pLineLeaseCatalogSnapshot(List.of(line)),
                Map.of(line.definition().destination(), true));

        DspOperationalReleaseDecision decision = scheduler.evaluate(snapshot)
                .releaseDecision().orElseThrow();

        assertEquals(new PhysicalToteId("tote-2"), decision.command().physicalToteId());
    }

    @Test
    void shouldBlockWithoutCompatibleLineAndLeaveNonP2pCandidateUnassigned() {
        DspOperationalReleaseCandidate p2pCandidate = candidate(
                "p2p-tote",
                logicalState(
                        "p2p-order", 1, OrderType.FULL_PACK, "sc-1", 999,
                        DspOrderLineType.FULL_PACK, p2pRoute()),
                1,
                OsrProcessingReleaseAvailability.AVAILABLE,
                Optional.empty());
        P2pLineLeaseSnapshot otherOwner = leasedLine(
                "line-1", "sc-2", Optional.empty());
        DspOperationalReleaseEvaluation blocked = scheduler.evaluate(stickySnapshot(
                List.of(p2pCandidate),
                Map.of(StationType.P2P, openAdmission(StationType.P2P, "target-line-1")),
                Set.of(),
                new P2pLineLeaseCatalogSnapshot(List.of(otherOwner)),
                Map.of(otherOwner.definition().destination(), true)));

        assertTrue(blocked.releaseDecision().isEmpty());
        assertEquals(OperationalReleaseBlockType.P2P_LINE_ALLOCATION,
                blocked.blockedCandidates().getFirst().blocks().getFirst().type());
        assertEquals("NO_COMPATIBLE_P2P_LINE",
                blocked.blockedCandidates().getFirst().blocks().getFirst().reason());

        DspOperationalReleaseCandidate adapted = candidate(
                "adapted-tote",
                logicalState(
                        "adapted-order", 1, OrderType.ADAPTED, "sc-1", 999,
                        DspOrderLineType.ADAPTED,
                        new RouteRequirements(false, true, false, false, false, StartLocation.OSR)),
                2,
                OsrProcessingReleaseAvailability.AVAILABLE,
                Optional.empty());
        DspOperationalReleaseDecision nonP2p = scheduler.evaluate(stickySnapshot(
                List.of(adapted),
                Map.of(StationType.ADAPTING,
                        openAdmission(StationType.ADAPTING, "adapting-1")),
                Set.of(),
                new P2pLineLeaseCatalogSnapshot(List.of(otherOwner)),
                Map.of(otherOwner.definition().destination(), true)))
                .releaseDecision().orElseThrow();
        assertTrue(nonP2p.command().proposedP2pAssignment().isEmpty());
    }

    @Test
    void shouldApplyElasticBudgetOnlyWhenElasticPolicyIsExplicitlyInjected() {
        DspOperationalReleaseCandidate candidate = candidate(
                "tote-1",
                logicalState(
                        "order-1", 1, OrderType.FULL_PACK, "sc-1", 999,
                        DspOrderLineType.FULL_PACK, p2pRoute()),
                1,
                OsrProcessingReleaseAvailability.AVAILABLE,
                Optional.empty());
        P2pLineLeaseSnapshot line = leasedLine(
                "line-1", "sc-1", Optional.empty());
        P2pLineLeaseCatalogSnapshot catalog = new P2pLineLeaseCatalogSnapshot(List.of(line));
        P2pElasticAllocationSnapshot allocation = elasticAllocation(
                catalog,
                List.of(demand("sc-1", 999, 0, List.of(), List.of("line-1"))));
        DspOperationalReleaseSnapshot snapshot = elasticSnapshot(
                List.of(candidate),
                Map.of(StationType.P2P, openAdmission(StationType.P2P, "target-line-1")),
                Set.of(),
                catalog,
                Map.of(line.definition().destination(), true),
                allocation);

        assertTrue(scheduler.evaluate(snapshot).releaseDecision().isPresent());

        DspOperationalReleaseScheduler elasticScheduler = elasticScheduler();
        DspOperationalReleaseEvaluation blocked = elasticScheduler.evaluate(snapshot);
        assertTrue(blocked.releaseDecision().isEmpty());
        assertEquals("NO_ELASTIC_LINE_BUDGET",
                blocked.blockedCandidates().getFirst().blocks().getFirst().reason());
    }

    @Test
    void shouldReleaseLaterEligibleCentreWhenEarlierCandidateHasNoElasticBudget() {
        DspOperationalReleaseCandidate earlier = candidate(
                "tote-1",
                logicalState(
                        "order-1", 1, OrderType.FULL_PACK, "sc-1", 999,
                        DspOrderLineType.FULL_PACK, p2pRoute()),
                1,
                OsrProcessingReleaseAvailability.AVAILABLE,
                Optional.empty());
        DspOperationalReleaseCandidate later = candidate(
                "tote-2",
                logicalState(
                        "order-2", 1, OrderType.FULL_PACK, "sc-2", 998,
                        DspOrderLineType.FULL_PACK, p2pRoute()),
                2,
                OsrProcessingReleaseAvailability.AVAILABLE,
                Optional.empty());
        P2pLineLeaseSnapshot line = leasedLine("line-1", "sc-2", Optional.empty());
        P2pLineLeaseCatalogSnapshot catalog = new P2pLineLeaseCatalogSnapshot(List.of(line));
        P2pElasticAllocationSnapshot allocation = elasticAllocation(
                catalog,
                List.of(
                        demand("sc-1", 999, 0, List.of(), List.of()),
                        demand("sc-2", 998, 1, List.of("line-1"), List.of())));
        DspOperationalReleaseSnapshot snapshot = elasticSnapshot(
                List.of(earlier, later),
                Map.of(StationType.P2P, openAdmission(StationType.P2P, "target-line-1")),
                Set.of(),
                catalog,
                Map.of(line.definition().destination(), true),
                allocation);

        DspOperationalReleaseEvaluation evaluation = elasticScheduler().evaluate(snapshot);

        assertEquals(new PhysicalToteId("tote-2"),
                evaluation.releaseDecision().orElseThrow().command().physicalToteId());
        assertEquals(new PhysicalToteId("tote-1"),
                evaluation.blockedCandidates().getFirst().physicalToteId());
    }

    @Test
    void shouldLeaveNonP2pCandidateIndependentOfElasticBudget() {
        DspOperationalReleaseCandidate adapted = candidate(
                "adapted-tote",
                logicalState(
                        "adapted-order", 1, OrderType.ADAPTED, "sc-1", 999,
                        DspOrderLineType.ADAPTED,
                        new RouteRequirements(
                                false, true, false, false, false, StartLocation.OSR)),
                1,
                OsrProcessingReleaseAvailability.AVAILABLE,
                Optional.empty());
        P2pLineLeaseSnapshot line = new P2pLineLeaseSnapshot(
                new P2pLineDefinition(
                        new P2pLineId("line-1"),
                        new OperationalRouteDestination(StationType.P2P, "target-line-1")),
                Optional.empty(),
                P2pLineActivitySnapshot.idle(),
                List.of());
        P2pLineLeaseCatalogSnapshot catalog = new P2pLineLeaseCatalogSnapshot(List.of(line));
        P2pElasticAllocationSnapshot allocation = elasticAllocation(catalog, List.of());
        DspOperationalReleaseSnapshot snapshot = elasticSnapshot(
                List.of(adapted),
                Map.of(StationType.ADAPTING,
                        openAdmission(StationType.ADAPTING, "adapting-1")),
                Set.of(),
                catalog,
                Map.of(line.definition().destination(), false),
                allocation);

        DspOperationalReleaseDecision decision = elasticScheduler().evaluate(snapshot)
                .releaseDecision().orElseThrow();

        assertEquals(StationType.ADAPTING, decision.routeEntry().stationType());
        assertTrue(decision.command().proposedP2pAssignment().isEmpty());
    }

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

    private static DspOperationalReleaseSnapshot stickySnapshot(
            List<DspOperationalReleaseCandidate> candidates,
            Map<StationType, StationAdmissionSnapshot> stationAdmissions,
            Set<PreparedLineKey> preparedLineKeys,
            P2pLineLeaseCatalogSnapshot lineLeases,
            Map<OperationalRouteDestination, Boolean> targetAdmissions) {
        List<ServiceCentrePharmacyGroup> groups = new java.util.ArrayList<>();
        Map<String, Integer> groupIndexByServiceCentre = new java.util.LinkedHashMap<>();
        for (DspOperationalReleaseCandidate candidate : candidates) {
            for (String pharmacyId : candidate.pharmacyIds()) {
                boolean exists = groups.stream().anyMatch(group ->
                        group.serviceCentreId().equals(candidate.physicalCandidate().serviceCentreId())
                                && group.pharmacyId().equals(pharmacyId));
                if (!exists) {
                    String serviceCentreId = candidate.physicalCandidate().serviceCentreId();
                    int groupIndex = groupIndexByServiceCentre.getOrDefault(serviceCentreId, 0);
                    groups.add(new ServiceCentrePharmacyGroup(
                            serviceCentreId, pharmacyId, groupIndex,
                            candidate.physicalCandidate().sourceSequenceNumber()));
                    groupIndexByServiceCentre.put(serviceCentreId, groupIndex + 1);
                }
            }
        }
        OperationalRouteEntrySelector selector = new OperationalRouteEntrySelector();
        List<OperationalCandidateRouteAdmission> routeAdmissions = candidates.stream()
                .map(candidate -> selector.firstStation(
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
                routeAdmissions,
                lineLeases,
                targetAdmissions);
    }

    private static DspOperationalReleaseSnapshot elasticSnapshot(
            List<DspOperationalReleaseCandidate> candidates,
            Map<StationType, StationAdmissionSnapshot> stationAdmissions,
            Set<PreparedLineKey> preparedLineKeys,
            P2pLineLeaseCatalogSnapshot lineLeases,
            Map<OperationalRouteDestination, Boolean> targetAdmissions,
            P2pElasticAllocationSnapshot allocation) {
        DspOperationalReleaseSnapshot sticky = stickySnapshot(
                candidates,
                stationAdmissions,
                preparedLineKeys,
                lineLeases,
                targetAdmissions);
        return new DspOperationalReleaseSnapshot(
                sticky.candidates(),
                sticky.pharmacyGroups(),
                sticky.stationAdmissions(),
                sticky.preparedLineKeys(),
                sticky.routeAdmissions(),
                sticky.p2pLineLeases(),
                sticky.p2pRouteAdmissions(),
                Optional.of(allocation));
    }

    private static DspOperationalReleaseScheduler elasticScheduler() {
        return new DspOperationalReleaseScheduler(
                new OperationalDependencyReadinessPolicy(),
                new OperationalRouteEntryAdmissionPolicy(),
                new PharmacyGroupedSourceSequenceRankingPolicy(),
                new DeadlineAwareElasticStickyP2pLineAllocationPolicy());
    }

    private static P2pElasticAllocationSnapshot elasticAllocation(
            P2pLineLeaseCatalogSnapshot catalog,
            List<P2pServiceCentreLineDemandSnapshot> demands) {
        return new P2pElasticAllocationSnapshot(
                P2pElasticAllocationSnapshot.DEADLINE_AWARE_ELASTIC_STICKY_LEASES,
                P2pElasticAllocationCalibrationStatus.UNCALIBRATED,
                LocalDateTime.of(2026, 8, 24, 6, 0),
                catalog.lines().stream().map(line -> line.definition().lineId()).toList(),
                1,
                demands,
                List.of());
    }

    private static P2pServiceCentreLineDemandSnapshot demand(
            String serviceCentreId,
            int priority,
            int desiredLines,
            List<String> feedingLineIds,
            List<String> drainingLineIds) {
        LocalDateTime evaluatedAt = LocalDateTime.of(2026, 8, 24, 6, 0);
        List<P2pLineId> feeding = feedingLineIds.stream().map(P2pLineId::new).toList();
        List<P2pLineId> draining = drainingLineIds.stream().map(P2pLineId::new).toList();
        P2pServiceCentreWorkloadSnapshot workload = new P2pServiceCentreWorkloadSnapshot(
                serviceCentreId,
                List.of(new PhysicalToteId("remaining-" + serviceCentreId)),
                0,
                List.of(),
                List.of(),
                Duration.ofHours(1));
        ServiceCentreDeadlineSnapshot deadline = new ServiceCentreDeadlineSnapshot(
                serviceCentreId,
                "Centre " + serviceCentreId,
                priority,
                evaluatedAt,
                evaluatedAt.plusHours(11),
                evaluatedAt.plusHours(10),
                evaluatedAt.plusHours(10),
                evaluatedAt.plusHours(10),
                Duration.ofHours(10),
                false,
                false);
        return new P2pServiceCentreLineDemandSnapshot(
                serviceCentreId,
                priority,
                Duration.ZERO,
                deadline,
                workload,
                Duration.ofHours(1),
                1,
                1,
                desiredLines,
                feeding,
                draining,
                Math.max(0, desiredLines - feeding.size()),
                Math.max(0, 1 - desiredLines),
                true,
                List.of());
    }

    private static P2pLineLeaseSnapshot leasedLine(
            String lineId,
            String serviceCentreId,
            Optional<String> activePharmacyId) {
        P2pLineDefinition definition = new P2pLineDefinition(
                new P2pLineId(lineId),
                new OperationalRouteDestination(StationType.P2P, "target-" + lineId));
        P2pLineActivitySnapshot activity = activePharmacyId
                .map(pharmacyId -> new P2pLineActivitySnapshot(
                        P2pInputActivitySnapshot.idle(),
                        P2pPackPathActivitySnapshot.idle(),
                        P2pBaggingActivitySnapshot.idle(),
                        Optional.of(new OutboundToteSnapshot(
                                new PhysicalToteId("outbound-" + lineId),
                                definition.lineId(),
                                Optional.of(serviceCentreId),
                                Optional.of(pharmacyId),
                                10,
                                List.of(),
                                Optional.empty()))))
                .orElseGet(P2pLineActivitySnapshot::idle);
        return new P2pLineLeaseSnapshot(
                definition, Optional.of(serviceCentreId), activity, List.of());
    }

    private static DspOperationalReleaseCandidate candidateWithPharmacy(
            String physicalToteId,
            DspSchedulerOrderState logicalState,
            long sourceSequenceNumber,
            String pharmacyId) {
        OsrProcessingReleaseCandidate physicalCandidate = new OsrProcessingReleaseCandidate(
                new PhysicalToteId(physicalToteId),
                logicalState.order().orderSheetKey(),
                logicalState.order().orderType(),
                logicalState.order().serviceCentreId(),
                sourceSequenceNumber,
                OsrProcessingReleaseAvailability.AVAILABLE,
                Optional.empty());
        return new DspOperationalReleaseCandidate(
                physicalCandidate, logicalState, List.of(pharmacyId));
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
