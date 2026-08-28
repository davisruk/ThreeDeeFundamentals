package online.davisfamily.warehouse.sim.dsp.transport.routing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.threedee.sim.framework.events.DetectionEvent;
import online.davisfamily.threedee.sim.framework.events.DetectionEvent.DetectionType;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.StartLocation;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteLaunchRequestFactory;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OsrOutboundRouteLaunchQueue;
import online.davisfamily.warehouse.sim.dsp.osr.release.route.OperationalRouteEntryQueue;
import online.davisfamily.warehouse.sim.dsp.osr.release.route.OperationalRouteTargetDefinition;
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspOrderStatus;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;
import online.davisfamily.warehouse.sim.dsp.transport.OsrOutboundTransportQueue;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;
import online.davisfamily.warehouse.sim.tote.Tote.ToteMotionState;
import online.davisfamily.warehouse.sim.totebag.assembly.TipperInputQueue;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;
import online.davisfamily.warehouse.sim.transfer.TransferRoutingDecision;
import online.davisfamily.warehouse.sim.transfer.TransferZoneMachine;

class DspWarehouseTransportRoutingScenarioTest {

    @Test
    void shouldReenterExactPublishedObjectsThroughASecondTransportArrivalLeg() {
        WarehouseTransferRoutingTableTest.Topology topology =
                WarehouseTransferRoutingTableTest.topology();
        OperationalRouteDestination destination = topology.p2p();
        WarehouseRouteDefinition definition = topology.catalog().find(destination).orElseThrow();
        OsrOutboundTransportQueue transportQueue = new OsrOutboundTransportQueue("reentry", 2);
        WarehouseTransportInFlightRegistry inFlight = new WarehouseTransportInFlightRegistry(1);
        StationRoutedToteArrivalQueue arrivalQueue =
                new StationRoutedToteArrivalQueue(destination, 2);
        StationRoutedToteArrivalRegistry arrivals =
                new StationRoutedToteArrivalRegistry(List.of(arrivalQueue));
        SimulationContext context = new SimulationContext();
        List<RenderableObject> renderables = new ArrayList<>();
        List<RoutedPhysicalTote> published = new ArrayList<>();
        Map<String, RoutedPhysicalTote> publishedById = new LinkedHashMap<>();
        WarehouseTransportPublisher publisher = new WarehouseTransportPublisher() {
            @Override
            public boolean contains(online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId id) {
                return publishedById.containsKey(id.value());
            }

            @Override
            public WarehouseTransportPublicationState publicationState(RoutedPhysicalTote tote) {
                RoutedPhysicalTote existing = publishedById.get(tote.physicalToteId().value());
                if (existing == null) {
                    return WarehouseTransportPublicationState.UNPUBLISHED;
                }
                return existing.tote() == tote.tote()
                        && existing.renderable() == tote.renderable()
                        ? WarehouseTransportPublicationState.PUBLISHED_EXACT_OBJECTS
                        : WarehouseTransportPublicationState.PHYSICAL_ID_CONFLICT;
            }

            @Override
            public void publish(RoutedPhysicalTote tote) {
                if (publishedById.containsKey(tote.physicalToteId().value())) {
                    throw new IllegalArgumentException("duplicate publication");
                }
                publishedById.put(tote.physicalToteId().value(), tote);
                published.add(tote);
                renderables.add(tote.renderable());
                context.addTrackedObject(tote.tote());
            }
        };
        WarehouseTransportIngressController ingress = new WarehouseTransportIngressController(
                transportQueue, topology.catalog(), inFlight, publisher);
        WarehouseTransportArrivalController arrival = new WarehouseTransportArrivalController(
                topology.catalog(), inFlight, arrivals);
        RoutedPhysicalTote initial = source("reentry", destination);
        initial.tote().getRouteFollower().setCurrentSegment(definition.entrySegment());
        initial.tote().getRouteFollower().setDistanceAlongSegment(definition.entryDistance());
        initial.tote().getRouteFollower().setTravelDirection(definition.entryDirection());
        transportQueue.enqueue(initial);

        ingress.update(context, 0d);
        assertEquals(1, published.size());
        assertEquals(1, inFlight.snapshot().occupancy());

        initial.tote().getRouteFollower().setCurrentSegment(definition.terminalSegment());
        arrival.handleDetection(new DetectionEvent(
                "terminal",
                0d,
                definition.terminalArrivalSensorId(),
                initial.physicalToteId().value(),
                DetectionType.ENTER),
                context);
        arrival.update(context, 0d);
        assertSame(initial, arrivalQueue.dequeue().orElseThrow());
        assertEquals(0, inFlight.snapshot().occupancy());

        RoutedPhysicalTote continued = new RoutedPhysicalTote(
                OperationalRouteLaunchRequestFactory.continueTo(
                        initial.launchRequest(), destination),
                initial.loadPlan(),
                initial.tote(),
                initial.renderable());
        continued.tote().setInteractionMode(ToteMotionState.HELD);
        continued.tote().getRouteFollower().setCurrentSegment(definition.entrySegment());
        continued.tote().getRouteFollower().setDistanceAlongSegment(definition.entryDistance());
        continued.tote().getRouteFollower().setTravelDirection(definition.entryDirection());
        transportQueue.enqueue(continued);

        ingress.update(context, 0d);
        assertEquals(1, published.size());
        assertSame(continued, inFlight.find(continued.physicalToteId()).orElseThrow());
        assertEquals(ToteMotionState.MOVING, continued.tote().getInteractionMode());
        assertEquals(1, renderables.size());
        assertEquals(1, context.getTrackedObjects().size());
        assertEquals(1, ingress.snapshot().initialPublicationCount());
        assertEquals(1, ingress.snapshot().exactObjectReentryCount());

        continued.tote().getRouteFollower().setCurrentSegment(definition.terminalSegment());
        arrival.handleDetection(new DetectionEvent(
                "terminal",
                0d,
                definition.terminalArrivalSensorId(),
                continued.physicalToteId().value(),
                DetectionType.ENTER),
                context);
        arrival.update(context, 0d);

        assertSame(continued, arrivalQueue.peek().orElseThrow());
        assertEquals(0, inFlight.snapshot().occupancy());
        assertEquals(WarehouseTransportPublicationState.PUBLISHED_EXACT_OBJECTS,
                publisher.publicationState(continued));
    }

    @Test
    void shouldRouteMixedDestinationsFromOneLaunchFifoToExactArrivalQueues() {
        WarehouseTransferRoutingTableTest.Topology topology =
                WarehouseTransferRoutingTableTest.topology();
        List<RoutedPhysicalTote> sources = List.of(
                source("third-1", topology.thirdParty()),
                source("adapting-1", topology.adapting()),
                source("p2p-1", topology.p2p()));
        Scenario scenario = new Scenario(topology, sources, 3, Map.of(
                topology.thirdParty().targetId(), 2,
                topology.adapting().targetId(), 2,
                topology.p2p().targetId(), 2));
        List<DspSchedulerOrderState> logicalStates = sources.stream()
                .map(DspWarehouseTransportRoutingScenarioTest::logicalState)
                .toList();
        List<DspOrderStatus> originalStatuses = logicalStates.stream()
                .map(DspSchedulerOrderState::status)
                .toList();

        scenario.enqueueAndPublishAll();

        assertEquals(
                sources.stream().map(RoutedPhysicalTote::destination).toList(),
                scenario.published.stream().map(RoutedPhysicalTote::destination).toList());
        assertEquals(3, scenario.published.size());
        assertEquals(3, scenario.published.stream()
                .map(RoutedPhysicalTote::physicalToteId)
                .collect(java.util.stream.Collectors.toSet()).size());
        assertEquals(3, scenario.published.stream()
                .map(RoutedPhysicalTote::renderable)
                .collect(java.util.stream.Collectors.toSet()).size());
        for (RoutedPhysicalTote tote : scenario.published) {
            assertSame(topology.catalog().commonEntrySegment(),
                    tote.tote().getRouteFollower().getCurrentSegment());
        }

        RoutedPhysicalTote thirdParty = scenario.published.get(0);
        RoutedPhysicalTote adapting = scenario.published.get(1);
        RoutedPhysicalTote p2p = scenario.published.get(2);
        assertTrue(scenario.decision(topology.firstMachine(), thirdParty)
                .isContinueOnCurrentRoute());
        assertSame(topology.sharedBranchTarget(),
                scenario.decision(topology.firstMachine(), adapting).transferTarget());
        assertSame(topology.adaptingTarget(),
                scenario.decision(topology.secondMachine(), adapting).transferTarget());
        assertSame(topology.sharedBranchTarget(),
                scenario.decision(topology.firstMachine(), p2p).transferTarget());
        assertTrue(scenario.decision(topology.secondMachine(), p2p)
                .isContinueOnCurrentRoute());

        scenario.detectAtTerminal(thirdParty);
        scenario.detectAtTerminal(adapting);
        scenario.detectAtTerminal(p2p);
        scenario.arrivePending(3);

        assertSame(thirdParty, scenario.arrivalQueue(topology.thirdParty()).peek()
                .orElseThrow());
        assertSame(adapting, scenario.arrivalQueue(topology.adapting()).peek()
                .orElseThrow());
        assertSame(p2p, scenario.arrivalQueue(topology.p2p()).peek().orElseThrow());
        assertEquals(0, scenario.runtime.inFlightSnapshot().occupancy());
        assertEquals(ToteMotionState.HELD, p2p.tote().getInteractionMode());

        for (int i = 0; i < sources.size(); i++) {
            RoutedPhysicalTote source = sources.get(i);
            RoutedPhysicalTote arrived = scenario.arrivalQueue(source.destination())
                    .peek().orElseThrow();
            if (scenario.arrivalQueue(source.destination()).snapshot().occupancy() == 1) {
                assertSame(source.launchRequest(), arrived.launchRequest());
                assertSame(source.launchRequest().releaseRequest(),
                        arrived.launchRequest().releaseRequest());
                assertSame(source.loadPlan(), arrived.loadPlan());
                assertEquals(source.destination(), arrived.destination());
            }
        }

        TipperInputQueue tipperQueue = new TipperInputQueue("tipper", 2);
        assertEquals(0, tipperQueue.snapshot().toteIds().size());
        for (OperationalRouteEntryQueue legacyQueue : legacyQueues(topology)) {
            assertEquals(0, legacyQueue.snapshot().occupancy());
        }
        assertEquals(originalStatuses,
                logicalStates.stream().map(DspSchedulerOrderState::status).toList());
    }

    @Test
    void shouldRetainTransportHeadWhenInFlightCapacityIsFullWithoutRepublishing() {
        WarehouseTransferRoutingTableTest.Topology topology =
                WarehouseTransferRoutingTableTest.topology();
        List<RoutedPhysicalTote> sources = List.of(
                source("first", topology.p2p()),
                source("second", topology.p2p()));
        Scenario scenario = new Scenario(topology, sources, 1, capacities(topology, 2));
        sources.forEach(source -> scenario.launchQueue.enqueue(source.launchRequest()));

        scenario.world.update(0d);
        scenario.world.update(0d);
        scenario.world.update(0d);

        assertEquals(1, scenario.published.size());
        assertEquals(1, scenario.runtime.inFlightSnapshot().occupancy());
        assertEquals(sources.get(1).physicalToteId(), scenario.runtime
                .outboundTransportSnapshot().entries().get(0).physicalToteId());
        assertTrue(scenario.runtime.ingressController().snapshot().blockedReason()
                .contains("capacity"));
        assertEquals(sources.get(1).physicalToteId(), scenario.runtime
                .routeLaunchController().snapshot().lastHydratedPhysicalToteId()
                .orElseThrow());
    }

    @Test
    void shouldHoldFullArrivalAndLaterPreserveDestinationFifoIdentity() {
        WarehouseTransferRoutingTableTest.Topology topology =
                WarehouseTransferRoutingTableTest.topology();
        List<RoutedPhysicalTote> sources = List.of(
                source("first", topology.p2p()),
                source("second", topology.p2p()));
        Scenario scenario = new Scenario(topology, sources, 2, capacities(topology, 1));
        scenario.enqueueAndPublishAll();
        StationRoutedToteArrivalQueue p2pQueue = scenario.arrivalQueue(topology.p2p());
        RoutedPhysicalTote occupying = source("occupying", topology.p2p());
        p2pQueue.enqueue(occupying);

        scenario.detectAtTerminal(scenario.published.get(0));
        scenario.detectAtTerminal(scenario.published.get(1));
        scenario.arrivePending(1);

        assertEquals(2, scenario.runtime.inFlightSnapshot().occupancy());
        assertEquals(2, scenario.runtime.arrivalController().snapshot()
                .pendingArrivals().size());
        assertEquals(ToteMotionState.HELD,
                scenario.published.get(0).tote().getInteractionMode());

        assertSame(occupying, p2pQueue.dequeue().orElseThrow());
        scenario.arrivePending(1);
        assertSame(scenario.published.get(0), p2pQueue.dequeue().orElseThrow());
        scenario.arrivePending(1);
        assertSame(scenario.published.get(1), p2pQueue.dequeue().orElseThrow());
        assertEquals(0, scenario.runtime.inFlightSnapshot().occupancy());
    }

    @Test
    void shouldBlockUnknownRouteTransferAndSensorWithoutTeleportationOrLoss() {
        WarehouseTransferRoutingTableTest.Topology topology =
                WarehouseTransferRoutingTableTest.topology();
        RoutedPhysicalTote known = source("known", topology.p2p());
        Scenario scenario = new Scenario(
                topology, List.of(known), 1, capacities(topology, 1));
        scenario.enqueueAndPublishAll();
        RoutedPhysicalTote active = scenario.published.get(0);

        DestinationAwareTransferTargetDecisionStrategy firstStrategy =
                scenario.strategies.get(topology.firstMachine().getId());
        assertTrue(firstStrategy.decide(active.tote(), topology.secondMachine()).isEmpty());
        assertTrue(firstStrategy.blocked());
        assertEquals(1, scenario.runtime.inFlightSnapshot().occupancy());
        assertEquals(0, scenario.arrivalQueue(topology.p2p()).snapshot().occupancy());

        scenario.runtime.arrivalController().handleDetection(new DetectionEvent(
                "source", 0d, "unknown-sensor", active.physicalToteId().value(),
                DetectionType.ENTER), scenario.context);
        assertEquals(ToteMotionState.MOVING, active.tote().getInteractionMode());
        assertEquals(1, scenario.runtime.inFlightSnapshot().occupancy());

        WarehouseRouteDefinition wrongTerminal = topology.catalog()
                .find(topology.adapting()).orElseThrow();
        active.tote().getRouteFollower().setCurrentSegment(
                wrongTerminal.terminalSegment());
        scenario.runtime.arrivalController().handleDetection(new DetectionEvent(
                "source",
                0d,
                wrongTerminal.terminalArrivalSensorId(),
                active.physicalToteId().value(),
                DetectionType.ENTER), scenario.context);
        assertTrue(scenario.runtime.arrivalController().snapshot().blockedReason()
                .contains("destination"));
        assertEquals(1, scenario.runtime.inFlightSnapshot().occupancy());

        OperationalRouteDestination unknown =
                new OperationalRouteDestination(StationType.P2P, "unknown-target");
        RoutedPhysicalTote unknownSource = source("unknown", unknown);
        scenario.loadPlans.put(unknownSource.physicalToteId().value(),
                unknownSource.loadPlan());
        scenario.launchQueue.enqueue(unknownSource.launchRequest());
        scenario.world.update(0d);

        assertSame(unknownSource.launchRequest(), scenario.launchQueue.peek().orElseThrow());
        assertTrue(scenario.runtime.routeLaunchController().snapshot().blockedReason()
                .contains("invalid payload"));
        assertEquals(1, scenario.published.size());
    }

    private static Map<String, Integer> capacities(
            WarehouseTransferRoutingTableTest.Topology topology,
            int capacity) {
        return Map.of(
                topology.thirdParty().targetId(), capacity,
                topology.adapting().targetId(), capacity,
                topology.p2p().targetId(), capacity);
    }

    private static RoutedPhysicalTote source(
            String physicalToteId,
            OperationalRouteDestination destination) {
        return RoutedToteRoutingTestFixtures.routedTote(physicalToteId, destination);
    }

    private static DspSchedulerOrderState logicalState(RoutedPhysicalTote source) {
        var launchRequest = source.launchRequest();
        String physicalToteId = launchRequest.physicalToteId().value();
        DspOrderItem item = new DspOrderItem(
                "line-" + physicalToteId,
                "product-" + physicalToteId,
                1,
                launchRequest.pharmacyIds().getFirst(),
                "patient-" + physicalToteId,
                "prescription-" + physicalToteId,
                DspOrderLineType.FULL_PACK,
                launchRequest.orderSheetKey().orderId(),
                1,
                1);
        NotionalToteOrder order = new NotionalToteOrder(
                launchRequest.orderSheetKey().orderId(),
                "notional-" + physicalToteId,
                launchRequest.serviceCentreId(),
                launchRequest.orderSheetKey().sheetNumber(),
                launchRequest.orderType(),
                List.of(item),
                999,
                launchRequest.identity().sourceSequenceNumber());
        return new DspSchedulerOrderState(
                order,
                new RouteRequirements(false, false, false, true, false,
                        StartLocation.OSR),
                DspOrderStatus.RELEASED);
    }

    private static List<OperationalRouteEntryQueue> legacyQueues(
            WarehouseTransferRoutingTableTest.Topology topology) {
        return topology.catalog().definitions().stream()
                .map(definition -> new OperationalRouteEntryQueue(
                        new OperationalRouteTargetDefinition(
                                definition.destination().stationType(),
                                definition.destination().targetId(),
                                2)))
                .toList();
    }

    private static final class Scenario {
        private final WarehouseTransferRoutingTableTest.Topology topology;
        private final SimulationWorld world = new SimulationWorld();
        private final SimulationContext context = new SimulationContext();
        private final OsrOutboundRouteLaunchQueue launchQueue =
                new OsrOutboundRouteLaunchQueue("scenario-launch", 12);
        private final Map<String, ToteLoadPlan> loadPlans = new LinkedHashMap<>();
        private final List<RoutedPhysicalTote> sources;
        private final List<RoutedPhysicalTote> published = new ArrayList<>();
        private final Set<String> publishedIds = new LinkedHashSet<>();
        private final Map<String, RoutedPhysicalTote> publishedById = new LinkedHashMap<>();
        private final Map<String, DestinationAwareTransferTargetDecisionStrategy> strategies =
                new LinkedHashMap<>();
        private final Map<String, StationRoutedToteArrivalQueue> arrivalQueues =
                new LinkedHashMap<>();
        private final DspWarehouseTransportRuntime runtime;

        private Scenario(
                WarehouseTransferRoutingTableTest.Topology topology,
                List<RoutedPhysicalTote> sources,
                int inFlightCapacity,
                Map<String, Integer> arrivalCapacities) {
            this.topology = topology;
            this.sources = List.copyOf(sources);
            sources.forEach(source -> loadPlans.put(
                    source.physicalToteId().value(), source.loadPlan()));
            List<StationRoutedToteArrivalQueue> configuredQueues =
                    topology.catalog().definitions().stream()
                            .map(definition -> new StationRoutedToteArrivalQueue(
                                    definition.destination(),
                                    arrivalCapacities.get(definition.destination().targetId())))
                            .toList();
            configuredQueues.forEach(queue -> arrivalQueues.put(
                    queue.destination().targetId(), queue));
            WarehouseTransportPublisher publisher = new WarehouseTransportPublisher() {
                @Override
                public boolean contains(
                        online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId id) {
                    return publishedIds.contains(id.value());
                }

                @Override
                public WarehouseTransportPublicationState publicationState(RoutedPhysicalTote tote) {
                    RoutedPhysicalTote publishedTote = publishedById.get(tote.physicalToteId().value());
                    if (publishedTote == null) {
                        return WarehouseTransportPublicationState.UNPUBLISHED;
                    }
                    return publishedTote.tote() == tote.tote()
                            && publishedTote.renderable() == tote.renderable()
                            ? WarehouseTransportPublicationState.PUBLISHED_EXACT_OBJECTS
                            : WarehouseTransportPublicationState.PHYSICAL_ID_CONFLICT;
                }

                @Override
                public void publish(RoutedPhysicalTote tote) {
                    if (!publishedIds.add(tote.physicalToteId().value())) {
                        throw new IllegalArgumentException("duplicate publication");
                    }
                    publishedById.put(tote.physicalToteId().value(), tote);
                    published.add(tote);
                    context.addTrackedObject(tote.tote());
                }
            };
            runtime = new DspWarehouseTransportRuntimeFactory().create(
                    world,
                    launchQueue,
                    loadPlans::get,
                    topology.catalog(),
                    (request, plan) -> RoutedToteRoutingTestFixtures.renderable(
                            request.physicalToteId().value()),
                    1d,
                    new online.davisfamily.threedee.matrices.Vec3(),
                    0f,
                    12,
                    inFlightCapacity,
                    publisher,
                    List.of(topology.firstMachine(), topology.secondMachine()),
                    topology.entries(),
                    (machine, strategy) -> strategies.put(machine.getId(), strategy),
                    configuredQueues);
        }

        private void enqueueAndPublishAll() {
            for (RoutedPhysicalTote source : sources) {
                launchQueue.enqueue(source.launchRequest());
            }
            for (int i = 0; i < sources.size(); i++) {
                world.update(0d);
            }
        }

        private TransferRoutingDecision decision(
                TransferZoneMachine machine,
                RoutedPhysicalTote tote) {
            return strategies.get(machine.getId()).decide(tote.tote(), machine).orElseThrow();
        }

        private void detectAtTerminal(RoutedPhysicalTote tote) {
            WarehouseRouteDefinition definition = topology.catalog()
                    .find(tote.destination()).orElseThrow();
            tote.tote().getRouteFollower().setCurrentSegment(definition.terminalSegment());
            runtime.arrivalController().handleDetection(
                    new DetectionEvent(
                            "terminal",
                            0d,
                            definition.terminalArrivalSensorId(),
                            tote.physicalToteId().value(),
                            DetectionType.ENTER),
                    context);
        }

        private void arrivePending(int updates) {
            for (int i = 0; i < updates; i++) {
                runtime.arrivalController().update(context, 0d);
            }
        }

        private StationRoutedToteArrivalQueue arrivalQueue(
                OperationalRouteDestination destination) {
            return arrivalQueues.get(destination.targetId());
        }
    }
}
