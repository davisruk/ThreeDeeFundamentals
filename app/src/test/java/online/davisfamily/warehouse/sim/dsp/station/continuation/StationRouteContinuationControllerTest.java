package online.davisfamily.warehouse.sim.dsp.station.continuation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.behaviour.routing.RouteFollower;
import online.davisfamily.threedee.behaviour.routing.RouteFollower.TravelDirection;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.matrices.Vec4;
import online.davisfamily.threedee.model.Mesh;
import online.davisfamily.threedee.path.LinearSegment3;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.warehouse.sim.transfer.TransferZoneMachine;
import online.davisfamily.warehouse.sim.dsp.adapting.MapBackedToteLoadPlanRegistry;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRole;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.ProductMasterRecord;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.model.StartLocation;
import online.davisfamily.warehouse.sim.dsp.osr.release.OperationalPhysicalToteReleaseRequest;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteIdentity;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteSource;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteLaunchRequest;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pPhysicalToteAssignment;
import online.davisfamily.warehouse.sim.dsp.routing.DspRouteDeriver;
import online.davisfamily.warehouse.sim.dsp.routing.InMemoryProductMasterRepository;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingCoordinator;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingDisposition;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingDispositionType;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingOrderCatalog;
import online.davisfamily.warehouse.sim.dsp.transport.OsrOutboundTransportQueue;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;
import online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseRouteCatalog;
import online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseRouteDefinition;
import online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseTransportPublicationState;
import online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseTransportPublisher;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

class StationRouteContinuationControllerTest {

    @Test
    void shouldDrainAtMostOneHeadAndClearAnOldBlockWhenIdle() {
        Rig first = Rig.consume("fifo-first", StationType.ADAPTING, "bench-1");
        Rig second = Rig.consume("fifo-second", StationType.P2P, "line-1");
        StationProcessingCoordinator coordinator = first.coordinator;
        coordinator.claim(first.routed, Duration.ZERO);
        StationProcessingDisposition firstDisposition = coordinator.complete(
                first.routed.physicalToteId(),
                StationProcessingDispositionType.CONSUME,
                first.plan,
                Duration.ofSeconds(1));
        coordinator.claim(second.routed, Duration.ZERO);
        StationProcessingDisposition secondDisposition = coordinator.complete(
                second.routed.physicalToteId(),
                StationProcessingDispositionType.CONSUME,
                second.plan,
                Duration.ofSeconds(1));

        // Reuse one controller's dependencies, while the second disposition remains the exact
        // FIFO head after the first acknowledgement.
        Rig rig = first.withCoordinator(coordinator, List.of(first.order, second.order));
        first.routed.tote().openLids();
        first.routed.tote().setInteractionMode(Tote.ToteMotionState.MOVING);
        first.routed.renderable().setVisible(true);
        rig.controller.update(context(), 0d);
        assertSame(firstDisposition, coordinator.peekDisposition().orElseThrow());
        assertTrue(rig.controller.snapshot().blocked());

        first.routed.tote().closeLids();
        first.routed.tote().setInteractionMode(Tote.ToteMotionState.HELD);
        first.routed.renderable().setVisible(false);
        rig.controller.update(context(), 0d);
        assertSame(secondDisposition, coordinator.peekDisposition().orElseThrow());
        assertEquals(1, rig.controller.snapshot().consumedAcknowledgementCount());

        second.routed.tote().closeLids();
        second.routed.tote().setInteractionMode(Tote.ToteMotionState.HELD);
        second.routed.renderable().setVisible(false);
        rig.controller.update(context(), 0d);
        assertTrue(coordinator.pendingDispositions().isEmpty());
        assertEquals(2, rig.controller.snapshot().consumedAcknowledgementCount());

        rig.controller.update(context(), 0d);
        assertFalse(rig.controller.snapshot().blocked());
        assertTrue(rig.controller.snapshot().headPhysicalToteId().isEmpty());
    }

    @Test
    void shouldContinueThirdPartyToP2pWithExactObjectsAndSourceNeutralRelease() {
        NotionalToteOrder order = order(
                "tp-p2p",
                OrderType.FULL_PACK,
                item("tp-p2p", DspOrderLineType.FULL_PACK),
                true);
        OperationalRouteDestination completed = destination(StationType.THIRD_PARTY, "tp-bench");
        OperationalRouteDestination next = destination(StationType.P2P, "line-1");
        P2pPhysicalToteAssignment assignment = assignment("tp-p2p", next);
        ToteLoadPlan replacement = plan("tp-p2p");
        Rig rig = Rig.continueRig(
                "tp-p2p", order, completed, next, Optional.of(assignment), replacement);

        rig.controller.update(context(), 0d);

        assertTrue(rig.coordinator.pendingDispositions().isEmpty());
        RoutedPhysicalTote continued = rig.transportQueue.peek().orElseThrow();
        assertNotSame(rig.routed, continued);
        assertSame(replacement, continued.loadPlan());
        assertSame(rig.routed.tote(), continued.tote());
        assertSame(rig.routed.renderable(), continued.renderable());
        assertSame(rig.routed.launchRequest().releaseRequest(),
                continued.launchRequest().releaseRequest());
        assertSame(rig.routed.launchRequest().p2pAssignment().orElseThrow(),
                continued.launchRequest().p2pAssignment().orElseThrow());
        assertEquals(next, continued.destination());
        assertSame(rig.follower, continued.tote().getRouteFollower());
        assertSame(rig.nextEntry, rig.follower.getCurrentSegment());
        assertEquals(1f, rig.follower.getDistanceAlongSegment());
        assertEquals(TravelDirection.FORWARD, rig.follower.getTravelDirection());
        assertFalse(rig.routed.tote().areLidsOpen());
        assertEquals(Tote.ToteMotionState.HELD, rig.routed.tote().getInteractionMode());
        assertEquals(1, rig.controller.continuedCount());
        assertEquals(next, rig.controller.snapshot().lastHandledNextDestination().orElseThrow());
    }

    @Test
    void shouldUseResolverSelectedAdaptingBench() {
        NotionalToteOrder order = order(
                "tp-adapting",
                OrderType.ADAPTED,
                item("tp-adapting", DspOrderLineType.ADAPTED),
                true);
        OperationalRouteDestination completed = destination(StationType.THIRD_PARTY, "tp-bench");
        OperationalRouteDestination next = destination(StationType.ADAPTING, "bench-selected");
        Rig rig = Rig.continueRig(
                "tp-adapting", order, completed, next, Optional.empty(), plan("tp-adapting"));

        rig.controller.update(context(), 0d);

        assertEquals(List.of(StationType.ADAPTING), rig.targetResolver.stations);
        assertEquals(List.of(next), rig.transportQueue.snapshot().entries().stream()
                .map(entry -> entry.destination()).toList());
        assertEquals(next, rig.transportQueue.peek().orElseThrow().destination());
    }

    @Test
    void shouldContinueAdaptingCollectToPinnedP2p() {
        NotionalToteOrder order = order(
                "adapt-collect",
                OrderType.ASSOCIATED,
                item("adapt-collect", DspOrderLineType.ADAPTED),
                true);
        OperationalRouteDestination completed = destination(StationType.ADAPTING, "bench-1");
        OperationalRouteDestination next = destination(StationType.P2P, "line-1");
        Rig rig = Rig.continueRig(
                "adapt-collect", order, completed, next,
                Optional.of(assignment("adapt-collect", next)), plan("adapt-collect"));

        rig.controller.update(context(), 0d);

        assertEquals(StationType.P2P, rig.targetResolver.stations.getFirst());
        assertEquals(next, rig.transportQueue.peek().orElseThrow().destination());
        assertSame(rig.routed.tote(), rig.transportQueue.peek().orElseThrow().tote());
    }

    @Test
    void shouldAcknowledgePresentedAdaptingAndP2pConsumesTerminally() {
        Rig adapting = Rig.consume("consume-adapting", StationType.ADAPTING, "bench-1");
        Rig p2p = Rig.consume("consume-p2p", StationType.P2P, "line-1");
        StationProcessingCoordinator coordinator = adapting.coordinator;
        coordinator.claim(adapting.routed, Duration.ZERO);
        coordinator.complete(adapting.routed.physicalToteId(), StationProcessingDispositionType.CONSUME,
                adapting.plan, Duration.ofSeconds(1));
        coordinator.claim(p2p.routed, Duration.ZERO);
        coordinator.complete(p2p.routed.physicalToteId(), StationProcessingDispositionType.CONSUME,
                p2p.plan, Duration.ofSeconds(1));
        presentConsume(adapting.routed);
        presentConsume(p2p.routed);
        Rig rig = adapting.withCoordinator(coordinator, List.of(adapting.order, p2p.order));

        rig.controller.update(context(), 0d);
        assertEquals(1, rig.controller.snapshot().consumedAcknowledgementCount());
        assertThrows(IllegalStateException.class,
                () -> coordinator.claim(adapting.routed, Duration.ofSeconds(2)));
        assertSame(p2p.routed, coordinator.peekDisposition().orElseThrow().claim().routedTote());
        assertTrue(rig.transportQueue.snapshot().entries().isEmpty());

        rig.controller.update(context(), 0d);
        assertEquals(2, rig.controller.snapshot().consumedAcknowledgementCount());
        assertThrows(IllegalStateException.class,
                () -> coordinator.claim(p2p.routed, Duration.ofSeconds(2)));
    }

    @Test
    void shouldDeferUnpresentedConsumeFullQueueAndResolverWithoutPhysicalMutation() {
        Rig consume = Rig.consume("defer-consume", StationType.P2P, "line-1");
        consume.coordinator.claim(consume.routed, Duration.ZERO);
        consume.coordinator.complete(consume.routed.physicalToteId(), StationProcessingDispositionType.CONSUME,
                consume.plan, Duration.ofSeconds(1));
        SnapshotState beforeConsume = SnapshotState.capture(consume);
        consume.controller.update(context(), 0d);
        assertEquals(beforeConsume.coordinator, consume.coordinator.snapshot());
        assertEquals(beforeConsume.transport, consume.transportQueue.snapshot());
        assertEquals(beforeConsume.followerSegment, consume.follower.getCurrentSegment());
        assertEquals(beforeConsume.followerDistance, consume.follower.getDistanceAlongSegment());
        assertTrue(consume.controller.snapshot().blocked());

        NotionalToteOrder order = order("full-queue", OrderType.FULL_PACK,
                item("full-queue", DspOrderLineType.FULL_PACK), true);
        OperationalRouteDestination completed = destination(StationType.THIRD_PARTY, "tp-bench");
        OperationalRouteDestination next = destination(StationType.P2P, "line-1");
        Rig full = Rig.continueRig("full-queue", order, completed, next,
                Optional.of(assignment("full-queue", next)), plan("full-queue"), 1,
                StationRouteContinuationDecision.continueTo(next));
        RoutedPhysicalTote blocker = Rig.simpleRouted("queue-blocker", next);
        full.transportQueue.enqueue(blocker);
        SnapshotState beforeFull = SnapshotState.capture(full);
        full.controller.update(context(), 0d);
        assertEquals(beforeFull.coordinator, full.coordinator.snapshot());
        assertEquals(beforeFull.transport, full.transportQueue.snapshot());
        assertEquals(beforeFull.followerSegment, full.follower.getCurrentSegment());
        assertEquals(beforeFull.followerDistance, full.follower.getDistanceAlongSegment());
        assertEquals(1, full.targetResolver.calls);
        assertTrue(full.controller.snapshot().blocked());

        Rig rejected = Rig.continueRig("defer-target", order("defer-target", OrderType.FULL_PACK,
                item("defer-target", DspOrderLineType.FULL_PACK), true), completed, next,
                Optional.of(assignment("defer-target", next)), plan("defer-target"), 4,
                StationRouteContinuationDecision.defer("selected P2P line is unavailable"));
        SnapshotState beforeRejected = SnapshotState.capture(rejected);
        rejected.controller.update(context(), 0d);
        assertEquals(beforeRejected.coordinator, rejected.coordinator.snapshot());
        assertEquals(beforeRejected.transport, rejected.transportQueue.snapshot());
        assertEquals(beforeRejected.followerSegment, rejected.follower.getCurrentSegment());
        assertEquals(beforeRejected.followerDistance, rejected.follower.getDistanceAlongSegment());
        assertTrue(rejected.controller.snapshot().blocked());
    }

    @Test
    void shouldRejectStaleIdentityTopologyPublisherAndAssignmentBeforeMutation() {
        NotionalToteOrder order = order("invalid", OrderType.FULL_PACK,
                item("invalid", DspOrderLineType.FULL_PACK), true);
        OperationalRouteDestination completed = destination(StationType.THIRD_PARTY, "tp-bench");
        OperationalRouteDestination next = destination(StationType.P2P, "line-1");

        Rig stalePlan = Rig.continueRig("stale-plan", order("stale-plan", OrderType.FULL_PACK,
                item("stale-plan", DspOrderLineType.FULL_PACK), true), completed, next,
                Optional.of(assignment("stale-plan", next)), plan("stale-plan"));
        stalePlan.registry.putLoadPlan(new ToteLoadPlan(
                new PhysicalToteId("stale-plan"), List.of()));
        assertThrows(IllegalStateException.class, () -> stalePlan.controller.update(context(), 0d));
        assertTrue(stalePlan.transportQueue.snapshot().entries().isEmpty());

        Rig missingOrder = Rig.continueRig("missing-order", order, completed, next,
                Optional.of(assignment("missing-order", next)), plan("missing-order"), 4,
                StationRouteContinuationDecision.continueTo(next), null);
        assertThrows(IllegalStateException.class, () -> missingOrder.controller.update(context(), 0d));

        Rig unpublished = Rig.continueRig("unpublished", order("unpublished", OrderType.FULL_PACK,
                item("unpublished", DspOrderLineType.FULL_PACK), true), completed, next,
                Optional.of(assignment("unpublished", next)), plan("unpublished"));
        unpublished.publisher.state = WarehouseTransportPublicationState.UNPUBLISHED;
        assertThrows(IllegalStateException.class, () -> unpublished.controller.update(context(), 0d));
        assertEquals(1, unpublished.publisher.publicationStateCalls);
        // The fake's state query is still the one read by the controller; it must never publish.
        assertEquals(0, unpublished.publisher.publishCalls);

        Rig conflict = Rig.continueRig("publisher-conflict", order("publisher-conflict", OrderType.FULL_PACK,
                item("publisher-conflict", DspOrderLineType.FULL_PACK), true), completed, next,
                Optional.of(assignment("publisher-conflict", next)), plan("publisher-conflict"));
        conflict.publisher.state = WarehouseTransportPublicationState.PHYSICAL_ID_CONFLICT;
        assertThrows(IllegalStateException.class, () -> conflict.controller.update(context(), 0d));
        assertTrue(conflict.transportQueue.snapshot().entries().isEmpty());

        Rig noAssignment = Rig.continueRig("missing-assignment", order("missing-assignment", OrderType.FULL_PACK,
                item("missing-assignment", DspOrderLineType.FULL_PACK), true), completed, next,
                Optional.empty(), plan("missing-assignment"));
        assertThrows(IllegalStateException.class, () -> noAssignment.controller.update(context(), 0d));

        OperationalRouteDestination otherLine = destination(StationType.P2P, "line-other");
        Rig mismatchedAssignment = Rig.continueRig("mismatched-assignment",
                order("mismatched-assignment", OrderType.FULL_PACK,
                        item("mismatched-assignment", DspOrderLineType.FULL_PACK), true),
                completed,
                next,
                Optional.of(assignment("mismatched-assignment", next)),
                plan("mismatched-assignment"),
                4,
                StationRouteContinuationDecision.continueTo(otherLine));
        assertThrows(IllegalStateException.class,
                () -> mismatchedAssignment.controller.update(context(), 0d));
    }

    @Test
    void shouldRejectMissingOrderMismatchRoutesManualNextWrongTerminalNonHeldAndInvisible() {
        NotionalToteOrder identityOrder = order("mismatch", OrderType.FULL_PACK,
                item("mismatch", DspOrderLineType.FULL_PACK), true);
        NotionalToteOrder mismatchedOrder = new NotionalToteOrder(
                "mismatch", "notional-mismatch", "999", 1, OrderType.FULL_PACK,
                identityOrder.items(), 0, 0);
        Rig mismatch = Rig.continueRig("mismatch", identityOrder,
                destination(StationType.THIRD_PARTY, "tp-bench"),
                destination(StationType.P2P, "line-1"),
                Optional.of(assignment("mismatch", destination(StationType.P2P, "line-1"))),
                plan("mismatch"), 4, StationRouteContinuationDecision.continueTo(
                        destination(StationType.P2P, "line-1")), mismatchedOrder);
        assertThrows(IllegalStateException.class, () -> mismatch.controller.update(context(), 0d));

        Rig missingCurrent = Rig.continueRig("missing-current", identityOrder,
                destination(StationType.THIRD_PARTY, "not-configured"),
                destination(StationType.P2P, "line-1"),
                Optional.of(assignment("missing-current", destination(StationType.P2P, "line-1"))),
                plan("missing-current"));
        assertThrows(IllegalStateException.class, () -> missingCurrent.controller.update(context(), 0d));

        Rig wrongTerminal = Rig.continueRig("wrong-terminal", identityOrder,
                destination(StationType.THIRD_PARTY, "tp-bench"),
                destination(StationType.P2P, "line-1"),
                Optional.of(assignment("wrong-terminal", destination(StationType.P2P, "line-1"))),
                plan("wrong-terminal"));
        wrongTerminal.routed.tote().getRouteFollower().setCurrentSegment(segment("wrong", 10f));
        assertThrows(IllegalStateException.class, () -> wrongTerminal.controller.update(context(), 0d));

        Rig nonHeld = Rig.continueRig("non-held", identityOrder,
                destination(StationType.THIRD_PARTY, "tp-bench"),
                destination(StationType.P2P, "line-1"),
                Optional.of(assignment("non-held", destination(StationType.P2P, "line-1"))),
                plan("non-held"));
        nonHeld.routed.tote().setInteractionMode(Tote.ToteMotionState.MOVING);
        assertThrows(IllegalStateException.class, () -> nonHeld.controller.update(context(), 0d));

        Rig invisible = Rig.continueRig("invisible", identityOrder,
                destination(StationType.THIRD_PARTY, "tp-bench"),
                destination(StationType.P2P, "line-1"),
                Optional.of(assignment("invisible", destination(StationType.P2P, "line-1"))),
                plan("invisible"));
        invisible.routed.renderable().setVisible(false);
        assertThrows(IllegalStateException.class, () -> invisible.controller.update(context(), 0d));
    }

    @Test
    void shouldRejectNoNextMissingNextRouteManualNextActiveReservationAndDuplicateId() {
        NotionalToteOrder adapted = order("no-next", OrderType.ADAPTED,
                item("no-next", DspOrderLineType.ADAPTED), false);
        OperationalRouteDestination adapting = destination(StationType.ADAPTING, "bench-1");
        OperationalRouteDestination unusedNext = destination(StationType.P2P, "line-1");
        Rig noNext = Rig.continueRig("no-next", adapted, adapting, unusedNext, Optional.empty(),
                plan("no-next"));
        assertThrows(IllegalStateException.class, () -> noNext.controller.update(context(), 0d));

        NotionalToteOrder fullPack = order("missing-next-route", OrderType.FULL_PACK,
                item("missing-next-route", DspOrderLineType.FULL_PACK), true);
        OperationalRouteDestination thirdParty = destination(StationType.THIRD_PARTY, "tp-bench");
        OperationalRouteDestination missingNext = destination(StationType.P2P, "not-configured");
        Rig missingNextRoute = Rig.continueRig("missing-next-route", fullPack, thirdParty, missingNext,
                Optional.of(assignment("missing-next-route", missingNext)), plan("missing-next-route"));
        assertThrows(IllegalStateException.class,
                () -> missingNextRoute.controller.update(context(), 0d));

        Rig manual = Rig.continueRig("manual", adapted, adapting, unusedNext, Optional.empty(),
                plan("manual"));
        DspRouteDeriver manualDeriver = new DspRouteDeriver(new InMemoryProductMasterRepository(List.of(
                new ProductMasterRecord("product-manual", "Product", Optional.empty(), Optional.empty())))) {
            @Override
            public online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements derive(NotionalToteOrder ignored) {
                return new online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements(
                        false, true, true, false, false, StartLocation.OSR);
            }
        };
        manual.controller = new StationRouteContinuationController(
                manual.coordinator,
                manual.orderCatalog,
                manual.registry,
                manualDeriver,
                new StationRouteContinuationSelector(),
                manual.targetResolver,
                manual.routeCatalog,
                manual.transportQueue,
                manual.publisher);
        assertThrows(IllegalStateException.class, () -> manual.controller.update(context(), 0d));

        Rig reserved = Rig.continueRig("reserved", fullPack, thirdParty,
                destination(StationType.P2P, "line-1"),
                Optional.of(assignment("reserved", destination(StationType.P2P, "line-1"))), plan("reserved"));
        reserved.routed.tote().reserveForTransfer(new TransferZoneMachine("machine", null, null, null));
        assertThrows(IllegalStateException.class, () -> reserved.controller.update(context(), 0d));

        Rig duplicate = Rig.continueRig("duplicate", fullPack, thirdParty,
                destination(StationType.P2P, "line-1"),
                Optional.of(assignment("duplicate", destination(StationType.P2P, "line-1"))), plan("duplicate"));
        duplicate.transportQueue.enqueue(new RoutedPhysicalTote(
                duplicate.routed.launchRequest(), duplicate.routed.loadPlan(), duplicate.routed.tote(),
                duplicate.routed.renderable()));
        assertThrows(IllegalStateException.class, () -> duplicate.controller.update(context(), 0d));
    }

    private static SimulationContext context() {
        SimulationContext context = new SimulationContext();
        context.setSimulationTimeSeconds(1d);
        return context;
    }

    private static void presentConsume(RoutedPhysicalTote routed) {
        routed.tote().closeLids();
        routed.tote().setInteractionMode(Tote.ToteMotionState.HELD);
        routed.renderable().setVisible(false);
    }

    private static NotionalToteOrder order(
            String id,
            OrderType orderType,
            DspOrderItem item,
            boolean thirdParty) {
        return new NotionalToteOrder(
                id,
                "notional-" + id,
                "104",
                1,
                orderType,
                List.of(item),
                0,
                0);
    }

    private static DspOrderItem item(String id, DspOrderLineType lineType) {
        return new DspOrderItem(
                "line-" + id,
                "product-" + id,
                1,
                "pharmacy-" + id,
                "patient-" + id,
                "prescription-" + id,
                lineType,
                id,
                1,
                0);
    }

    private static OperationalRouteDestination destination(StationType type, String id) {
        return new OperationalRouteDestination(type, id);
    }

    private static P2pPhysicalToteAssignment assignment(
            String physicalToteId,
            OperationalRouteDestination destination) {
        return new P2pPhysicalToteAssignment(
                new PhysicalToteId(physicalToteId),
                "104",
                new P2pLineId(destination.targetId()),
                destination);
    }

    private static ToteLoadPlan plan(String id) {
        return new ToteLoadPlan(new PhysicalToteId(id), List.of());
    }

    private static RouteSegment segment(String label, float length) {
        return new RouteSegment(
                label,
                new LinearSegment3(new Vec3(), new Vec3(length, 0f, 0f), false));
    }

    private record SnapshotState(
            online.davisfamily.warehouse.sim.dsp.station.processing.StationProcessingSnapshot coordinator,
            online.davisfamily.warehouse.sim.dsp.transport.OsrOutboundTransportQueueSnapshot transport,
            RouteSegment followerSegment,
            double followerDistance) {
        static SnapshotState capture(Rig rig) {
            return new SnapshotState(
                    rig.coordinator.snapshot(),
                    rig.transportQueue.snapshot(),
                    rig.follower.getCurrentSegment(),
                    rig.follower.getDistanceAlongSegment());
        }
    }

    private static final class Rig {
        final String id;
        final NotionalToteOrder order;
        final StationProcessingCoordinator coordinator;
        final MapBackedToteLoadPlanRegistry registry;
        final FakeResolver targetResolver;
        final FakePublisher publisher;
        final OsrOutboundTransportQueue transportQueue;
        StationRouteContinuationController controller;
        final RoutedPhysicalTote routed;
        final RouteFollower follower;
        final RouteSegment nextEntry;
        final ToteLoadPlan plan;
        final WarehouseRouteCatalog routeCatalog;
        final StationProcessingOrderCatalog orderCatalog;
        final DspRouteDeriver routeDeriver;

        private Rig(
                String id,
                NotionalToteOrder order,
                StationProcessingCoordinator coordinator,
                MapBackedToteLoadPlanRegistry registry,
                FakeResolver targetResolver,
                FakePublisher publisher,
                OsrOutboundTransportQueue transportQueue,
                StationRouteContinuationController controller,
                RoutedPhysicalTote routed,
                RouteFollower follower,
                RouteSegment nextEntry,
                ToteLoadPlan plan,
                WarehouseRouteCatalog routeCatalog,
                StationProcessingOrderCatalog orderCatalog,
                DspRouteDeriver routeDeriver) {
            this.id = id;
            this.order = order;
            this.coordinator = coordinator;
            this.registry = registry;
            this.targetResolver = targetResolver;
            this.publisher = publisher;
            this.transportQueue = transportQueue;
            this.controller = controller;
            this.routed = routed;
            this.follower = follower;
            this.nextEntry = nextEntry;
            this.plan = plan;
            this.routeCatalog = routeCatalog;
            this.orderCatalog = orderCatalog;
            this.routeDeriver = routeDeriver;
        }

        static Rig continueRig(
                String id,
                NotionalToteOrder order,
                OperationalRouteDestination completed,
                OperationalRouteDestination next,
                Optional<P2pPhysicalToteAssignment> assignment,
                ToteLoadPlan completionPlan) {
            return continueRig(id, order, completed, next, assignment, completionPlan, 4,
                    StationRouteContinuationDecision.continueTo(next), order);
        }

        static Rig continueRig(
                String id,
                NotionalToteOrder order,
                OperationalRouteDestination completed,
                OperationalRouteDestination next,
                Optional<P2pPhysicalToteAssignment> assignment,
                ToteLoadPlan completionPlan,
                int capacity,
                StationRouteContinuationDecision decision) {
            return continueRig(id, order, completed, next, assignment, completionPlan, capacity,
                    decision, order);
        }

        static Rig continueRig(
                String id,
                NotionalToteOrder releaseOrder,
                OperationalRouteDestination completed,
                OperationalRouteDestination next,
                Optional<P2pPhysicalToteAssignment> assignment,
                ToteLoadPlan completionPlan,
                int capacity,
                StationRouteContinuationDecision decision,
                NotionalToteOrder catalogOrder) {
            RouteSegment commonEntry = segment("common-entry-" + id, 10f);
            RouteSegment currentTerminal = segment("current-terminal-" + id, 10f);
            RouteSegment nextTerminal = segment("next-terminal-" + id, 10f);
            RouteSegment nextEntry = commonEntry;
            List<WarehouseRouteDefinition> definitions = new ArrayList<>();
            if (!completed.targetId().equals("not-configured")) {
                definitions.add(new WarehouseRouteDefinition(
                        completed, commonEntry, 1f, TravelDirection.FORWARD,
                        "sensor-current-" + id, currentTerminal));
            }
            if (!next.targetId().equals("not-configured")) {
                definitions.add(new WarehouseRouteDefinition(
                        next, commonEntry, 1f, TravelDirection.FORWARD,
                        "sensor-next-" + id, nextTerminal));
            }
            WarehouseRouteCatalog routeCatalog = new WarehouseRouteCatalog(definitions);
            OperationalPhysicalToteSource source = releaseOrder.orderType() == OrderType.EMPTY
                    ? OperationalPhysicalToteSource.AV02
                    : OperationalPhysicalToteSource.OSR;
            PhysicalToteRole role = source == OperationalPhysicalToteSource.AV02
                    ? PhysicalToteRole.PRE_P2P
                    : PhysicalToteRole.INBOUND_PACK;
            PhysicalToteId physicalId = new PhysicalToteId(id);
            OperationalPhysicalToteIdentity identity = new OperationalPhysicalToteIdentity(
                    source,
                    physicalId,
                    releaseOrder.orderSheetKey(),
                    releaseOrder.orderType(),
                    releaseOrder.serviceCentreId(),
                    role,
                    0);
            OperationalPhysicalToteReleaseRequest releaseRequest =
                    new OperationalPhysicalToteReleaseRequest(
                            identity,
                            List.of(releaseOrder.items().getFirst().pharmacyId()),
                            Duration.ZERO,
                            assignment);
            OperationalRouteLaunchRequest launchRequest =
                    new OperationalRouteLaunchRequest(releaseRequest, completed);
            RenderableObject renderable = renderable(id);
            RouteFollower follower = new RouteFollower(id, currentTerminal, currentTerminal.length(), 1d);
            Tote tote = new Tote(id, follower, renderable, new Vec3(), 0f);
            tote.setInteractionMode(Tote.ToteMotionState.HELD);
            tote.closeLids();
            renderable.setVisible(true);
            RoutedPhysicalTote routed = new RoutedPhysicalTote(
                    launchRequest,
                    plan(id),
                    tote,
                    renderable);
            StationProcessingCoordinator coordinator = new StationProcessingCoordinator();
            coordinator.claim(routed, Duration.ZERO);
            coordinator.complete(new PhysicalToteId(id), StationProcessingDispositionType.CONTINUE,
                    completionPlan, Duration.ofSeconds(1));
            MapBackedToteLoadPlanRegistry registry = new MapBackedToteLoadPlanRegistry();
            registry.putLoadPlan(completionPlan);
            FakeResolver resolver = new FakeResolver(decision);
            FakePublisher publisher = new FakePublisher(WarehouseTransportPublicationState.PUBLISHED_EXACT_OBJECTS);
            OsrOutboundTransportQueue queue = new OsrOutboundTransportQueue("queue-" + id, capacity);
            StationProcessingOrderCatalog orderCatalog = catalogOrder == null
                    ? new StationProcessingOrderCatalog(List.of())
                    : new StationProcessingOrderCatalog(List.of(catalogOrder));
            DspRouteDeriver deriver = new DspRouteDeriver(
                    new InMemoryProductMasterRepository(List.of(new ProductMasterRecord(
                            releaseOrder.items().getFirst().productId(),
                            "Product " + id,
                            Optional.of("Y74"),
                            Optional.empty()))));
            StationRouteContinuationController controller = new StationRouteContinuationController(
                    coordinator,
                    orderCatalog,
                    registry,
                    deriver,
                    new StationRouteContinuationSelector(),
                    resolver,
                    routeCatalog,
                    queue,
                    publisher);
            return new Rig(id, releaseOrder, coordinator, registry, resolver, publisher, queue,
                    controller, routed, follower, nextEntry, completionPlan, routeCatalog,
                    orderCatalog, deriver);
        }

        static Rig consume(String id, StationType completedStation, String targetId) {
            NotionalToteOrder order = order(id, OrderType.FULL_PACK,
                    item(id, DspOrderLineType.FULL_PACK), false);
            OperationalRouteDestination completed = destination(completedStation, targetId);
            RouteSegment current = segment("consume-current-" + id, 10f);
            RouteSegment common = segment("consume-entry-" + id, 10f);
            WarehouseRouteCatalog routes = new WarehouseRouteCatalog(List.of(
                    new WarehouseRouteDefinition(completed, common, 1f, TravelDirection.FORWARD,
                            "consume-sensor-" + id, current)));
            RoutedPhysicalTote routed = simpleRouted(id, completed, current);
            StationProcessingCoordinator coordinator = new StationProcessingCoordinator();
            ToteLoadPlan plan = routed.loadPlan();
            FakeResolver resolver = new FakeResolver(StationRouteContinuationDecision.defer("unused"));
            FakePublisher publisher = new FakePublisher(WarehouseTransportPublicationState.PUBLISHED_EXACT_OBJECTS);
            MapBackedToteLoadPlanRegistry registry = new MapBackedToteLoadPlanRegistry();
            registry.putLoadPlan(plan);
            OsrOutboundTransportQueue queue = new OsrOutboundTransportQueue("consume-queue-" + id, 4);
            StationRouteContinuationController controller = new StationRouteContinuationController(
                    coordinator,
                    new StationProcessingOrderCatalog(List.of(order)),
                    registry,
                    new DspRouteDeriver(new InMemoryProductMasterRepository(List.of(
                            new ProductMasterRecord("product-" + id, "Product", Optional.empty(), Optional.empty())))),
                    new StationRouteContinuationSelector(),
                    resolver,
                    routes,
                    queue,
                    publisher);
            StationProcessingOrderCatalog orderCatalog = new StationProcessingOrderCatalog(List.of(order));
            DspRouteDeriver routeDeriver = new DspRouteDeriver(new InMemoryProductMasterRepository(List.of(
                    new ProductMasterRecord("product-" + id, "Product", Optional.empty(), Optional.empty()))));
            return new Rig(id, order, coordinator, registry, resolver, publisher, queue, controller,
                    routed, routed.tote().getRouteFollower(), common, plan, routes, orderCatalog,
                    routeDeriver);
        }

        Rig withCoordinator(StationProcessingCoordinator shared, List<NotionalToteOrder> orders) {
            return new Rig(
                    id,
                    order,
                    shared,
                    registry,
                    targetResolver,
                    publisher,
                    transportQueue,
                    new StationRouteContinuationController(
                            shared,
                            new StationProcessingOrderCatalog(orders),
                            registry,
                            new DspRouteDeriver(new InMemoryProductMasterRepository(List.of(
                                    new ProductMasterRecord(order.items().getFirst().productId(), "Product",
                                            Optional.empty(), Optional.empty())))),
                            new StationRouteContinuationSelector(),
                            targetResolver,
                            new WarehouseRouteCatalog(List.of(new WarehouseRouteDefinition(
                                    routed.destination(), nextEntry, 1f, TravelDirection.FORWARD,
                                    "shared-sensor-" + id, follower.getCurrentSegment()))),
                            transportQueue,
                            publisher),
                    routed,
                    follower,
                    nextEntry,
                    plan,
                    routeCatalog,
                    new StationProcessingOrderCatalog(orders),
                    new DspRouteDeriver(new InMemoryProductMasterRepository(List.of(
                            new ProductMasterRecord(order.items().getFirst().productId(), "Product",
                                    Optional.empty(), Optional.empty())))));
        }

        static RoutedPhysicalTote simpleRouted(String id, OperationalRouteDestination destination) {
            return simpleRouted(id, destination, segment("simple-" + id, 10f));
        }

        static RoutedPhysicalTote simpleRouted(String id, OperationalRouteDestination destination,
                RouteSegment segment) {
            RenderableObject renderable = renderable(id);
            Tote tote = new Tote(id, new RouteFollower(id, segment, segment.length(), 1d), renderable,
                    new Vec3(), 0f);
            return new RoutedPhysicalTote(
                    new OperationalRouteLaunchRequest(
                            new OperationalPhysicalToteReleaseRequest(
                                    new OperationalPhysicalToteIdentity(
                                            OperationalPhysicalToteSource.OSR,
                                            new PhysicalToteId(id),
                                            new OrderSheetKey("simple-" + id, 1),
                                            OrderType.FULL_PACK,
                                            "104",
                                            PhysicalToteRole.INBOUND_PACK,
                                            0),
                                    List.of("pharmacy-" + id),
                                    Duration.ZERO,
                                    Optional.empty()),
                            destination),
                    plan(id),
                    tote,
                    renderable);
        }
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
                new Mat4.ObjectTransformation(0f, 0f, 0f, 0f, 0f, 0f, new Mat4()),
                triangleIndex -> 0,
                false);
    }

    private static final class FakeResolver implements StationRouteContinuationTargetResolver {
        private final StationRouteContinuationDecision decision;
        private final List<StationType> stations = new ArrayList<>();
        private int calls;

        FakeResolver(StationRouteContinuationDecision decision) {
            this.decision = decision;
        }

        @Override
        public StationRouteContinuationDecision resolve(
                StationProcessingDisposition disposition,
                NotionalToteOrder order,
                StationType nextStation) {
            calls++;
            stations.add(nextStation);
            return decision;
        }
    }

    private static final class FakePublisher implements WarehouseTransportPublisher {
        private WarehouseTransportPublicationState state;
        private int publicationStateCalls;
        private int publishCalls;

        FakePublisher(WarehouseTransportPublicationState state) {
            this.state = state;
        }

        @Override
        public boolean contains(PhysicalToteId physicalToteId) {
            return state != WarehouseTransportPublicationState.UNPUBLISHED;
        }

        @Override
        public WarehouseTransportPublicationState publicationState(RoutedPhysicalTote routedTote) {
            publicationStateCalls++;
            return state;
        }

        @Override
        public void publish(RoutedPhysicalTote routedTote) {
            publishCalls++;
        }
    }
}
