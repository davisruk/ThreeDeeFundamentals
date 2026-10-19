package online.davisfamily.warehouse.sim.dsp.p2p.allocation;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.LocalTime;
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
import online.davisfamily.warehouse.sim.dsp.bagging.BagKey;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedBag;
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
import online.davisfamily.warehouse.sim.dsp.model.StartLocation;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseRequest;
import online.davisfamily.warehouse.sim.dsp.osr.release.ReleasePhysicalToteFromOsrCommand;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteLaunchRequestFactory;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.arrival.P2pTipperArrivalTarget;
import online.davisfamily.warehouse.sim.dsp.p2p.arrival.StationProcessingP2pToteCompletedListener;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.InboundLifecycleP2pToteCompletedListener;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineDefinition;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pPhysicalToteAssignment;
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;
import online.davisfamily.warehouse.sim.dsp.runtime.operational.DspOperationalWorkLedger;
import online.davisfamily.warehouse.sim.dsp.schedule.DspServiceCentreTimetable;
import online.davisfamily.warehouse.sim.dsp.schedule.ServiceCentreSchedule;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspOrderStatus;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;
import online.davisfamily.warehouse.sim.dsp.scheduler.policy.WholeServiceCentreReleaseLedger;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingCoordinator;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingDispositionType;
import online.davisfamily.warehouse.sim.dsp.time.OperationalDayTime;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperInputQueue;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperTotePayload;
import online.davisfamily.warehouse.sim.totebag.control.TipperToteCompletedListener;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

class OperationalWindowP2pToteCompletedListenerTest {
    @Test
    void shouldAccountOnlyAfterLifecycleStationAndWholeCentreWatermarkHaveCompleted() {
        Fixture f = new Fixture();
        RoutedPhysicalTote routed = f.routed();
        f.station.claim(routed, Duration.ZERO);
        var before = f.work.snapshot();
        AtomicInteger calls = new AtomicInteger();
        var whole = new WholeServiceCentreP2pToteCompletedListener(f.line.lineId(), f.releases, (tote, context) -> {
            assertSame(before, f.work.snapshot());
            f.stationListener().onToteCompleted(tote, context);
            assertEquals(PhysicalToteLifecycleState.CONSUMED_AT_P2P,
                    f.lifecycle.snapshot().totes().get(f.manifest.physicalToteId()).state());
            assertTrue(f.station.snapshot().activeClaims().isEmpty());
            assertEquals(StationProcessingDispositionType.CONSUME, f.station.peekDisposition().orElseThrow().type());
            assertEquals(1, f.releases.snapshot().outstandingToteCount(f.line.lineId()));
            assertSame(before, f.work.snapshot());
        });
        var listener = new OperationalWindowP2pToteCompletedListener(f.line.lineId(), f.work, (tote, context) -> {
            calls.incrementAndGet();
            whole.onToteCompleted(tote, context);
            assertEquals(0, f.releases.snapshot().outstandingToteCount(f.line.lineId()));
            assertSame(before, f.work.snapshot());
        });
        listener.onToteCompleted(routed.tote(), context());
        assertTrue(f.work.snapshot().remainingInboundTotes().isEmpty());
        assertEquals(1L, f.work.snapshot().totalsByLine().get(f.line.lineId()).consumedTotes());
        assertEquals(1L, f.work.snapshot().totalsByServiceCentre().get("104").consumedAtP2p());
        assertSame(f.bag, f.work.remainingBag(f.bag.bagKey()).orElseThrow());
        assertEquals(2, f.work.snapshot().unallocatedEmptyOrders().size());
        var after = f.work.snapshot();
        assertThrows(IllegalArgumentException.class, () -> listener.onToteCompleted(routed.tote(), context()));
        assertSame(after, f.work.snapshot());
        assertEquals(1, calls.get());
    }

    @Test
    void shouldNotConsumeWorkOnArrivalOrWhenTheExactStationClaimIsMissingOrWrong() {
        Fixture f = new Fixture();
        RoutedPhysicalTote routed = f.routed();
        var before = f.work.snapshot();
        var lifecycleBefore = f.lifecycle.snapshot();
        var releasesBefore = f.releases.snapshot();
        var target = new P2pTipperArrivalTarget(f.line.destination(), new TipperInputQueue("input", 2));
        target.accept(routed, new TipperTotePayload(routed.tote(), routed.renderable(), 0f, Map.of()));
        assertSame(before, f.work.snapshot());
        var listener = new OperationalWindowP2pToteCompletedListener(f.line.lineId(), f.work,
                new WholeServiceCentreP2pToteCompletedListener(f.line.lineId(), f.releases, f.stationListener()));
        assertThrows(IllegalStateException.class, () -> listener.onToteCompleted(routed.tote(), context()));
        assertSame(before, f.work.snapshot());
        f.station.claim(routed, Duration.ZERO);
        var stationBefore = f.station.snapshot();
        assertThrows(IllegalStateException.class, () -> listener.onToteCompleted(f.routed().tote(), context()));
        assertSame(before, f.work.snapshot());
        assertSame(lifecycleBefore, f.lifecycle.snapshot());
        assertSame(releasesBefore, f.releases.snapshot());
        assertEquals(stationBefore, f.station.snapshot());
        listener.onToteCompleted(routed.tote(), context());
        assertEquals(1L, f.work.snapshot().totalsByLine().get(f.line.lineId()).consumedTotes());
    }

    @Test
    void shouldPropagateDelegateFailureWithoutChangingAccounting() {
        Fixture f = new Fixture();
        IllegalStateException failure = new IllegalStateException("delegate rejected completion");
        var listener = new OperationalWindowP2pToteCompletedListener(f.line.lineId(), f.work,
                (tote, context) -> { throw failure; });
        var before = f.work.snapshot();
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> listener.onToteCompleted(f.routed().tote(), context())));
        assertSame(before, f.work.snapshot());
        assertEquals(2, f.work.snapshot().unallocatedEmptyOrders().size());
    }

    @Test
    void shouldRejectUnknownWorkAndInvalidArgumentsBeforeInvokingTheDelegate() {
        Fixture f = new Fixture();
        AtomicInteger calls = new AtomicInteger();
        var listener = new OperationalWindowP2pToteCompletedListener(f.line.lineId(), f.work,
                (tote, context) -> calls.incrementAndGet());
        var before = f.work.snapshot();
        assertThrows(IllegalArgumentException.class, () -> listener.onToteCompleted(tote("unknown"), context()));
        assertThrows(IllegalArgumentException.class, () -> listener.onToteCompleted(null, context()));
        assertThrows(IllegalArgumentException.class, () -> listener.onToteCompleted(f.routed().tote(), null));
        assertEquals(0, calls.get());
        assertSame(before, f.work.snapshot());
        assertThrows(IllegalArgumentException.class,
                () -> new OperationalWindowP2pToteCompletedListener(null, f.work, TipperToteCompletedListener.NO_OP));
        assertThrows(IllegalArgumentException.class,
                () -> new OperationalWindowP2pToteCompletedListener(f.line.lineId(), null, TipperToteCompletedListener.NO_OP));
        assertThrows(IllegalArgumentException.class,
                () -> new OperationalWindowP2pToteCompletedListener(f.line.lineId(), f.work, null));
        assertThrows(IllegalArgumentException.class, () -> new OperationalWindowP2pToteCompletedListener(
                new P2pLineId("unknown"), f.work, TipperToteCompletedListener.NO_OP));
    }

    private static SimulationContext context() {
        SimulationContext context = new SimulationContext();
        context.setSimulationTimeSeconds(2d);
        return context;
    }

    private static Tote tote(String id) {
        RenderableObject renderable = RenderableObject.create(id, null,
                new Mesh(new Vec4[] { new Vec4(0, 0, 0, 1), new Vec4(0, 0, 0, 1), new Vec4(0, 0, 0, 1) },
                        new int[][] {{0, 1, 2}}, "anchor"),
                new Mat4.ObjectTransformation(0, 0, 0, 0, 0, 0, new Mat4()), ignored -> 0, false);
        RouteSegment terminal = new RouteSegment("terminal-" + id,
                new LinearSegment3(new Vec3(), new Vec3(1f, 0f, 0f), false));
        Tote tote = new Tote(id, new RouteFollower(id, terminal, 0f, 1d), renderable, new Vec3(), 0f);
        tote.setInteractionMode(Tote.ToteMotionState.HELD);
        return tote;
    }

    private static NotionalToteOrder order(String id, OrderType type) {
        return new NotionalToteOrder(id, "notional-" + id, "104", 1, type,
                List.of(new DspOrderItem("line-" + id, "product", 1, "pharmacy", "patient", "rx-" + id,
                        DspOrderLineType.FULL_PACK, id, 1, 1)), 999, 0);
    }

    private static final class Fixture {
        final P2pLineDefinition line = new P2pLineDefinition(new P2pLineId("line-1"),
                new OperationalRouteDestination(StationType.P2P, "p2p-1"));
        final NotionalToteOrder full = order("full", OrderType.FULL_PACK);
        final NotionalToteOrder empty1 = order("empty-1", OrderType.EMPTY);
        final NotionalToteOrder empty2 = order("empty-2", OrderType.EMPTY);
        final InboundToteManifest manifest = new InboundToteManifest(new PhysicalToteId("input"), full.orderSheetKey(),
                full.orderType(), "104", full.items(), 0);
        final PlannedBag bag = new PlannedBag(new BagKey("rx-full", 1), "104", "pharmacy", "patient", "rx-full",
                List.of("pack-full"), List.of(full.orderSheetKey()));
        final InboundToteManifestCatalog catalog = new InboundToteManifestCatalog(List.of(manifest));
        final InboundToteLifecycleController lifecycle = new InboundToteLifecycleController(
                new PhysicalToteLifecycleLedger(), catalog);
        final StationProcessingCoordinator station = new StationProcessingCoordinator();
        final P2pPhysicalToteAssignment assignment = new P2pPhysicalToteAssignment(
                manifest.physicalToteId(), "104", line.lineId(), line.destination());
        final DspOperationalWorkLedger work;
        final WholeServiceCentreReleaseLedger releases;

        Fixture() {
            List<NotionalToteOrder> orders = List.of(full, empty1, empty2);
            work = new DspOperationalWorkLedger(orders.stream().map(order -> new DspSchedulerOrderState(order,
                    new RouteRequirements(false, false, false, true, false,
                            order.orderType() == OrderType.EMPTY ? StartLocation.AV02 : StartLocation.OSR),
                    DspOrderStatus.WAITING)).toList(), catalog, List.of(bag), List.of(line));
            var data = new LoadedDspData(List.of(), orders, List.of(), Set.of(), Set.of(), List.of(manifest),
                    DspDatasetLoadReport.empty());
            releases = new WholeServiceCentreReleaseLedger(data, new DspServiceCentreTimetable(List.of(
                    new ServiceCentreSchedule("104", "104", 999, OperationalDayTime.day0(LocalTime.of(17, 0))))),
                    List.of(line), 2);
            releases.recordApplied(new ReleasePhysicalToteFromOsrCommand(manifest.physicalToteId(), full.orderSheetKey(),
                    "104", line.destination().targetId(), Optional.of(assignment)));
            lifecycle.activate(manifest.physicalToteId(), Duration.ZERO);
        }

        StationProcessingP2pToteCompletedListener stationListener() {
            return new StationProcessingP2pToteCompletedListener(new InboundLifecycleP2pToteCompletedListener(lifecycle), station);
        }

        RoutedPhysicalTote routed() {
            Tote tote = tote(manifest.physicalToteId().value());
            return new RoutedPhysicalTote(OperationalRouteLaunchRequestFactory.fromOsr(new OsrProcessingReleaseRequest(
                    manifest, Duration.ZERO, Optional.of(assignment)), line.destination()),
                    new ToteLoadPlan(manifest.physicalToteId(), List.of()), tote, tote.getRenderable());
        }
    }
}
