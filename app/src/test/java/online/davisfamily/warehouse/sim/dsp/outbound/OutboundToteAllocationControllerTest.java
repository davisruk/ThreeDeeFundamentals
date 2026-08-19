package online.davisfamily.warehouse.sim.dsp.outbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.warehouse.sim.dsp.bagging.BagKey;
import online.davisfamily.warehouse.sim.dsp.bagging.BagPlanningResult;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedBag;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleLedger;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.totebag.bag.Bag;
import online.davisfamily.warehouse.sim.totebag.handoff.BagReservation;
import online.davisfamily.warehouse.sim.totebag.handoff.StoredBagReceiver;
import online.davisfamily.warehouse.sim.totebag.pack.PackDimensions;
import online.davisfamily.warehouse.sim.totebag.plan.BagSpec;
import online.davisfamily.warehouse.sim.totebag.plan.PackPlan;

class OutboundToteAllocationControllerTest {
    private static final P2pLineId LINE = new P2pLineId("p2p-1");

    @Test
    void shouldAllocateReceivedRuntimeBagUsingPlannedBagCorrelation() {
        PlannedBag plannedBag = plannedBag("rx-1", 1, sheet("order-1", 1), "pack-1");
        Fixture fixture = fixture(plannedBag);
        receive(fixture.receiver(), runtimeBag(plannedBag.bagKey().correlationId(), "pack-1"));

        fixture.controller().update(contextAt(1.25d), 0.1d);

        AllocatedOutboundBag allocation = fixture.allocator().snapshot().allocatedBags().getFirst();
        assertEquals(plannedBag.bagKey(), allocation.bagKey());
        assertEquals("outbound-p2p-1-1", allocation.outboundPhysicalToteId().value());
        assertEquals(Duration.ofMillis(1_250),
                fixture.ledger().activeAssignmentFor(sheet("order-1", 1)).orElseThrow().activatedAt());
        assertTrue(fixture.receiver().getReceivedBags().isEmpty());
    }

    @Test
    void shouldPreserveReceiverOrderAcrossSeveralCompletedBags() {
        PlannedBag first = plannedBag("rx-1", 1, sheet("order-1", 1), "pack-1");
        PlannedBag second = plannedBag("rx-2", 1, sheet("order-2", 1), "pack-2");
        Fixture fixture = fixture(first, second);
        receive(fixture.receiver(), runtimeBag(first.bagKey().correlationId(), "pack-1"));
        receive(fixture.receiver(), runtimeBag(second.bagKey().correlationId(), "pack-2"));

        fixture.controller().update(contextAt(2d), 0.1d);

        assertEquals(
                List.of(first.bagKey(), second.bagKey()),
                fixture.allocator().snapshot().allocatedBags().stream()
                        .map(AllocatedOutboundBag::bagKey)
                        .toList());
        assertTrue(fixture.receiver().getReceivedBags().isEmpty());
    }

    @Test
    void shouldRemoveBagOnlyAfterSuccessfulAllocation() {
        PlannedBag plannedBag = plannedBag("rx-1", 1, sheet("order-1", 1), "pack-1");
        Fixture fixture = fixture(plannedBag);
        fixture.allocator().allocate(LINE, plannedBag, Duration.ofSeconds(1));
        Bag runtimeBag = runtimeBag(plannedBag.bagKey().correlationId(), "pack-1");
        receive(fixture.receiver(), runtimeBag);

        assertThrows(
                IllegalStateException.class,
                () -> fixture.controller().update(contextAt(2d), 0.1d));

        assertEquals(List.of(runtimeBag), fixture.receiver().getReceivedBags());
        assertEquals(1, fixture.allocator().snapshot().allocatedBags().size());
    }

    @Test
    void shouldRejectUnknownCorrelationOrPackMismatch() {
        PlannedBag plannedBag = plannedBag("rx-1", 1, sheet("order-1", 1), "pack-1");
        Fixture unknownFixture = fixture(plannedBag);
        Bag unknownBag = runtimeBag("unknown/bag-1", "pack-1");
        receive(unknownFixture.receiver(), unknownBag);

        assertThrows(
                IllegalStateException.class,
                () -> unknownFixture.controller().update(contextAt(1d), 0.1d));
        assertEquals(List.of(unknownBag), unknownFixture.receiver().getReceivedBags());

        Fixture mismatchFixture = fixture(plannedBag);
        Bag mismatchedBag = runtimeBag(plannedBag.bagKey().correlationId(), "wrong-pack");
        receive(mismatchFixture.receiver(), mismatchedBag);

        assertThrows(
                IllegalStateException.class,
                () -> mismatchFixture.controller().update(contextAt(1d), 0.1d));
        assertEquals(List.of(mismatchedBag), mismatchFixture.receiver().getReceivedBags());
        assertTrue(mismatchFixture.allocator().snapshot().allocatedBags().isEmpty());
    }

    @Test
    void shouldApplyEachReceivedBagExactlyOnce() {
        PlannedBag plannedBag = plannedBag("rx-1", 1, sheet("order-1", 1), "pack-1");
        Fixture fixture = fixture(plannedBag);
        receive(fixture.receiver(), runtimeBag(plannedBag.bagKey().correlationId(), "pack-1"));
        SimulationContext context = contextAt(1d);

        fixture.controller().update(context, 0.1d);
        fixture.controller().update(context, 0.1d);

        assertEquals(1, fixture.allocator().snapshot().allocatedBags().size());
        assertTrue(fixture.receiver().getReceivedBags().isEmpty());
    }

    private static Fixture fixture(PlannedBag... plannedBags) {
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        OutboundToteAllocator allocator = new OutboundToteAllocator(
                ledger,
                new DeterministicOutboundToteIdSource(),
                new OutputSheetAllocator(Arrays.stream(plannedBags)
                        .flatMap(bag -> bag.owningOrderSheetKeys().stream())
                        .toList()),
                new OutboundToteConfig(10));
        StoredBagReceiver receiver = new StoredBagReceiver("completed-bags");
        BagPlanningResult planningResult = new BagPlanningResult(
                List.of(plannedBags), List.of(), List.of());
        return new Fixture(
                ledger,
                receiver,
                allocator,
                new OutboundToteAllocationController(LINE, receiver, planningResult, allocator));
    }

    private static PlannedBag plannedBag(
            String prescriptionId,
            int ordinal,
            OrderSheetKey owningSheet,
            String... packIds) {
        return new PlannedBag(
                new BagKey(prescriptionId, ordinal),
                "SC-1",
                "pharmacy-1",
                "patient-1",
                prescriptionId,
                List.of(packIds),
                List.of(owningSheet));
    }

    private static Bag runtimeBag(String correlationId, String... packIds) {
        return new Bag(
                "runtime-" + correlationId,
                correlationId,
                Arrays.stream(packIds)
                        .map(packId -> new PackPlan(
                                packId,
                                correlationId,
                                new PackDimensions(0.20f, 0.10f, 0.08f)))
                        .toList(),
                new BagSpec(0.34f, 0.28f, 0.22f));
    }

    private static void receive(StoredBagReceiver receiver, Bag bag) {
        BagReservation reservation = receiver.reserveIncomingBag(bag);
        receiver.beginReceiving(reservation);
        receiver.completeReceiving(reservation);
    }

    private static SimulationContext contextAt(double seconds) {
        SimulationContext context = new SimulationContext();
        context.setSimulationTimeSeconds(seconds);
        return context;
    }

    private static OrderSheetKey sheet(String orderId, int sheetNumber) {
        return new OrderSheetKey(orderId, sheetNumber);
    }

    private record Fixture(
            PhysicalToteLifecycleLedger ledger,
            StoredBagReceiver receiver,
            OutboundToteAllocator allocator,
            OutboundToteAllocationController controller) {
    }
}
