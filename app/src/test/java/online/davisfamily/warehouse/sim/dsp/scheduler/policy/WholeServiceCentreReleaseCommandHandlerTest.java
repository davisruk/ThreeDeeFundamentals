package online.davisfamily.warehouse.sim.dsp.scheduler.policy;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.adapting.MapBackedToteLoadPlanRegistry;
import online.davisfamily.warehouse.sim.dsp.av02.*;
import online.davisfamily.warehouse.sim.dsp.io.DspDatasetLoadReport;
import online.davisfamily.warehouse.sim.dsp.io.LoadedDspData;
import online.davisfamily.warehouse.sim.dsp.lifecycle.*;
import online.davisfamily.warehouse.sim.dsp.model.*;
import online.davisfamily.warehouse.sim.dsp.osr.*;
import online.davisfamily.warehouse.sim.dsp.osr.release.*;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.*;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.allocation.*;
import online.davisfamily.warehouse.sim.dsp.p2p.bag.*;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.*;
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;
import online.davisfamily.warehouse.sim.dsp.runtime.SchedulerCommandApplicationResult;
import online.davisfamily.warehouse.sim.dsp.runtime.operational.CompositeOperationalCommandHandler;
import online.davisfamily.warehouse.sim.dsp.schedule.*;
import online.davisfamily.warehouse.sim.dsp.scheduler.SchedulerCommand;
import online.davisfamily.warehouse.sim.dsp.time.*;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

class WholeServiceCentreReleaseCommandHandlerTest {
    @Test
    void shouldGuardFutureDuplicateAndOldCommandsBeforeAnyTargetOrStateMutation() {
        var fixture = new Fixture();
        fixture.assertHeld(fixture.futureCommand);
        fixture.available = List.of();
        fixture.assertHeld(fixture.osrCommand);
        fixture.available = fixture.lineIds;
        var wrongDestination = new ReleasePhysicalToteFromOsrCommand(
                fixture.osrCommand.physicalToteId(), fixture.osrCommand.orderSheetKey(), "104", "p2p-1",
                Optional.of(new P2pPhysicalToteAssignment(fixture.osrCommand.physicalToteId(), "104",
                        fixture.lineIds.getFirst(), new OperationalRouteDestination(StationType.P2P, "wrong"))));
        fixture.assertHeld(wrongDestination);
        fixture.assertHeld(new SchedulerCommand() { });

        assertTrue(fixture.handler.apply(fixture.osrCommand).applied());
        assertEquals(1, fixture.releases.snapshot().version());
        assertEquals(1, fixture.releases.snapshot().committedToteCount("104", fixture.lineIds.getFirst()));
        assertEquals(Optional.of("104"), fixture.releases.snapshot().releaseServiceCentreId());
        fixture.assertHeld(fixture.osrCommand); // Duplicate while EMPTY still holds the cursor.
        fixture.assertHeld(fixture.futureCommand);
        assertTrue(fixture.handler.apply(fixture.av02Command).applied());
        assertEquals(2, fixture.releases.snapshot().version());
        assertEquals(Optional.of("108"), fixture.releases.snapshot().releaseServiceCentreId());
        assertEquals(1, fixture.releases.snapshot().committedToteCount("104", fixture.lineIds.get(1)));
        fixture.assertHeld(fixture.osrCommand); // Stale old owner after cursor advancement.
        fixture.assertHeld(fixture.av02Command);
        assertEquals(2, fixture.targetCalls);
        assertEquals(2, fixture.correlations.snapshot().assignments().size());
        assertEquals(1, fixture.osr.snapshot().departedTotes().size());
        assertEquals(1, fixture.av02.snapshot().departedTotes().size());
    }

    @Test
    void shouldLeaveEveryLocalStateUnchangedWhenEitherRealDelegateDefersRejectsOrThrows() {
        for (boolean empty : List.of(false, true)) {
            var fixture = new Fixture();
            SchedulerCommand command = empty ? fixture.av02Command : fixture.osrCommand;
            for (var result : List.of(SchedulerCommandApplicationResult.deferredResult("full"),
                    SchedulerCommandApplicationResult.rejectedResult("invalid"))) {
                fixture.targetResult = result;
                var before = fixture.state();
                assertSame(result, fixture.handler.apply(command));
                assertEquals(before, fixture.state());
            }
            var failure = new IllegalStateException("downstream invariant");
            fixture.targetFailure = failure;
            var before = fixture.state();
            assertSame(failure, assertThrows(IllegalStateException.class, () -> fixture.handler.apply(command)));
            assertEquals(before, fixture.state());
            assertEquals(3, fixture.targetCalls);
        }
    }

    @Test
    void shouldRequireFreshSharedPolicyAndPropagatePostAcceptanceFailuresWithoutInventingRollback() {
        var fixture = new Fixture();
        var captured = fixture.allocation();
        var stale = new WholeServiceCentreReleaseCommandHandler(command -> fail("stale delegate"),
                fixture.releases, () -> captured);
        assertTrue(fixture.handler.apply(fixture.osrCommand).applied());
        assertThrows(IllegalStateException.class, () -> stale.apply(fixture.av02Command));
        assertThrows(IllegalArgumentException.class, () -> fixture.handler.apply(null));
        assertThrows(IllegalArgumentException.class, () -> new WholeServiceCentreReleaseCommandHandler(
                null, fixture.releases, fixture::allocation));
        var failure = new IllegalStateException("commit failed after external acceptance");
        var broken = new WholeServiceCentreReleaseCommandHandler(command -> { throw failure; },
                fixture.releases, fixture::allocation);
        var before = fixture.releases.snapshot();
        assertSame(failure, assertThrows(IllegalStateException.class, () -> broken.apply(fixture.av02Command)));
        assertSame(before, fixture.releases.snapshot());
    }

    @Test
    void shouldDeferAStaleP2pProposalAtTheLiveWatermarkAndAllowItAfterTipperCompletion() {
        var fixture = new Fixture(1);
        var captured = fixture.allocation();
        var staleProposal = fixture.av02CommandFor(captured);
        assertEquals(fixture.lineIds.getFirst(), staleProposal.proposedP2pAssignment().orElseThrow().lineId());
        assertEquals(0, captured.wholeServiceCentrePolicy().orElseThrow().releases()
                .outstandingToteCount(fixture.lineIds.getFirst()));

        assertTrue(fixture.handler.apply(fixture.osrCommand).applied());
        var beforeDeferredAttempt = fixture.state();
        int targetCallsBeforeDeferredAttempt = fixture.targetCalls;

        var deferred = fixture.handler.apply(staleProposal);

        assertTrue(deferred.deferred());
        assertTrue(deferred.reason().startsWith("OUTSTANDING_TOTE_WATERMARK:"));
        assertTrue(deferred.reason().contains("1 outstanding totes (limit 1)"));
        assertEquals(targetCallsBeforeDeferredAttempt, fixture.targetCalls);
        assertEquals(beforeDeferredAttempt, fixture.state());

        fixture.releases.recordTippingCompleted(fixture.osrCommand.physicalToteId(), fixture.lineIds.getFirst());
        assertTrue(fixture.handler.apply(staleProposal).applied());
        assertEquals(targetCallsBeforeDeferredAttempt + 1, fixture.targetCalls);
        assertEquals(1, fixture.releases.snapshot().outstandingToteCount(fixture.lineIds.getFirst()));
        assertEquals(0, fixture.releases.snapshot().outstandingToteCount(fixture.lineIds.get(1)));
    }

    @Test
    void shouldNotApplyTheP2pWatermarkToCurrentCentreNonP2pReleases() {
        var fixture = new Fixture(1);
        int[] delegateCalls = {0};
        var nonP2pHandler = new WholeServiceCentreReleaseCommandHandler(command -> {
            delegateCalls[0]++;
            return SchedulerCommandApplicationResult.appliedResult();
        }, fixture.releases, fixture::allocation);
        var nonP2pCommand = new ReleasePhysicalToteFromOsrCommand(
                fixture.osrCommand.physicalToteId(), fixture.osrCommand.orderSheetKey(), "104", "adapting",
                Optional.empty());

        assertTrue(nonP2pHandler.apply(nonP2pCommand).applied());

        assertEquals(1, delegateCalls[0]);
        assertEquals(0, fixture.releases.snapshot().outstandingToteCount(fixture.lineIds.getFirst()));
        assertEquals(0, fixture.releases.snapshot().committedToteCount("104", fixture.lineIds.getFirst()));
    }

    /** Unit boundary fixture: real inventory, lifecycle, lease and correlation committers. */
    private static final class Fixture {
        final List<P2pLineDefinition> lines = java.util.stream.IntStream.rangeClosed(1, 5)
                .mapToObj(i -> new P2pLineDefinition(new P2pLineId("line-" + i),
                        new OperationalRouteDestination(StationType.P2P, "p2p-" + i))).toList();
        final List<P2pLineId> lineIds = lines.stream().map(P2pLineDefinition::lineId).toList();
        List<P2pLineId> available = lineIds;
        final PhysicalToteLifecycleLedger lifecycle = new PhysicalToteLifecycleLedger();
        final OsrPhysicalInventory osr = new OsrPhysicalInventory(new OsrInventoryConfig(2, List.of()));
        final Av02PhysicalToteInventory av02 = new Av02PhysicalToteInventory(new Av02AllocationConfig(2));
        final P2pLineLeaseRegistry leases = new P2pLineLeaseRegistry(lines);
        final P2pBagCorrelationAssignmentRegistry correlations = new P2pBagCorrelationAssignmentRegistry();
        final Map<P2pLineId, P2pLineActivitySnapshot> activities = lines.stream().collect(
                java.util.stream.Collectors.toMap(P2pLineDefinition::lineId, ignored -> P2pLineActivitySnapshot.idle()));
        final WholeServiceCentreReleaseLedger releases;
        final WholeServiceCentreReleaseCommandHandler handler;
        final ReleasePhysicalToteFromOsrCommand osrCommand;
        final ReleasePhysicalToteFromOsrCommand futureCommand;
        final ReleasePhysicalToteFromAv02Command av02Command;
        SchedulerCommandApplicationResult targetResult = SchedulerCommandApplicationResult.appliedResult();
        RuntimeException targetFailure;
        int targetCalls;

        Fixture() {
            this(Integer.MAX_VALUE);
        }

        Fixture(int watermark) {
            var current = order("current", "104", OrderType.FULL_PACK);
            var empty = order("empty", "104", OrderType.EMPTY);
            var future = order("future", "108", OrderType.FULL_PACK);
            var currentManifest = manifest(current);
            var futureManifest = manifest(future);
            osr.storeAll(List.of(currentManifest, futureManifest));
            var inbound = new InboundToteLifecycleController(lifecycle,
                    new InboundToteManifestCatalog(List.of(currentManifest, futureManifest)));
            var physicalId = new PhysicalToteId("av02-000001");
            var physical = new Av02ToteLifecycleController(lifecycle, () -> physicalId)
                    .allocateFor(empty, Duration.ZERO, physicalId);
            av02.store(new Av02AllocatedTote(new OperationalPhysicalToteIdentity(
                    OperationalPhysicalToteSource.AV02, physicalId, empty.orderSheetKey(), OrderType.EMPTY,
                    "104", PhysicalToteRole.PRE_P2P, 1), physical, "pharmacy"));
            var loadPlans = new MapBackedToteLoadPlanRegistry();
            loadPlans.putLoadPlan(new ToteLoadPlan(physicalId, List.of()));
            var data = new LoadedDspData(List.of(), List.of(current, empty, future), List.of(),
                    Set.of(), Set.of(), List.of(currentManifest, futureManifest), DspDatasetLoadReport.empty());
            var timetable = new DspServiceCentreTimetable(List.of(schedule("104", 999), schedule("108", 998)));
            releases = new WholeServiceCentreReleaseLedger(data, timetable, lines, watermark);
            var requirements = new P2pBagCorrelationRequirementCatalog(
                    Map.of(currentManifest.physicalToteId(), List.of(new P2pBagCorrelationRequirement("current", 1)),
                            futureManifest.physicalToteId(), List.of(new P2pBagCorrelationRequirement("future", 1))),
                    Map.of(empty.orderSheetKey(), List.of(new P2pBagCorrelationRequirement("empty", 1))));
            var probes = lines.stream().collect(java.util.stream.Collectors.toMap(
                    P2pLineDefinition::lineId, line -> (P2pLineActivityProbe) P2pLineActivitySnapshot::idle));
            var strict = new StrictP2pReleaseAssignmentCommitter(leases,
                    sheet -> new RouteRequirements(false, false, false, true, false, StartLocation.OSR), probes);
            var committer = new BagCoherentOperationalP2pReleaseAssignmentCommitter(strict, requirements, correlations);
            var osrTargets = lines.stream().map(line -> (OsrProcessingReleaseTarget) new OsrProcessingReleaseTarget() {
                public String targetId() { return line.destination().targetId(); }
                public SchedulerCommandApplicationResult accept(OsrProcessingReleaseRequest request) { return acceptTarget(); }
            }).toList();
            var av02Targets = lines.stream().map(line -> (OperationalPhysicalToteReleaseTarget) new OperationalPhysicalToteReleaseTarget() {
                public String targetId() { return line.destination().targetId(); }
                public SchedulerCommandApplicationResult accept(OperationalPhysicalToteReleaseRequest request) { return acceptTarget(); }
            }).toList();
            var osrHandler = new OsrProcessingReleaseCommandHandler(osr, inbound, Fixture::clock,
                    new OsrProcessingReleaseTargetRegistry(osrTargets), committer);
            var av02Handler = new Av02OperationalCommandHandler(av02, lifecycle, loadPlans, Fixture::clock,
                    new OperationalPhysicalToteReleaseTargetRegistry(av02Targets), committer);
            handler = new WholeServiceCentreReleaseCommandHandler(
                    new CompositeOperationalCommandHandler(osrHandler, av02Handler), releases, this::allocation);
            osrCommand = osrCommand(currentManifest, lines.getFirst());
            futureCommand = osrCommand(futureManifest, lines.get(2));
            av02Command = new ReleasePhysicalToteFromAv02Command(physicalId, empty.orderSheetKey(), "104", "p2p-2",
                    Optional.of(new P2pPhysicalToteAssignment(physicalId, "104", lineIds.get(1), lines.get(1).destination())));
        }

        SchedulerCommandApplicationResult acceptTarget() {
            targetCalls++;
            if (targetFailure != null) { throw targetFailure; }
            return targetResult;
        }

        P2pElasticAllocationSnapshot allocation() {
            var cursor = releases.snapshot().releaseServiceCentreId();
            return new P2pElasticAllocationSnapshot(DspSchedulerPolicy.WHOLE_SERVICE_CENTRE_DRAINED_HANDOVER.name(),
                    P2pElasticAllocationCalibrationStatus.UNCALIBRATED, clock().businessDateTime(), lineIds, 1,
                    List.of(), List.of(), Optional.of(new WholeServiceCentrePolicySnapshot(releases.snapshot(), cursor, available)));
        }

        ReleasePhysicalToteFromAv02Command av02CommandFor(P2pElasticAllocationSnapshot allocation) {
            var lineCatalog = leases.snapshot(activities);
            var targetAdmissions = lines.stream().collect(java.util.stream.Collectors.toMap(
                    P2pLineDefinition::destination, ignored -> true));
            var decision = new WholeServiceCentreP2pLineAllocationPolicy().allocate(new P2pLineAllocationRequest(
                    av02Command.physicalToteId(), "104", List.of("pharmacy"), true, lineCatalog,
                    targetAdmissions, Optional.of(allocation)));
            var assignment = decision.assignment().orElseThrow();
            return new ReleasePhysicalToteFromAv02Command(av02Command.physicalToteId(),
                    av02Command.orderSheetKey(), "104", assignment.destination().targetId(), Optional.of(assignment));
        }

        List<Object> state() {
            return List.of(releases.snapshot(), osr.snapshot(), av02.snapshot(), lifecycle.snapshot(),
                    leases.snapshot(activities), correlations.snapshot());
        }

        void assertHeld(SchedulerCommand command) {
            var before = state();
            int calls = targetCalls;
            assertFalse(handler.apply(command).applied());
            assertEquals(calls, targetCalls);
            assertEquals(before, state());
        }

        static ReleasePhysicalToteFromOsrCommand osrCommand(InboundToteManifest manifest, P2pLineDefinition line) {
            return new ReleasePhysicalToteFromOsrCommand(manifest.physicalToteId(), manifest.orderSheetKey(),
                    manifest.serviceCentreId(), line.destination().targetId(), Optional.of(new P2pPhysicalToteAssignment(
                            manifest.physicalToteId(), manifest.serviceCentreId(), line.lineId(), line.destination())));
        }

        static NotionalToteOrder order(String id, String centre, OrderType type) {
            return new NotionalToteOrder(id, id, centre, 1, type, List.of(new DspOrderItem("line", "product", 1,
                    "pharmacy", "patient", id, DspOrderLineType.FULL_PACK, id, 1, 1)), centre.equals("104") ? 999 : 998, 0);
        }

        static InboundToteManifest manifest(NotionalToteOrder order) {
            return new InboundToteManifest(new PhysicalToteId(order.orderId()), order.orderSheetKey(), order.orderType(),
                    order.serviceCentreId(), order.items(), order.sequenceNumber());
        }

        static ServiceCentreSchedule schedule(String id, int priority) {
            return new ServiceCentreSchedule(id, id, priority, OperationalDayTime.day0(LocalTime.of(17, 0)));
        }

        static DspOperationalClockSnapshot clock() {
            var date = LocalDate.of(2026, 9, 2);
            return new DspOperationalClockSnapshot(Duration.ZERO, date, date.atTime(6, 0),
                    OperationalDayTime.day0(LocalTime.of(6, 0)), DspOperatingPhase.NORMAL_OPERATIONS,
                    date.atTime(22, 0), date.plusDays(1).atStartOfDay());
        }
    }
}
