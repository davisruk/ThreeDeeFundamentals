package online.davisfamily.warehouse.sim.dsp.osr.release.launch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
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
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseRequest;
import online.davisfamily.warehouse.sim.dsp.transport.OsrOutboundToteHydrationException;
import online.davisfamily.warehouse.sim.dsp.transport.OsrOutboundTransportQueue;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

class OsrOutboundRouteLaunchControllerTest {

    @Test
    void shouldHydrateAtMostOneMixedDestinationRequestPerUpdateInGlobalFifoOrder() {
        OsrOutboundRouteLaunchQueue launchQueue = launchQueue(3);
        OsrOutboundTransportQueue transportQueue = transportQueue(3);
        OperationalRouteLaunchRequest first = request("tote-1", StationType.P2P, "p2p-1");
        OperationalRouteLaunchRequest second = request(
                "tote-2", StationType.THIRD_PARTY, "third-party-1");
        OperationalRouteLaunchRequest third = request(
                "tote-3", StationType.ADAPTING, "adapting-1");
        launchQueue.enqueue(first);
        launchQueue.enqueue(second);
        launchQueue.enqueue(third);
        AtomicInteger hydrationCount = new AtomicInteger();
        OsrOutboundRouteLaunchController controller = new OsrOutboundRouteLaunchController(
                launchQueue,
                transportQueue,
                launchRequest -> {
                    hydrationCount.incrementAndGet();
                    return routed(launchRequest);
                });

        controller.update(new SimulationContext(), 0.1d);

        assertEquals(1, hydrationCount.get());
        assertEquals(2, launchQueue.snapshot().occupancy());
        assertEquals(1, transportQueue.snapshot().occupancy());
        assertSame(first, transportQueue.peek().orElseThrow().launchRequest());
        assertSame(second, launchQueue.peek().orElseThrow());
        OsrOutboundRouteLaunchControllerSnapshot firstSnapshot = controller.snapshot();
        assertEquals(1, firstSnapshot.successfulHydrationCount());
        assertEquals(first.physicalToteId(),
                firstSnapshot.lastHydratedPhysicalToteId().orElseThrow());
        assertSame(first.destination(),
                firstSnapshot.lastHydratedDestination().orElseThrow());

        controller.update(new SimulationContext(), 0d);

        assertEquals(2, hydrationCount.get());
        assertSame(third, launchQueue.peek().orElseThrow());
        assertSame(first, transportQueue.dequeue().orElseThrow().launchRequest());
        assertSame(second, transportQueue.dequeue().orElseThrow().launchRequest());
        assertEquals(1, firstSnapshot.successfulHydrationCount());
        assertEquals(first.physicalToteId(),
                firstSnapshot.lastHydratedPhysicalToteId().orElseThrow());
    }

    @Test
    void shouldApplyBackpressureBeforeHydrationAndRecoverWhenTransportCapacityReturns() {
        OsrOutboundRouteLaunchQueue launchQueue = launchQueue(1);
        OsrOutboundTransportQueue transportQueue = transportQueue(1);
        OperationalRouteLaunchRequest request = request(
                "waiting", StationType.P2P, "p2p-1");
        launchQueue.enqueue(request);
        transportQueue.enqueue(routed(request(
                "occupying", StationType.ADAPTING, "adapting-1")));
        AtomicInteger hydrationCount = new AtomicInteger();
        OsrOutboundRouteLaunchController controller = new OsrOutboundRouteLaunchController(
                launchQueue,
                transportQueue,
                launchRequest -> {
                    hydrationCount.incrementAndGet();
                    return routed(launchRequest);
                });

        controller.update(new SimulationContext(), 0.1d);

        assertEquals(0, hydrationCount.get());
        assertSame(request, launchQueue.peek().orElseThrow());
        assertTrue(controller.snapshot().blocked());
        assertEquals(request.physicalToteId(),
                controller.snapshot().blockedPhysicalToteId().orElseThrow());
        assertTrue(controller.snapshot().blockedReason().contains("no capacity"));

        transportQueue.dequeue().orElseThrow();
        controller.update(new SimulationContext(), 0.1d);

        assertEquals(1, hydrationCount.get());
        assertTrue(launchQueue.peek().isEmpty());
        assertSame(request, transportQueue.peek().orElseThrow().launchRequest());
        assertFalse(controller.snapshot().blocked());
    }

    @Test
    void shouldRetainHeadAfterExpectedHydrationFailureAndRetryDeterministically() {
        OsrOutboundRouteLaunchQueue launchQueue = launchQueue(1);
        OsrOutboundTransportQueue transportQueue = transportQueue(1);
        OperationalRouteLaunchRequest request = request(
                "missing-plan", StationType.THIRD_PARTY, "third-party-1");
        launchQueue.enqueue(request);
        AtomicBoolean planAvailable = new AtomicBoolean();
        AtomicInteger hydrationCount = new AtomicInteger();
        OsrOutboundRouteLaunchController controller = new OsrOutboundRouteLaunchController(
                launchQueue,
                transportQueue,
                launchRequest -> {
                    hydrationCount.incrementAndGet();
                    if (!planAvailable.get()) {
                        throw new OsrOutboundToteHydrationException(
                                launchRequest.physicalToteId(), "load plan is unavailable");
                    }
                    return routed(launchRequest);
                });

        controller.update(new SimulationContext(), 0.1d);

        assertSame(request, launchQueue.peek().orElseThrow());
        assertTrue(transportQueue.peek().isEmpty());
        assertTrue(controller.snapshot().blockedReason().contains("load plan is unavailable"));

        planAvailable.set(true);
        controller.update(new SimulationContext(), 0.1d);

        assertEquals(2, hydrationCount.get());
        assertTrue(launchQueue.peek().isEmpty());
        assertSame(request, transportQueue.peek().orElseThrow().launchRequest());
    }

    @Test
    void shouldRetainBothQueuesForNullOrMismatchedHydrationResult() {
        OperationalRouteLaunchRequest head = request(
                "head", StationType.P2P, "p2p-1");
        assertBlockedWithoutMutation(head, launchRequest -> null, "returned null");

        OperationalRouteLaunchRequest mismatched = request(
                "head", StationType.ADAPTING, "adapting-1");
        assertBlockedWithoutMutation(
                head,
                launchRequest -> routed(mismatched),
                "exact launch request");
    }

    @Test
    void shouldRejectDuplicateTransportIdentityWithoutHydration() {
        OsrOutboundRouteLaunchQueue launchQueue = launchQueue(1);
        OsrOutboundTransportQueue transportQueue = transportQueue(2);
        OperationalRouteLaunchRequest request = request(
                "duplicate", StationType.P2P, "p2p-1");
        launchQueue.enqueue(request);
        transportQueue.enqueue(routed(request));
        AtomicInteger hydrationCount = new AtomicInteger();
        OsrOutboundRouteLaunchController controller = new OsrOutboundRouteLaunchController(
                launchQueue,
                transportQueue,
                launchRequest -> {
                    hydrationCount.incrementAndGet();
                    return routed(launchRequest);
                });

        controller.update(new SimulationContext(), 0.1d);

        assertEquals(0, hydrationCount.get());
        assertSame(request, launchQueue.peek().orElseThrow());
        assertEquals(1, transportQueue.snapshot().occupancy());
        assertTrue(controller.snapshot().blockedReason().contains("already present"));
    }

    @Test
    void shouldKeepSourceOwnedUntilHydrationAndTransportAcceptance() {
        OsrOutboundRouteLaunchQueue launchQueue = launchQueue(1);
        OsrOutboundTransportQueue transportQueue = transportQueue(1);
        OperationalRouteLaunchRequest request = request(
                "ordered", StationType.ADAPTING, "adapting-1");
        launchQueue.enqueue(request);
        OsrOutboundRouteLaunchController controller = new OsrOutboundRouteLaunchController(
                launchQueue,
                transportQueue,
                launchRequest -> {
                    assertSame(request, launchQueue.peek().orElseThrow());
                    assertTrue(transportQueue.peek().isEmpty());
                    return routed(launchRequest);
                });

        controller.update(new SimulationContext(), 0.1d);

        assertTrue(launchQueue.peek().isEmpty());
        assertSame(request, transportQueue.peek().orElseThrow().launchRequest());
    }

    @Test
    void shouldRemainIdleAndPropagateUnexpectedFailuresAndRejectInvalidUpdates() {
        OsrOutboundRouteLaunchQueue launchQueue = launchQueue(1);
        OsrOutboundTransportQueue transportQueue = transportQueue(1);
        AtomicInteger hydrationCount = new AtomicInteger();
        OsrOutboundRouteLaunchController idleController = new OsrOutboundRouteLaunchController(
                launchQueue,
                transportQueue,
                launchRequest -> {
                    hydrationCount.incrementAndGet();
                    return routed(launchRequest);
                });

        idleController.update(new SimulationContext(), 0d);
        assertEquals(0, hydrationCount.get());
        assertFalse(idleController.snapshot().blocked());

        OperationalRouteLaunchRequest request = request(
                "unexpected", StationType.P2P, "p2p-1");
        launchQueue.enqueue(request);
        IllegalStateException failure = new IllegalStateException("unexpected failure");
        OsrOutboundRouteLaunchController failingController =
                new OsrOutboundRouteLaunchController(
                        launchQueue,
                        transportQueue,
                        launchRequest -> {
                            throw failure;
                        });

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> failingController.update(new SimulationContext(), 0.1d)));
        assertSame(request, launchQueue.peek().orElseThrow());
        assertTrue(transportQueue.peek().isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> idleController.update(null, 0.1d));
        assertThrows(IllegalArgumentException.class,
                () -> idleController.update(new SimulationContext(), -0.1d));
        assertThrows(IllegalArgumentException.class,
                () -> idleController.update(new SimulationContext(), Double.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> idleController.update(new SimulationContext(), Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class,
                () -> new OsrOutboundRouteLaunchController(null, transportQueue,
                        launchRequest -> routed(launchRequest)));
        assertThrows(IllegalArgumentException.class,
                () -> new OsrOutboundRouteLaunchController(launchQueue, null,
                        launchRequest -> routed(launchRequest)));
        assertThrows(IllegalArgumentException.class,
                () -> new OsrOutboundRouteLaunchController(launchQueue, transportQueue, null));
    }

    private static void assertBlockedWithoutMutation(
            OperationalRouteLaunchRequest head,
            online.davisfamily.warehouse.sim.dsp.transport.OsrOutboundToteHydrator hydrator,
            String reasonFragment) {
        OsrOutboundRouteLaunchQueue launchQueue = launchQueue(1);
        OsrOutboundTransportQueue transportQueue = transportQueue(1);
        launchQueue.enqueue(head);
        OsrOutboundRouteLaunchController controller = new OsrOutboundRouteLaunchController(
                launchQueue, transportQueue, hydrator);

        controller.update(new SimulationContext(), 0.1d);

        assertSame(head, launchQueue.peek().orElseThrow());
        assertTrue(transportQueue.peek().isEmpty());
        assertTrue(controller.snapshot().blockedReason().contains(reasonFragment));
        assertEquals(0, controller.snapshot().successfulHydrationCount());
    }

    private static OsrOutboundRouteLaunchQueue launchQueue(int capacity) {
        return new OsrOutboundRouteLaunchQueue("launch", capacity);
    }

    private static OsrOutboundTransportQueue transportQueue(int capacity) {
        return new OsrOutboundTransportQueue("transport", capacity);
    }

    private static OperationalRouteLaunchRequest request(
            String physicalToteId,
            StationType stationType,
            String targetId) {
        InboundToteManifest manifest = new InboundToteManifest(
                new PhysicalToteId(physicalToteId),
                new OrderSheetKey("order-" + physicalToteId, 1),
                OrderType.FULL_PACK,
                "104",
                List.of(new DspOrderItem(
                        "line-" + physicalToteId,
                        "product-" + physicalToteId,
                        1)),
                0);
        return OperationalRouteLaunchRequestFactory.fromOsr(
                new OsrProcessingReleaseRequest(manifest, Duration.ofSeconds(5)),
                new OperationalRouteDestination(stationType, targetId));
    }

    private static RoutedPhysicalTote routed(OperationalRouteLaunchRequest launchRequest) {
        String physicalToteId = launchRequest.physicalToteId().value();
        RenderableObject renderable = RenderableObject.create(
                physicalToteId,
                null,
                anchorMesh(),
                new Mat4.ObjectTransformation(0f, 0f, 0f, 0f, 0f, 0f, new Mat4()),
                triangleIndex -> 0,
                false);
        RouteSegment routeSegment = new RouteSegment(
                "osr-outbound-test-route-" + physicalToteId,
                new LinearSegment3(
                        new Vec3(0f, 0f, 0f),
                        new Vec3(1f, 0f, 0f),
                        false));
        Tote tote = new Tote(
                physicalToteId,
                new RouteFollower(physicalToteId, routeSegment, 0f, 1d),
                renderable,
                new Vec3(),
                0f);
        return new RoutedPhysicalTote(
                launchRequest,
                new ToteLoadPlan(launchRequest.physicalToteId(), List.of()),
                tote,
                renderable);
    }

    private static Mesh anchorMesh() {
        return new Mesh(
                new Vec4[] {
                        new Vec4(0f, 0f, 0f, 1f),
                        new Vec4(0f, 0f, 0f, 1f),
                        new Vec4(0f, 0f, 0f, 1f)
                },
                new int[][] { {0, 1, 2} },
                "anchor");
    }
}
