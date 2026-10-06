package online.davisfamily.warehouse.sim.dsp.outbound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.warehouse.sim.dsp.bagging.BagKey;
import online.davisfamily.warehouse.sim.dsp.bagging.BagPlanningResult;
import online.davisfamily.warehouse.sim.dsp.bagging.BagPlanningResultTestFixtures;
import online.davisfamily.warehouse.sim.dsp.bagging.PackSourceProvenance;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedBag;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedPackTrace;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleLedger;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
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
                fixture.ledger().activeAssignmentFor(sheet("order-1", 101)).orElseThrow().activatedAt());
        assertTrue(receivedBags(fixture).isEmpty());
        assertEquals(1, ((CountingStoredBagReceiver) fixture.receiver()).viewCalls());
    }

    @Test
    void shouldAllocatePartialRuntimeBagUsingRegisteredMissingPackIds() {
        PlannedBag plannedBag = plannedBag("rx-partial", 1, sheet("order-1", 1), "p1", "p2", "p3");
        int[] providerCalls = {0};
        Fixture fixture = fixture(correlationId -> {
            assertEquals(plannedBag.bagKey().correlationId(), correlationId);
            providerCalls[0]++;
            return Set.of("p2");
        }, plannedBag);
        OutboundAllocationSnapshot beforeAllocation = fixture.allocator().snapshot();
        var lifecycleBefore = fixture.ledger().snapshot();
        receive(fixture.receiver(), runtimeBag(plannedBag.bagKey().correlationId(), "p1", "p3"));

        fixture.controller().update(contextAt(1.25d), 0.1d);

        OutboundAllocationSnapshot afterAllocation = fixture.allocator().snapshot();
        AllocatedOutboundBag allocation = afterAllocation.allocatedBags().getFirst();
        assertNotSame(beforeAllocation, afterAllocation);
        assertNotSame(lifecycleBefore, fixture.ledger().snapshot());
        assertSame(plannedBag, allocation.plannedBag());
        assertEquals(List.of("p1", "p3"), allocation.actualPhysicalPackIds());
        assertEquals(List.of("p2"), allocation.missingPhysicalPackIds());
        assertTrue(afterAllocation.openToteFor(LINE).orElseThrow().requiresExceptionProcessing());
        assertEquals(1, providerCalls[0]);
        assertTrue(receivedBags(fixture).isEmpty());
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
        OutboundAllocationSnapshot unknownBefore = unknownFixture.allocator().snapshot();
        var unknownLifecycleBefore = unknownFixture.ledger().snapshot();

        assertThrows(
                IllegalStateException.class,
                () -> unknownFixture.controller().update(contextAt(1d), 0.1d));
        assertEquals(List.of(unknownBag), unknownFixture.receiver().getReceivedBags());
        assertSame(unknownBefore, unknownFixture.allocator().snapshot());
        assertSame(unknownLifecycleBefore, unknownFixture.ledger().snapshot());

        Fixture mismatchFixture = fixture(plannedBag);
        Bag mismatchedBag = runtimeBag(plannedBag.bagKey().correlationId(), "wrong-pack");
        receive(mismatchFixture.receiver(), mismatchedBag);
        OutboundAllocationSnapshot mismatchBefore = mismatchFixture.allocator().snapshot();
        var mismatchLifecycleBefore = mismatchFixture.ledger().snapshot();

        assertThrows(
                IllegalStateException.class,
                () -> mismatchFixture.controller().update(contextAt(1d), 0.1d));
        assertEquals(List.of(mismatchedBag), mismatchFixture.receiver().getReceivedBags());
        assertTrue(mismatchFixture.allocator().snapshot().allocatedBags().isEmpty());
        assertSame(mismatchBefore, mismatchFixture.allocator().snapshot());
        assertSame(mismatchLifecycleBefore, mismatchFixture.ledger().snapshot());
    }

    @Test
    void shouldRejectInvalidRuntimeAndProviderPartitionsWithoutMutation() {
        PlannedBag planned = plannedBag("rx-invalid", 1, sheet("order-1", 1), "p1", "p2", "p3");
        String correlationId = planned.bagKey().correlationId();

        assertRejected(planned, runtimeBag(correlationId, "p3", "p1"), ignored -> Set.of("p2"));
        assertRejected(planned, runtimeBag(correlationId, "p1", "p1", "p3"), ignored -> Set.of());
        assertRejected(planned, runtimeBag(correlationId, "p1", "foreign", "p3"), ignored -> Set.of());
        assertRejected(planned, runtimeBag(correlationId, "p1", "p3"), ignored -> Set.of());
        assertRejected(planned, runtimeBag(correlationId, "p1", "p2", "p3"), ignored -> Set.of("p2"));
        assertRejected(planned, runtimeBag(correlationId, "p1", "p2", "p3"), ignored -> Set.of("foreign"));
        assertRejected(planned, runtimeBag(correlationId, "p1", "p2", "p3"), ignored -> null);
    }

    @Test
    void shouldQueryProviderOncePerBagAndNeverOnIdleUpdates() {
        PlannedBag first = plannedBag("rx-1", 1, sheet("order-1", 1), "pack-1");
        PlannedBag second = plannedBag("rx-2", 1, sheet("order-2", 1), "pack-2");
        int[] providerCalls = {0};
        Fixture fixture = fixture(ignored -> {
            providerCalls[0]++;
            return Set.of();
        }, first, second);
        OutboundAllocationSnapshot initial = fixture.allocator().snapshot();
        var initialLifecycle = fixture.ledger().snapshot();

        fixture.controller().update(contextAt(0d), 0.1d);
        fixture.controller().update(contextAt(0d), 0.1d);
        assertEquals(0, providerCalls[0]);
        assertSame(initial, fixture.allocator().snapshot());
        assertSame(initialLifecycle, fixture.ledger().snapshot());

        receive(fixture.receiver(), runtimeBag(first.bagKey().correlationId(), "pack-1"));
        receive(fixture.receiver(), runtimeBag(second.bagKey().correlationId(), "pack-2"));
        fixture.controller().update(contextAt(1d), 0.1d);
        fixture.controller().update(contextAt(1d), 0.1d);

        assertEquals(2, providerCalls[0]);
        assertTrue(receivedBags(fixture).isEmpty());
        assertEquals(2, fixture.allocator().snapshot().allocatedBags().size());
        assertEquals(1, ((CountingStoredBagReceiver) fixture.receiver()).viewCalls());
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
        return fixture(ignored -> Set.of(), plannedBags);
    }

    private static Fixture fixture(
            Function<String, Set<String>> missingPackIdsProvider,
            PlannedBag... plannedBags) {
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        OutboundToteAllocator allocator = new OutboundToteAllocator(
                ledger,
                new DeterministicOutboundToteIdSource(),
                new OutputSheetAllocator(Arrays.stream(plannedBags)
                        .flatMap(bag -> bag.owningOrderSheetKeys().stream())
                        .toList()),
                new OutboundToteConfig(10));
        StoredBagReceiver receiver = new CountingStoredBagReceiver("completed-bags");
        List<PlannedPackTrace> traces = Arrays.stream(plannedBags)
                .flatMap(bag -> bag.physicalPackIds().stream().map(packId ->
                        new PlannedPackTrace(
                                packId,
                                new PackSourceProvenance(
                                        bag.owningOrderSheetKeys().getFirst(),
                                        "line-" + packId,
                                        "product-" + packId,
                                        bag.serviceCentreId(),
                                        bag.pharmacyId(),
                                        bag.patientId(),
                                        bag.prescriptionId()),
                                new PhysicalToteId("input-" + packId),
                                bag.owningOrderSheetKeys().getFirst(),
                                bag.bagKey())))
                .toList();
        BagPlanningResult planningResult = BagPlanningResultTestFixtures.complete(
                List.of(plannedBags), List.of(), traces);
        return new Fixture(
                ledger,
                receiver,
                allocator,
                new OutboundToteAllocationController(
                        LINE, receiver, planningResult, allocator, missingPackIdsProvider));
    }

    private static void assertRejected(
            PlannedBag plannedBag,
            Bag runtimeBag,
            Function<String, Set<String>> missingPackIdsProvider) {
        Fixture fixture = fixture(missingPackIdsProvider, plannedBag);
        OutboundAllocationSnapshot outboundBefore = fixture.allocator().snapshot();
        var lifecycleBefore = fixture.ledger().snapshot();
        receive(fixture.receiver(), runtimeBag);

        assertThrows(IllegalStateException.class,
                () -> fixture.controller().update(contextAt(1d), 0.1d));

        assertEquals(List.of(runtimeBag), receivedBags(fixture));
        assertSame(outboundBefore, fixture.allocator().snapshot());
        assertSame(lifecycleBefore, fixture.ledger().snapshot());
        assertEquals(1, ((CountingStoredBagReceiver) fixture.receiver()).viewCalls());
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

    private static List<Bag> receivedBags(Fixture fixture) {
        return ((CountingStoredBagReceiver) fixture.receiver()).receivedBagsWithoutCounting();
    }

    private static final class CountingStoredBagReceiver extends StoredBagReceiver {
        private int viewCalls;

        private CountingStoredBagReceiver(String id) {
            super(id);
        }

        @Override
        public List<Bag> getReceivedBags() {
            viewCalls++;
            return super.getReceivedBags();
        }

        private int viewCalls() {
            return viewCalls;
        }

        private List<Bag> receivedBagsWithoutCounting() {
            return super.getReceivedBags();
        }
    }
}
