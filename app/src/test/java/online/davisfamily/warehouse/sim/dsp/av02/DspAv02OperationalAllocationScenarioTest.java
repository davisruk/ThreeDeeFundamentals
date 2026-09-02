package online.davisfamily.warehouse.sim.dsp.av02;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

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
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifestCatalog;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteAssignment;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteAssignmentStage;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleLedger;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleSnapshot;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRole;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StartLocation;
import online.davisfamily.warehouse.sim.dsp.osr.OsrInventoryConfig;
import online.davisfamily.warehouse.sim.dsp.osr.OsrInventorySnapshot;
import online.davisfamily.warehouse.sim.dsp.osr.OsrPhysicalInventory;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteSource;
import online.davisfamily.warehouse.sim.dsp.runtime.DspSchedulerRuntimeState;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspOrderStatus;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;
import online.davisfamily.warehouse.sim.dsp.supply.DspSupplySnapshot;
import online.davisfamily.warehouse.sim.dsp.supply.ServiceCentreAuthorizationState;
import online.davisfamily.warehouse.sim.dsp.supply.ServiceCentreSupplySnapshot;
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
