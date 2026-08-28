package online.davisfamily.warehouse.sim.dsp.station.processing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRole;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.OperationalPhysicalToteReleaseRequest;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteIdentity;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteSource;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteLaunchRequest;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pPhysicalToteAssignment;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

class StationConsumedToteControllerTest {

    @Test
    void shouldApplyConsumePresentationExactlyOnceWithoutTakingDisposition() {
        StationProcessingCoordinator coordinator = new StationProcessingCoordinator();
        RoutedPhysicalTote routed = StationProcessingTestFixtures.routedTote(
                "consumed-once", StationProcessingTestFixtures.destination("store-1"));
        prepareVisibleMovingOpen(routed.tote());
        coordinator.claim(routed, Duration.ZERO);
        StationProcessingDisposition disposition = coordinator.complete(
                routed.physicalToteId(),
                StationProcessingDispositionType.CONSUME,
                routed.loadPlan(),
                Duration.ofSeconds(1));
        StationProcessingSnapshot before = coordinator.snapshot();
        List<StationProcessingDisposition> pendingBefore = coordinator.pendingDispositions();
        StationConsumedToteController controller = new StationConsumedToteController(coordinator);

        controller.update(context(1d), 0d);

        assertFalse(routed.tote().areLidsOpen());
        assertEquals(Tote.ToteMotionState.HELD, routed.tote().getInteractionMode());
        assertFalse(routed.renderable().isVisible());
        assertEquals(before, coordinator.snapshot());
        assertEquals(pendingBefore, coordinator.pendingDispositions());
        assertSame(disposition, coordinator.peekDisposition().orElseThrow());

        routed.tote().openLids();
        routed.tote().setInteractionMode(Tote.ToteMotionState.MOVING);
        routed.renderable().setVisible(true);
        controller.update(context(2d), 0d);

        assertTrue(routed.tote().areLidsOpen());
        assertEquals(Tote.ToteMotionState.MOVING, routed.tote().getInteractionMode());
        assertTrue(routed.renderable().isVisible());
        assertSame(disposition, coordinator.peekDisposition().orElseThrow());
    }

    @Test
    void shouldLeaveContinuePresentationAndOwnershipUnchanged() {
        StationProcessingCoordinator coordinator = new StationProcessingCoordinator();
        RoutedPhysicalTote routed = StationProcessingTestFixtures.routedTote(
                "continued", StationProcessingTestFixtures.destination("store-2"));
        prepareVisibleMovingOpen(routed.tote());
        RouteFollower follower = routed.tote().getRouteFollower();
        RouteSegment segment = follower.getCurrentSegment();
        double distance = follower.getDistanceAlongSegment();
        var direction = follower.getTravelDirection();
        ToteLoadPlan plan = routed.loadPlan();
        OperationalRouteDestination destination = routed.destination();
        OperationalPhysicalToteSource source = routed.launchRequest().source();
        Optional<P2pPhysicalToteAssignment> assignment = routed.p2pAssignment();
        coordinator.claim(routed, Duration.ZERO);
        StationProcessingDisposition disposition = coordinator.complete(
                routed.physicalToteId(),
                StationProcessingDispositionType.CONTINUE,
                plan,
                Duration.ofSeconds(1));
        StationConsumedToteController controller = new StationConsumedToteController(coordinator);

        controller.update(context(1d), 0d);

        assertTrue(routed.renderable().isVisible());
        assertTrue(routed.tote().areLidsOpen());
        assertEquals(Tote.ToteMotionState.MOVING, routed.tote().getInteractionMode());
        assertSame(follower, routed.tote().getRouteFollower());
        assertSame(segment, follower.getCurrentSegment());
        assertEquals(distance, follower.getDistanceAlongSegment());
        assertEquals(direction, follower.getTravelDirection());
        assertSame(plan, routed.loadPlan());
        assertSame(destination, routed.destination());
        assertEquals(source, routed.launchRequest().source());
        assertSame(assignment, routed.p2pAssignment());
        assertSame(disposition, coordinator.peekDisposition().orElseThrow());
    }

    @Test
    void shouldProcessNewMixedDispositionsInCoordinatorFifoOrder() {
        StationProcessingCoordinator coordinator = new StationProcessingCoordinator();
        RoutedPhysicalTote continued = StationProcessingTestFixtures.routedTote(
                "mixed-continue", StationProcessingTestFixtures.destination("store-3"));
        RoutedPhysicalTote storeConsumed = routedTote(
                "mixed-store", new OperationalRouteDestination(StationType.ADAPTING, "bench-1"),
                OperationalPhysicalToteSource.OSR, OrderType.ADAPTED, Optional.empty());
        RoutedPhysicalTote p2pConsumed = routedTote(
                "mixed-p2p", new OperationalRouteDestination(StationType.P2P, "line-1"),
                OperationalPhysicalToteSource.AV02, OrderType.EMPTY,
                Optional.of(new P2pPhysicalToteAssignment(
                        new PhysicalToteId("mixed-p2p"),
                        "104",
                        new P2pLineId("line-1"),
                        new OperationalRouteDestination(StationType.P2P, "line-1"))));
        prepareVisibleMovingOpen(continued.tote());
        prepareVisibleMovingOpen(storeConsumed.tote());
        prepareVisibleMovingOpen(p2pConsumed.tote());
        StationProcessingDisposition continueDisposition = complete(
                coordinator, continued, StationProcessingDispositionType.CONTINUE);
        StationProcessingDisposition storeDisposition = complete(
                coordinator, storeConsumed, StationProcessingDispositionType.CONSUME);
        StationProcessingDisposition p2pDisposition = complete(
                coordinator, p2pConsumed, StationProcessingDispositionType.CONSUME);
        List<StationProcessingDisposition> pendingBefore = coordinator.pendingDispositions();
        StationConsumedToteController controller = new StationConsumedToteController(coordinator);

        controller.update(context(1d), 0d);

        assertTrue(continued.renderable().isVisible());
        assertTrue(continued.tote().areLidsOpen());
        assertEquals(Tote.ToteMotionState.MOVING, continued.tote().getInteractionMode());
        assertFalse(storeConsumed.renderable().isVisible());
        assertFalse(p2pConsumed.renderable().isVisible());
        assertEquals(Tote.ToteMotionState.HELD, storeConsumed.tote().getInteractionMode());
        assertEquals(Tote.ToteMotionState.HELD, p2pConsumed.tote().getInteractionMode());
        assertSame(continueDisposition, pendingBefore.get(0));
        assertSame(storeDisposition, pendingBefore.get(1));
        assertSame(p2pDisposition, pendingBefore.get(2));
        assertEquals(pendingBefore, coordinator.pendingDispositions());

        p2pConsumed.renderable().setVisible(true);
        p2pConsumed.tote().setInteractionMode(Tote.ToteMotionState.MOVING);
        p2pConsumed.tote().openLids();
        RoutedPhysicalTote late = routedTote(
                "mixed-late", new OperationalRouteDestination(StationType.THIRD_PARTY, "store-4"),
                OperationalPhysicalToteSource.OSR, OrderType.FULL_PACK, Optional.empty());
        prepareVisibleMovingOpen(late.tote());
        StationProcessingDisposition lateDisposition = complete(
                coordinator, late, StationProcessingDispositionType.CONSUME);
        List<StationProcessingDisposition> pendingAfterAppend = coordinator.pendingDispositions();

        controller.update(context(2d), 0d);

        assertFalse(late.renderable().isVisible());
        assertEquals(Tote.ToteMotionState.HELD, late.tote().getInteractionMode());
        assertTrue(p2pConsumed.renderable().isVisible());
        assertEquals(Tote.ToteMotionState.MOVING, p2pConsumed.tote().getInteractionMode());
        assertTrue(p2pConsumed.tote().areLidsOpen());
        assertSame(lateDisposition, pendingAfterAppend.get(3));
        assertEquals(pendingAfterAppend, coordinator.pendingDispositions());
    }

    @Test
    void shouldNotMutateLogicalOrDownstreamState() {
        StationProcessingCoordinator coordinator = new StationProcessingCoordinator();
        OperationalRouteDestination destination =
                new OperationalRouteDestination(StationType.P2P, "line-sentinel");
        P2pPhysicalToteAssignment assignment = new P2pPhysicalToteAssignment(
                new PhysicalToteId("sentinel"),
                "104",
                new P2pLineId("line-sentinel"),
                destination);
        RoutedPhysicalTote routed = routedTote(
                "sentinel", destination, OperationalPhysicalToteSource.AV02,
                OrderType.EMPTY, Optional.of(assignment));
        prepareVisibleMovingOpen(routed.tote());
        ToteLoadPlan plan = routed.loadPlan();
        OperationalPhysicalToteIdentity identity = routed.launchRequest().identity();
        Optional<P2pPhysicalToteAssignment> beforeAssignment = routed.p2pAssignment();
        StationProcessingDisposition disposition = complete(
                coordinator, routed, StationProcessingDispositionType.CONSUME);
        StationProcessingSnapshot beforeCoordinator = coordinator.snapshot();
        StationConsumedToteController controller = new StationConsumedToteController(coordinator);

        controller.update(context(1d), 0d);

        assertSame(plan, routed.loadPlan());
        assertSame(identity, routed.launchRequest().identity());
        assertEquals(OperationalPhysicalToteSource.AV02, routed.launchRequest().source());
        assertSame(destination, routed.destination());
        assertSame(beforeAssignment, routed.p2pAssignment());
        assertSame(disposition, coordinator.peekDisposition().orElseThrow());
        assertEquals(beforeCoordinator, coordinator.snapshot());
    }

    @Test
    void shouldRejectInvalidConstructionAndUpdateWithoutPresentationMutation() {
        assertThrows(IllegalArgumentException.class,
                () -> new StationConsumedToteController(null));

        StationProcessingCoordinator coordinator = new StationProcessingCoordinator();
        RoutedPhysicalTote routed = StationProcessingTestFixtures.routedTote(
                "invalid-update", StationProcessingTestFixtures.destination("store-5"));
        prepareVisibleMovingOpen(routed.tote());
        coordinator.claim(routed, Duration.ZERO);
        coordinator.complete(
                routed.physicalToteId(),
                StationProcessingDispositionType.CONSUME,
                routed.loadPlan(),
                Duration.ofSeconds(1));
        StationProcessingSnapshot before = coordinator.snapshot();
        StationConsumedToteController controller = new StationConsumedToteController(coordinator);

        assertThrows(IllegalArgumentException.class, () -> controller.update(null, 0d));
        assertThrows(IllegalArgumentException.class, () -> controller.update(context(1d), -0.1d));
        assertThrows(IllegalArgumentException.class,
                () -> controller.update(context(1d), Double.NaN));

        assertTrue(routed.tote().areLidsOpen());
        assertEquals(Tote.ToteMotionState.MOVING, routed.tote().getInteractionMode());
        assertTrue(routed.renderable().isVisible());
        assertEquals(before, coordinator.snapshot());
    }

    private static StationProcessingDisposition complete(
            StationProcessingCoordinator coordinator,
            RoutedPhysicalTote routed,
            StationProcessingDispositionType type) {
        coordinator.claim(routed, Duration.ZERO);
        return coordinator.complete(
                routed.physicalToteId(), type, routed.loadPlan(), Duration.ofSeconds(1));
    }

    private static void prepareVisibleMovingOpen(Tote tote) {
        tote.setInteractionMode(Tote.ToteMotionState.MOVING);
        tote.openLids();
        tote.getRenderable().setVisible(true);
    }

    private static RoutedPhysicalTote routedTote(
            String physicalToteId,
            OperationalRouteDestination destination,
            OperationalPhysicalToteSource source,
            OrderType orderType,
            Optional<P2pPhysicalToteAssignment> assignment) {
        PhysicalToteId id = new PhysicalToteId(physicalToteId);
        PhysicalToteRole role = source == OperationalPhysicalToteSource.AV02
                ? PhysicalToteRole.PRE_P2P
                : PhysicalToteRole.INBOUND_PACK;
        OperationalPhysicalToteIdentity identity = new OperationalPhysicalToteIdentity(
                source,
                id,
                new OrderSheetKey("sheet-" + physicalToteId, 1),
                orderType,
                "104",
                role,
                0);
        OperationalPhysicalToteReleaseRequest releaseRequest =
                new OperationalPhysicalToteReleaseRequest(
                        identity,
                        List.of("pharmacy-" + physicalToteId),
                        Duration.ZERO,
                        assignment);
        OperationalRouteLaunchRequest launchRequest =
                new OperationalRouteLaunchRequest(releaseRequest, destination);
        RenderableObject renderable = renderable(physicalToteId);
        RouteSegment segment = new RouteSegment(
                "segment-" + physicalToteId,
                new LinearSegment3(new Vec3(), new Vec3(1f, 0f, 0f), false));
        Tote tote = new Tote(
                physicalToteId,
                new RouteFollower(physicalToteId, segment, 0f, 1d),
                renderable,
                new Vec3(),
                0f);
        return new RoutedPhysicalTote(
                launchRequest,
                new ToteLoadPlan(id, List.of()),
                tote,
                renderable);
    }

    private static RenderableObject renderable(String id) {
        return RenderableObject.create(
                id,
                null,
                new Mesh(
                        new Vec4[] {
                                new Vec4(0f, 0f, 0f, 1f),
                                new Vec4(0f, 0f, 0f, 1f),
                                new Vec4(0f, 0f, 0f, 1f)
                        },
                        new int[][] {{0, 1, 2}},
                        "anchor"),
                new Mat4.ObjectTransformation(
                        0f, 0f, 0f, 0f, 0f, 0f, new Mat4()),
                triangleIndex -> 0,
                false);
    }

    private static SimulationContext context(double seconds) {
        SimulationContext context = new SimulationContext();
        context.setSimulationTimeSeconds(seconds);
        return context;
    }
}
