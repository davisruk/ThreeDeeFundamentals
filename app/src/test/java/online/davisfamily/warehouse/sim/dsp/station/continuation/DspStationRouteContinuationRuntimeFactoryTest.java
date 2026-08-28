package online.davisfamily.warehouse.sim.dsp.station.continuation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
import online.davisfamily.threedee.sim.framework.SimulationController;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.sim.dsp.adapting.MutableToteLoadPlanRegistry;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRole;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
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
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;
import online.davisfamily.warehouse.sim.dsp.station.processing.StationConsumedToteController;
import online.davisfamily.warehouse.sim.dsp.station.processing.DspStationProcessingRuntimeFactory;
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

class DspStationRouteContinuationRuntimeFactoryTest {

    @Test
    void shouldValidateEveryDependencyBeforeRegistering() {
        ContinuationRuntimeFixture fixture = ContinuationRuntimeFixture.create(true);
        DspStationRouteContinuationRuntimeFactory factory =
                new DspStationRouteContinuationRuntimeFactory();

        assertThrows(IllegalArgumentException.class, () -> factory.create(
                null,
                fixture.coordinator,
                fixture.orderCatalog,
                fixture.loadPlanRegistry,
                fixture.routeDeriver,
                fixture.selector,
                fixture.targetResolver,
                fixture.routeCatalog,
                fixture.transportQueue,
                fixture.publisher));
        assertThrows(IllegalArgumentException.class, () -> factory.create(
                fixture.world,
                null,
                fixture.orderCatalog,
                fixture.loadPlanRegistry,
                fixture.routeDeriver,
                fixture.selector,
                fixture.targetResolver,
                fixture.routeCatalog,
                fixture.transportQueue,
                fixture.publisher));
        assertThrows(IllegalArgumentException.class, () -> factory.create(
                fixture.world,
                fixture.coordinator,
                null,
                fixture.loadPlanRegistry,
                fixture.routeDeriver,
                fixture.selector,
                fixture.targetResolver,
                fixture.routeCatalog,
                fixture.transportQueue,
                fixture.publisher));
        assertThrows(IllegalArgumentException.class, () -> factory.create(
                fixture.world,
                fixture.coordinator,
                fixture.orderCatalog,
                null,
                fixture.routeDeriver,
                fixture.selector,
                fixture.targetResolver,
                fixture.routeCatalog,
                fixture.transportQueue,
                fixture.publisher));
        assertThrows(IllegalArgumentException.class, () -> factory.create(
                fixture.world,
                fixture.coordinator,
                fixture.orderCatalog,
                fixture.loadPlanRegistry,
                null,
                fixture.selector,
                fixture.targetResolver,
                fixture.routeCatalog,
                fixture.transportQueue,
                fixture.publisher));
        assertThrows(IllegalArgumentException.class, () -> factory.create(
                fixture.world,
                fixture.coordinator,
                fixture.orderCatalog,
                fixture.loadPlanRegistry,
                fixture.routeDeriver,
                null,
                fixture.targetResolver,
                fixture.routeCatalog,
                fixture.transportQueue,
                fixture.publisher));
        assertThrows(IllegalArgumentException.class, () -> factory.create(
                fixture.world,
                fixture.coordinator,
                fixture.orderCatalog,
                fixture.loadPlanRegistry,
                fixture.routeDeriver,
                fixture.selector,
                null,
                fixture.routeCatalog,
                fixture.transportQueue,
                fixture.publisher));
        assertThrows(IllegalArgumentException.class, () -> factory.create(
                fixture.world,
                fixture.coordinator,
                fixture.orderCatalog,
                fixture.loadPlanRegistry,
                fixture.routeDeriver,
                fixture.selector,
                fixture.targetResolver,
                null,
                fixture.transportQueue,
                fixture.publisher));
        assertThrows(IllegalArgumentException.class, () -> factory.create(
                fixture.world,
                fixture.coordinator,
                fixture.orderCatalog,
                fixture.loadPlanRegistry,
                fixture.routeDeriver,
                fixture.selector,
                fixture.targetResolver,
                fixture.routeCatalog,
                null,
                fixture.publisher));
        assertThrows(IllegalArgumentException.class, () -> factory.create(
                fixture.world,
                fixture.coordinator,
                fixture.orderCatalog,
                fixture.loadPlanRegistry,
                fixture.routeDeriver,
                fixture.selector,
                fixture.targetResolver,
                fixture.routeCatalog,
                fixture.transportQueue,
                null));

        assertEquals(0, fixture.world.addedControllers().size());
    }

    @Test
    void shouldRegisterOneControllerAndPassExactDependenciesByBehavior() {
        ContinuationRuntimeFixture fixture = ContinuationRuntimeFixture.create(true);
        DspStationRouteContinuationRuntime runtime =
                new DspStationRouteContinuationRuntimeFactory().create(
                        fixture.world,
                        fixture.coordinator,
                        fixture.orderCatalog,
                        fixture.loadPlanRegistry,
                        fixture.routeDeriver,
                        fixture.selector,
                        fixture.targetResolver,
                        fixture.routeCatalog,
                        fixture.transportQueue,
                        fixture.publisher);

        assertEquals(1, fixture.world.addedControllers().size());
        assertInstanceOf(StationRouteContinuationController.class,
                fixture.world.addedControllers().getFirst());
        assertFalse(runtime.isClosed());

        fixture.world.update(0d);

        assertSame(fixture.order, fixture.routeDeriver.lastOrder);
        assertSame(fixture.order, fixture.targetResolver.lastOrder);
        assertSame(fixture.disposition, fixture.targetResolver.lastDisposition);
        assertSame(fixture.routed, fixture.publisher.lastPublicationStatePayload);
        assertSame(fixture.plan, fixture.loadPlanRegistry.lastReturnedPlan);
        assertEquals(1, runtime.snapshot().continuedCount());
        assertEquals(fixture.nextDestination, fixture.transportQueue.peek()
                .orElseThrow().destination());
        assertSame(fixture.routed.tote(), fixture.transportQueue.peek().orElseThrow().tote());
        assertSame(fixture.routed.renderable(), fixture.transportQueue.peek().orElseThrow().renderable());
    }

    @Test
    void shouldRegisterExactlyOneFreshControllerForEachRuntimeCreation() {
        ContinuationRuntimeFixture fixture = ContinuationRuntimeFixture.create(false);
        DspStationRouteContinuationRuntimeFactory factory =
                new DspStationRouteContinuationRuntimeFactory();

        DspStationRouteContinuationRuntime first = factory.create(
                fixture.world,
                fixture.coordinator,
                fixture.orderCatalog,
                fixture.loadPlanRegistry,
                fixture.routeDeriver,
                fixture.selector,
                fixture.targetResolver,
                fixture.routeCatalog,
                fixture.transportQueue,
                fixture.publisher);
        DspStationRouteContinuationRuntime second = factory.create(
                fixture.world,
                fixture.coordinator,
                fixture.orderCatalog,
                fixture.loadPlanRegistry,
                fixture.routeDeriver,
                fixture.selector,
                fixture.targetResolver,
                fixture.routeCatalog,
                fixture.transportQueue,
                fixture.publisher);

        assertEquals(2, fixture.world.addedControllers().size());
        assertNotSame(first, second);
        assertNotSame(fixture.world.addedControllers().get(0),
                fixture.world.addedControllers().get(1));
        assertEquals(first.snapshot(), second.snapshot());
    }

    @Test
    void shouldAppendContinuationAfterMinimalStationProcessingRuntime() {
        ContinuationRuntimeFixture fixture = ContinuationRuntimeFixture.create(false);
        new DspStationProcessingRuntimeFactory().create(
                        fixture.world,
                        fixture.coordinator,
                        List.of(),
                        List.of());

        new DspStationRouteContinuationRuntimeFactory().create(
                fixture.world,
                fixture.coordinator,
                fixture.orderCatalog,
                fixture.loadPlanRegistry,
                fixture.routeDeriver,
                fixture.selector,
                fixture.targetResolver,
                fixture.routeCatalog,
                fixture.transportQueue,
                fixture.publisher);

        assertEquals(List.of(
                StationConsumedToteController.class,
                StationRouteContinuationController.class),
                fixture.world.addedControllers().stream().map(Object::getClass).toList());
    }

    static final class RecordingSimulationWorld extends SimulationWorld {
        private final List<SimulationController> addedControllers = new ArrayList<>();

        @Override
        public void addController(SimulationController controller) {
            addedControllers.add(controller);
            super.addController(controller);
        }

        List<SimulationController> addedControllers() {
            return List.copyOf(addedControllers);
        }
    }

    static final class ContinuationRuntimeFixture {
        final RecordingSimulationWorld world;
        final StationProcessingCoordinator coordinator;
        final NotionalToteOrder order;
        final StationProcessingOrderCatalog orderCatalog;
        final RecordingLoadPlanRegistry loadPlanRegistry;
        final RecordingRouteDeriver routeDeriver;
        final StationRouteContinuationSelector selector;
        final RecordingTargetResolver targetResolver;
        final WarehouseRouteCatalog routeCatalog;
        final OsrOutboundTransportQueue transportQueue;
        final RecordingPublisher publisher;
        final OperationalRouteDestination nextDestination;
        final ToteLoadPlan plan;
        final RoutedPhysicalTote routed;
        final StationProcessingDisposition disposition;

        private ContinuationRuntimeFixture(
                RecordingSimulationWorld world,
                StationProcessingCoordinator coordinator,
                NotionalToteOrder order,
                StationProcessingOrderCatalog orderCatalog,
                RecordingLoadPlanRegistry loadPlanRegistry,
                RecordingRouteDeriver routeDeriver,
                StationRouteContinuationSelector selector,
                RecordingTargetResolver targetResolver,
                WarehouseRouteCatalog routeCatalog,
                OsrOutboundTransportQueue transportQueue,
                RecordingPublisher publisher,
                OperationalRouteDestination nextDestination,
                ToteLoadPlan plan,
                RoutedPhysicalTote routed,
                StationProcessingDisposition disposition) {
            this.world = world;
            this.coordinator = coordinator;
            this.order = order;
            this.orderCatalog = orderCatalog;
            this.loadPlanRegistry = loadPlanRegistry;
            this.routeDeriver = routeDeriver;
            this.selector = selector;
            this.targetResolver = targetResolver;
            this.routeCatalog = routeCatalog;
            this.transportQueue = transportQueue;
            this.publisher = publisher;
            this.nextDestination = nextDestination;
            this.plan = plan;
            this.routed = routed;
            this.disposition = disposition;
        }

        static ContinuationRuntimeFixture create(boolean seedContinuation) {
            String id = seedContinuation ? "runtime-factory" : "runtime-empty";
            NotionalToteOrder order = new NotionalToteOrder(
                    "order-" + id,
                    "notional-" + id,
                    "104",
                    1,
                    OrderType.FULL_PACK,
                    List.of(new DspOrderItem(
                            "line-" + id,
                            "product-" + id,
                            1,
                            "pharmacy-" + id,
                            "patient-" + id,
                            "prescription-" + id,
                            DspOrderLineType.FULL_PACK,
                            "order-" + id,
                            1,
                            0)),
                    0,
                    0);
            OperationalRouteDestination completedDestination =
                    new OperationalRouteDestination(StationType.THIRD_PARTY, "third-party-" + id);
            OperationalRouteDestination nextDestination =
                    new OperationalRouteDestination(StationType.P2P, "p2p-" + id);
            RouteSegment commonEntry = segment("common-entry-" + id, 10f);
            RouteSegment currentTerminal = segment("current-terminal-" + id, 10f);
            RouteSegment nextTerminal = segment("next-terminal-" + id, 10f);
            WarehouseRouteCatalog routeCatalog = new WarehouseRouteCatalog(List.of(
                    new WarehouseRouteDefinition(
                            completedDestination,
                            commonEntry,
                            1f,
                            TravelDirection.FORWARD,
                            "current-sensor-" + id,
                            currentTerminal),
                    new WarehouseRouteDefinition(
                            nextDestination,
                            commonEntry,
                            1f,
                            TravelDirection.FORWARD,
                            "next-sensor-" + id,
                            nextTerminal)));

            PhysicalToteId physicalToteId = new PhysicalToteId(id);
            P2pPhysicalToteAssignment assignment = new P2pPhysicalToteAssignment(
                    physicalToteId,
                    "104",
                    new P2pLineId(nextDestination.targetId()),
                    nextDestination);
            OperationalPhysicalToteIdentity identity = new OperationalPhysicalToteIdentity(
                    OperationalPhysicalToteSource.OSR,
                    physicalToteId,
                    order.orderSheetKey(),
                    order.orderType(),
                    order.serviceCentreId(),
                    PhysicalToteRole.INBOUND_PACK,
                    0);
            OperationalPhysicalToteReleaseRequest releaseRequest =
                    new OperationalPhysicalToteReleaseRequest(
                            identity,
                            List.of(order.items().getFirst().pharmacyId()),
                            Duration.ZERO,
                            Optional.of(assignment));
            OperationalRouteLaunchRequest launchRequest =
                    new OperationalRouteLaunchRequest(releaseRequest, completedDestination);
            RenderableObject renderable = renderable(id);
            RouteFollower follower = new RouteFollower(
                    id,
                    currentTerminal,
                    currentTerminal.length(),
                    1d);
            Tote tote = new Tote(id, follower, renderable, new Vec3(), 0f);
            tote.closeLids();
            tote.setInteractionMode(Tote.ToteMotionState.HELD);
            renderable.setVisible(true);
            ToteLoadPlan plan = new ToteLoadPlan(physicalToteId, List.of());
            RoutedPhysicalTote routed = new RoutedPhysicalTote(
                    launchRequest,
                    plan,
                    tote,
                    renderable);

            StationProcessingCoordinator coordinator = new StationProcessingCoordinator();
            StationProcessingDisposition disposition = null;
            if (seedContinuation) {
                coordinator.claim(routed, Duration.ZERO);
                disposition = coordinator.complete(
                        physicalToteId,
                        StationProcessingDispositionType.CONTINUE,
                        plan,
                        Duration.ofSeconds(1));
            }
            RecordingLoadPlanRegistry loadPlanRegistry = new RecordingLoadPlanRegistry(plan);
            loadPlanRegistry.putLoadPlan(plan);
            RecordingRouteDeriver routeDeriver = new RecordingRouteDeriver();
            RecordingTargetResolver targetResolver = new RecordingTargetResolver(
                    StationRouteContinuationDecision.continueTo(nextDestination));
            RecordingPublisher publisher = new RecordingPublisher();
            return new ContinuationRuntimeFixture(
                    new RecordingSimulationWorld(),
                    coordinator,
                    order,
                    new StationProcessingOrderCatalog(List.of(order)),
                    loadPlanRegistry,
                    routeDeriver,
                    new StationRouteContinuationSelector(),
                    targetResolver,
                    routeCatalog,
                    new OsrOutboundTransportQueue("queue-" + id, 2),
                    publisher,
                    nextDestination,
                    plan,
                    routed,
                    disposition);
        }

        private static RouteSegment segment(String label, float length) {
            return new RouteSegment(
                    label,
                    new LinearSegment3(new Vec3(), new Vec3(length, 0f, 0f), false));
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
    }

    static final class RecordingLoadPlanRegistry implements MutableToteLoadPlanRegistry {
        private final ToteLoadPlan plan;
        PhysicalToteId lastPhysicalToteId;
        ToteLoadPlan lastReturnedPlan;

        RecordingLoadPlanRegistry(ToteLoadPlan plan) {
            this.plan = plan;
        }

        @Override
        public ToteLoadPlan getLoadPlanFor(PhysicalToteId physicalToteId) {
            lastPhysicalToteId = physicalToteId;
            lastReturnedPlan = plan;
            return plan;
        }

        @Override
        public void putLoadPlan(ToteLoadPlan toteLoadPlan) {
            if (toteLoadPlan != plan) {
                throw new AssertionError("fixture registry only accepts its exact plan");
            }
        }
    }

    static final class RecordingRouteDeriver extends DspRouteDeriver {
        NotionalToteOrder lastOrder;

        RecordingRouteDeriver() {
            super(new InMemoryProductMasterRepository(List.of()));
        }

        @Override
        public RouteRequirements derive(NotionalToteOrder order) {
            lastOrder = order;
            return new RouteRequirements(
                    true,
                    false,
                    false,
                    true,
                    false,
                    StartLocation.OSR);
        }
    }

    static final class RecordingTargetResolver implements StationRouteContinuationTargetResolver {
        private final StationRouteContinuationDecision decision;
        StationProcessingDisposition lastDisposition;
        NotionalToteOrder lastOrder;
        StationType lastNextStation;

        RecordingTargetResolver(StationRouteContinuationDecision decision) {
            this.decision = decision;
        }

        @Override
        public StationRouteContinuationDecision resolve(
                StationProcessingDisposition disposition,
                NotionalToteOrder order,
                StationType nextStation) {
            lastDisposition = disposition;
            lastOrder = order;
            lastNextStation = nextStation;
            return decision;
        }
    }

    static final class RecordingPublisher implements WarehouseTransportPublisher {
        RoutedPhysicalTote lastPublicationStatePayload;

        @Override
        public boolean contains(PhysicalToteId physicalToteId) {
            return false;
        }

        @Override
        public WarehouseTransportPublicationState publicationState(
                RoutedPhysicalTote routedTote) {
            lastPublicationStatePayload = routedTote;
            return WarehouseTransportPublicationState.PUBLISHED_EXACT_OBJECTS;
        }

        @Override
        public void publish(RoutedPhysicalTote routedTote) {
            throw new AssertionError("continuation must not publish a second object");
        }
    }
}
