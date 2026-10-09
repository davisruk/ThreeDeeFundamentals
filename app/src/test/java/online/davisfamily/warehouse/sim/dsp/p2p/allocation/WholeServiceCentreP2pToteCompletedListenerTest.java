package online.davisfamily.warehouse.sim.dsp.p2p.allocation;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.behaviour.routing.RouteFollower;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.matrices.Vec4;
import online.davisfamily.threedee.model.Mesh;
import online.davisfamily.threedee.path.LinearSegment3;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.warehouse.sim.dsp.av02.ReleasePhysicalToteFromAv02Command;
import online.davisfamily.warehouse.sim.dsp.io.DspDatasetLoadReport;
import online.davisfamily.warehouse.sim.dsp.io.LoadedDspData;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteLifecycleController;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifestCatalog;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleLedger;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleState;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseRequest;
import online.davisfamily.warehouse.sim.dsp.osr.release.ReleasePhysicalToteFromOsrCommand;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteLaunchRequestFactory;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.arrival.P2pTipperArrivalTarget;
import online.davisfamily.warehouse.sim.dsp.p2p.arrival.StationProcessingP2pToteCompletedListener;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineDefinition;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.InboundLifecycleP2pToteCompletedListener;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pPhysicalToteAssignment;
import online.davisfamily.warehouse.sim.dsp.schedule.DspServiceCentreTimetable;
import online.davisfamily.warehouse.sim.dsp.schedule.ServiceCentreSchedule;
import online.davisfamily.warehouse.sim.dsp.scheduler.policy.WholeServiceCentreReleaseLedger;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingCoordinator;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingDispositionType;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.tote.Tote.ToteMotionState;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperInputQueue;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperTotePayload;
import online.davisfamily.warehouse.sim.totebag.control.TipperToteCompletedListener;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;
import online.davisfamily.warehouse.sim.dsp.time.OperationalDayTime;

class WholeServiceCentreP2pToteCompletedListenerTest {
    @Test
    void shouldDelegateBeforeReleasingTheCommittedOsrToteCount() {
        var fixture = new Fixture();
        var routedTote = fixture.routedOsrTote();
        var delegateCalls = new AtomicInteger();
        var listener = new WholeServiceCentreP2pToteCompletedListener(
                fixture.osrLine.lineId(), fixture.ledger, (tote, context) -> {
                    delegateCalls.incrementAndGet();
                    assertSame(routedTote.tote(), tote);
                    assertEquals(1, fixture.ledger.snapshot().outstandingToteCount(fixture.osrLine.lineId()));
                });

        listener.onToteCompleted(routedTote.tote(), context(2.5d));

        assertEquals(1, delegateCalls.get());
        assertEquals(0, fixture.ledger.snapshot().outstandingToteCount(fixture.osrLine.lineId()));
        assertEquals(1, fixture.ledger.snapshot().outstandingToteCount(fixture.av02Line.lineId()));
        assertEquals(3, fixture.ledger.snapshot().outstandingVersion());
        assertEquals(2, fixture.ledger.snapshot().version());
    }

    @Test
    void shouldDecrementGeneratedAv02AssignmentOnlyAfterItsDelegateReturns() {
        var fixture = new Fixture();
        var tote = tote(fixture.av02PhysicalToteId.value());
        var listener = new WholeServiceCentreP2pToteCompletedListener(
                fixture.av02Line.lineId(), fixture.ledger, (completedTote, context) -> {
                    assertSame(tote, completedTote);
                    assertEquals(1, fixture.ledger.snapshot().outstandingToteCount(fixture.av02Line.lineId()));
                });

        listener.onToteCompleted(tote, context(3d));

        assertEquals(0, fixture.ledger.snapshot().outstandingToteCount(fixture.av02Line.lineId()));
        assertEquals(1, fixture.ledger.snapshot().outstandingToteCount(fixture.osrLine.lineId()));
        assertEquals(3, fixture.ledger.snapshot().outstandingVersion());
    }

    @Test
    void shouldRejectInvalidAndDuplicateCallbacksBeforeCallingTheDelegate() {
        var fixture = new Fixture();
        var delegateCalls = new AtomicInteger();
        var listener = new WholeServiceCentreP2pToteCompletedListener(
                fixture.osrLine.lineId(), fixture.ledger, (tote, context) -> delegateCalls.incrementAndGet());
        var context = context(4d);
        var before = fixture.ledger.snapshot();

        assertThrows(IllegalArgumentException.class,
                () -> listener.onToteCompleted(tote("not-committed"), context));
        var wrongLineListener = new WholeServiceCentreP2pToteCompletedListener(
                fixture.av02Line.lineId(), fixture.ledger, (tote, callbackContext) -> delegateCalls.incrementAndGet());
        assertThrows(IllegalArgumentException.class,
                () -> wrongLineListener.onToteCompleted(tote(fixture.osrPhysicalToteId.value()), context));
        assertThrows(IllegalArgumentException.class, () -> listener.onToteCompleted(null, context));
        assertThrows(IllegalArgumentException.class, () -> listener.onToteCompleted(tote("osr-tote"), null));
        assertEquals(0, delegateCalls.get());
        assertSame(before, fixture.ledger.snapshot());

        var validTote = fixture.routedOsrTote().tote();
        listener.onToteCompleted(validTote, context);
        var afterCompletion = fixture.ledger.snapshot();
        assertEquals(1, delegateCalls.get());
        assertThrows(IllegalArgumentException.class, () -> listener.onToteCompleted(validTote, context));
        assertEquals(1, delegateCalls.get());
        assertSame(afterCompletion, fixture.ledger.snapshot());
    }

    @Test
    void shouldRejectNullCollaboratorsAndUnconfiguredLines() {
        var fixture = new Fixture();
        assertThrows(IllegalArgumentException.class, () -> new WholeServiceCentreP2pToteCompletedListener(
                null, fixture.ledger, TipperToteCompletedListener.NO_OP));
        assertThrows(IllegalArgumentException.class, () -> new WholeServiceCentreP2pToteCompletedListener(
                fixture.osrLine.lineId(), null, TipperToteCompletedListener.NO_OP));
        assertThrows(IllegalArgumentException.class, () -> new WholeServiceCentreP2pToteCompletedListener(
                fixture.osrLine.lineId(), fixture.ledger, null));
        assertThrows(IllegalArgumentException.class, () -> new WholeServiceCentreP2pToteCompletedListener(
                new P2pLineId("unconfigured"), fixture.ledger, TipperToteCompletedListener.NO_OP));
    }

    @Test
    void shouldLeaveTheLedgerUnchangedWhenTheExistingCompletionDelegateFails() {
        var fixture = new Fixture();
        var failure = new IllegalStateException("station completion failed");
        var listener = new WholeServiceCentreP2pToteCompletedListener(
                fixture.osrLine.lineId(), fixture.ledger, (tote, context) -> { throw failure; });
        var before = fixture.ledger.snapshot();

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> listener.onToteCompleted(fixture.routedOsrTote().tote(), context(5d))));

        assertSame(before, fixture.ledger.snapshot());
        assertEquals(1, before.outstandingToteCount(fixture.osrLine.lineId()));
        assertEquals(2, before.outstandingVersion());
    }

    @Test
    void shouldKeepTheCountOutstandingWhenTheToteIsOnlyAcceptedAtTipperInput() {
        var fixture = new Fixture();
        var routedTote = fixture.routedOsrTote();
        var inputQueue = new TipperInputQueue("line-1-input", 2);
        var target = new P2pTipperArrivalTarget(fixture.osrLine.destination(), inputQueue);
        var before = fixture.ledger.snapshot();

        target.accept(routedTote, new TipperTotePayload(
                routedTote.tote(), routedTote.renderable(), 0f, Map.of()));

        assertTrue(target.hasAccepted(fixture.osrPhysicalToteId));
        assertSame(before, fixture.ledger.snapshot());
        assertEquals(1, fixture.ledger.snapshot().outstandingToteCount(fixture.osrLine.lineId()));
    }

    @Test
    void shouldCompleteTheExactStationClaimAndLifecycleBeforeReleasingTheCount() {
        var fixture = new Fixture();
        var routedTote = fixture.routedOsrTote();
        var wrongToteInstance = fixture.routedOsrTote().tote();
        var lifecycleLedger = new PhysicalToteLifecycleLedger();
        var inboundLifecycle = new InboundToteLifecycleController(
                lifecycleLedger, new InboundToteManifestCatalog(List.of(fixture.osrManifest)));
        inboundLifecycle.activate(fixture.osrPhysicalToteId, Duration.ZERO);
        var coordinator = new StationProcessingCoordinator();
        coordinator.claim(routedTote, Duration.ZERO);
        var lifecycleListener = new InboundLifecycleP2pToteCompletedListener(inboundLifecycle);
        var stationListener = new StationProcessingP2pToteCompletedListener(lifecycleListener, coordinator);
        var delegateCalls = new AtomicInteger();
        var completionOrder = new ArrayList<String>();
        var listener = new WholeServiceCentreP2pToteCompletedListener(
                fixture.osrLine.lineId(), fixture.ledger, (tote, context) -> {
                    delegateCalls.incrementAndGet();
                    stationListener.onToteCompleted(tote, context);
                    assertEquals(PhysicalToteLifecycleState.CONSUMED_AT_P2P,
                            inboundLifecycle.snapshot().totes().get(fixture.osrPhysicalToteId).state());
                    assertTrue(coordinator.snapshot().activeClaims().isEmpty());
                    assertEquals(StationProcessingDispositionType.CONSUME,
                            coordinator.snapshot().pendingDispositions().getFirst().type());
                    assertEquals(1, fixture.ledger.snapshot().outstandingToteCount(fixture.osrLine.lineId()));
                    completionOrder.add("lifecycle-and-station-complete");
                });
        var beforeLedger = fixture.ledger.snapshot();
        var beforeLifecycle = inboundLifecycle.snapshot();
        var beforeCoordinator = coordinator.snapshot();

        assertThrows(IllegalStateException.class,
                () -> listener.onToteCompleted(wrongToteInstance, context(6d)));
        assertEquals(1, delegateCalls.get());
        assertSame(beforeLedger, fixture.ledger.snapshot());
        assertEquals(beforeLifecycle, inboundLifecycle.snapshot());
        assertEquals(beforeCoordinator, coordinator.snapshot());

        listener.onToteCompleted(routedTote.tote(), context(6d));
        completionOrder.add("watermark-count-released");

        assertEquals(List.of("lifecycle-and-station-complete", "watermark-count-released"), completionOrder);
        assertEquals(2, delegateCalls.get());
        assertEquals(0, fixture.ledger.snapshot().outstandingToteCount(fixture.osrLine.lineId()));
        assertEquals(PhysicalToteLifecycleState.CONSUMED_AT_P2P,
                inboundLifecycle.snapshot().totes().get(fixture.osrPhysicalToteId).state());
        assertTrue(coordinator.snapshot().activeClaims().isEmpty());
        assertEquals(StationProcessingDispositionType.CONSUME,
                coordinator.snapshot().pendingDispositions().getFirst().type());
    }

    private static SimulationContext context(double seconds) {
        var context = new SimulationContext();
        context.setSimulationTimeSeconds(seconds);
        return context;
    }

    private static Tote tote(String id) {
        RouteSegment terminal = terminalSegment(id);
        var tote = new Tote(id, new RouteFollower(id, terminal, 0f, 1d), renderable(id), new Vec3(), 0f);
        tote.setInteractionMode(ToteMotionState.HELD);
        return tote;
    }

    private static RouteSegment terminalSegment(String id) {
        return new RouteSegment(id + "-terminal", new LinearSegment3(
                new Vec3(0f, 0f, 0f), new Vec3(1f, 0f, 0f), false));
    }

    private static RenderableObject renderable(String id) {
        return RenderableObject.create(id, null, new Mesh(
                new Vec4[] {
                        new Vec4(0f, 0f, 0f, 1f),
                        new Vec4(0f, 0f, 0f, 1f),
                        new Vec4(0f, 0f, 0f, 1f)
                }, new int[][] {{0, 1, 2}}, "anchor"),
                new Mat4.ObjectTransformation(0f, 0f, 0f, 0f, 0f, 0f, new Mat4()),
                triangleIndex -> 0, false);
    }

    private static NotionalToteOrder order(String id, int sheetNumber, OrderType orderType) {
        var item = new DspOrderItem(id + "-line", "product", 1, "pharmacy", "patient", "rx-" + id,
                DspOrderLineType.FULL_PACK, id, sheetNumber, 1);
        return new NotionalToteOrder(id, "notional-" + id, "104", sheetNumber, orderType,
                List.of(item), 999, 0);
    }

    private static InboundToteManifest manifest(PhysicalToteId physicalToteId, NotionalToteOrder order) {
        return new InboundToteManifest(physicalToteId, order.orderSheetKey(), order.orderType(),
                order.serviceCentreId(), order.items(), order.sequenceNumber());
    }

    private static P2pPhysicalToteAssignment assignment(PhysicalToteId physicalToteId,
            P2pLineDefinition line) {
        return new P2pPhysicalToteAssignment(physicalToteId, "104", line.lineId(), line.destination());
    }

    private static P2pLineDefinition line(String lineId, String targetId) {
        return new P2pLineDefinition(new P2pLineId(lineId),
                new OperationalRouteDestination(StationType.P2P, targetId));
    }

    private static final class Fixture {
        final P2pLineDefinition osrLine = line("line-1", "p2p-1");
        final P2pLineDefinition av02Line = line("line-2", "p2p-2");
        final PhysicalToteId osrPhysicalToteId = new PhysicalToteId("osr-tote");
        final PhysicalToteId av02PhysicalToteId = new PhysicalToteId("av02-generated-1");
        final NotionalToteOrder fullPackOrder = order("full-pack", 1, OrderType.FULL_PACK);
        final NotionalToteOrder emptyOrder = order("empty", 1, OrderType.EMPTY);
        final InboundToteManifest osrManifest = manifest(osrPhysicalToteId, fullPackOrder);
        final WholeServiceCentreReleaseLedger ledger;

        Fixture() {
            var data = new LoadedDspData(List.of(), List.of(fullPackOrder, emptyOrder), List.of(),
                    Set.of(), Set.of(), List.of(osrManifest), DspDatasetLoadReport.empty());
            var timetable = new DspServiceCentreTimetable(List.of(new ServiceCentreSchedule(
                    "104", "104", 999, OperationalDayTime.day0(LocalTime.of(17, 0)))));
            ledger = new WholeServiceCentreReleaseLedger(data, timetable, List.of(osrLine, av02Line), 2);

            var osrAssignment = assignment(osrPhysicalToteId, osrLine);
            var osrCommand = new ReleasePhysicalToteFromOsrCommand(osrPhysicalToteId,
                    fullPackOrder.orderSheetKey(), "104", osrLine.destination().targetId(), Optional.of(osrAssignment));
            ledger.validateUnreleased(osrCommand);
            ledger.recordApplied(osrCommand);

            var av02Assignment = assignment(av02PhysicalToteId, av02Line);
            var av02Command = new ReleasePhysicalToteFromAv02Command(av02PhysicalToteId,
                    emptyOrder.orderSheetKey(), "104", av02Line.destination().targetId(), Optional.of(av02Assignment));
            ledger.validateUnreleased(av02Command);
            ledger.recordApplied(av02Command);
        }

        RoutedPhysicalTote routedOsrTote() {
            RouteSegment terminal = terminalSegment(osrPhysicalToteId.value());
            var renderable = renderable(osrPhysicalToteId.value());
            var tote = new Tote(osrPhysicalToteId.value(),
                    new RouteFollower(osrPhysicalToteId.value(), terminal, 0f, 1d), renderable, new Vec3(), 0f);
            tote.setInteractionMode(ToteMotionState.HELD);
            var request = OperationalRouteLaunchRequestFactory.fromOsr(
                    new OsrProcessingReleaseRequest(osrManifest, Duration.ZERO,
                            Optional.of(assignment(osrPhysicalToteId, osrLine))), osrLine.destination());
            return new RoutedPhysicalTote(request, new ToteLoadPlan(osrPhysicalToteId, List.of()), tote, renderable);
        }
    }
}
