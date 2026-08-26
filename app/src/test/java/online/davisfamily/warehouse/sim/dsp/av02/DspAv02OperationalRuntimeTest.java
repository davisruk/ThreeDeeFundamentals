package online.davisfamily.warehouse.sim.dsp.av02;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.warehouse.sim.dsp.adapting.MapBackedToteLoadPlanRegistry;
import online.davisfamily.warehouse.sim.dsp.adapting.MutableToteLoadPlanRegistry;
import online.davisfamily.warehouse.sim.dsp.lifecycle.Av02ToteLifecycleController;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteLifecycleController;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifestCatalog;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleLedger;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleSnapshot;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRole;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StartLocation;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.OsrInventoryConfig;
import online.davisfamily.warehouse.sim.dsp.osr.OsrInventorySnapshot;
import online.davisfamily.warehouse.sim.dsp.osr.OsrPhysicalInventory;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteLaunchRequest;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OsrOutboundRouteLaunchQueue;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OsrOutboundRouteLaunchQueueSnapshot;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OsrOutboundRouteLaunchTargetRegistry;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteTargetAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.allocation.DeadlineAwareElasticStickyP2pLineAllocationPolicy;
import online.davisfamily.warehouse.sim.dsp.p2p.allocation.DspP2pElasticAllocationRuntime;
import online.davisfamily.warehouse.sim.dsp.p2p.allocation.DspP2pElasticAllocationRuntimeSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.allocation.ElasticRuntimeTestFixture;
import online.davisfamily.warehouse.sim.dsp.runtime.operational.DspOperationalReleaseRuntime;
import online.davisfamily.warehouse.sim.dsp.runtime.operational.DspOperationalReleaseRuntimeFactory;
import online.davisfamily.warehouse.sim.dsp.runtime.operational.SynchronousOperationalReleaseEvaluationSource;
import online.davisfamily.warehouse.sim.dsp.runtime.operational.OperationalReleaseEvaluationSource;
import online.davisfamily.warehouse.sim.dsp.runtime.operational.ThreadedOperationalReleaseEvaluationSource;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspOrderStatus;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationCapacity;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionResolver;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseScheduler;
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;
import online.davisfamily.warehouse.sim.dsp.supply.DspSupplySnapshot;
import online.davisfamily.warehouse.sim.dsp.supply.PhysicalToteSupplySnapshot;
import online.davisfamily.warehouse.sim.dsp.supply.PhysicalToteSupplyState;
import online.davisfamily.warehouse.sim.dsp.supply.ServiceCentreAuthorizationState;
import online.davisfamily.warehouse.sim.dsp.supply.ServiceCentreSupplySnapshot;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClock;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClockConfig;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteIdentity;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteSource;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRecord;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

class DspAv02OperationalRuntimeTest {

    @Test
    void shouldComposeOneOperationalRuntimeWithOsrAndAv02LaunchAdapters() {
        ElasticRuntimeTestFixture elasticFixture = new ElasticRuntimeTestFixture();
        DspP2pElasticAllocationRuntime elasticRuntime = elasticFixture.createRuntime();
        List<OperationalRouteDestination> destinations = new ArrayList<>(
                elasticFixture.definitions().stream()
                        .map(definition -> definition.destination())
                        .toList());
        destinations.add(new OperationalRouteDestination(
                StationType.THIRD_PARTY, "third-party-1"));
        OsrOutboundRouteLaunchTargetRegistry routeTargetRegistry =
                new OsrOutboundRouteLaunchTargetRegistry(
                        new OsrOutboundRouteLaunchQueue("operational-launch", 2),
                        destinations);
        PhysicalToteLifecycleLedger lifecycleLedger = new PhysicalToteLifecycleLedger();
        InboundToteManifestCatalog manifestCatalog = new InboundToteManifestCatalog(List.of());
        InboundToteLifecycleController lifecycleController =
                new InboundToteLifecycleController(lifecycleLedger, manifestCatalog);
        DspOperationalClock clock = new DspOperationalClock(
                DspOperationalClockConfig.productionBaseline(LocalDate.of(2026, 8, 26)));
        StationAdmissionResolver stationAdmissionResolver =
                DspAv02OperationalRuntimeTest::openAdmission;
        DspOperationalReleaseScheduler scheduler = new DspOperationalReleaseScheduler(
                new online.davisfamily.warehouse.sim.dsp.scheduler.operational
                        .OperationalDependencyReadinessPolicy(),
                new online.davisfamily.warehouse.sim.dsp.scheduler.operational
                        .OperationalRouteEntryAdmissionPolicy(),
                new online.davisfamily.warehouse.sim.dsp.scheduler.operational
                        .PharmacyGroupedSourceSequenceRankingPolicy(),
                new DeadlineAwareElasticStickyP2pLineAllocationPolicy());

        try (elasticRuntime;
                DspOperationalReleaseRuntime runtime =
                        new DspOperationalReleaseRuntimeFactory().createElasticWithAv02(
                                new SynchronousOperationalReleaseEvaluationSource(scheduler),
                                new OsrPhysicalInventory(new OsrInventoryConfig(2, List.of())),
                                lifecycleController,
                                manifestCatalog,
                                () -> new WarehouseSchedulerSnapshot(
                                        List.of(), Map.of(), Set.of(), Optional.empty()),
                                clock::initialSnapshot,
                                stationAdmissionResolver,
                                routeTargetRegistry,
                                new Av02PhysicalToteInventory(new Av02AllocationConfig(1)),
                                lifecycleLedger,
                                new MapBackedToteLoadPlanRegistry(),
                                elasticRuntime)) {
            runtime.controller().update(new SimulationContext(), 0.1d);

            assertEquals(6, runtime.routeTargetAdmissionSnapshots().size());
            assertTrue(runtime.routeTargetAdmissionSnapshots().stream()
                    .anyMatch(admission -> admission.stationType() == StationType.THIRD_PARTY
                            && admission.targetId().equals("third-party-1")));
            assertEquals(0, runtime.outboundRouteLaunchQueueSnapshot().occupancy());
            assertTrue(runtime.controller().snapshot().lastEvaluation().isPresent());
        }
    }

    @Test
    void shouldApplyOneMixedSourceReleasePerEvaluationAndPreserveTheOtherSource() {
        CombinedRuntimeFixture fixture = new CombinedRuntimeFixture();

        try (fixture.elasticRuntime;
                DspOperationalReleaseRuntime runtime = fixture.createRuntime()) {
            runtime.controller().update(new SimulationContext(), 0.1d);

            var firstEvaluation = runtime.controller().snapshot().lastEvaluation().orElseThrow();
            var firstDecision = firstEvaluation.releaseDecision().orElseThrow();
            assertTrue(runtime.controller().snapshot().lastCommandApplicationResult()
                    .orElseThrow().applied(),
                    () -> runtime.controller().snapshot().lastCommandApplicationResult()
                            .orElseThrow().reason());
            assertEquals(1, runtime.outboundRouteLaunchQueueSnapshot().occupancy());
            OperationalRouteLaunchRequest firstRequest =
                    fixture.launchQueue.peek().orElseThrow();
            assertEquals(fixture.av02PhysicalToteId, firstRequest.physicalToteId());
            assertEquals(OperationalPhysicalToteSource.AV02, firstRequest.source());
            assertEquals(fixture.av02Order.orderSheetKey(), firstRequest.orderSheetKey());
            assertEquals(fixture.av02Order.orderType(), firstRequest.orderType());
            assertEquals(fixture.av02Order.serviceCentreId(), firstRequest.serviceCentreId());
            assertEquals(List.of("pharmacy-1"), firstRequest.pharmacyIds());
            assertEquals(PhysicalToteRole.PRE_P2P, firstRequest.identity().physicalToteRole());
            assertEquals(
                    fixture.av02Order.sequenceNumber(),
                    firstRequest.identity().sourceSequenceNumber());
            assertEquals(
                    new OperationalRouteDestination(StationType.THIRD_PARTY, "third-party-1"),
                    firstRequest.destination());
            assertEquals(Duration.ZERO, firstRequest.releaseTime());
            assertTrue(firstRequest.p2pAssignment().isPresent());
            assertEquals(
                    fixture.av02PhysicalToteId,
                    firstRequest.p2pAssignment().orElseThrow().physicalToteId());
            assertSame(
                    firstDecision.command().proposedP2pAssignment().orElseThrow(),
                    firstRequest.p2pAssignment().orElseThrow());
            assertFalse(fixture.av02Inventory
                    .findWaiting(fixture.av02PhysicalToteId).isPresent());
            assertTrue(fixture.osrInventory
                    .snapshot()
                    .findStored(fixture.osrPhysicalToteId)
                    .isPresent());
            assertEquals(
                    fixture.av02PhysicalToteId,
                    runtime.controller().snapshot().lastPhysicalToteId().orElseThrow());

            runtime.controller().update(new SimulationContext(), 0.1d);

            var secondDecision = runtime.controller().snapshot().lastEvaluation()
                    .orElseThrow().releaseDecision().orElseThrow();
            assertEquals(2, runtime.outboundRouteLaunchQueueSnapshot().occupancy());
            assertEquals(
                    fixture.osrPhysicalToteId,
                    fixture.launchQueue.snapshot().entries().get(1).physicalToteId());
            fixture.launchQueue.dequeue();
            OperationalRouteLaunchRequest secondRequest =
                    fixture.launchQueue.peek().orElseThrow();
            assertEquals(fixture.osrPhysicalToteId, secondRequest.physicalToteId());
            assertEquals(OperationalPhysicalToteSource.OSR, secondRequest.source());
            assertTrue(secondRequest.p2pAssignment().isPresent());
            assertEquals(
                    fixture.osrPhysicalToteId,
                    secondRequest.p2pAssignment().orElseThrow().physicalToteId());
            assertEquals(
                    secondDecision.command().proposedP2pAssignment().orElseThrow(),
                    secondRequest.p2pAssignment().orElseThrow());
            assertFalse(fixture.osrInventory
                    .snapshot()
                    .findStored(fixture.osrPhysicalToteId)
                    .isPresent());
            assertEquals(
                    fixture.osrPhysicalToteId,
                    runtime.controller().snapshot().lastPhysicalToteId().orElseThrow());
        }
    }

    @Test
    void shouldDeferSecondSourceWhenSharedLaunchCapacityIsFullWithoutMutation() {
        CombinedRuntimeFixture fixture = new CombinedRuntimeFixture(1);

        try (fixture.elasticRuntime;
                DspOperationalReleaseRuntime runtime = fixture.createRuntime()) {
            runtime.controller().update(new SimulationContext(), 0.1d);

            assertEquals(1, runtime.outboundRouteLaunchQueueSnapshot().occupancy());
            assertFalse(fixture.av02Inventory
                    .findWaiting(fixture.av02PhysicalToteId).isPresent());
            assertTrue(fixture.osrInventory
                    .snapshot()
                    .findStored(fixture.osrPhysicalToteId)
                    .isPresent());

            CombinedRuntimeState beforeDeferredEvaluation = fixture.mutableState();

            runtime.controller().update(new SimulationContext(), 0.1d);

            var blockedEvaluation = runtime.controller().snapshot().lastEvaluation().orElseThrow();
            assertTrue(blockedEvaluation.releaseDecision().isEmpty());
            assertEquals(beforeDeferredEvaluation, fixture.mutableState());
            assertEquals(
                    fixture.av02PhysicalToteId,
                    runtime.controller().snapshot().lastPhysicalToteId().orElseThrow());
        }
    }

    @Test
    void shouldEvaluateCombinedRuntimeOnWorkerWithoutMutatingLiveState() {
        CombinedRuntimeFixture fixture = new CombinedRuntimeFixture();
        ThreadedOperationalReleaseEvaluationSource source =
                new ThreadedOperationalReleaseEvaluationSource(
                        elasticScheduler(), "dsp-av02-operational-release-worker");

        try (fixture.elasticRuntime;
                source;
                DspOperationalReleaseRuntime runtime = fixture.createRuntime(source)) {
            CombinedRuntimeState beforeWorkerEvaluation = fixture.mutableState();

            runtime.controller().update(new SimulationContext(), 0.1d);
            awaitEvaluationCompletion(source);

            assertEquals(beforeWorkerEvaluation, fixture.mutableState());
            assertTrue(runtime.controller().snapshot().lastEvaluation().isEmpty());

            runtime.controller().update(new SimulationContext(), 0.1d);

            assertEquals(1, runtime.outboundRouteLaunchQueueSnapshot().occupancy());
            assertFalse(fixture.av02Inventory
                    .findWaiting(fixture.av02PhysicalToteId)
                    .isPresent());
            assertTrue(fixture.osrInventory
                    .snapshot()
                    .findStored(fixture.osrPhysicalToteId)
                    .isPresent());
            assertEquals(
                    fixture.av02PhysicalToteId,
                    runtime.controller().snapshot().lastPhysicalToteId().orElseThrow());
        }
    }

    private static final class CombinedRuntimeFixture {
        private final ElasticRuntimeTestFixture elasticFixture = new ElasticRuntimeTestFixture();
        private final PhysicalToteId osrPhysicalToteId = new PhysicalToteId("osr-000001");
        private final PhysicalToteId av02PhysicalToteId = new PhysicalToteId("av02-000001");
        private final InboundToteManifest osrManifest = manifest(
                osrPhysicalToteId, "osr-order", OrderType.FULL_PACK, 2);
        private final NotionalToteOrder av02Order = order(
                "empty-order", OrderType.EMPTY, 1);
        private final InboundToteManifestCatalog manifestCatalog =
                new InboundToteManifestCatalog(List.of(osrManifest));
        private final PhysicalToteLifecycleLedger lifecycleLedger =
                new PhysicalToteLifecycleLedger();
        private final InboundToteLifecycleController lifecycleController =
                new InboundToteLifecycleController(lifecycleLedger, manifestCatalog);
        private final OsrPhysicalInventory osrInventory = new OsrPhysicalInventory(
                new OsrInventoryConfig(2, List.of()));
        private final Av02PhysicalToteInventory av02Inventory =
                new Av02PhysicalToteInventory(new Av02AllocationConfig(2));
        private final MutableToteLoadPlanRegistry loadPlanRegistry =
                new MapBackedToteLoadPlanRegistry();
        private final DspOperationalClock clock = new DspOperationalClock(
                DspOperationalClockConfig.productionBaseline(LocalDate.of(2026, 8, 26)));
        private final OsrOutboundRouteLaunchQueue launchQueue;
        private final OsrOutboundRouteLaunchTargetRegistry routeTargetRegistry;
        private final DspP2pElasticAllocationRuntime elasticRuntime;

        private CombinedRuntimeFixture() {
            this(2);
        }

        private CombinedRuntimeFixture(int launchCapacity) {
            launchQueue = new OsrOutboundRouteLaunchQueue(
                    "operational-launch", launchCapacity);
            osrInventory.store(osrManifest);

            PhysicalToteRecord av02PhysicalTote = new Av02ToteLifecycleController(
                    lifecycleLedger,
                    new DeterministicAv02PhysicalToteIdAllocator())
                    .allocateFor(av02Order, Duration.ZERO, av02PhysicalToteId);
            av02Inventory.store(new Av02AllocatedTote(
                    new OperationalPhysicalToteIdentity(
                            OperationalPhysicalToteSource.AV02,
                            av02PhysicalToteId,
                            av02Order.orderSheetKey(),
                            av02Order.orderType(),
                            av02Order.serviceCentreId(),
                            PhysicalToteRole.PRE_P2P,
                            av02Order.sequenceNumber()),
                    av02PhysicalTote,
                    "pharmacy-1"));
            loadPlanRegistry.putLoadPlan(new ToteLoadPlan(av02PhysicalToteId, List.of()));

            List<OperationalRouteDestination> destinations = new ArrayList<>(
                    elasticFixture.definitions().stream()
                            .map(definition -> definition.destination())
                            .toList());
            destinations.add(new OperationalRouteDestination(
                    StationType.THIRD_PARTY, "third-party-1"));
            routeTargetRegistry = new OsrOutboundRouteLaunchTargetRegistry(
                    launchQueue, destinations);
            elasticRuntime = elasticFixture.createRuntime(
                    this::logicalSnapshot,
                    manifestCatalog,
                    lifecycleLedger::snapshot,
                    av02Inventory::snapshot,
                    this::supplySnapshot);
        }

        private DspOperationalReleaseRuntime createRuntime() {
            return createRuntime(new SynchronousOperationalReleaseEvaluationSource(
                    elasticScheduler()));
        }

        private DspOperationalReleaseRuntime createRuntime(
                OperationalReleaseEvaluationSource evaluationSource) {
            return new DspOperationalReleaseRuntimeFactory().createElasticWithAv02(
                    evaluationSource,
                    osrInventory,
                    lifecycleController,
                    manifestCatalog,
                    this::logicalSnapshot,
                    clock::initialSnapshot,
                    this::admission,
                    routeTargetRegistry,
                    av02Inventory,
                    lifecycleLedger,
                    loadPlanRegistry,
                    elasticRuntime);
        }

        private CombinedRuntimeState mutableState() {
            return new CombinedRuntimeState(
                    osrInventory.snapshot(),
                    av02Inventory.snapshot(),
                    lifecycleLedger.snapshot(),
                    elasticRuntime.operationalSnapshot(),
                    loadPlanRegistry.getLoadPlanFor(osrPhysicalToteId),
                    loadPlanRegistry.getLoadPlanFor(av02PhysicalToteId),
                    routeTargetRegistry.snapshotAdmissions(),
                    launchQueue.snapshot());
        }

        private WarehouseSchedulerSnapshot logicalSnapshot() {
            return new WarehouseSchedulerSnapshot(
                    List.of(
                            logicalState(osrManifest.orderSheetKey().orderId(),
                                    osrManifest.orderType(), StartLocation.OSR,
                                    osrManifest.sourceSequenceNumber()),
                            logicalState(av02Order.orderId(), av02Order.orderType(),
                                    StartLocation.AV02, av02Order.sequenceNumber())),
                    Map.of(),
                    Set.of(),
                    Optional.empty());
        }

        private StationAdmissionSnapshot admission(
                StationType stationType,
                DspSchedulerOrderState candidate,
                WarehouseSchedulerSnapshot snapshot) {
            String targetId = stationType == StationType.P2P
                    ? "p2p-1"
                    : "third-party-1";
            return new StationAdmissionSnapshot(
                    stationType,
                    new StationCapacity(1, 1),
                    new StationSnapshot(stationType, 0, 0),
                    true,
                    "",
                    Optional.of(targetId));
        }

        private DspSupplySnapshot supplySnapshot() {
            PhysicalToteSupplySnapshot physicalTote = new PhysicalToteSupplySnapshot(
                    osrPhysicalToteId,
                    osrManifest.orderSheetKey(),
                    osrManifest.orderType(),
                    osrManifest.serviceCentreId(),
                    osrManifest.sourceSequenceNumber(),
                    PhysicalToteSupplyState.PRELOADED_IN_OSR);
            ServiceCentreSupplySnapshot serviceCentre = new ServiceCentreSupplySnapshot(
                    "104",
                    999,
                    ServiceCentreAuthorizationState.PRELOADED,
                    Optional.of(Duration.ZERO),
                    1,
                    1,
                    0,
                    0,
                    Set.of(),
                    List.of(physicalTote));
            return new DspSupplySnapshot(
                    "test-supply",
                    0,
                    1200,
                    1,
                    Optional.of("104"),
                    Optional.empty(),
                    Set.of(),
                    List.of(serviceCentre),
                    0);
        }
    }

    private record CombinedRuntimeState(
            OsrInventorySnapshot osrInventory,
            Av02InventorySnapshot av02Inventory,
            PhysicalToteLifecycleSnapshot lifecycle,
            DspP2pElasticAllocationRuntimeSnapshot elastic,
            ToteLoadPlan osrLoadPlan,
            ToteLoadPlan av02LoadPlan,
            List<OperationalRouteTargetAdmissionSnapshot> targets,
            OsrOutboundRouteLaunchQueueSnapshot launchQueue) {
    }

    private static void awaitEvaluationCompletion(
            ThreadedOperationalReleaseEvaluationSource source) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (source.evaluationInFlight() && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        if (source.evaluationInFlight()) {
            throw new IllegalStateException(
                    "Timed out waiting for operational evaluation completion");
        }
    }

    private static StationAdmissionSnapshot openAdmission(
            StationType stationType,
            DspSchedulerOrderState candidate,
            WarehouseSchedulerSnapshot snapshot) {
        return new StationAdmissionSnapshot(
                stationType,
                new StationCapacity(1, 1),
                new StationSnapshot(stationType, 0, 0),
                true,
                "");
    }

    private static DspOperationalReleaseScheduler elasticScheduler() {
        return new DspOperationalReleaseScheduler(
                new online.davisfamily.warehouse.sim.dsp.scheduler.operational
                        .OperationalDependencyReadinessPolicy(),
                new online.davisfamily.warehouse.sim.dsp.scheduler.operational
                        .OperationalRouteEntryAdmissionPolicy(),
                new online.davisfamily.warehouse.sim.dsp.scheduler.operational
                        .PharmacyGroupedSourceSequenceRankingPolicy(),
                new DeadlineAwareElasticStickyP2pLineAllocationPolicy());
    }

    private static InboundToteManifest manifest(
            PhysicalToteId physicalToteId,
            String orderId,
            OrderType orderType,
            long sequenceNumber) {
        NotionalToteOrder order = order(orderId, orderType, sequenceNumber);
        return new InboundToteManifest(
                physicalToteId,
                order.orderSheetKey(),
                order.orderType(),
                order.serviceCentreId(),
                order.items(),
                sequenceNumber);
    }

    private static NotionalToteOrder order(
            String orderId,
            OrderType orderType,
            long sequenceNumber) {
        DspOrderItem item = new DspOrderItem(
                "line-" + orderId,
                "product-" + orderId,
                1,
                "pharmacy-1",
                "patient-" + orderId,
                "prescription-" + orderId,
                DspOrderLineType.FULL_PACK,
                orderId,
                1,
                1);
        return new NotionalToteOrder(
                orderId,
                "notional-" + orderId,
                    "104",
                1,
                orderType,
                List.of(item),
                999,
                sequenceNumber);
    }

    private static DspSchedulerOrderState logicalState(
            String orderId,
            OrderType orderType,
            StartLocation startLocation,
            long sequenceNumber) {
        return new DspSchedulerOrderState(
                order(orderId, orderType, sequenceNumber),
                new RouteRequirements(
                        true, false, false, true, false, startLocation),
                DspOrderStatus.WAITING);
    }
}
