package online.davisfamily.warehouse.sim.dsp.av02;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.warehouse.sim.dsp.adapting.MapBackedToteLoadPlanRegistry;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteAssignmentStage;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteIdAllocator;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleLedger;
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

class Av02AllocationControllerTest {

    @Test
    void shouldAllocateOneToteAndEmptyLoadPlanOnOneSimulationUpdate() {
        Fixture fixture = fixture(1, new DeterministicAv02PhysicalToteIdAllocator(), fullPackOrder("empty-1", 0));
        fixture.submitCurrentCommand();
        SimulationContext context = new SimulationContext();
        context.setSimulationTimeSeconds(2.5d);

        fixture.controller.update(context, 0.016d);

        Av02AllocatedTote allocated = fixture.inventory.head().orElseThrow();
        assertEquals("av02-000001", allocated.physicalToteId().value());
        assertEquals(PhysicalToteRole.PRE_P2P, allocated.physicalTote().role());
        assertEquals(1, fixture.inventory.occupancy());
        assertEquals(allocated, fixture.controller.lastAllocatedTote().orElseThrow());
        assertEquals(
                Duration.ofMillis(2500),
                fixture.ledger.activeAssignmentFor(allocated.orderSheetKey()).orElseThrow().activatedAt());
        ToteLoadPlan loadPlan = fixture.loadPlans.getLoadPlanFor(allocated.physicalToteId());
        assertEquals(allocated.physicalToteId(), loadPlan.physicalToteId());
        assertTrue(loadPlan.getPackPlans().isEmpty());
    }

    @Test
    void shouldApplyAtMostOneAllocationPerUpdate() {
        Fixture fixture = fixture(
                2,
                new DeterministicAv02PhysicalToteIdAllocator(),
                fullPackOrder("empty-1", 0),
                fullPackOrder("empty-2", 1));
        fixture.submitCurrentCommand();

        fixture.controller.update(new SimulationContext(), 0.016d);

        assertEquals(1, fixture.inventory.occupancy());
        assertEquals(List.of("empty-1"), fixture.inventory.snapshot().waitingTotes().stream()
                .map(tote -> tote.orderSheetKey().orderId()).toList());

        fixture.sequence.incrementAndGet();
        fixture.submitCurrentCommand();
        fixture.controller.update(new SimulationContext(), 0.016d);

        assertEquals(2, fixture.inventory.occupancy());
        assertEquals(List.of("av02-000001", "av02-000002"),
                fixture.inventory.snapshot().waitingTotes().stream()
                        .map(tote -> tote.physicalToteId().value()).toList());
    }

    @Test
    void shouldIgnoreStaleSequenceOrFreshAuthorizationChangeWithoutAllocatingId() {
        CountingIdAllocator staleAllocator = new CountingIdAllocator();
        Fixture stale = fixture(1, staleAllocator, fullPackOrder("empty-stale", 0));
        stale.submitCurrentCommand();
        stale.sequence.incrementAndGet();

        stale.controller.update(new SimulationContext(), 0.016d);

        assertEquals(0, staleAllocator.calls);
        assertTrue(stale.inventory.snapshot().waitingTotes().isEmpty());
        assertTrue(stale.ledger.snapshot().totes().isEmpty());

        CountingIdAllocator authorizationAllocator = new CountingIdAllocator();
        Fixture authorization = fixture(
                1, authorizationAllocator, fullPackOrder("empty-authorization", 0));
        authorization.submitCurrentCommand();
        authorization.supply.set(supply(authorization.states, Set.of()));

        authorization.controller.update(new SimulationContext(), 0.016d);

        assertEquals(0, authorizationAllocator.calls);
        assertTrue(authorization.inventory.snapshot().waitingTotes().isEmpty());
    }

    @Test
    void shouldRevalidateDependencyCapacityAndAllocationHistory() {
        NotionalToteOrder adaptedOrder = adaptedOrder("empty-adapted", 0);
        PreparedLineKey preparedLine = PreparedLineKey.forDispatchLine(
                adaptedOrder, adaptedOrder.items().getFirst());
        CountingIdAllocator dependencyAllocator = new CountingIdAllocator();
        Fixture dependency = fixture(1, dependencyAllocator, adaptedOrder);
        dependency.scheduler.set(scheduler(dependency.states, Set.of(preparedLine)));
        dependency.submitCurrentCommand();
        dependency.scheduler.set(scheduler(dependency.states, Set.of()));

        dependency.controller.update(new SimulationContext(), 0.016d);

        assertEquals(0, dependencyAllocator.calls);
        assertTrue(dependency.inventory.snapshot().waitingTotes().isEmpty());

        CountingIdAllocator capacityAllocator = new CountingIdAllocator();
        Fixture capacity = fixture(1, capacityAllocator, fullPackOrder("empty-capacity", 0));
        capacity.submitCurrentCommand();
        capacity.inventory.store(allocated(fullPackOrder("blocker", 9), "av02-blocker", 9));

        capacity.controller.update(new SimulationContext(), 0.016d);

        assertEquals(0, capacityAllocator.calls);
        assertEquals(1, capacity.inventory.occupancy());

        CountingIdAllocator historyAllocator = new CountingIdAllocator();
        Fixture history = fixture(1, historyAllocator, fullPackOrder("empty-history", 0));
        history.submitCurrentCommand();
        Av02AllocatedTote historical = allocated(
                history.states.getFirst().order(), "av02-historical", 0);
        history.inventory.store(historical);
        history.inventory.recordDeparture(historical.physicalToteId());

        history.controller.update(new SimulationContext(), 0.016d);

        assertEquals(0, historyAllocator.calls);
        assertEquals(List.of(historical), history.inventory.snapshot().departedTotes());
    }

    @Test
    void shouldRejectActiveAssignmentOrReusedPhysicalIdBeforeMutation() {
        CountingIdAllocator activeAllocator = new CountingIdAllocator();
        Fixture active = fixture(1, activeAllocator, fullPackOrder("empty-active", 0));
        active.submitCurrentCommand();
        PhysicalToteId activeId = new PhysicalToteId("active-existing");
        active.ledger.register(PhysicalToteRecord.preP2p(activeId));
        active.ledger.assign(
                active.states.getFirst().order().orderSheetKey(),
                activeId,
                PhysicalToteAssignmentStage.PRE_P2P,
                Duration.ZERO);

        active.controller.update(new SimulationContext(), 0.016d);

        assertEquals(0, activeAllocator.calls);
        assertTrue(active.inventory.snapshot().waitingTotes().isEmpty());

        PhysicalToteId duplicateId = new PhysicalToteId("av02-duplicate");
        Fixture duplicate = fixture(1, () -> duplicateId, fullPackOrder("empty-duplicate", 0));
        duplicate.submitCurrentCommand();
        duplicate.ledger.register(PhysicalToteRecord.preP2p(duplicateId));
        int toteCountBefore = duplicate.ledger.snapshot().totes().size();

        assertThrows(IllegalStateException.class,
                () -> duplicate.controller.update(new SimulationContext(), 0.016d));
        assertEquals(toteCountBefore, duplicate.ledger.snapshot().totes().size());
        assertTrue(duplicate.inventory.snapshot().waitingTotes().isEmpty());
        assertTrue(duplicate.loadPlans.getLoadPlanFor(duplicateId) == null);
    }

    @Test
    void shouldRejectExistingLoadPlanBeforeLifecycleMutation() {
        PhysicalToteId duplicateId = new PhysicalToteId("av02-load-plan");
        Fixture fixture = fixture(1, () -> duplicateId, fullPackOrder("empty-load", 0));
        fixture.submitCurrentCommand();
        fixture.loadPlans.putLoadPlan(new ToteLoadPlan(duplicateId, List.of()));

        assertThrows(IllegalStateException.class,
                () -> fixture.controller.update(new SimulationContext(), 0.016d));

        assertTrue(fixture.ledger.snapshot().totes().isEmpty());
        assertTrue(fixture.inventory.snapshot().waitingTotes().isEmpty());
        assertEquals(duplicateId, fixture.loadPlans.getLoadPlanFor(duplicateId).physicalToteId());
    }

    @Test
    void shouldResetDeterministicIdentityByReconstruction() {
        Fixture first = fixture(1, new DeterministicAv02PhysicalToteIdAllocator(), fullPackOrder("empty-1", 0));
        first.submitCurrentCommand();
        first.controller.update(new SimulationContext(), 0.016d);

        Fixture reconstructed = fixture(
                1, new DeterministicAv02PhysicalToteIdAllocator(), fullPackOrder("empty-1", 0));
        reconstructed.submitCurrentCommand();
        reconstructed.controller.update(new SimulationContext(), 0.016d);

        assertEquals("av02-000001", first.inventory.head().orElseThrow().physicalToteId().value());
        assertEquals(
                first.inventory.head().orElseThrow().physicalToteId(),
                reconstructed.inventory.head().orElseThrow().physicalToteId());
    }

    private static Fixture fixture(
            int capacity,
            PhysicalToteIdAllocator idAllocator,
            NotionalToteOrder... orders) {
        return new Fixture(capacity, idAllocator, List.of(orders));
    }

    private static NotionalToteOrder fullPackOrder(String orderId, long sequence) {
        return order(orderId, sequence, DspOrderLineType.FULL_PACK);
    }

    private static NotionalToteOrder adaptedOrder(String orderId, long sequence) {
        return order(orderId, sequence, DspOrderLineType.ADAPTED);
    }

    private static NotionalToteOrder order(
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
                PhysicalToteRecord.preP2p(id));
    }

    private static WarehouseSchedulerSnapshot scheduler(
            List<DspSchedulerOrderState> states,
            Set<PreparedLineKey> preparedLines) {
        return new WarehouseSchedulerSnapshot(states, Map.of(), preparedLines, Optional.empty());
    }

    private static DspSupplySnapshot supply(
            List<DspSchedulerOrderState> states,
            Set<OrderSheetKey> authorizedKeys) {
        ServiceCentreSupplySnapshot serviceCentre = new ServiceCentreSupplySnapshot(
                "104",
                999,
                ServiceCentreAuthorizationState.AUTHORIZED,
                Optional.of(Duration.ZERO),
                0,
                0,
                0,
                0,
                authorizedKeys,
                List.of());
        return new DspSupplySnapshot(
                "test",
                0,
                10,
                0,
                Optional.empty(),
                Optional.empty(),
                authorizedKeys,
                List.of(serviceCentre),
                0);
    }

    private static final class Fixture {
        private final List<DspSchedulerOrderState> states;
        private final AtomicReference<WarehouseSchedulerSnapshot> scheduler;
        private final AtomicReference<DspSupplySnapshot> supply;
        private final Av02PhysicalToteInventory inventory;
        private final PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        private final MapBackedToteLoadPlanRegistry loadPlans = new MapBackedToteLoadPlanRegistry();
        private final AtomicLong sequence = new AtomicLong(7);
        private final AtomicReference<Optional<AllocateEmptyToteAtAv02Command>> command =
                new AtomicReference<>(Optional.empty());
        private final Av02AllocationSnapshotFactory snapshotFactory =
                new Av02AllocationSnapshotFactory();
        private final Av02AllocationController controller;

        private Fixture(
                int capacity,
                PhysicalToteIdAllocator idAllocator,
                List<NotionalToteOrder> orders) {
            this.states = orders.stream()
                    .map(order -> new DspSchedulerOrderState(
                            order,
                            new RouteRequirements(
                                    false, false, false, true, false, StartLocation.AV02),
                            DspOrderStatus.WAITING))
                    .toList();
            this.scheduler = new AtomicReference<>(Av02AllocationControllerTest.scheduler(states, Set.of()));
            Set<OrderSheetKey> authorizedKeys = states.stream()
                    .map(state -> state.order().orderSheetKey())
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            this.supply = new AtomicReference<>(Av02AllocationControllerTest.supply(states, authorizedKeys));
            this.inventory = new Av02PhysicalToteInventory(new Av02AllocationConfig(capacity));
            this.controller = new Av02AllocationController(
                    () -> command.getAndSet(Optional.empty()),
                    this::freshSnapshot,
                    inventory,
                    ledger,
                    idAllocator,
                    loadPlans);
        }

        private Av02AllocationSnapshot freshSnapshot() {
            return snapshotFactory.create(
                    sequence.get(),
                    scheduler.get(),
                    supply.get(),
                    inventory.snapshot(),
                    ledger.snapshot());
        }

        private void submitCurrentCommand() {
            command.set(freshSnapshot().command());
        }
    }

    private static final class CountingIdAllocator implements PhysicalToteIdAllocator {
        private int calls;

        @Override
        public PhysicalToteId nextPhysicalToteId() {
            calls++;
            return new PhysicalToteId("counted-" + calls);
        }
    }
}
