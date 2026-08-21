package online.davisfamily.warehouse.sim.dsp.osr.release.launch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDate;
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
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptedLineStore;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingArea;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingBench;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingStationAdmissionResolver;
import online.davisfamily.warehouse.sim.dsp.adapting.MapBackedToteLoadPlanRegistry;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteLifecycleController;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifestCatalog;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleLedger;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.ProductMasterRecord;
import online.davisfamily.warehouse.sim.dsp.model.StartLocation;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.OsrInventoryConfig;
import online.davisfamily.warehouse.sim.dsp.osr.OsrPhysicalInventory;
import online.davisfamily.warehouse.sim.dsp.osr.release.OsrProcessingReleaseRequest;
import online.davisfamily.warehouse.sim.dsp.p2p.P2pAdmissionResult;
import online.davisfamily.warehouse.sim.dsp.p2p.P2pAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.P2pStationAdmissionResolver;
import online.davisfamily.warehouse.sim.dsp.p2p.StaticP2pAdmission;
import online.davisfamily.warehouse.sim.dsp.routing.InMemoryProductMasterRepository;
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;
import online.davisfamily.warehouse.sim.dsp.runtime.DspSchedulerRuntimeState;
import online.davisfamily.warehouse.sim.dsp.runtime.operational.DspOperationalReleaseRuntime;
import online.davisfamily.warehouse.sim.dsp.runtime.operational.DspOperationalReleaseRuntimeFactory;
import online.davisfamily.warehouse.sim.dsp.runtime.operational.SynchronousOperationalReleaseEvaluationSource;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspOrderStatus;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;
import online.davisfamily.warehouse.sim.dsp.scheduler.SnapshotStationAdmissionResolver;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionResolver;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationCapacity;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseScheduler;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.OperationalReleaseBlockType;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyArea;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyAreaConfig;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyStationAdmissionResolver;
import online.davisfamily.warehouse.sim.dsp.thirdparty.ThirdPartyVisitFactory;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClock;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClockConfig;
import online.davisfamily.warehouse.sim.dsp.transport.LoadPlanOsrOutboundToteHydrator;
import online.davisfamily.warehouse.sim.dsp.transport.OsrOutboundTransportQueue;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

class DspOsrOutboundRouteLaunchScenarioTest {

    @Test
    void shouldReleaseAndHydrateMixedDestinationsWithoutStationTeleportation() {
        DspSchedulerOrderState thirdParty = state(order(
                "third-party", OrderType.FULL_PACK, "pharmacy-1",
                DspOrderLineType.FULL_PACK, "third-party-product", 1),
                route(true, false, true));
        DspSchedulerOrderState adapted = state(order(
                "adapted", OrderType.ADAPTED, "pharmacy-2",
                DspOrderLineType.ADAPTED, "regular-product", 2),
                route(false, true, false));
        DspSchedulerOrderState p2p = state(order(
                "p2p", OrderType.FULL_PACK, "pharmacy-3",
                DspOrderLineType.FULL_PACK, "regular-product", 3),
                route(false, false, true));

        try (Scenario scenario = Scenario.standard(
                List.of(thirdParty, adapted, p2p), 3, 3, true)) {
            scenario.releaseUpdates(3);

            assertEquals(
                    List.of("tote-third-party", "tote-adapted", "tote-p2p"),
                    scenario.launchIds());
            assertEquals(
                    List.of(
                            StationType.THIRD_PARTY,
                            StationType.ADAPTING,
                            StationType.P2P),
                    scenario.launchQueue.snapshot().entries().stream()
                            .map(entry -> entry.destination().stationType())
                            .toList());
            assertEquals(
                    List.of("third-party-ingress", "bench-1", "p2p-ingress"),
                    scenario.launchQueue.snapshot().entries().stream()
                            .map(entry -> entry.destination().targetId())
                            .toList());
            assertEquals(0, scenario.inventory.snapshot().occupancy());

            scenario.launchUpdates(3);

            assertTrue(scenario.launchQueue.peek().isEmpty());
            assertEquals(
                    List.of("tote-third-party", "tote-adapted", "tote-p2p"),
                    scenario.transportIds());
            assertEquals(3, scenario.hydrationFactoryCalls.get());
            for (int index = 0; index < 3; index++) {
                RoutedPhysicalTote routedTote = scenario.transportQueue.dequeue().orElseThrow();
                OsrOutboundRouteLaunchRequest request = routedTote.launchRequest();
                ToteLoadPlan expectedPlan = scenario.loadPlans.getLoadPlanFor(
                        request.physicalToteId());
                assertSame(request, routedTote.launchRequest());
                assertSame(request.releaseRequest().manifest(),
                        scenario.catalog.findByPhysicalToteId(request.physicalToteId())
                                .orElseThrow());
                assertSame(expectedPlan, routedTote.loadPlan());
                assertSame(request.destination(), routedTote.destination());
                assertEquals(Duration.ZERO, request.releaseRequest().releaseTime());
                assertEquals(
                        request.releaseRequest().releaseTime(),
                        scenario.lifecycle.snapshot()
                                .activeAssignmentFor(
                                        request.releaseRequest().manifest().orderSheetKey())
                                .orElseThrow()
                                .activatedAt());
            }
            assertEquals(DspOrderStatus.WAITING, thirdParty.status());
            assertEquals(DspOrderStatus.WAITING, adapted.status());
            assertEquals(DspOrderStatus.WAITING, p2p.status());
        }
    }

    @Test
    void shouldBlockAtSharedLaunchCapacityAndReleaseAfterLaunchConsumption() {
        DspSchedulerOrderState first = state(order(
                "first", OrderType.FULL_PACK, "pharmacy-1",
                DspOrderLineType.FULL_PACK, "regular-product", 1),
                route(false, false, true));
        DspSchedulerOrderState second = state(order(
                "second", OrderType.FULL_PACK, "pharmacy-2",
                DspOrderLineType.FULL_PACK, "regular-product", 2),
                route(false, false, true));

        try (Scenario scenario = Scenario.standard(
                List.of(first, second), 1, 2, true)) {
            scenario.releaseUpdates(2);

            assertEquals(List.of("tote-first"), scenario.launchIds());
            assertTrue(scenario.inventory.snapshot().findStored(
                    new PhysicalToteId("tote-second")).isPresent());
            assertTrue(scenario.lifecycle.snapshot()
                    .activeAssignmentFor(second.order().orderSheetKey()).isEmpty());
            assertEquals(
                    OperationalReleaseBlockType.STATION_ADMISSION,
                    scenario.runtime.controller().snapshot().lastEvaluation().orElseThrow()
                            .blockedCandidates().get(0).blocks().get(0).type());

            scenario.launchUpdates(1);
            scenario.releaseUpdates(1);

            assertEquals(List.of("tote-second"), scenario.launchIds());
            assertTrue(scenario.inventory.snapshot().findStored(
                    new PhysicalToteId("tote-second")).isEmpty());
            assertTrue(scenario.lifecycle.snapshot()
                    .activeAssignmentFor(second.order().orderSheetKey()).isPresent());
        }
    }

    @Test
    void shouldAvoidAllocationUnderTransportBackpressureAndRetryMissingPlan() {
        DspSchedulerOrderState first = state(order(
                "first", OrderType.FULL_PACK, "pharmacy-1",
                DspOrderLineType.FULL_PACK, "regular-product", 1),
                route(false, false, true));
        DspSchedulerOrderState second = state(order(
                "second", OrderType.FULL_PACK, "pharmacy-2",
                DspOrderLineType.FULL_PACK, "regular-product", 2),
                route(false, false, true));

        try (Scenario scenario = Scenario.standard(
                List.of(first, second), 2, 1, false)) {
            scenario.releaseUpdates(2);
            OsrOutboundRouteLaunchRequest firstRequest =
                    scenario.launchQueue.peek().orElseThrow();
            OsrOutboundRouteLaunchRequest blocker =
                    requestForBlocker("transport-blocker");
            scenario.transportQueue.enqueue(scenario.detached(
                    blocker,
                    new ToteLoadPlan(blocker.physicalToteId(), List.of())));

            scenario.launchUpdates(1);

            assertEquals(0, scenario.hydrationFactoryCalls.get());
            assertSame(firstRequest, scenario.launchQueue.peek().orElseThrow());
            assertTrue(scenario.launchController.snapshot().blockedReason()
                    .contains("no capacity"));

            scenario.transportQueue.dequeue().orElseThrow();
            scenario.launchUpdates(1);

            assertEquals(0, scenario.hydrationFactoryCalls.get());
            assertSame(firstRequest, scenario.launchQueue.peek().orElseThrow());
            assertTrue(scenario.launchController.snapshot().blockedReason()
                    .contains("load plan"));

            scenario.addPlan(firstRequest.physicalToteId());
            scenario.launchUpdates(1);

            assertEquals(1, scenario.hydrationFactoryCalls.get());
            assertEquals("tote-second",
                    scenario.launchQueue.peek().orElseThrow().physicalToteId().value());
            assertEquals("tote-first",
                    scenario.transportQueue.peek().orElseThrow().physicalToteId().value());
        }
    }

    @Test
    void shouldExposeUnknownAndWrongStationTargetsWithoutRelease() {
        DspSchedulerOrderState p2p = state(order(
                "p2p", OrderType.FULL_PACK, "pharmacy-1",
                DspOrderLineType.FULL_PACK, "regular-product", 1),
                route(false, false, true));

        try (Scenario unknown = new Scenario(
                List.of(p2p),
                List.of(manifest(p2p.order())),
                1,
                1,
                List.of(destination(StationType.P2P, "configured-p2p")),
                resolver("unknown-p2p", adaptingArea()),
                true)) {
            unknown.releaseUpdates(1);

            assertTrue(unknown.launchQueue.peek().isEmpty());
            assertTrue(unknown.inventory.snapshot().findStored(
                    new PhysicalToteId("tote-p2p")).isPresent());
            assertFalse(unknown.runtime.controller().snapshot()
                    .lastCommandApplicationResult().isPresent());
            assertTrue(unknown.runtime.controller().snapshot().lastEvaluation()
                    .orElseThrow().blockedCandidates().get(0).blocks().get(0).reason()
                    .contains("Unknown operational route admission target"));
        }

        try (Scenario wrongStation = new Scenario(
                List.of(p2p),
                List.of(manifest(p2p.order())),
                1,
                1,
                List.of(destination(StationType.THIRD_PARTY, "wrong-station")),
                resolver("wrong-station", adaptingArea()),
                true)) {
            wrongStation.releaseUpdates(1);

            assertTrue(wrongStation.launchQueue.peek().isEmpty());
            assertTrue(wrongStation.inventory.snapshot().findStored(
                    new PhysicalToteId("tote-p2p")).isPresent());
            assertFalse(wrongStation.runtime.controller().snapshot()
                    .lastCommandApplicationResult().isPresent());
            assertTrue(wrongStation.runtime.controller().snapshot().lastEvaluation()
                    .orElseThrow().blockedCandidates().get(0).blocks().get(0).reason()
                    .contains("belongs to station THIRD_PARTY instead of P2P"));
        }
    }

    @Test
    void shouldKeepRepeatedPhysicalManifestsDistinctAndLogicallyUnreleased() {
        NotionalToteOrder sharedOrder = new NotionalToteOrder(
                "shared-order",
                "notional-shared-order",
                "sc-1",
                1,
                OrderType.FULL_PACK,
                List.of(
                        item("line-1", "pharmacy-1"),
                        item("line-2", "pharmacy-1")),
                999,
                1);
        DspSchedulerOrderState sharedState = state(
                sharedOrder, route(false, false, true));
        List<InboundToteManifest> manifests = List.of(
                new InboundToteManifest(
                        new PhysicalToteId("shared-tote-1"),
                        sharedOrder.orderSheetKey(),
                        sharedOrder.orderType(),
                        sharedOrder.serviceCentreId(),
                        List.of(sharedOrder.items().get(0)),
                        1),
                new InboundToteManifest(
                        new PhysicalToteId("shared-tote-2"),
                        sharedOrder.orderSheetKey(),
                        sharedOrder.orderType(),
                        sharedOrder.serviceCentreId(),
                        List.of(sharedOrder.items().get(1)),
                        2));

        try (Scenario scenario = new Scenario(
                List.of(sharedState),
                manifests,
                2,
                2,
                standardDestinations(),
                resolver("p2p-ingress", adaptingArea()),
                true)) {
            scenario.releaseUpdates(1);
            scenario.launchUpdates(1);
            scenario.releaseUpdates(1);

            assertEquals(List.of("shared-tote-1"), scenario.transportIds());
            assertTrue(scenario.launchQueue.peek().isEmpty());
            assertTrue(scenario.inventory.snapshot().findStored(
                    new PhysicalToteId("shared-tote-2")).isPresent());
            assertEquals(
                    OperationalReleaseBlockType.ACTIVE_SHEET_ASSIGNMENT,
                    scenario.runtime.controller().snapshot().lastEvaluation().orElseThrow()
                            .blockedCandidates().get(0).blocks().get(0).type());
            assertEquals(DspOrderStatus.WAITING, sharedState.status());
            assertEquals(1, scenario.lifecycle.snapshot()
                    .assignmentHistoryFor(sharedOrder.orderSheetKey()).size());
        }
    }

    private static List<OperationalRouteDestination> standardDestinations() {
        return List.of(
                destination(StationType.THIRD_PARTY, "third-party-ingress"),
                destination(StationType.ADAPTING, "bench-1"),
                destination(StationType.ADAPTING, "bench-2"),
                destination(StationType.P2P, "p2p-ingress"));
    }

    private static OperationalRouteDestination destination(
            StationType stationType,
            String targetId) {
        return new OperationalRouteDestination(stationType, targetId);
    }

    private static StationAdmissionResolver resolver(
            String p2pTargetId,
            AdaptingArea adaptingArea) {
        InMemoryProductMasterRepository products = new InMemoryProductMasterRepository(List.of(
                new ProductMasterRecord(
                        "third-party-product", "Third party",
                        Optional.of("Y74"), Optional.empty()),
                new ProductMasterRecord(
                        "regular-product", "Regular",
                        Optional.empty(), Optional.empty())));
        ThirdPartyArea thirdPartyArea = new ThirdPartyArea(
                new ThirdPartyAreaConfig(4, 1, 1d));
        StationAdmissionResolver thirdParty = new ThirdPartyStationAdmissionResolver(
                new SnapshotStationAdmissionResolver(),
                new ThirdPartyVisitFactory(products),
                thirdPartyArea::snapshot,
                "third-party-ingress");
        StationAdmissionResolver adapting = new AdaptingStationAdmissionResolver(
                thirdParty, adaptingArea, new StationCapacity(4, 4));
        return new P2pStationAdmissionResolver(
                adapting,
                new StaticP2pAdmission(P2pAdmissionResult.acceptedResult()),
                () -> new P2pAdmissionSnapshot(
                        "p2p-1", 1, Set.of(), Set.of(), true),
                new StationCapacity(1, 1),
                () -> new StationSnapshot(StationType.P2P, 0, 0),
                p2pTargetId);
    }

    private static AdaptingArea adaptingArea() {
        AdaptedLineStore store = new AdaptedLineStore();
        return new AdaptingArea(List.of(
                new AdaptingBench("bench-1", store, 1d),
                new AdaptingBench("bench-2", store, 1d)), 1);
    }

    private static DspSchedulerOrderState state(
            NotionalToteOrder order,
            RouteRequirements route) {
        return new DspSchedulerOrderState(order, route, DspOrderStatus.WAITING);
    }

    private static RouteRequirements route(
            boolean thirdParty,
            boolean adapting,
            boolean p2p) {
        return new RouteRequirements(
                thirdParty, adapting, false, p2p, false, StartLocation.OSR);
    }

    private static NotionalToteOrder order(
            String orderId,
            OrderType orderType,
            String pharmacyId,
            DspOrderLineType lineType,
            String productId,
            long sequence) {
        DspOrderItem item = new DspOrderItem(
                "line-" + orderId,
                productId,
                1,
                pharmacyId,
                "patient-" + orderId,
                "prescription-" + orderId,
                lineType,
                "associated-" + orderId,
                1,
                0);
        return new NotionalToteOrder(
                orderId,
                "notional-" + orderId,
                "sc-1",
                1,
                orderType,
                List.of(item),
                999,
                sequence);
    }

    private static InboundToteManifest manifest(NotionalToteOrder order) {
        return new InboundToteManifest(
                new PhysicalToteId("tote-" + order.orderId()),
                order.orderSheetKey(),
                order.orderType(),
                order.serviceCentreId(),
                order.items(),
                order.sequenceNumber());
    }

    private static DspOrderItem item(String lineReference, String pharmacyId) {
        return new DspOrderItem(
                lineReference,
                "regular-product",
                1,
                pharmacyId,
                "patient-" + lineReference,
                "prescription-" + lineReference,
                DspOrderLineType.FULL_PACK,
                "shared-order",
                1,
                0);
    }

    private static OsrOutboundRouteLaunchRequest requestForBlocker(String physicalToteId) {
        NotionalToteOrder order = order(
                physicalToteId,
                OrderType.FULL_PACK,
                "pharmacy-blocker",
                DspOrderLineType.FULL_PACK,
                "regular-product",
                99);
        return new OsrOutboundRouteLaunchRequest(
                new OsrProcessingReleaseRequest(manifest(order), Duration.ZERO),
                destination(StationType.P2P, "p2p-ingress"));
    }

    private static final class Scenario implements AutoCloseable {
        private final InboundToteManifestCatalog catalog;
        private final OsrPhysicalInventory inventory;
        private final InboundToteLifecycleController lifecycle;
        private final OsrOutboundRouteLaunchQueue launchQueue;
        private final OsrOutboundTransportQueue transportQueue;
        private final MapBackedToteLoadPlanRegistry loadPlans =
                new MapBackedToteLoadPlanRegistry();
        private final AtomicInteger hydrationFactoryCalls = new AtomicInteger();
        private final DspOperationalReleaseRuntime runtime;
        private final OsrOutboundRouteLaunchController launchController;

        private static Scenario standard(
                List<DspSchedulerOrderState> states,
                int launchCapacity,
                int transportCapacity,
                boolean preparePlans) {
            return new Scenario(
                    states,
                    states.stream().map(state -> manifest(state.order())).toList(),
                    launchCapacity,
                    transportCapacity,
                    standardDestinations(),
                    resolver("p2p-ingress", adaptingArea()),
                    preparePlans);
        }

        private Scenario(
                List<DspSchedulerOrderState> states,
                List<InboundToteManifest> manifests,
                int launchCapacity,
                int transportCapacity,
                List<OperationalRouteDestination> destinations,
                StationAdmissionResolver admissionResolver,
                boolean preparePlans) {
            catalog = new InboundToteManifestCatalog(manifests);
            inventory = new OsrPhysicalInventory(new OsrInventoryConfig(
                    Math.max(1, manifests.size()), List.of()));
            inventory.storeAll(manifests);
            lifecycle = new InboundToteLifecycleController(
                    new PhysicalToteLifecycleLedger(), catalog);
            DspSchedulerRuntimeState logicalState = new DspSchedulerRuntimeState(
                    new WarehouseSchedulerSnapshot(
                            states, Map.of(), Set.of(), Optional.empty()));
            launchQueue = new OsrOutboundRouteLaunchQueue(
                    "osr-outbound-launch", launchCapacity);
            OsrOutboundRouteLaunchTargetRegistry launchRegistry =
                    new OsrOutboundRouteLaunchTargetRegistry(launchQueue, destinations);
            DspOperationalClock clock = new DspOperationalClock(
                    DspOperationalClockConfig.productionBaseline(
                            LocalDate.of(2026, 8, 21)));
            runtime = new DspOperationalReleaseRuntimeFactory().create(
                    new SynchronousOperationalReleaseEvaluationSource(
                            new DspOperationalReleaseScheduler()),
                    inventory,
                    lifecycle,
                    catalog,
                    logicalState::snapshot,
                    clock::initialSnapshot,
                    admissionResolver,
                    launchRegistry);
            if (preparePlans) {
                manifests.forEach(manifest -> addPlan(manifest.physicalToteId()));
            }
            transportQueue = new OsrOutboundTransportQueue(
                    "osr-outbound-transport", transportCapacity);
            LoadPlanOsrOutboundToteHydrator hydrator =
                    new LoadPlanOsrOutboundToteHydrator(
                            loadPlans,
                            (request, loadPlan) -> {
                                hydrationFactoryCalls.incrementAndGet();
                                return detached(request, loadPlan);
                            });
            launchController = new OsrOutboundRouteLaunchController(
                    launchQueue, transportQueue, hydrator);
        }

        private void addPlan(PhysicalToteId physicalToteId) {
            loadPlans.putLoadPlan(new ToteLoadPlan(physicalToteId, List.of()));
        }

        private void releaseUpdates(int count) {
            for (int index = 0; index < count; index++) {
                runtime.controller().update(new SimulationContext(), 0.1d);
            }
        }

        private void launchUpdates(int count) {
            for (int index = 0; index < count; index++) {
                launchController.update(new SimulationContext(), 0.1d);
            }
        }

        private List<String> launchIds() {
            return launchQueue.snapshot().entries().stream()
                    .map(entry -> entry.physicalToteId().value())
                    .toList();
        }

        private List<String> transportIds() {
            return transportQueue.snapshot().entries().stream()
                    .map(entry -> entry.physicalToteId().value())
                    .toList();
        }

        private RoutedPhysicalTote detached(
                OsrOutboundRouteLaunchRequest request,
                ToteLoadPlan loadPlan) {
            String physicalToteId = request.physicalToteId().value();
            RenderableObject renderable = RenderableObject.create(
                    physicalToteId,
                    null,
                    anchorMesh(),
                    new Mat4.ObjectTransformation(
                            0f, 0f, 0f, 0f, 0f, 0f, new Mat4()),
                    triangleIndex -> 0,
                    false);
            RouteSegment routeSegment = new RouteSegment(
                    "osr-outbound-route-" + physicalToteId,
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
            return new RoutedPhysicalTote(request, loadPlan, tote, renderable);
        }

        @Override
        public void close() {
            runtime.close();
        }
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
