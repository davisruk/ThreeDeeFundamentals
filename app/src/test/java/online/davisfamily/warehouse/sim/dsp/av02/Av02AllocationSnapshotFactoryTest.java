package online.davisfamily.warehouse.sim.dsp.av02;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteAssignmentStage;
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
import online.davisfamily.warehouse.sim.dsp.scheduler.DspDependencyEvaluator;
import online.davisfamily.warehouse.sim.dsp.scheduler.DependencyBlock;
import online.davisfamily.warehouse.sim.dsp.scheduler.policy.WholeServiceCentreReleaseSnapshot;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.scheduler.DspSchedulerOrderState;
import online.davisfamily.warehouse.sim.dsp.scheduler.PreparedLineKey;
import online.davisfamily.warehouse.sim.dsp.scheduler.WarehouseSchedulerSnapshot;
import online.davisfamily.warehouse.sim.dsp.supply.DspSupplySnapshot;
import online.davisfamily.warehouse.sim.dsp.supply.ServiceCentreAuthorizationState;
import online.davisfamily.warehouse.sim.dsp.supply.ServiceCentreSupplySnapshot;

class Av02AllocationSnapshotFactoryTest {
    private final Av02AllocationSnapshotFactory factory = new Av02AllocationSnapshotFactory();

    @Test
    void shouldSelectAuthorizedReadyEmptyByPriorityPharmacyGroupAndSourceOrder() {
        DspSchedulerOrderState laterSamePharmacy = state(order(
                "empty-2", "104", 999, 7, "pharmacy-b", DspOrderLineType.FULL_PACK));
        DspSchedulerOrderState firstPharmacy = state(order(
                "empty-1", "104", 999, 4, "pharmacy-a", DspOrderLineType.FULL_PACK));
        DspSchedulerOrderState earlierSecondPharmacy = state(order(
                "empty-3", "104", 999, 3, "pharmacy-b", DspOrderLineType.FULL_PACK));
        DspSchedulerOrderState lowerPriority = state(order(
                "empty-4", "108", 998, 1, "pharmacy-c", DspOrderLineType.FULL_PACK));
        List<DspSchedulerOrderState> states = List.of(
                laterSamePharmacy, firstPharmacy, earlierSecondPharmacy, lowerPriority);

        Av02AllocationSnapshot snapshot = factory.create(
                12,
                scheduler(states, Set.of()),
                supply(states, sheetKeys(states)),
                inventory(2),
                emptyLifecycle());

        assertEquals(
                List.of("empty-3", "empty-2", "empty-1", "empty-4"),
                snapshot.candidates().stream().map(candidate -> candidate.order().orderId()).toList());
        assertEquals(4, snapshot.eligibleCandidates().size());
        AllocateEmptyToteAtAv02Command command = snapshot.command().orElseThrow();
        assertEquals(12, command.snapshotSequence());
        assertEquals(earlierSecondPharmacy.order().orderSheetKey(), command.orderSheetKey());
        assertEquals("104", command.serviceCentreId());
    }

    @Test
    void shouldReportAuthorizationDependencyAndCapacityBlocksDeterministically() {
        DspSchedulerOrderState unauthorized = state(order(
                "empty-unauthorized", "104", 999, 0, "pharmacy-a", DspOrderLineType.FULL_PACK));
        DspSchedulerOrderState dependencyBlocked = state(order(
                "empty-dependency", "104", 999, 1, "pharmacy-b", DspOrderLineType.ADAPTED));
        List<DspSchedulerOrderState> states = List.of(unauthorized, dependencyBlocked);

        Av02AllocationSnapshot snapshot = factory.create(
                2,
                scheduler(states, Set.of()),
                supply(states, Set.of(dependencyBlocked.order().orderSheetKey())),
                fullInventory(),
                emptyLifecycle());

        assertEquals(
                List.of(
                        Av02AllocationBlockReason.NO_AV02_CAPACITY,
                        Av02AllocationBlockReason.EMPTY_NOT_AUTHORIZED),
                candidate(snapshot, "empty-unauthorized").blockReasons());
        assertEquals(
                List.of(
                        Av02AllocationBlockReason.NO_AV02_CAPACITY,
                        Av02AllocationBlockReason.DEPENDENCY_NOT_READY),
                candidate(snapshot, "empty-dependency").blockReasons());
        assertTrue(snapshot.command().isEmpty());
        assertTrue(snapshot.eligibleCandidates().isEmpty());
    }

    @Test
    void shouldBecomeEligibleWhenAdaptedPreparedLineExists() {
        DspSchedulerOrderState adapted = state(order(
                "empty-adapted", "104", 999, 0, "pharmacy-a", DspOrderLineType.ADAPTED));
        Set<OrderSheetKey> authorized = Set.of(adapted.order().orderSheetKey());

        Av02AllocationSnapshot blocked = factory.create(
                0,
                scheduler(List.of(adapted), Set.of()),
                supply(List.of(adapted), authorized),
                inventory(1),
                emptyLifecycle());
        PreparedLineKey preparedLineKey = PreparedLineKey.forDispatchLine(
                adapted.order(), adapted.order().items().getFirst());
        Av02AllocationSnapshot ready = factory.create(
                1,
                scheduler(List.of(adapted), Set.of(preparedLineKey)),
                supply(List.of(adapted), authorized),
                inventory(1),
                emptyLifecycle());

        assertEquals(
                List.of(Av02AllocationBlockReason.DEPENDENCY_NOT_READY),
                blocked.candidates().getFirst().blockReasons());
        assertTrue(blocked.command().isEmpty());
        assertTrue(ready.candidates().getFirst().eligible());
        assertEquals(adapted.order().orderSheetKey(), ready.command().orElseThrow().orderSheetKey());
    }

    @Test
    void shouldExcludeWaitingAndTerminalSheetsAndBlockOtherActiveAssignment() {
        DspSchedulerOrderState waitingAtAv02 = state(order(
                "empty-waiting", "104", 999, 0, "pharmacy-a", DspOrderLineType.FULL_PACK));
        DspSchedulerOrderState activeElsewhere = state(order(
                "empty-active", "104", 999, 1, "pharmacy-b", DspOrderLineType.FULL_PACK));
        DspSchedulerOrderState released = new DspSchedulerOrderState(
                order("empty-released", "104", 999, 2, "pharmacy-c", DspOrderLineType.FULL_PACK),
                route(),
                DspOrderStatus.RELEASED);
        DspSchedulerOrderState completed = new DspSchedulerOrderState(
                order("empty-completed", "104", 999, 3, "pharmacy-d", DspOrderLineType.FULL_PACK),
                route(),
                DspOrderStatus.COMPLETED);
        List<DspSchedulerOrderState> states = List.of(
                waitingAtAv02, activeElsewhere, released, completed);
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        PhysicalToteId activeId = new PhysicalToteId("av02-active");
        ledger.register(PhysicalToteRecord.preP2p(activeId));
        ledger.assign(
                activeElsewhere.order().orderSheetKey(),
                activeId,
                PhysicalToteAssignmentStage.PRE_P2P,
                Duration.ZERO);

        Av02AllocationSnapshot snapshot = factory.create(
                3,
                scheduler(states, Set.of()),
                supply(states, sheetKeys(states)),
                inventory(2, allocated(waitingAtAv02.order(), "av02-waiting", 0)),
                ledger.snapshot());

        assertEquals(List.of("empty-active"), snapshot.candidates().stream()
                .map(candidate -> candidate.order().orderId()).toList());
        assertEquals(
                List.of(Av02AllocationBlockReason.ACTIVE_PHYSICAL_ASSIGNMENT),
                snapshot.candidates().getFirst().blockReasons());
        assertTrue(snapshot.command().isEmpty());
    }

    @Test
    void shouldAllowEligibleLowerPriorityCentreWhenHigherPriorityCentreIsBlocked() {
        DspSchedulerOrderState blockedHigh = state(order(
                "empty-high", "104", 999, 0, "pharmacy-a", DspOrderLineType.ADAPTED));
        DspSchedulerOrderState readyLow = state(order(
                "empty-low", "108", 998, 1, "pharmacy-b", DspOrderLineType.FULL_PACK));
        List<DspSchedulerOrderState> states = List.of(blockedHigh, readyLow);

        Av02AllocationSnapshot snapshot = factory.create(
                4,
                scheduler(states, Set.of()),
                supply(states, sheetKeys(states)),
                inventory(1),
                emptyLifecycle());

        assertEquals(
                List.of(Av02AllocationBlockReason.DEPENDENCY_NOT_READY),
                candidate(snapshot, "empty-high").blockReasons());
        assertEquals(readyLow.order().orderSheetKey(), snapshot.command().orElseThrow().orderSheetKey());
        assertEquals("108", snapshot.command().orElseThrow().serviceCentreId());
    }

    @Test
    void shouldIgnoreNonEmptyOrdersAndRejectInvalidIdentityOrSnapshotInputs() {
        DspSchedulerOrderState empty = state(order(
                "empty-1", "104", 999, 0, "pharmacy-a", DspOrderLineType.FULL_PACK));
        DspSchedulerOrderState fullPack = new DspSchedulerOrderState(
                new NotionalToteOrder(
                        "full-pack-1", "full-pack-1", "104", 1, OrderType.FULL_PACK,
                        List.of(item("full-line", "pharmacy-a", DspOrderLineType.FULL_PACK, "full-pack-1")),
                        999, 1),
                new RouteRequirements(false, false, false, true, false, StartLocation.OSR),
                DspOrderStatus.WAITING);
        List<DspSchedulerOrderState> states = List.of(fullPack, empty);

        Av02AllocationSnapshot snapshot = factory.create(
                5,
                scheduler(states, Set.of()),
                supply(states, Set.of(empty.order().orderSheetKey())),
                inventory(1),
                emptyLifecycle());

        assertEquals(List.of("empty-1"), snapshot.candidates().stream()
                .map(candidate -> candidate.order().orderId()).toList());
        assertFalse(List.of(AllocateEmptyToteAtAv02Command.class.getRecordComponents()).stream()
                .anyMatch(component -> component.getType() == PhysicalToteId.class));
        assertThrows(IllegalArgumentException.class, () -> factory.create(
                -1, scheduler(states, Set.of()), supply(states, Set.of()), inventory(1), emptyLifecycle()));
        assertThrows(IllegalArgumentException.class, () -> factory.create(
                0, null, supply(states, Set.of()), inventory(1), emptyLifecycle()));
    }

    @Test
    void shouldHoldReadyFutureEmptyWhenCurrentEmptyIsUnreadyOrUnauthorized() {
        var current = state(order("current", "104", 999, 0, "old", DspOrderLineType.ADAPTED));
        var future = state(order("future", "108", 998, 1, "new", DspOrderLineType.FULL_PACK));
        var states = List.of(current, future);
        var releases = releaseFacts(0, 0, 1, 1);
        var blocked = factory.create(0, scheduler(states, Set.of()), supply(states, sheetKeys(states)),
                inventory(2), emptyLifecycle(), Optional.of(releases));
        assertTrue(blocked.command().isEmpty());
        assertEquals(List.of(Av02AllocationBlockReason.DEPENDENCY_NOT_READY),
                candidate(blocked, "current").blockReasons());
        assertEquals(List.of(Av02AllocationBlockReason.SERVICE_CENTRE_SEQUENCE),
                candidate(blocked, "future").blockReasons());

        var prepared = Set.of(PreparedLineKey.forDispatchLine(current.order(), current.order().items().getFirst()));
        var unauthorized = factory.create(1, scheduler(states, prepared),
                supply(states, Set.of(future.order().orderSheetKey())), inventory(2), emptyLifecycle(), Optional.of(releases));
        assertTrue(unauthorized.command().isEmpty());
        assertEquals(List.of(Av02AllocationBlockReason.EMPTY_NOT_AUTHORIZED),
                candidate(unauthorized, "current").blockReasons());
        var ready = factory.create(2, scheduler(states, prepared), supply(states, sheetKeys(states)),
                inventory(2), emptyLifecycle(), Optional.of(releases));
        assertEquals(current.order().orderSheetKey(), ready.command().orElseThrow().orderSheetKey());
        assertEquals(1, releases.unreleasedEmptySheetCounts().get("104"), "Allocation does not count a departure");

        var advanced = factory.create(3, scheduler(states, prepared), supply(states, sheetKeys(states)),
                inventory(2), emptyLifecycle(), Optional.of(releaseFacts(1, 0, 0, 1)));
        assertEquals(future.order().orderSheetKey(), advanced.command().orElseThrow().orderSheetKey());
        assertEquals(List.of(Av02AllocationBlockReason.SERVICE_CENTRE_SEQUENCE),
                candidate(advanced, "current").blockReasons());
        var legacy = factory.create(4, scheduler(states, Set.of()), supply(states, sheetKeys(states)),
                inventory(2), emptyLifecycle(), Optional.empty());
        assertEquals(future.order().orderSheetKey(), legacy.command().orElseThrow().orderSheetKey());
    }

    @Test
    void shouldHoldFutureEmptyForOutstandingCurrentOsrAndSkipItsDependencyEvaluation() {
        var future = state(order("future", "108", 998, 1, "new", DspOrderLineType.FULL_PACK));
        var states = List.of(future);
        var noDependencyReads = new Av02AllocationSnapshotFactory(new DspDependencyEvaluator() {
            @Override
            public List<DependencyBlock> findBlocks(DspSchedulerOrderState candidate, WarehouseSchedulerSnapshot snapshot) {
                throw new AssertionError("Future-centre dependency evaluation must be skipped");
            }
        });
        for (var releases : List.of(releaseFacts(0, 1, 0, 1), releaseFacts(2, 0, 0, 0))) {
            var snapshot = noDependencyReads.create(0, scheduler(states, Set.of()), supply(states, sheetKeys(states)),
                    inventory(2), emptyLifecycle(), Optional.of(releases));
            assertTrue(snapshot.command().isEmpty());
            assertEquals(List.of(Av02AllocationBlockReason.SERVICE_CENTRE_SEQUENCE),
                    snapshot.candidates().getFirst().blockReasons());
        }
    }

    @Test
    void shouldRejectNullReleaseOptionalAndUnknownCandidateCentre() {
        var unknown = state(order("unknown", "109", 997, 0, "unknown", DspOrderLineType.FULL_PACK));
        var states = List.of(unknown);
        assertThrows(IllegalArgumentException.class, () -> factory.create(0, scheduler(states, Set.of()),
                supply(states, sheetKeys(states)), inventory(2), emptyLifecycle(), null));
        assertThrows(IllegalArgumentException.class, () -> factory.create(0, scheduler(states, Set.of()),
                supply(states, sheetKeys(states)), inventory(2), emptyLifecycle(), Optional.of(releaseFacts(0, 1, 0, 1))));
    }

    static WholeServiceCentreReleaseSnapshot releaseFacts(long version, int currentOsr, int currentEmpty, int nextEmpty) {
        return new WholeServiceCentreReleaseSnapshot(version, List.of("104", "108"),
                currentOsr + currentEmpty > 0 ? Optional.of("104")
                        : nextEmpty > 0 ? Optional.of("108") : Optional.empty(),
                Map.of("104", currentOsr, "108", 0), Map.of("104", currentEmpty, "108", nextEmpty),
                Map.of("104", Map.of(new P2pLineId("p2p-1"), 0), "108", Map.of(new P2pLineId("p2p-1"), 0)));
    }

    private static Av02AllocationCandidate candidate(
            Av02AllocationSnapshot snapshot,
            String orderId) {
        return snapshot.candidates().stream()
                .filter(candidate -> candidate.order().orderId().equals(orderId))
                .findFirst()
                .orElseThrow();
    }

    private static DspSchedulerOrderState state(NotionalToteOrder order) {
        return new DspSchedulerOrderState(order, route(), DspOrderStatus.WAITING);
    }

    private static RouteRequirements route() {
        return new RouteRequirements(false, false, false, true, false, StartLocation.AV02);
    }

    private static NotionalToteOrder order(
            String orderId,
            String serviceCentreId,
            int priority,
            long sequence,
            String pharmacyId,
            DspOrderLineType lineType) {
        return new NotionalToteOrder(
                orderId,
                orderId,
                serviceCentreId,
                1,
                OrderType.EMPTY,
                List.of(item("line-" + orderId, pharmacyId, lineType, orderId)),
                priority,
                sequence);
    }

    private static DspOrderItem item(
            String lineReference,
            String pharmacyId,
            DspOrderLineType lineType,
            String referenceOrderId) {
        return new DspOrderItem(
                lineReference,
                "product-" + lineReference,
                1,
                pharmacyId,
                "patient-" + lineReference,
                "prescription-" + lineReference,
                lineType,
                referenceOrderId,
                1,
                1);
    }

    private static WarehouseSchedulerSnapshot scheduler(
            List<DspSchedulerOrderState> states,
            Set<PreparedLineKey> preparedLineKeys) {
        return new WarehouseSchedulerSnapshot(
                states,
                Map.of(),
                preparedLineKeys,
                Optional.empty());
    }

    private static DspSupplySnapshot supply(
            List<DspSchedulerOrderState> states,
            Set<OrderSheetKey> authorizedKeys) {
        Map<String, Integer> priorities = new java.util.LinkedHashMap<>();
        states.forEach(state -> priorities.putIfAbsent(
                state.order().serviceCentreId().trim(), state.order().orderPriority()));
        List<ServiceCentreSupplySnapshot> serviceCentres = priorities.entrySet().stream()
                .map(entry -> {
                    Set<OrderSheetKey> centreKeys = authorizedKeys.stream()
                            .filter(key -> states.stream().anyMatch(state ->
                                    state.order().orderSheetKey().equals(key)
                                            && state.order().serviceCentreId().trim().equals(entry.getKey())))
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

    private static Set<OrderSheetKey> sheetKeys(List<DspSchedulerOrderState> states) {
        return states.stream()
                .filter(state -> state.order().orderType() == OrderType.EMPTY)
                .map(state -> state.order().orderSheetKey())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private static Av02InventorySnapshot inventory(int capacity, Av02AllocatedTote... totes) {
        return new Av02InventorySnapshot(capacity, List.of(totes), List.of());
    }

    private static Av02InventorySnapshot fullInventory() {
        NotionalToteOrder storedOrder = order(
                "stored", "104", 999, 9, "pharmacy-z", DspOrderLineType.FULL_PACK);
        return inventory(1, allocated(storedOrder, "av02-stored", 9));
    }

    private static Av02AllocatedTote allocated(
            NotionalToteOrder order,
            String physicalToteId,
            long sourceSequenceNumber) {
        PhysicalToteId id = new PhysicalToteId(physicalToteId);
        OperationalPhysicalToteIdentity identity = new OperationalPhysicalToteIdentity(
                OperationalPhysicalToteSource.AV02,
                id,
                order.orderSheetKey(),
                OrderType.EMPTY,
                order.serviceCentreId(),
                PhysicalToteRole.PRE_P2P,
                sourceSequenceNumber);
        return new Av02AllocatedTote(
                identity,
                PhysicalToteRecord.preP2p(id),
                order.items().getFirst().pharmacyId());
    }

    private static PhysicalToteLifecycleSnapshot emptyLifecycle() {
        return new PhysicalToteLifecycleSnapshot(Map.of(), List.of());
    }
}
