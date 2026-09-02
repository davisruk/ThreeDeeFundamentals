package online.davisfamily.warehouse.sim.dsp.av02;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.behaviour.routing.RouteFollower;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.matrices.Mat4;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.matrices.Vec4;
import online.davisfamily.threedee.model.Mesh;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.SimulationWorld;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptedLineStore;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptedLineStoreSnapshot;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingArea;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingAreaController;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingBench;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingBenchCompletion;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingBenchId;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingBenchSelection;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingStorageMap;
import online.davisfamily.warehouse.sim.dsp.adapting.AdaptingVisit;
import online.davisfamily.warehouse.sim.dsp.adapting.MapBackedToteLoadPlanRegistry;
import online.davisfamily.warehouse.sim.dsp.adapting.MutableToteLoadPlanRegistry;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteLifecycleController;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifestCatalog;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteAssignment;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteAssignmentStage;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleLedger;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleSnapshot;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleState;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRole;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StartLocation;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.OsrInventoryConfig;
import online.davisfamily.warehouse.sim.dsp.osr.OsrInventorySnapshot;
import online.davisfamily.warehouse.sim.dsp.osr.OsrPhysicalInventory;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteLaunchRequest;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteSource;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OsrOutboundRouteLaunchController;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OsrOutboundRouteLaunchQueue;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OsrOutboundRouteLaunchTargetRegistry;
import online.davisfamily.warehouse.sim.dsp.p2p.allocation.DeadlineAwareElasticStickyP2pLineAllocationPolicy;
import online.davisfamily.warehouse.sim.dsp.p2p.allocation.DspP2pElasticAllocationRuntime;
import online.davisfamily.warehouse.sim.dsp.p2p.allocation.DspP2pElasticAllocationRuntimeSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.allocation.ElasticRuntimeTestFixture;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pPhysicalToteAssignment;
import online.davisfamily.warehouse.sim.dsp.runtime.DspSchedulerRuntimeState;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspOrderStatus;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationAdmissionSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationCapacity;
import online.davisfamily.warehouse.sim.dsp.scheduler.StationSnapshot;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;
import online.davisfamily.warehouse.sim.dsp.supply.DspSupplySnapshot;
import online.davisfamily.warehouse.sim.dsp.supply.ServiceCentreAuthorizationState;
import online.davisfamily.warehouse.sim.dsp.supply.ServiceCentreSupplySnapshot;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClock;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClockConfig;
import online.davisfamily.warehouse.sim.dsp.runtime.operational.DspOperationalReleaseRuntime;
import online.davisfamily.warehouse.sim.dsp.runtime.operational.DspOperationalReleaseRuntimeFactory;
import online.davisfamily.warehouse.sim.dsp.runtime.operational.SynchronousOperationalReleaseEvaluationSource;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseEvaluation;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.DspOperationalReleaseScheduler;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.OperationalReleaseBlockType;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.OperationalDependencyReadinessPolicy;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.OperationalRouteEntryAdmissionPolicy;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.PharmacyGroupedSourceSequenceRankingPolicy;
import online.davisfamily.warehouse.sim.dsp.transport.LoadPlanOsrOutboundToteHydrator;
import online.davisfamily.warehouse.sim.dsp.transport.OsrOutboundTransportQueue;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;
import online.davisfamily.warehouse.sim.tote.Tote;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

/** Real allocation-boundary scenario for operational EMPTY work. */
class DspAv02OperationalAllocationScenarioTest {

    private static final String SERVICE_CENTRE_104 = "104";
    private static final String SERVICE_CENTRE_108 = "108";
    private static final int PRIORITY_104 = 999;
    private static final int PRIORITY_108 = 998;

    private static final OrderSheetKey EMPTY_DIRECT_104 = sheet("empty-direct-104");
    private static final OrderSheetKey EMPTY_THIRD_PARTY_104 = sheet("empty-third-party-104");
    private static final OrderSheetKey EMPTY_ADAPTED_104 = sheet("empty-adapted-104");
    private static final OrderSheetKey EMPTY_DIRECT_108 = sheet("empty-direct-108");
    private static final PreparedLineKey EMPTY_ADAPTED_LINE =
            new PreparedLineKey(EMPTY_ADAPTED_104.orderId(), "adapted-line-empty-104");

    @Test
    void shouldAllocateOnlyAuthorizedDependencyReadyEmptyWithinCapacity() {
        ScenarioFixture dependencyFixture = new ScenarioFixture(List.of(
                emptyOrder(
                        EMPTY_ADAPTED_104.orderId(), SERVICE_CENTRE_104, PRIORITY_104, 3,
                        "pharmacy-104-b", DspOrderLineType.ADAPTED)));
        dependencyFixture.authorizeAllEmptySheets();
        Av02AllocationSnapshot dependencyBlocked = dependencyFixture.freshAllocationSnapshot();
        assertEquals(List.of(Av02AllocationBlockReason.DEPENDENCY_NOT_READY),
                dependencyFixture.candidate(dependencyBlocked, EMPTY_ADAPTED_104).blockReasons());
        assertTrue(dependencyBlocked.command().isEmpty());
        AllocationState dependencyBefore = dependencyFixture.state();
        dependencyFixture.submitCurrentAllocationCommand();
        dependencyFixture.update();
        assertEquals(dependencyBefore, dependencyFixture.state());
        assertTrue(dependencyFixture.av02Inventory.snapshot().waitingTotes().isEmpty());
        assertTrue(dependencyFixture.lifecycle.snapshot().totes().isEmpty());

        ScenarioFixture fixture = new ScenarioFixture();

        fixture.submitCurrentAllocationCommand();
        AllocationState unauthorizedBefore = fixture.state();
        fixture.update();
        assertEquals(unauthorizedBefore, fixture.state());
        assertTrue(fixture.av02Inventory.snapshot().waitingTotes().isEmpty());
        assertTrue(fixture.lifecycle.snapshot().totes().isEmpty());
        assertNull(fixture.loadPlans.getLoadPlanFor(fixture.firstAv02Id()));

        fixture.authorizeAllEmptySheets();
        AllocationState authorizedBeforeCommand = fixture.state();
        fixture.update();
        assertEquals(authorizedBeforeCommand, fixture.state());

        Av02AllocationSnapshot mainSnapshot = fixture.freshAllocationSnapshot();
        Av02AllocationCandidate blockedAdapted = fixture.candidate(
                mainSnapshot, EMPTY_ADAPTED_104);
        assertEquals(List.of(Av02AllocationBlockReason.DEPENDENCY_NOT_READY),
                blockedAdapted.blockReasons());
        assertTrue(mainSnapshot.command().isPresent());
        assertEquals(EMPTY_DIRECT_104,
                mainSnapshot.command().orElseThrow().orderSheetKey());

        fixture.completePreparedInputThroughRealAdaptingStore();
        assertTrue(fixture.runtimeState.snapshot().preparedLineKeys()
                .contains(EMPTY_ADAPTED_LINE));
        assertTrue(fixture.adaptedStore.snapshot().stagedLineKeys()
                .contains(EMPTY_ADAPTED_LINE));
        assertTrue(fixture.av02Inventory.snapshot().waitingTotes().isEmpty());

        Av02AllocationSnapshot ready = fixture.freshAllocationSnapshot();
        assertTrue(fixture.candidate(ready, EMPTY_ADAPTED_104).eligible());
        assertEquals(EMPTY_DIRECT_104, ready.command().orElseThrow().orderSheetKey());

        fixture.submitCurrentAllocationCommand();
        fixture.update();

        PhysicalToteId allocatedId = fixture.firstAv02Id();
        Av02AllocatedTote allocated = fixture.av02Inventory.findWaiting(allocatedId)
                .orElseThrow();
        ToteLoadPlan emptyPlan = fixture.loadPlans.getLoadPlanFor(allocatedId);
        assertNotNull(emptyPlan);
        assertTrue(emptyPlan.getPackPlans().isEmpty());
        assertEquals(allocatedId, emptyPlan.physicalToteId());
        assertEquals(OperationalPhysicalToteSource.AV02, allocated.identity().source());
        assertEquals(OrderType.EMPTY, allocated.identity().orderType());
        assertEquals(PhysicalToteRole.PRE_P2P, allocated.identity().physicalToteRole());
        assertEquals(EMPTY_DIRECT_104, allocated.orderSheetKey());
        assertEquals(SERVICE_CENTRE_104, allocated.serviceCentreId());
        assertEquals(List.of("pharmacy-104-a"), allocated.pharmacyIds());
        assertEquals(1L, allocated.sourceSequenceNumber());
        assertEquals(1, fixture.av02Inventory.snapshot().occupancy());
        assertEquals(1, fixture.lifecycle.snapshot().totes().size());

        PhysicalToteAssignment assignment = fixture.lifecycle
                .activeAssignmentFor(EMPTY_DIRECT_104)
                .orElseThrow();
        assertEquals(allocatedId, assignment.physicalToteId());
        assertEquals(PhysicalToteAssignmentStage.PRE_P2P, assignment.stage());
        assertTrue(assignment.active());
        assertEquals(allocatedId, fixture.lifecycle.tote(allocatedId).orElseThrow().id());
        assertEquals(PhysicalToteRole.PRE_P2P,
                fixture.lifecycle.tote(allocatedId).orElseThrow().role());

        assertTrue(fixture.manifestCatalog.findByPhysicalToteId(allocatedId).isEmpty());
        assertTrue(fixture.osrInventory.snapshot().storedTotes().isEmpty());
        assertTrue(fixture.osrInventory.snapshot().departedTotes().isEmpty());
        assertTrue(fixture.av02Inventory.snapshot().departedTotes().isEmpty());
        assertTrue(fixture.lifecycle.activeAssignmentFor(EMPTY_DIRECT_108).isEmpty());

        Av02AllocationSnapshot capacityBlocked = fixture.freshAllocationSnapshot();
        assertTrue(capacityBlocked.command().isEmpty());
        assertEquals(
                List.of(EMPTY_THIRD_PARTY_104, EMPTY_ADAPTED_104, EMPTY_DIRECT_108),
                capacityBlocked.candidates().stream()
                        .map(Av02AllocationCandidate::orderSheetKey)
                        .toList());
        for (Av02AllocationCandidate candidate : capacityBlocked.candidates()) {
            assertFalse(candidate.eligible());
            assertEquals(List.of(Av02AllocationBlockReason.NO_AV02_CAPACITY),
                    candidate.blockReasons());
        }

        AllocationState capacityBefore = fixture.state();
        fixture.submitCurrentAllocationCommand();
        fixture.update();
        assertEquals(capacityBefore, fixture.state());
        assertEquals(1, fixture.av02Inventory.snapshot().occupancy());
        assertTrue(fixture.av02Inventory.findWaiting(allocatedId).isPresent());
        assertTrue(fixture.lifecycle.activeAssignmentFor(EMPTY_THIRD_PARTY_104).isEmpty());
        assertTrue(fixture.lifecycle.activeAssignmentFor(EMPTY_ADAPTED_104).isEmpty());
        assertTrue(fixture.lifecycle.activeAssignmentFor(EMPTY_DIRECT_108).isEmpty());
    }

    @Test
    void shouldRankOsrAndAv02ThroughOneOperationalReleaseBoundary() {
        assertMixedReleaseBoundary(false);
        assertMixedReleaseBoundary(true);
    }

    private void assertMixedReleaseBoundary(boolean osrFirst) {
        try (MixedRuntimeFixture fixture = new MixedRuntimeFixture(osrFirst)) {
            fixture.assertExactReleaseTargets();

            PhysicalToteId firstExpected = osrFirst
                    ? fixture.osrPhysicalToteId
                    : fixture.av02FirstPhysicalToteId;
            PhysicalToteId secondExpected = osrFirst
                    ? fixture.av02FirstPhysicalToteId
                    : fixture.osrPhysicalToteId;

            fixture.releaseOnce();
            fixture.assertAppliedRelease(firstExpected);
            if (osrFirst) {
                fixture.assertOsrReleased(fixture.osrPhysicalToteId);
                fixture.assertAv02Waiting(fixture.av02FirstPhysicalToteId);
            } else {
                fixture.assertAv02Released(fixture.av02FirstPhysicalToteId);
                fixture.assertThirdPartyFirstDestination(fixture.av02FirstPhysicalToteId);
                fixture.assertOsrWaiting(fixture.osrPhysicalToteId);
            }

            MixedRuntimeState beforeDeferral = fixture.mutableState();
            fixture.releaseOnce();
            DspOperationalReleaseEvaluation deferredEvaluation = fixture.runtime.controller()
                    .snapshot().lastEvaluation().orElseThrow();
            assertTrue(deferredEvaluation.releaseDecision().isEmpty());
            assertTrue(deferredEvaluation.blockedCandidates().stream()
                    .filter(candidate -> candidate.physicalToteId().equals(secondExpected))
                    .flatMap(candidate -> candidate.blocks().stream())
                    .anyMatch(block -> block.type() == OperationalReleaseBlockType.STATION_ADMISSION));
            assertEquals(beforeDeferral, fixture.mutableState());

            fixture.launchHead();
            fixture.releaseOnce();
            fixture.assertAppliedRelease(secondExpected);
            if (osrFirst) {
                fixture.assertAv02Released(fixture.av02FirstPhysicalToteId);
                fixture.assertThirdPartyFirstDestination(fixture.av02FirstPhysicalToteId);
            } else {
                fixture.assertOsrReleased(fixture.osrPhysicalToteId);
            }
            fixture.launchHead();

            fixture.assertAuthorized108AndRemaining104Work();
            fixture.allocateRemainingAv02ThroughProductionPath();
            fixture.releaseOnce();
            fixture.assertAppliedRelease(fixture.av02SecondPhysicalToteId);
            fixture.assertAv02Released(fixture.av02SecondPhysicalToteId);
            fixture.assertThirdPartyFirstDestination(fixture.av02SecondPhysicalToteId);
            fixture.launchHead();
        }
    }

    private static OrderSheetKey sheet(String orderId) {
        return new OrderSheetKey(orderId, 1);
    }

    private static DspSchedulerOrderState state(NotionalToteOrder order) {
        return new DspSchedulerOrderState(
                order,
                new RouteRequirements(false, false, false, true, false, StartLocation.AV02),
                DspOrderStatus.WAITING);
    }

    private static NotionalToteOrder emptyOrder(
            String orderId,
            String serviceCentreId,
            int priority,
            long sequence,
            String pharmacyId,
            DspOrderLineType lineType) {
        String lineReference = orderId.equals(EMPTY_ADAPTED_104.orderId())
                ? "adapted-line-empty-104" : "line-" + orderId;
        return new NotionalToteOrder(
                orderId,
                orderId,
                serviceCentreId,
                1,
                OrderType.EMPTY,
                List.of(new DspOrderItem(
                        lineReference,
                        "product-" + lineReference,
                        1,
                        pharmacyId,
                        "patient-" + orderId,
                        "prescription-" + orderId,
                        lineType,
                        orderId,
                        1,
                        0)),
                priority,
                sequence);
    }

    private static NotionalToteOrder fullPackOrder(
            String orderId,
            String serviceCentreId,
            int priority,
            long sequence,
            String pharmacyId) {
        return new NotionalToteOrder(
                orderId,
                "notional-" + orderId,
                serviceCentreId,
                1,
                OrderType.FULL_PACK,
                List.of(new DspOrderItem(
                        "line-" + orderId,
                        "product-" + orderId,
                        1,
                        pharmacyId,
                        "patient-" + orderId,
                        "prescription-" + orderId,
                        DspOrderLineType.FULL_PACK,
                        orderId,
                        1,
                        0)),
                priority,
                sequence);
    }

    private static DspSchedulerOrderState mixedLogicalState(
            NotionalToteOrder order,
            StartLocation startLocation) {
        return new DspSchedulerOrderState(
                order,
                new RouteRequirements(true, false, false, true, false, startLocation),
                DspOrderStatus.WAITING);
    }

    private static List<OperationalRouteDestination> mixedDestinations(
            ElasticRuntimeTestFixture elasticFixture) {
        List<OperationalRouteDestination> destinations = new ArrayList<>();
        destinations.add(new OperationalRouteDestination(
                StationType.THIRD_PARTY, "third-party-1"));
        destinations.add(new OperationalRouteDestination(
                StationType.ADAPTING, "adapting-1"));
        destinations.addAll(elasticFixture.definitions().stream()
                .map(definition -> definition.destination())
                .toList());
        return List.copyOf(destinations);
    }

    private static DspOperationalReleaseScheduler elasticScheduler() {
        return new DspOperationalReleaseScheduler(
                new OperationalDependencyReadinessPolicy(),
                new OperationalRouteEntryAdmissionPolicy(),
                new PharmacyGroupedSourceSequenceRankingPolicy(),
                new DeadlineAwareElasticStickyP2pLineAllocationPolicy());
    }

    private static DspOrderItem preparedLine(NotionalToteOrder adaptedOrder) {
        return new DspOrderItem(
                adaptedOrder.items().getFirst().lineReference(),
                "prepared-product-empty-104",
                1,
                adaptedOrder.items().getFirst().pharmacyId(),
                "prepared-patient-empty-104",
                "prepared-prescription-empty-104",
                DspOrderLineType.ADAPTED,
                adaptedOrder.orderId(),
                1,
                0);
    }

    private static final class ScenarioFixture {
        private final SimulationWorld world = new SimulationWorld();
        private final PhysicalToteLifecycleLedger lifecycle = new PhysicalToteLifecycleLedger();
        private final Av02PhysicalToteInventory av02Inventory =
                new Av02PhysicalToteInventory(new Av02AllocationConfig(1));
        private final OsrPhysicalInventory osrInventory =
                new OsrPhysicalInventory(new OsrInventoryConfig(2, List.of()));
        private final InboundToteManifestCatalog manifestCatalog =
                new InboundToteManifestCatalog(List.of());
        private final MutableToteLoadPlanRegistry loadPlans =
                new MapBackedToteLoadPlanRegistry();
        private final List<NotionalToteOrder> orders;
        private final DspSchedulerRuntimeState runtimeState;
        private final AdaptedLineStore adaptedStore = new AdaptedLineStore();
        private final AdaptingArea adaptingArea;
        private final AdaptingAreaController adaptingAreaController;
        private final Av02AllocationSnapshotFactory snapshotFactory =
                new Av02AllocationSnapshotFactory();
        private final AtomicReference<Optional<AllocateEmptyToteAtAv02Command>> command =
                new AtomicReference<>(Optional.empty());
        private final Av02AllocationController allocationController;
        private final Set<OrderSheetKey> authorizedEmptySheets = new LinkedHashSet<>();
        private long allocationSnapshotSequence;

        private ScenarioFixture() {
            this(List.of(
                    emptyOrder(
                            EMPTY_DIRECT_104.orderId(), SERVICE_CENTRE_104, PRIORITY_104, 1,
                            "pharmacy-104-a", DspOrderLineType.FULL_PACK),
                    emptyOrder(
                            EMPTY_THIRD_PARTY_104.orderId(), SERVICE_CENTRE_104, PRIORITY_104, 2,
                            "pharmacy-104-a", DspOrderLineType.FULL_PACK),
                    emptyOrder(
                            EMPTY_ADAPTED_104.orderId(), SERVICE_CENTRE_104, PRIORITY_104, 3,
                            "pharmacy-104-b", DspOrderLineType.ADAPTED),
                    emptyOrder(
                            EMPTY_DIRECT_108.orderId(), SERVICE_CENTRE_108, PRIORITY_108, 1,
                            "pharmacy-108-a", DspOrderLineType.FULL_PACK)));
        }

        private ScenarioFixture(List<NotionalToteOrder> orders) {
            if (orders == null || orders.isEmpty()) {
                throw new IllegalArgumentException("orders must not be null or empty");
            }
            this.orders = List.copyOf(orders);
            List<DspSchedulerOrderState> states = this.orders.stream()
                    .map(DspAv02OperationalAllocationScenarioTest::state)
                    .toList();
            runtimeState = new DspSchedulerRuntimeState(new WarehouseSchedulerSnapshot(
                    states, Map.of(), Set.of(), Optional.empty()));

            AdaptingStorageMap storageMap = new AdaptingStorageMap();
            AdaptingBenchId benchId = new AdaptingBenchId("av02-preparation-bench");
            storageMap.assignPharmacyToBench("pharmacy-104-b", benchId);
            adaptingArea = new AdaptingArea(
                    List.of(new AdaptingBench(benchId.value(), adaptedStore, 0d)), 0, storageMap);
            adaptingAreaController = new AdaptingAreaController(adaptingArea, runtimeState);

            allocationController = new Av02AllocationController(
                    () -> command.getAndSet(Optional.empty()),
                    this::freshAllocationSnapshot,
                    av02Inventory,
                    lifecycle,
                    new DeterministicAv02PhysicalToteIdAllocator(),
                    loadPlans);
            world.addController(allocationController);
        }

        private PhysicalToteId firstAv02Id() {
            return new PhysicalToteId("av02-000001");
        }

        private void authorizeAllEmptySheets() {
            orders.stream().map(NotionalToteOrder::orderSheetKey).forEach(authorizedEmptySheets::add);
        }

        private void submitCurrentAllocationCommand() {
            allocationSnapshotSequence++;
            command.set(freshAllocationSnapshot().command());
        }

        private Av02AllocationSnapshot freshAllocationSnapshot() {
            return snapshotFactory.create(
                    allocationSnapshotSequence,
                    runtimeState.snapshot(),
                    supplySnapshot(),
                    av02Inventory.snapshot(),
                    lifecycle.snapshot());
        }

        private Av02AllocationCandidate candidate(
                Av02AllocationSnapshot snapshot,
                OrderSheetKey orderSheetKey) {
            return snapshot.candidates().stream()
                    .filter(candidate -> candidate.orderSheetKey().equals(orderSheetKey))
                    .findFirst()
                    .orElseThrow();
        }

        private void completePreparedInputThroughRealAdaptingStore() {
            NotionalToteOrder adaptedOrder = orders.stream()
                    .filter(order -> order.orderSheetKey().equals(EMPTY_ADAPTED_104))
                    .findFirst()
                    .orElseThrow();
            DspOrderItem preparedLine = preparedLine(adaptedOrder);
            AdaptingVisit visit = AdaptingVisit.store(
                    new PhysicalToteId("prepared-source-empty-104"),
                    new OrderSheetKey("prepared-empty-adapted-104", 1),
                    SERVICE_CENTRE_104,
                    List.of(preparedLine));
            AdaptingBenchSelection selection = adaptingArea.submitVisit(visit);
            assertTrue(selection.accepted());
            AdaptingBench bench = adaptingArea.bench(selection.benchId());
            bench.startProcessing();
            AdaptingBenchCompletion completion = adaptingAreaController
                    .applyBenchCompletion(new AdaptingBenchId(selection.benchId().value()))
                    .orElseThrow();
            assertEquals(visit, completion.visit());
        }

        private DspSupplySnapshot supplySnapshot() {
            return new DspSupplySnapshot(
                    "av02-scenario-supply",
                    0,
                    1200,
                    0,
                    Optional.empty(),
                    Optional.of(Duration.ZERO),
                    authorizedEmptySheets,
                    List.of(
                            serviceCentre(SERVICE_CENTRE_104, PRIORITY_104),
                            serviceCentre(SERVICE_CENTRE_108, PRIORITY_108)),
                    0);
        }

        private ServiceCentreSupplySnapshot serviceCentre(String serviceCentreId, int priority) {
            Set<OrderSheetKey> authorizedForCentre = new LinkedHashSet<>();
            if (serviceCentreId.equals(SERVICE_CENTRE_104)) {
                authorizedForCentre.addAll(authorizedEmptySheets.stream()
                        .filter(key -> orders.stream().anyMatch(order ->
                                order.orderSheetKey().equals(key)
                                        && order.serviceCentreId().equals(SERVICE_CENTRE_104)))
                        .toList());
            } else {
                authorizedForCentre.addAll(authorizedEmptySheets.stream()
                        .filter(key -> key.equals(EMPTY_DIRECT_108))
                        .toList());
            }
            ServiceCentreAuthorizationState state = authorizedForCentre.isEmpty()
                    ? ServiceCentreAuthorizationState.HELD_UPSTREAM
                    : ServiceCentreAuthorizationState.AUTHORIZED;
            return new ServiceCentreSupplySnapshot(
                    serviceCentreId,
                    priority,
                    state,
                    Optional.of(Duration.ZERO),
                    0,
                    0,
                    0,
                    0,
                    authorizedForCentre,
                    List.of());
        }

        private void update() {
            world.update(0d);
        }

        private AllocationState state() {
            return new AllocationState(
                    runtimeState.snapshot(),
                    supplySnapshot(),
                    av02Inventory.snapshot(),
                    osrInventory.snapshot(),
                    lifecycle.snapshot(),
                    adaptedStore.snapshot(),
                    loadPlanState(),
                    allocationController.lastAllocatedTote());
        }

        private Map<PhysicalToteId, ToteLoadPlan> loadPlanState() {
            Map<PhysicalToteId, ToteLoadPlan> plans = new java.util.LinkedHashMap<>();
            for (PhysicalToteId id : List.of(firstAv02Id())) {
                ToteLoadPlan plan = loadPlans.getLoadPlanFor(id);
                if (plan != null) {
                    plans.put(id, plan);
                }
            }
            return Map.copyOf(plans);
        }
    }

    private static final class MixedRuntimeFixture implements AutoCloseable {
        private final ScenarioFixture allocationFixture;
        private final PhysicalToteId av02FirstPhysicalToteId =
                new PhysicalToteId("av02-000001");
        private final PhysicalToteId av02SecondPhysicalToteId =
                new PhysicalToteId("av02-000002");
        private final PhysicalToteId osrPhysicalToteId =
                new PhysicalToteId("osr-000001");
        private final NotionalToteOrder av02FirstOrder;
        private final NotionalToteOrder av02SecondOrder;
        private final InboundToteManifest osrManifest;
        private final InboundToteManifestCatalog manifestCatalog;
        private final InboundToteLifecycleController osrLifecycleController;
        private final ElasticRuntimeTestFixture elasticFixture =
                new ElasticRuntimeTestFixture();
        private final DspP2pElasticAllocationRuntime elasticRuntime;
        private final OsrOutboundRouteLaunchQueue launchQueue =
                new OsrOutboundRouteLaunchQueue("mixed-operational-launch", 1);
        private final OsrOutboundRouteLaunchTargetRegistry routeTargetRegistry;
        private final DspOperationalReleaseRuntime runtime;
        private final OsrOutboundTransportQueue transportQueue =
                new OsrOutboundTransportQueue("mixed-operational-transport", 4);
        private final OsrOutboundRouteLaunchController launchController;
        private long evaluationCount;

        private MixedRuntimeFixture(boolean osrFirst) {
            allocationFixture = new ScenarioFixture(List.of(
                    emptyOrder(
                            EMPTY_DIRECT_104.orderId(), SERVICE_CENTRE_104, PRIORITY_104, 1,
                            "pharmacy-104-a", DspOrderLineType.FULL_PACK),
                    emptyOrder(
                            EMPTY_THIRD_PARTY_104.orderId(), SERVICE_CENTRE_104, PRIORITY_104, 2,
                            "pharmacy-104-a", DspOrderLineType.FULL_PACK),
                    emptyOrder(
                            EMPTY_ADAPTED_104.orderId(), SERVICE_CENTRE_104, PRIORITY_104, 3,
                            "pharmacy-104-b", DspOrderLineType.ADAPTED),
                    emptyOrder(
                            EMPTY_DIRECT_108.orderId(), SERVICE_CENTRE_108, PRIORITY_108, 1,
                            "pharmacy-108-a", DspOrderLineType.FULL_PACK)));
            allocationFixture.authorizeAllEmptySheets();
            allocationFixture.submitCurrentAllocationCommand();
            allocationFixture.update();

            av02FirstOrder = allocationFixture.orders.stream()
                    .filter(order -> order.orderSheetKey().equals(EMPTY_DIRECT_104))
                    .findFirst()
                    .orElseThrow();
            av02SecondOrder = allocationFixture.orders.stream()
                    .filter(order -> order.orderSheetKey().equals(EMPTY_THIRD_PARTY_104))
                    .findFirst()
                    .orElseThrow();
            assertTrue(allocationFixture.av02Inventory.findWaiting(av02FirstPhysicalToteId)
                    .isPresent());

            NotionalToteOrder osrOrder = fullPackOrder(
                    "osr-order",
                    SERVICE_CENTRE_104,
                    PRIORITY_104,
                    osrFirst ? 0 : 4,
                    "pharmacy-104-a");
            osrManifest = new InboundToteManifest(
                    osrPhysicalToteId,
                    osrOrder.orderSheetKey(),
                    osrOrder.orderType(),
                    osrOrder.serviceCentreId(),
                    osrOrder.items(),
                    osrOrder.sequenceNumber());
            manifestCatalog = new InboundToteManifestCatalog(List.of(osrManifest));
            osrLifecycleController = new InboundToteLifecycleController(
                    allocationFixture.lifecycle,
                    manifestCatalog);
            allocationFixture.osrInventory.store(osrManifest);
            allocationFixture.loadPlans.putLoadPlan(
                    new ToteLoadPlan(osrPhysicalToteId, List.of()));

            routeTargetRegistry = new OsrOutboundRouteLaunchTargetRegistry(
                    launchQueue,
                    mixedDestinations(elasticFixture));
            elasticRuntime = elasticFixture.createRuntime(
                    this::elasticLogicalSnapshot,
                    manifestCatalog,
                    allocationFixture.lifecycle::snapshot,
                    allocationFixture.av02Inventory::snapshot,
                    this::elasticSupplySnapshot);
            DspOperationalClock clock = new DspOperationalClock(
                    DspOperationalClockConfig.productionBaseline(
                            LocalDate.of(2026, 8, 26)));
            runtime = new DspOperationalReleaseRuntimeFactory().createElasticWithAv02(
                    new SynchronousOperationalReleaseEvaluationSource(elasticScheduler()),
                    allocationFixture.osrInventory,
                    osrLifecycleController,
                    manifestCatalog,
                    this::logicalSnapshot,
                    clock::initialSnapshot,
                    MixedRuntimeFixture::openAdmission,
                    routeTargetRegistry,
                    allocationFixture.av02Inventory,
                    allocationFixture.lifecycle,
                    allocationFixture.loadPlans,
                    elasticRuntime);
            launchController = new OsrOutboundRouteLaunchController(
                    launchQueue,
                    transportQueue,
                    new LoadPlanOsrOutboundToteHydrator(
                            allocationFixture.loadPlans,
                            this::detached));
        }

        private void assertExactReleaseTargets() {
            List<OperationalRouteDestination> expected = mixedDestinations(elasticFixture);
            assertEquals(expected, routeTargetRegistry.destinations());
            assertEquals(expected, routeTargetRegistry.targets().stream()
                    .map(target -> target.destination())
                    .toList());
            assertEquals(expected, routeTargetRegistry.av02Targets().stream()
                    .map(target -> target.destination())
                    .toList());
            assertEquals(expected.size(), runtime.routeTargetAdmissionSnapshots().size());
            assertEquals(7, expected.size());
        }

        private void assertAuthorized108AndRemaining104Work() {
            Av02AllocationSnapshot snapshot = allocationFixture.freshAllocationSnapshot();
            Av02AllocationCandidate thirdParty = allocationFixture.candidate(
                    snapshot, EMPTY_THIRD_PARTY_104);
            Av02AllocationCandidate direct108 = allocationFixture.candidate(
                    snapshot, EMPTY_DIRECT_108);
            assertTrue(thirdParty.eligible());
            assertTrue(direct108.eligible());
            assertTrue(allocationFixture.supplySnapshot()
                    .authorizedEmptyOrderSheetKeys()
                    .contains(EMPTY_DIRECT_108));
            assertTrue(logicalSnapshot().orderStates().stream()
                    .anyMatch(state -> state.order().orderSheetKey().equals(EMPTY_DIRECT_108)));
        }

        private void releaseOnce() {
            runtime.controller().update(new SimulationContext(), 0.1d);
            evaluationCount++;
        }

        private void assertAppliedRelease(PhysicalToteId expectedPhysicalToteId) {
            var controllerSnapshot = runtime.controller().snapshot();
            assertEquals(evaluationCount - 1, controllerSnapshot.lastCompletedEvaluationSequence()
                    .orElseThrow());
            DspOperationalReleaseEvaluation evaluation = controllerSnapshot.lastEvaluation()
                    .orElseThrow();
            var decision = evaluation.releaseDecision().orElseThrow();
            assertEquals(expectedPhysicalToteId,
                    decision.candidate().physicalCandidate().physicalToteId());
            assertEquals(SERVICE_CENTRE_104,
                    decision.candidate().physicalCandidate().serviceCentreId());
            assertTrue(controllerSnapshot.lastCommandApplicationResult()
                    .orElseThrow().applied(),
                    () -> controllerSnapshot.lastCommandApplicationResult()
                            .orElseThrow().reason());
            assertEquals(1, launchQueue.snapshot().occupancy());
            OperationalRouteLaunchRequest request = launchQueue.peek().orElseThrow();
            assertEquals(expectedPhysicalToteId, request.physicalToteId());
            assertEquals(SERVICE_CENTRE_104, request.serviceCentreId());
            assertEquals(List.of("pharmacy-104-a"), request.pharmacyIds());
            assertEquals(decision.command().releaseTargetId(), request.destination().targetId());
            assertTrue(request.p2pAssignment().isPresent());
            assertSame(decision.command().proposedP2pAssignment().orElseThrow(),
                    request.p2pAssignment().orElseThrow());
        }

        private void assertAv02Waiting(PhysicalToteId physicalToteId) {
            assertTrue(allocationFixture.av02Inventory.findWaiting(physicalToteId).isPresent());
            assertTrue(allocationFixture.av02Inventory.snapshot().departedTotes().stream()
                    .noneMatch(tote -> tote.physicalToteId().equals(physicalToteId)));
        }

        private void assertOsrWaiting(PhysicalToteId physicalToteId) {
            assertTrue(allocationFixture.osrInventory.snapshot().findStored(physicalToteId)
                    .isPresent());
            assertFalse(allocationFixture.osrInventory.snapshot().hasDeparted(physicalToteId));
        }

        private void assertAv02Released(PhysicalToteId physicalToteId) {
            assertTrue(allocationFixture.av02Inventory.findWaiting(physicalToteId).isEmpty());
            assertEquals(1, allocationFixture.av02Inventory.snapshot().departedTotes().stream()
                    .filter(tote -> tote.physicalToteId().equals(physicalToteId))
                    .count());
            var lifecycleRecord = allocationFixture.lifecycle.tote(physicalToteId).orElseThrow();
            assertEquals(PhysicalToteRole.PRE_P2P, lifecycleRecord.role());
            assertEquals(PhysicalToteLifecycleState.ACTIVE_PRE_P2P, lifecycleRecord.state());
            List<PhysicalToteAssignment> assignments = allocationFixture.lifecycle
                    .activeAssignmentsFor(physicalToteId);
            assertEquals(1, assignments.size());
            assertEquals(PhysicalToteAssignmentStage.PRE_P2P, assignments.getFirst().stage());
            assertTrue(manifestCatalog.findByPhysicalToteId(physicalToteId).isEmpty());
            assertFalse(allocationFixture.osrInventory.snapshot().hasDeparted(physicalToteId));

            P2pPhysicalToteAssignment assignment = elasticRuntime.operationalSnapshot()
                    .leases().findAssignment(physicalToteId).orElseThrow();
            assertEquals(physicalToteId, assignment.physicalToteId());
            assertEquals(SERVICE_CENTRE_104, assignment.serviceCentreId());
            assertEquals(StationType.P2P, assignment.destination().stationType());
            OperationalRouteLaunchRequest request = launchQueue.peek().orElseThrow();
            assertEquals(OperationalPhysicalToteSource.AV02, request.source());
            assertEquals(physicalToteId, request.identity().physicalToteId());
            assertSame(request.p2pAssignment().orElseThrow(), assignment);
        }

        private void assertOsrReleased(PhysicalToteId physicalToteId) {
            assertTrue(allocationFixture.osrInventory.snapshot().findStored(physicalToteId)
                    .isEmpty());
            assertEquals(1, allocationFixture.osrInventory.snapshot().departedTotes().stream()
                    .filter(manifest -> manifest.physicalToteId().equals(physicalToteId))
                    .count());
            var lifecycleRecord = allocationFixture.lifecycle.tote(physicalToteId).orElseThrow();
            assertEquals(PhysicalToteRole.INBOUND_PACK, lifecycleRecord.role());
            assertEquals(PhysicalToteLifecycleState.INBOUND_PACK_TOTE, lifecycleRecord.state());
            PhysicalToteAssignment assignment = allocationFixture.lifecycle
                    .activeAssignmentFor(osrManifest.orderSheetKey()).orElseThrow();
            assertEquals(physicalToteId, assignment.physicalToteId());
            assertEquals(PhysicalToteAssignmentStage.INBOUND_PACK, assignment.stage());
            assertTrue(manifestCatalog.findByPhysicalToteId(physicalToteId).isPresent());
            OperationalRouteLaunchRequest request = launchQueue.peek().orElseThrow();
            assertEquals(OperationalPhysicalToteSource.OSR, request.source());
            assertEquals(physicalToteId, request.identity().physicalToteId());
            P2pPhysicalToteAssignment p2pAssignment = elasticRuntime.operationalSnapshot()
                    .leases().findAssignment(physicalToteId).orElseThrow();
            assertSame(request.p2pAssignment().orElseThrow(), p2pAssignment);
        }

        private void assertThirdPartyFirstDestination(PhysicalToteId physicalToteId) {
            OperationalRouteLaunchRequest request = launchQueue.peek().orElseThrow();
            assertEquals(physicalToteId, request.physicalToteId());
            assertEquals(new OperationalRouteDestination(
                    StationType.THIRD_PARTY, "third-party-1"), request.destination());
            assertTrue(request.p2pAssignment().isPresent());
            assertEquals(StationType.P2P,
                    request.p2pAssignment().orElseThrow().destination().stationType());
            assertFalse(request.destination().equals(
                    request.p2pAssignment().orElseThrow().destination()));
        }

        private MixedRuntimeState mutableState() {
            Map<PhysicalToteId, ToteLoadPlan> plans = new java.util.LinkedHashMap<>();
            for (PhysicalToteId physicalToteId : List.of(
                    av02FirstPhysicalToteId, av02SecondPhysicalToteId, osrPhysicalToteId)) {
                ToteLoadPlan plan = allocationFixture.loadPlans
                        .getLoadPlanFor(physicalToteId);
                if (plan != null) {
                    plans.put(physicalToteId, plan);
                }
            }
            return new MixedRuntimeState(
                    allocationFixture.av02Inventory.snapshot(),
                    allocationFixture.osrInventory.snapshot(),
                    allocationFixture.lifecycle.snapshot(),
                    elasticRuntime.operationalSnapshot(),
                    allocationFixture.supplySnapshot(),
                    logicalSnapshot(),
                    allocationFixture.adaptedStore.snapshot(),
                    Map.copyOf(plans),
                    runtime.routeTargetAdmissionSnapshots(),
                    launchQueue.snapshot(),
                    transportQueue.snapshot());
        }

        private void launchHead() {
            OperationalRouteLaunchRequest head = launchQueue.peek().orElseThrow();
            launchController.update(new SimulationContext(), 0.1d);
            assertTrue(launchQueue.peek().isEmpty());
            RoutedPhysicalTote routedTote = transportQueue.peek().orElseThrow();
            assertSame(head, routedTote.launchRequest());
            assertEquals(head.physicalToteId(), routedTote.physicalToteId());
            assertSame(allocationFixture.loadPlans.getLoadPlanFor(head.physicalToteId()),
                    routedTote.loadPlan());
            assertEquals(head.destination(), routedTote.destination());
            assertSame(routedTote, transportQueue.dequeue().orElseThrow());
        }

        private void allocateRemainingAv02ThroughProductionPath() {
            Av02AllocationSnapshot snapshot = allocationFixture.freshAllocationSnapshot();
            assertTrue(allocationFixture.candidate(snapshot, EMPTY_THIRD_PARTY_104).eligible());
            assertTrue(allocationFixture.candidate(snapshot, EMPTY_DIRECT_108).eligible());
            allocationFixture.submitCurrentAllocationCommand();
            allocationFixture.update();
            Av02AllocatedTote allocated = allocationFixture.av02Inventory
                    .findWaiting(av02SecondPhysicalToteId).orElseThrow();
            assertEquals(EMPTY_THIRD_PARTY_104, allocated.orderSheetKey());
            assertEquals(av02SecondOrder.serviceCentreId(), allocated.serviceCentreId());
        }

        private WarehouseSchedulerSnapshot logicalSnapshot() {
            return new WarehouseSchedulerSnapshot(logicalStates(true), Map.of(), Set.of(),
                    Optional.empty());
        }

        private WarehouseSchedulerSnapshot elasticLogicalSnapshot() {
            return new WarehouseSchedulerSnapshot(logicalStates(false), Map.of(), Set.of(),
                    Optional.empty());
        }

        private List<DspSchedulerOrderState> logicalStates(boolean include108) {
            List<DspSchedulerOrderState> states = new ArrayList<>();
            NotionalToteOrder osrOrder = fullPackOrder(
                    osrManifest.orderSheetKey().orderId(),
                    osrManifest.serviceCentreId(),
                    PRIORITY_104,
                    osrManifest.sourceSequenceNumber(),
                    "pharmacy-104-a");
            states.add(mixedLogicalState(osrOrder, StartLocation.OSR));
            allocationFixture.orders.stream()
                    .filter(order -> include108 || !order.serviceCentreId()
                            .equals(SERVICE_CENTRE_108))
                    .map(order -> mixedLogicalState(order, StartLocation.AV02))
                    .forEach(states::add);
            return List.copyOf(states);
        }

        private DspSupplySnapshot elasticSupplySnapshot() {
            DspSupplySnapshot source = allocationFixture.supplySnapshot();
            ServiceCentreSupplySnapshot serviceCentre = allocationFixture.serviceCentre(
                    SERVICE_CENTRE_104, PRIORITY_104);
            return new DspSupplySnapshot(
                    source.policyId(),
                    source.lowWaterMark(),
                    source.osrCapacity(),
                    source.osrOccupancy(),
                    source.activeInboundServiceCentreId(),
                    source.nextPhysicalAdmissionElapsedTime(),
                    serviceCentre.authorizedEmptyOrderSheetKeys(),
                    List.of(serviceCentre),
                    source.admittedAfterStartupCount());
        }

        private RoutedPhysicalTote detached(
                OperationalRouteLaunchRequest request,
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
                    "mixed-operational-route-" + physicalToteId,
                    new online.davisfamily.threedee.path.LinearSegment3(
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
            elasticRuntime.close();
        }

        private static StationAdmissionSnapshot openAdmission(
                StationType stationType,
                DspSchedulerOrderState candidate,
                WarehouseSchedulerSnapshot snapshot) {
            String targetId = stationType == StationType.THIRD_PARTY
                    ? "third-party-1"
                    : stationType == StationType.ADAPTING ? "adapting-1" : "p2p-1";
            return new StationAdmissionSnapshot(
                    stationType,
                    new StationCapacity(1, 1),
                    new StationSnapshot(stationType, 0, 0),
                    true,
                    "",
                    Optional.of(targetId));
        }
    }

    private record MixedRuntimeState(
            Av02InventorySnapshot av02Inventory,
            OsrInventorySnapshot osrInventory,
            PhysicalToteLifecycleSnapshot lifecycle,
            DspP2pElasticAllocationRuntimeSnapshot elastic,
            DspSupplySnapshot supply,
            WarehouseSchedulerSnapshot scheduler,
            AdaptedLineStoreSnapshot adaptedStore,
            Map<PhysicalToteId, ToteLoadPlan> loadPlans,
            List<online.davisfamily.warehouse.sim.dsp.osr.release.launch
                    .OperationalRouteTargetAdmissionSnapshot> targetAdmissions,
            online.davisfamily.warehouse.sim.dsp.osr.release.launch
                    .OsrOutboundRouteLaunchQueueSnapshot launchQueue,
            online.davisfamily.warehouse.sim.dsp.transport.OsrOutboundTransportQueueSnapshot
                    transportQueue) {
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

    private record AllocationState(
            WarehouseSchedulerSnapshot scheduler,
            DspSupplySnapshot supply,
            Av02InventorySnapshot av02Inventory,
            OsrInventorySnapshot osrInventory,
            PhysicalToteLifecycleSnapshot lifecycle,
            AdaptedLineStoreSnapshot adaptedStore,
            Map<PhysicalToteId, ToteLoadPlan> loadPlans,
            Optional<Av02AllocatedTote> lastAllocatedTote) {
    }
}
