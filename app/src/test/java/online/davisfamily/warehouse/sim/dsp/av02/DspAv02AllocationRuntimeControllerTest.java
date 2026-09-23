package online.davisfamily.warehouse.sim.dsp.av02;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.warehouse.sim.dsp.adapting.MapBackedToteLoadPlanRegistry;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteIdAllocator;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleLedger;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleSnapshot;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRecord;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRole;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StartLocation;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteIdentity;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteSource;
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspOrderStatus;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;
import online.davisfamily.warehouse.sim.dsp.supply.DspSupplySnapshot;
import online.davisfamily.warehouse.sim.dsp.supply.ServiceCentreAuthorizationState;
import online.davisfamily.warehouse.sim.dsp.supply.ServiceCentreSupplySnapshot;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

class DspAv02AllocationRuntimeControllerTest {

    @Test
    void shouldPublishMonotonicImmutableSnapshotsAndAllocateAtMostOnePerUpdate() {
        Fixture fixture = fixture(
                2,
                List.of(
                        emptyOrder("empty-1", 0, DspOrderLineType.FULL_PACK),
                        emptyOrder("empty-2", 1, DspOrderLineType.FULL_PACK)));

        DspAv02AllocationRuntimeSnapshot initial = fixture.runtime.snapshot();
        assertEquals(0, initial.sequence());
        assertEquals(0, initial.selectedCommand().orElseThrow().snapshotSequence());
        assertSame(initial, fixture.runtime.snapshot());

        fixture.runtime.update(new SimulationContext(), 0d);

        DspAv02AllocationRuntimeSnapshot first = fixture.runtime.snapshot();
        assertEquals(1, first.sequence());
        assertEquals(1, first.selectedCommand().orElseThrow().snapshotSequence());
        assertEquals(1, first.freshRevalidationSnapshot().orElseThrow().sequence());
        assertNotSame(first.snapshot(), first.freshRevalidationSnapshot().orElseThrow());
        assertEquals(1, fixture.inventory.occupancy());
        assertEquals(1, fixture.idAllocator.calls);
        Av02AllocatedTote firstTote = fixture.inventory.head().orElseThrow();
        assertSame(firstTote, fixture.runtime.lastAllocatedTote().orElseThrow());
        assertSame(firstTote, first.lastAllocatedTote().orElseThrow());
        ToteLoadPlan firstPlan = fixture.loadPlans.getLoadPlanFor(firstTote.physicalToteId());
        assertSame(firstPlan, fixture.loadPlans.getLoadPlanFor(firstTote.physicalToteId()));
        assertEquals(firstTote.physicalToteId(), firstPlan.physicalToteId());
        assertTrue(firstPlan.getPackPlans().isEmpty());
        assertEquals(1, fixture.lifecycle.snapshot().totes().size());
        assertEquals(1, fixture.lifecycle.snapshot().assignments().size());
        assertEquals(1, fixture.runtime.snapshot().sequence());

        assertEquals(0, initial.sequence());
        assertTrue(initial.selectedCommand().isPresent());
        assertEquals(0, initial.snapshot().sequence());

        fixture.runtime.update(new SimulationContext(), 0d);

        DspAv02AllocationRuntimeSnapshot second = fixture.runtime.snapshot();
        assertEquals(2, second.sequence());
        assertEquals(2, second.selectedCommand().orElseThrow().snapshotSequence());
        assertEquals(2, second.freshRevalidationSnapshot().orElseThrow().sequence());
        assertNotSame(second.snapshot(), second.freshRevalidationSnapshot().orElseThrow());
        assertEquals(2, fixture.inventory.occupancy());
        assertEquals(2, fixture.idAllocator.calls);
        assertEquals(
                List.of("av02-000001", "av02-000002"),
                fixture.inventory.snapshot().waitingTotes().stream()
                        .map(tote -> tote.physicalToteId().value())
                        .toList());
        assertEquals(2, fixture.lifecycle.snapshot().totes().size());
        assertEquals(2, fixture.lifecycle.snapshot().assignments().size());
        Av02AllocatedTote secondTote = fixture.inventory.snapshot().waitingTotes().getLast();
        assertSame(secondTote, fixture.runtime.lastAllocatedTote().orElseThrow());
        assertSame(secondTote, second.lastAllocatedTote().orElseThrow());
        assertEquals(secondTote.physicalToteId(),
                fixture.loadPlans.getLoadPlanFor(secondTote.physicalToteId()).physicalToteId());
    }

    @Test
    void shouldRevalidateLiveInputsBeforeAllocation() {
        NotionalToteOrder authorizationOrder = emptyOrder(
                "empty-authorization", 0, DspOrderLineType.FULL_PACK);
        DspSupplySnapshot authorized = supply(
                List.of(authorizationOrder), Set.of(authorizationOrder.orderSheetKey()));
        DspSupplySnapshot unauthorized = supply(List.of(authorizationOrder), Set.of());
        Fixture authorizationFixture = fixture(
                1,
                List.of(authorizationOrder),
                new SequenceSupplier<>(List.of(scheduler(
                        List.of(authorizationOrder), Set.of()))),
                new SequenceSupplier<>(Arrays.asList(authorized, authorized, unauthorized)),
                new SequenceSupplier<>(List.of(emptyLifecycle())));

        assertRejectedAfterFreshCommand(authorizationFixture);
        assertEquals(
                List.of(Av02AllocationBlockReason.EMPTY_NOT_AUTHORIZED),
                authorizationFixture.runtime.snapshot().freshRevalidationSnapshot()
                        .orElseThrow().candidates().getFirst().blockReasons());

        NotionalToteOrder dependencyOrder = emptyOrder(
                "empty-dependency", 0, DspOrderLineType.ADAPTED);
        PreparedLineKey preparedLine = PreparedLineKey.forDispatchLine(
                dependencyOrder, dependencyOrder.items().getFirst());
        WarehouseSchedulerSnapshot ready = scheduler(
                List.of(dependencyOrder), Set.of(preparedLine));
        WarehouseSchedulerSnapshot notReady = scheduler(List.of(dependencyOrder), Set.of());
        DspSupplySnapshot dependencySupply = supply(
                List.of(dependencyOrder), Set.of(dependencyOrder.orderSheetKey()));
        Fixture dependencyFixture = fixture(
                1,
                List.of(dependencyOrder),
                new SequenceSupplier<>(Arrays.asList(ready, ready, notReady)),
                new SequenceSupplier<>(List.of(dependencySupply)),
                new SequenceSupplier<>(List.of(emptyLifecycle())));

        assertRejectedAfterFreshCommand(dependencyFixture);
        assertEquals(
                List.of(Av02AllocationBlockReason.DEPENDENCY_NOT_READY),
                dependencyFixture.runtime.snapshot().freshRevalidationSnapshot()
                        .orElseThrow().candidates().getFirst().blockReasons());
    }

    @Test
    void shouldPublishBlockedDiagnosticsAndPreserveTheLastSnapshotOnFailure() {
        NotionalToteOrder unauthorizedOrder = emptyOrder(
                "empty-unauthorized", 0, DspOrderLineType.FULL_PACK);
        Fixture unauthorized = fixture(
                1,
                List.of(unauthorizedOrder),
                new SequenceSupplier<>(List.of(scheduler(List.of(unauthorizedOrder), Set.of()))),
                new SequenceSupplier<>(List.of(supply(List.of(unauthorizedOrder), Set.of()))),
                new SequenceSupplier<>(List.of(emptyLifecycle())));
        assertBlocked(unauthorized, Av02AllocationBlockReason.EMPTY_NOT_AUTHORIZED);

        NotionalToteOrder dependencyOrder = emptyOrder(
                "empty-dependency", 0, DspOrderLineType.ADAPTED);
        Fixture dependency = fixture(
                1,
                List.of(dependencyOrder),
                new SequenceSupplier<>(List.of(scheduler(List.of(dependencyOrder), Set.of()))),
                new SequenceSupplier<>(List.of(supply(
                        List.of(dependencyOrder), Set.of(dependencyOrder.orderSheetKey())))),
                new SequenceSupplier<>(List.of(emptyLifecycle())));
        assertBlocked(dependency, Av02AllocationBlockReason.DEPENDENCY_NOT_READY);

        NotionalToteOrder capacityOrder = emptyOrder(
                "empty-capacity", 0, DspOrderLineType.FULL_PACK);
        Av02PhysicalToteInventory fullInventory = new Av02PhysicalToteInventory(
                new Av02AllocationConfig(1));
        fullInventory.store(allocated(
                emptyOrder("capacity-blocker", 9, DspOrderLineType.FULL_PACK),
                "av02-blocker",
                9));
        Fixture capacity = fixture(
                1,
                List.of(capacityOrder),
                new SequenceSupplier<>(List.of(scheduler(List.of(capacityOrder), Set.of()))),
                new SequenceSupplier<>(List.of(supply(
                        List.of(capacityOrder), Set.of(capacityOrder.orderSheetKey())))),
                new SequenceSupplier<>(List.of(emptyLifecycle())),
                fullInventory);
        assertBlocked(capacity, Av02AllocationBlockReason.NO_AV02_CAPACITY);

        assertFailureRetainsPublishedSnapshot(false);
        assertFailureRetainsPublishedSnapshot(true);
    }

    @Test
    void shouldRejectInvalidConstructionAndUpdatesWithoutAdvancingSequence() {
        Fixture fixture = fixture(
                1,
                List.of(emptyOrder("empty-construction", 0, DspOrderLineType.FULL_PACK)));

        assertThrows(IllegalArgumentException.class, () -> new DspAv02AllocationRuntimeController(
                null,
                fixture.supplySupplier::get,
                fixture.lifecycleSupplier::get,
                fixture.inventory,
                fixture.lifecycle,
                fixture.idAllocator,
                fixture.loadPlans));
        assertThrows(IllegalArgumentException.class, () -> new DspAv02AllocationRuntimeController(
                fixture.schedulerSupplier::get,
                null,
                fixture.lifecycleSupplier::get,
                fixture.inventory,
                fixture.lifecycle,
                fixture.idAllocator,
                fixture.loadPlans));
        assertThrows(IllegalArgumentException.class, () -> new DspAv02AllocationRuntimeController(
                fixture.schedulerSupplier::get,
                fixture.supplySupplier::get,
                null,
                fixture.inventory,
                fixture.lifecycle,
                fixture.idAllocator,
                fixture.loadPlans));
        assertThrows(IllegalArgumentException.class, () -> new DspAv02AllocationRuntimeController(
                fixture.schedulerSupplier::get,
                fixture.supplySupplier::get,
                fixture.lifecycleSupplier::get,
                null,
                fixture.lifecycle,
                fixture.idAllocator,
                fixture.loadPlans));
        assertThrows(IllegalArgumentException.class, () -> new DspAv02AllocationRuntimeController(
                fixture.schedulerSupplier::get,
                fixture.supplySupplier::get,
                fixture.lifecycleSupplier::get,
                fixture.inventory,
                null,
                fixture.idAllocator,
                fixture.loadPlans));
        assertThrows(IllegalArgumentException.class, () -> new DspAv02AllocationRuntimeController(
                fixture.schedulerSupplier::get,
                fixture.supplySupplier::get,
                fixture.lifecycleSupplier::get,
                fixture.inventory,
                fixture.lifecycle,
                null,
                fixture.loadPlans));
        assertThrows(IllegalArgumentException.class, () -> new DspAv02AllocationRuntimeController(
                fixture.schedulerSupplier::get,
                fixture.supplySupplier::get,
                fixture.lifecycleSupplier::get,
                fixture.inventory,
                fixture.lifecycle,
                fixture.idAllocator,
                null));
        assertThrows(IllegalArgumentException.class, () -> new DspAv02AllocationRuntimeController(
                fixture.schedulerSupplier::get,
                fixture.supplySupplier::get,
                fixture.lifecycleSupplier::get,
                fixture.inventory,
                fixture.lifecycle,
                fixture.idAllocator,
                fixture.loadPlans,
                null));

        DspAv02AllocationRuntimeSnapshot before = fixture.runtime.snapshot();
        var inventoryBefore = fixture.inventory.snapshot();
        var lifecycleBefore = fixture.lifecycle.snapshot();

        assertThrows(IllegalArgumentException.class,
                () -> fixture.runtime.update(null, 0d));
        assertUnchangedAfterInvalidUpdate(fixture, before, inventoryBefore, lifecycleBefore);
        assertThrows(IllegalArgumentException.class,
                () -> fixture.runtime.update(new SimulationContext(), -1d));
        assertUnchangedAfterInvalidUpdate(fixture, before, inventoryBefore, lifecycleBefore);
        assertThrows(IllegalArgumentException.class,
                () -> fixture.runtime.update(new SimulationContext(), Double.NaN));
        assertUnchangedAfterInvalidUpdate(fixture, before, inventoryBefore, lifecycleBefore);
    }

    private static void assertRejectedAfterFreshCommand(Fixture fixture) {
        var inventoryBefore = fixture.inventory.snapshot();
        var lifecycleBefore = fixture.lifecycle.snapshot();

        DspAv02AllocationRuntimeSnapshot initial = fixture.runtime.snapshot();
        assertTrue(initial.selectedCommand().isPresent());
        fixture.runtime.update(new SimulationContext(), 0d);

        DspAv02AllocationRuntimeSnapshot rejected = fixture.runtime.snapshot();
        assertEquals(1, rejected.sequence());
        assertTrue(rejected.selectedCommand().isPresent());
        assertEquals(1, rejected.selectedCommand().orElseThrow().snapshotSequence());
        assertEquals(1, rejected.freshRevalidationSnapshot().orElseThrow().sequence());
        assertTrue(rejected.freshRevalidationSnapshot().orElseThrow().command().isEmpty());
        assertTrue(rejected.lastAllocatedTote().isEmpty());
        assertEquals(inventoryBefore, fixture.inventory.snapshot());
        assertEquals(lifecycleBefore, fixture.lifecycle.snapshot());
        assertEquals(0, fixture.idAllocator.calls);
        assertNull(fixture.loadPlans.getLoadPlanFor(new PhysicalToteId("av02-000001")));
    }

    private static void assertSingleChangedInputEvaluation(Fixture fixture) {
        DspAv02AllocationRuntimeSnapshot initial = fixture.runtime.snapshot();

        fixture.runtime.update(new SimulationContext(), 0d);

        DspAv02AllocationRuntimeSnapshot changed = fixture.runtime.snapshot();
        assertEquals(initial.sequence() + 1, changed.sequence());
        assertNotSame(initial, changed);
        assertNotSame(initial.snapshot(), changed.snapshot());
        assertTrue(changed.selectedCommand().isEmpty());

        fixture.runtime.update(new SimulationContext(), 0d);

        assertSame(changed, fixture.runtime.snapshot());
        assertSame(changed.snapshot(), fixture.runtime.snapshot().snapshot());
        assertEquals(changed.sequence(), fixture.runtime.snapshot().sequence());
    }

    private static Av02InventorySnapshot replaceInventorySnapshot(
            Av02PhysicalToteInventory inventory) {
        Av02InventorySnapshot current = inventory.snapshot();
        Av02InventorySnapshot replacement = new Av02InventorySnapshot(
                current.capacity(),
                current.waitingTotes(),
                current.departedTotes());
        try {
            var currentSnapshotField = Av02PhysicalToteInventory.class
                    .getDeclaredField("currentSnapshot");
            currentSnapshotField.setAccessible(true);
            currentSnapshotField.set(inventory, replacement);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Unable to replace AV02 inventory snapshot", failure);
        }
        return replacement;
    }

    private static void assertBlocked(
            Fixture fixture,
            Av02AllocationBlockReason expectedReason) {
        DspAv02AllocationRuntimeSnapshot snapshot = fixture.runtime.snapshot();
        assertTrue(snapshot.selectedCommand().isEmpty());
        assertTrue(snapshot.blocked());
        assertTrue(snapshot.diagnostic().contains(expectedReason.name()));
        assertFalse(snapshot.diagnostic().isBlank());
        Av02InventorySnapshot inventoryBefore = fixture.inventory.snapshot();
        PhysicalToteLifecycleSnapshot lifecycleBefore = fixture.lifecycle.snapshot();
        int idCallsBefore = fixture.idAllocator.calls;
        assertEquals(inventoryBefore, fixture.inventory.snapshot());
        assertEquals(lifecycleBefore, fixture.lifecycle.snapshot());
        assertEquals(idCallsBefore, fixture.idAllocator.calls);
        assertNull(fixture.loadPlans.getLoadPlanFor(new PhysicalToteId("av02-000001")));
        assertTrue(fixture.runtime.lastAllocatedTote().isEmpty());
    }

    private static void assertFailureRetainsPublishedSnapshot(boolean throwFailure) {
        List<NotionalToteOrder> orders = List.of(
                emptyOrder("failure-1", 0, DspOrderLineType.FULL_PACK),
                emptyOrder("failure-2", 1, DspOrderLineType.FULL_PACK));
        Fixture fixture = fixture(
                2,
                orders,
                new SequenceSupplier<>(List.of(scheduler(orders, Set.of()))),
                new SequenceSupplier<>(List.of(supply(orders, sheetKeys(orders)))),
                new SequenceSupplier<>(List.of(emptyLifecycle())));
        fixture.runtime.update(new SimulationContext(), 0d);

        DspAv02AllocationRuntimeSnapshot published = fixture.runtime.snapshot();
        var inventoryBeforeFailure = fixture.inventory.snapshot();
        var lifecycleBeforeFailure = fixture.lifecycle.snapshot();
        Av02AllocatedTote allocatedBeforeFailure = fixture.inventory.head().orElseThrow();
        ToteLoadPlan loadPlanBeforeFailure = fixture.loadPlans.getLoadPlanFor(
                allocatedBeforeFailure.physicalToteId());
        int idCallsBeforeFailure = fixture.idAllocator.calls;
        if (throwFailure) {
            fixture.schedulerSupplier.setFailure(new IllegalStateException("scheduler failure"));
        } else {
            fixture.schedulerSupplier.setNull(true);
        }

        assertThrows(IllegalStateException.class,
                () -> fixture.runtime.update(new SimulationContext(), 0d));
        assertSame(published, fixture.runtime.snapshot());
        assertSame(inventoryBeforeFailure, fixture.inventory.snapshot());
        assertSame(lifecycleBeforeFailure, fixture.lifecycle.snapshot());
        assertSame(loadPlanBeforeFailure, fixture.loadPlans.getLoadPlanFor(
                allocatedBeforeFailure.physicalToteId()));
        assertEquals(idCallsBeforeFailure, fixture.idAllocator.calls);
        assertEquals(1, fixture.inventory.occupancy());

        fixture.schedulerSupplier.clearFailure();
        fixture.schedulerSupplier.setNull(false);
        fixture.runtime.update(new SimulationContext(), 0d);
        assertTrue(fixture.runtime.snapshot().sequence() > published.sequence());
        assertEquals(2, fixture.inventory.occupancy());
        assertEquals(2, fixture.idAllocator.calls);
    }

    @Test
    void shouldSkipUnchangedNoCommandUpdatesAndPreservePublishedIdentities() {
        NotionalToteOrder order = emptyOrder("empty-unchanged", 0, DspOrderLineType.FULL_PACK);
        Fixture fixture = fixture(
                1,
                List.of(order),
                new SequenceSupplier<>(List.of(scheduler(List.of(order), Set.of()))),
                new SequenceSupplier<>(List.of(supply(List.of(order), Set.of()))),
                new SequenceSupplier<>(List.of(emptyLifecycle())));
        DspAv02AllocationRuntimeSnapshot initial = fixture.runtime.snapshot();
        int schedulerCalls = fixture.schedulerSupplier.calls;
        int supplyCalls = fixture.supplySupplier.calls;
        int lifecycleCalls = fixture.lifecycleSupplier.calls;

        fixture.runtime.update(new SimulationContext(), 0d);
        fixture.runtime.update(new SimulationContext(), 30d);

        assertSame(initial, fixture.runtime.snapshot());
        assertSame(initial.snapshot(), fixture.runtime.snapshot().snapshot());
        assertEquals(0, fixture.runtime.snapshot().sequence());
        assertTrue(fixture.runtime.snapshot().selectedCommand().isEmpty());
        assertEquals(0, fixture.idAllocator.calls);
        assertEquals(schedulerCalls + 2, fixture.schedulerSupplier.calls);
        assertEquals(supplyCalls + 2, fixture.supplySupplier.calls);
        assertEquals(lifecycleCalls + 2, fixture.lifecycleSupplier.calls);
    }

    @Test
    void shouldReevaluateEachChangedInputReferenceIncludingEqualDistinctValues() {
        NotionalToteOrder order = emptyOrder("empty-input-change", 0, DspOrderLineType.FULL_PACK);
        DspSupplySnapshot unauthorizedSupply = supply(List.of(order), Set.of());
        WarehouseSchedulerSnapshot baseScheduler = scheduler(List.of(order), Set.of());
        PhysicalToteLifecycleSnapshot baseLifecycle = emptyLifecycle();

        WarehouseSchedulerSnapshot equalScheduler = scheduler(List.of(order), Set.of());
        assertEquals(baseScheduler, equalScheduler);
        assertNotSame(baseScheduler, equalScheduler);
        Fixture schedulerFixture = fixture(
                1,
                List.of(order),
                new SequenceSupplier<>(List.of(baseScheduler, equalScheduler)),
                new SequenceSupplier<>(List.of(unauthorizedSupply)),
                new SequenceSupplier<>(List.of(baseLifecycle)));
        assertSingleChangedInputEvaluation(schedulerFixture);

        DspSupplySnapshot equalSupply = supply(List.of(order), Set.of());
        assertEquals(unauthorizedSupply, equalSupply);
        assertNotSame(unauthorizedSupply, equalSupply);
        Fixture supplyFixture = fixture(
                1,
                List.of(order),
                new SequenceSupplier<>(List.of(baseScheduler)),
                new SequenceSupplier<>(List.of(unauthorizedSupply, equalSupply)),
                new SequenceSupplier<>(List.of(baseLifecycle)));
        assertSingleChangedInputEvaluation(supplyFixture);

        PhysicalToteLifecycleSnapshot equalLifecycle = emptyLifecycle();
        assertEquals(baseLifecycle, equalLifecycle);
        assertNotSame(baseLifecycle, equalLifecycle);
        Fixture lifecycleFixture = fixture(
                1,
                List.of(order),
                new SequenceSupplier<>(List.of(baseScheduler)),
                new SequenceSupplier<>(List.of(unauthorizedSupply)),
                new SequenceSupplier<>(List.of(baseLifecycle, equalLifecycle)));
        assertSingleChangedInputEvaluation(lifecycleFixture);

        Fixture inventoryFixture = fixture(
                1,
                List.of(order),
                new SequenceSupplier<>(List.of(baseScheduler)),
                new SequenceSupplier<>(List.of(unauthorizedSupply)),
                new SequenceSupplier<>(List.of(baseLifecycle)));
        Av02InventorySnapshot initialInventory = inventoryFixture.inventory.snapshot();
        Av02InventorySnapshot equalInventory = replaceInventorySnapshot(inventoryFixture.inventory);
        assertEquals(initialInventory, equalInventory);
        assertNotSame(initialInventory, equalInventory);
        assertSingleChangedInputEvaluation(inventoryFixture);
    }

    @Test
    void shouldRetainPublishedSnapshotAndReconsiderAfterAllocationFailure() {
        NotionalToteOrder order = emptyOrder("empty-allocation-failure", 0, DspOrderLineType.FULL_PACK);
        WarehouseSchedulerSnapshot schedulerSnapshot = scheduler(List.of(order), Set.of());
        DspSupplySnapshot supplySnapshot = supply(List.of(order), sheetKeys(List.of(order)));
        PhysicalToteLifecycleSnapshot lifecycleSnapshot = emptyLifecycle();
        ControlledSupplier<WarehouseSchedulerSnapshot> schedulerSupplier =
                new SequenceSupplier<>(List.of(schedulerSnapshot));
        ControlledSupplier<DspSupplySnapshot> supplySupplier =
                new SequenceSupplier<>(List.of(supplySnapshot));
        ControlledSupplier<PhysicalToteLifecycleSnapshot> lifecycleSupplier =
                new SequenceSupplier<>(List.of(lifecycleSnapshot));
        Av02PhysicalToteInventory inventory = new Av02PhysicalToteInventory(
                new Av02AllocationConfig(1));
        PhysicalToteLifecycleLedger lifecycle = new PhysicalToteLifecycleLedger();
        FailingIdAllocator idAllocator = new FailingIdAllocator();
        MapBackedToteLoadPlanRegistry loadPlans = new MapBackedToteLoadPlanRegistry();
        DspAv02AllocationRuntimeController runtime = new DspAv02AllocationRuntimeController(
                schedulerSupplier::get,
                supplySupplier::get,
                lifecycleSupplier::get,
                inventory,
                lifecycle,
                idAllocator,
                loadPlans);
        DspAv02AllocationRuntimeSnapshot published = runtime.snapshot();
        Av02InventorySnapshot inventoryBefore = inventory.snapshot();
        PhysicalToteLifecycleSnapshot lifecycleBefore = lifecycle.snapshot();

        assertThrows(IllegalStateException.class,
                () -> runtime.update(new SimulationContext(), 0d));
        assertSame(published, runtime.snapshot());
        assertSame(inventoryBefore, inventory.snapshot());
        assertSame(lifecycleBefore, lifecycle.snapshot());
        assertEquals(1, idAllocator.calls);
        assertEquals(0, inventory.occupancy());
        assertNull(loadPlans.getLoadPlanFor(new PhysicalToteId("av02-000001")));

        assertThrows(IllegalStateException.class,
                () -> runtime.update(new SimulationContext(), 0d));
        assertSame(published, runtime.snapshot());
        assertEquals(2, idAllocator.calls);
        assertEquals(5, schedulerSupplier.calls);
        assertEquals(5, supplySupplier.calls);
        assertEquals(5, lifecycleSupplier.calls);
    }

    private static void assertUnchangedAfterInvalidUpdate(
            Fixture fixture,
            DspAv02AllocationRuntimeSnapshot before,
            Av02InventorySnapshot inventoryBefore,
            PhysicalToteLifecycleSnapshot lifecycleBefore) {
        assertSame(before, fixture.runtime.snapshot());
        assertSame(inventoryBefore, fixture.inventory.snapshot());
        assertSame(lifecycleBefore, fixture.lifecycle.snapshot());
        assertEquals(0, fixture.idAllocator.calls);
        assertNull(fixture.loadPlans.getLoadPlanFor(new PhysicalToteId("av02-000001")));
    }

    private static Fixture fixture(int capacity, List<NotionalToteOrder> orders) {
        return fixture(
                capacity,
                orders,
                new SequenceSupplier<>(List.of(scheduler(orders, Set.of()))),
                new SequenceSupplier<>(List.of(supply(orders, sheetKeys(orders)))),
                new SequenceSupplier<>(List.of(emptyLifecycle())));
    }

    private static Fixture fixture(
            int capacity,
            List<NotionalToteOrder> orders,
            Supplier<WarehouseSchedulerSnapshot> schedulerSupplier,
            Supplier<DspSupplySnapshot> supplySupplier,
            Supplier<PhysicalToteLifecycleSnapshot> lifecycleSupplier) {
        return fixture(
                capacity,
                orders,
                schedulerSupplier,
                supplySupplier,
                lifecycleSupplier,
                new Av02PhysicalToteInventory(new Av02AllocationConfig(capacity)));
    }

    private static Fixture fixture(
            int capacity,
            List<NotionalToteOrder> orders,
            Supplier<WarehouseSchedulerSnapshot> schedulerSupplier,
            Supplier<DspSupplySnapshot> supplySupplier,
            Supplier<PhysicalToteLifecycleSnapshot> lifecycleSupplier,
            Av02PhysicalToteInventory inventory) {
        return new Fixture(
                capacity,
                orders,
                schedulerSupplier,
                supplySupplier,
                lifecycleSupplier,
                inventory);
    }

    private static NotionalToteOrder emptyOrder(
            String orderId,
            long sequence,
            DspOrderLineType lineType) {
        return new NotionalToteOrder(
                orderId,
                orderId,
                "104",
                1,
                OrderType.EMPTY,
                List.of(new DspOrderItem(
                        "line-" + orderId,
                        "product-" + orderId,
                        1,
                        "pharmacy-1",
                        "patient-" + orderId,
                        "prescription-" + orderId,
                        lineType,
                        orderId,
                        1,
                        1)),
                999,
                sequence);
    }

    private static WarehouseSchedulerSnapshot scheduler(
            List<NotionalToteOrder> orders,
            Set<PreparedLineKey> preparedLineKeys) {
        return new WarehouseSchedulerSnapshot(
                orders.stream()
                        .map(order -> new DspSchedulerOrderState(
                                order,
                                new RouteRequirements(
                                        false, false, false, true, false, StartLocation.AV02),
                                DspOrderStatus.WAITING))
                        .toList(),
                Map.of(),
                preparedLineKeys,
                Optional.empty());
    }

    private static DspSupplySnapshot supply(
            List<NotionalToteOrder> orders,
            Set<OrderSheetKey> authorizedKeys) {
        Map<String, Integer> priorities = new java.util.LinkedHashMap<>();
        orders.forEach(order -> priorities.putIfAbsent(
                order.serviceCentreId().trim(), order.orderPriority()));
        List<ServiceCentreSupplySnapshot> serviceCentres = priorities.entrySet().stream()
                .map(entry -> {
                    Set<OrderSheetKey> centreKeys = authorizedKeys.stream()
                            .filter(key -> orders.stream().anyMatch(order ->
                                    order.orderSheetKey().equals(key)
                                            && order.serviceCentreId().trim()
                                                    .equals(entry.getKey())))
                            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
                    return new ServiceCentreSupplySnapshot(
                            entry.getKey(),
                            entry.getValue(),
                            ServiceCentreAuthorizationState.AUTHORIZED,
                            Optional.of(Duration.ZERO),
                            0,
                            0,
                            0,
                            0,
                            centreKeys,
                            List.of());
                })
                .toList();
        return new DspSupplySnapshot(
                "test",
                0,
                10,
                0,
                Optional.empty(),
                Optional.empty(),
                authorizedKeys,
                serviceCentres,
                0);
    }

    private static Set<OrderSheetKey> sheetKeys(List<NotionalToteOrder> orders) {
        return orders.stream()
                .map(NotionalToteOrder::orderSheetKey)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private static PhysicalToteLifecycleSnapshot emptyLifecycle() {
        return new PhysicalToteLifecycleSnapshot(Map.of(), List.of());
    }

    private static Av02AllocatedTote allocated(
            NotionalToteOrder order,
            String physicalToteId,
            long sourceSequenceNumber) {
        PhysicalToteId id = new PhysicalToteId(physicalToteId);
        return new Av02AllocatedTote(
                new OperationalPhysicalToteIdentity(
                        OperationalPhysicalToteSource.AV02,
                        id,
                        order.orderSheetKey(),
                        OrderType.EMPTY,
                        order.serviceCentreId(),
                        PhysicalToteRole.PRE_P2P,
                        sourceSequenceNumber),
                PhysicalToteRecord.preP2p(id),
                order.items().getFirst().pharmacyId());
    }

    private static final class Fixture {
        private final List<NotionalToteOrder> orders;
        private final ControlledSupplier<WarehouseSchedulerSnapshot> schedulerSupplier;
        private final ControlledSupplier<DspSupplySnapshot> supplySupplier;
        private final ControlledSupplier<PhysicalToteLifecycleSnapshot> lifecycleSupplier;
        private final Av02PhysicalToteInventory inventory;
        private final PhysicalToteLifecycleLedger lifecycle = new PhysicalToteLifecycleLedger();
        private final CountingIdAllocator idAllocator = new CountingIdAllocator();
        private final MapBackedToteLoadPlanRegistry loadPlans =
                new MapBackedToteLoadPlanRegistry();
        private final DspAv02AllocationRuntimeController runtime;

        private Fixture(
                int capacity,
                List<NotionalToteOrder> orders,
                Supplier<WarehouseSchedulerSnapshot> schedulerSupplier,
                Supplier<DspSupplySnapshot> supplySupplier,
                Supplier<PhysicalToteLifecycleSnapshot> lifecycleSupplier,
                Av02PhysicalToteInventory inventory) {
            this.orders = List.copyOf(orders);
            this.schedulerSupplier = requireControlled(schedulerSupplier);
            this.supplySupplier = requireControlled(supplySupplier);
            this.lifecycleSupplier = requireControlled(lifecycleSupplier);
            this.inventory = inventory;
            this.runtime = new DspAv02AllocationRuntimeController(
                    this.schedulerSupplier::get,
                    this.supplySupplier::get,
                    this.lifecycleSupplier::get,
                    inventory,
                    lifecycle,
                    idAllocator,
                    loadPlans);
        }

        @SuppressWarnings("unchecked")
        private static <T> ControlledSupplier<T> requireControlled(Supplier<T> supplier) {
            if (!(supplier instanceof ControlledSupplier<?> controlled)) {
                throw new IllegalArgumentException("test fixture requires controlled suppliers");
            }
            return (ControlledSupplier<T>) controlled;
        }
    }

    private static class ControlledSupplier<T> implements Supplier<T> {
        private List<T> values;
        private RuntimeException failure;
        private boolean returnNull;
        private int calls;

        private ControlledSupplier(List<T> values) {
            this.values = List.copyOf(values);
        }

        @Override
        public T get() {
            calls++;
            if (failure != null) {
                throw failure;
            }
            if (returnNull) {
                return null;
            }
            return values.get(Math.min(calls - 1, values.size() - 1));
        }

        private void setFailure(RuntimeException failure) {
            this.failure = failure;
        }

        private void clearFailure() {
            this.failure = null;
        }

        private void setNull(boolean returnNull) {
            this.returnNull = returnNull;
        }
    }

    private static final class SequenceSupplier<T> extends ControlledSupplier<T> {
        private SequenceSupplier(List<T> values) {
            super(values);
        }
    }

    private static final class CountingIdAllocator implements PhysicalToteIdAllocator {
        private int calls;

        @Override
        public PhysicalToteId nextPhysicalToteId() {
            calls++;
            return new PhysicalToteId("av02-" + String.format("%06d", calls));
        }
    }

    private static final class FailingIdAllocator implements PhysicalToteIdAllocator {
        private int calls;

        @Override
        public PhysicalToteId nextPhysicalToteId() {
            calls++;
            throw new IllegalStateException("test allocation failure");
        }
    }
}
