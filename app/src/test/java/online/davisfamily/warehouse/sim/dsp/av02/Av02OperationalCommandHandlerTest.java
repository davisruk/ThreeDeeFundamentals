package online.davisfamily.warehouse.sim.dsp.av02;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.adapting.MapBackedToteLoadPlanRegistry;
import online.davisfamily.warehouse.sim.dsp.adapting.MutableToteLoadPlanRegistry;
import online.davisfamily.warehouse.sim.dsp.lifecycle.Av02ToteLifecycleController;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteIdAllocator;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleLedger;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleState;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderItem;
import online.davisfamily.warehouse.sim.dsp.model.DspOrderLineType;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.osr.release.OperationalPhysicalToteReleaseRequest;
import online.davisfamily.warehouse.sim.dsp.osr.release.OperationalPhysicalToteReleaseTarget;
import online.davisfamily.warehouse.sim.dsp.osr.release.OperationalPhysicalToteReleaseTargetRegistry;
import online.davisfamily.warehouse.sim.dsp.osr.release.ReleasePhysicalToteFromOsrCommand;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteSource;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.OperationalP2pReleaseAssignmentCommitter;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pPhysicalToteAssignment;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pReleaseAssignmentCommit;
import online.davisfamily.warehouse.sim.dsp.runtime.SchedulerCommandApplicationResult;
import online.davisfamily.warehouse.sim.dsp.runtime.operational.CompositeOperationalCommandHandler;
import online.davisfamily.warehouse.sim.dsp.scheduler.SchedulerCommand;
import online.davisfamily.warehouse.sim.dsp.time.DspOperatingPhase;
import online.davisfamily.warehouse.sim.dsp.time.DspOperationalClockSnapshot;
import online.davisfamily.warehouse.sim.dsp.time.OperationalDayTime;
import online.davisfamily.warehouse.sim.totebag.plan.ToteLoadPlan;

class Av02OperationalCommandHandlerTest {

    @Test
    void shouldAcceptDownstreamBeforeCommittingAssignmentAndDepartingAv02Inventory() {
        Fixture fixture = fixture(true);
        fixture.target.observer = request -> {
            assertEquals(1, fixture.inventory.occupancy());
            assertEquals(
                    PhysicalToteLifecycleState.ACTIVE_PRE_P2P,
                    fixture.ledger.tote(fixture.physicalToteId).orElseThrow().state());
            assertEquals(1, fixture.ledger.activeAssignmentsFor(fixture.physicalToteId).size());
            assertEquals(
                    fixture.physicalToteId,
                    fixture.loadPlans.getLoadPlanFor(fixture.physicalToteId).physicalToteId());
        };

        SchedulerCommandApplicationResult result = fixture.handler.apply(fixture.command);

        assertTrue(result.applied());
        assertEquals(List.of("prepare", "target", "commit"), fixture.events);
        assertEquals(0, fixture.inventory.occupancy());
        assertEquals(
                List.of(fixture.allocatedTote),
                fixture.inventory.snapshot().departedTotes());
        assertEquals(1, fixture.ledger.activeAssignmentsFor(fixture.physicalToteId).size());
        assertEquals(
                PhysicalToteLifecycleState.ACTIVE_PRE_P2P,
                fixture.ledger.tote(fixture.physicalToteId).orElseThrow().state());
        assertEquals(1, fixture.target.callCount);
        assertEquals(OperationalPhysicalToteSource.AV02, fixture.target.request.source());
        assertEquals(List.of("pharmacy-1"), fixture.target.request.pharmacyIds());
    }

    @Test
    void shouldLeaveInventoryLifecycleAndLeaseUnchangedWhenTargetDefers() {
        Fixture fixture = fixture(true);
        fixture.target.result = SchedulerCommandApplicationResult.deferredResult("target full");

        SchedulerCommandApplicationResult result = fixture.handler.apply(fixture.command);

        assertTrue(result.deferred());
        assertEquals(1, fixture.inventory.occupancy());
        assertTrue(fixture.inventory.snapshot().departedTotes().isEmpty());
        assertEquals(1, fixture.ledger.activeAssignmentsFor(fixture.physicalToteId).size());
        assertEquals(List.of("prepare", "target"), fixture.events);
        assertEquals(1, fixture.target.callCount);
    }

    @Test
    void shouldRejectRepeatedAv02ReleaseWithoutInvokingTargetAgain() {
        Fixture fixture = fixture(true);

        assertTrue(fixture.handler.apply(fixture.command).applied());
        SchedulerCommandApplicationResult repeated = fixture.handler.apply(fixture.command);

        assertFalse(repeated.applied());
        assertFalse(repeated.deferred());
        assertEquals(1, fixture.target.callCount);
        assertEquals(List.of("prepare", "target", "commit"), fixture.events);
        assertEquals(0, fixture.inventory.occupancy());
        assertEquals(List.of(fixture.allocatedTote), fixture.inventory.snapshot().departedTotes());
    }

    @Test
    void shouldRejectInvalidLifecycleBeforeDownstreamAcceptance() {
        Fixture fixture = fixture(true);
        fixture.ledger.transitionTote(
                fixture.physicalToteId,
                PhysicalToteLifecycleState.CONSUMED_AT_P2P);

        SchedulerCommandApplicationResult result = fixture.handler.apply(fixture.command);

        assertFalse(result.applied());
        assertFalse(result.deferred());
        assertEquals(0, fixture.target.callCount);
        assertEquals(1, fixture.inventory.occupancy());
        assertTrue(fixture.events.isEmpty());
    }

    @Test
    void shouldRejectMissingLoadPlanBeforeDownstreamAcceptance() {
        Fixture fixture = fixture(false);

        SchedulerCommandApplicationResult result = fixture.handler.apply(fixture.command);

        assertFalse(result.applied());
        assertFalse(result.deferred());
        assertEquals(0, fixture.target.callCount);
        assertEquals(1, fixture.inventory.occupancy());
        assertTrue(fixture.events.isEmpty());
    }

    @Test
    void shouldDispatchOsrAndAv02CommandsByExactSourceType() {
        AtomicInteger osrCalls = new AtomicInteger();
        AtomicInteger av02Calls = new AtomicInteger();
        CompositeOperationalCommandHandler handler = new CompositeOperationalCommandHandler(
                command -> {
                    osrCalls.incrementAndGet();
                    return SchedulerCommandApplicationResult.appliedResult();
                },
                command -> {
                    av02Calls.incrementAndGet();
                    return SchedulerCommandApplicationResult.appliedResult();
                });

        handler.apply(new ReleasePhysicalToteFromOsrCommand(
                new PhysicalToteId("osr-1"),
                new online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey("order-1", 1),
                "104",
                "target-1"));
        handler.apply(new ReleasePhysicalToteFromAv02Command(
                new PhysicalToteId("av02-1"),
                new online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey("order-2", 1),
                "104",
                "target-1"));
        SchedulerCommandApplicationResult unsupported = handler.apply(new SchedulerCommand() { });

        assertEquals(1, osrCalls.get());
        assertEquals(1, av02Calls.get());
        assertFalse(unsupported.applied());
    }

    private static Fixture fixture(boolean withLoadPlan) {
        NotionalToteOrder order = order();
        PhysicalToteId physicalToteId = new PhysicalToteId("av02-000001");
        PhysicalToteLifecycleLedger ledger = new PhysicalToteLifecycleLedger();
        Av02ToteLifecycleController lifecycleController = new Av02ToteLifecycleController(
                ledger,
                new PhysicalToteIdAllocator() {
                    @Override
                    public PhysicalToteId nextPhysicalToteId() {
                        return physicalToteId;
                    }
                });
        var physicalTote = lifecycleController.allocateFor(order, Duration.ZERO, physicalToteId);
        Av02AllocatedTote allocatedTote = new Av02AllocatedTote(
                new online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteIdentity(
                        OperationalPhysicalToteSource.AV02,
                        physicalToteId,
                        order.orderSheetKey(),
                        OrderType.EMPTY,
                        "104",
                        online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteRole.PRE_P2P,
                        1),
                physicalTote,
                order.items().getFirst().pharmacyId());
        Av02PhysicalToteInventory inventory = new Av02PhysicalToteInventory(
                new Av02AllocationConfig(1));
        inventory.store(allocatedTote);
        MutableToteLoadPlanRegistry loadPlans = new MapBackedToteLoadPlanRegistry();
        if (withLoadPlan) {
            loadPlans.putLoadPlan(new ToteLoadPlan(physicalToteId, List.of()));
        }

        List<String> events = new ArrayList<>();
        RecordingTarget target = new RecordingTarget("target-1", events);
        OperationalP2pReleaseAssignmentCommitter committer = request -> {
            assertEquals(OperationalPhysicalToteSource.AV02, request.source());
            assertEquals(physicalToteId, request.physicalToteId());
            events.add("prepare");
            return () -> events.add("commit");
        };
        Av02OperationalCommandHandler handler = new Av02OperationalCommandHandler(
                inventory,
                ledger,
                loadPlans,
                () -> clock(Duration.ofSeconds(7)),
                new OperationalPhysicalToteReleaseTargetRegistry(List.of(target)),
                committer);
        OperationalRouteDestination destination = new OperationalRouteDestination(
                online.davisfamily.warehouse.sim.dsp.model.StationType.P2P,
                "p2p-1");
        P2pPhysicalToteAssignment assignment = new P2pPhysicalToteAssignment(
                physicalToteId,
                "104",
                new P2pLineId("line-1"),
                destination);
        ReleasePhysicalToteFromAv02Command command = new ReleasePhysicalToteFromAv02Command(
                physicalToteId,
                order.orderSheetKey(),
                "104",
                "target-1",
                Optional.of(assignment));
        return new Fixture(
                handler,
                inventory,
                ledger,
                loadPlans,
                physicalToteId,
                allocatedTote,
                target,
                command,
                events);
    }

    private static NotionalToteOrder order() {
        return new NotionalToteOrder(
                "empty-order",
                "notional-empty-order",
                "104",
                1,
                OrderType.EMPTY,
                List.of(new DspOrderItem(
                        "line-empty-order",
                        "product-empty-order",
                        1,
                        "pharmacy-1",
                        "patient-1",
                        "prescription-1",
                        DspOrderLineType.FULL_PACK,
                        "empty-order",
                        1,
                        1)),
                999,
                1);
    }

    private static DspOperationalClockSnapshot clock(Duration elapsedTime) {
        LocalDate operatingDate = LocalDate.of(2026, 8, 20);
        return new DspOperationalClockSnapshot(
                elapsedTime,
                operatingDate,
                operatingDate.atTime(6, 0),
                OperationalDayTime.day0(LocalTime.of(6, 0)),
                DspOperatingPhase.NORMAL_OPERATIONS,
                operatingDate.atTime(22, 0),
                operatingDate.plusDays(1).atStartOfDay());
    }

    private static final class RecordingTarget implements OperationalPhysicalToteReleaseTarget {
        private final String targetId;
        private final List<String> events;
        private SchedulerCommandApplicationResult result =
                SchedulerCommandApplicationResult.appliedResult();
        private java.util.function.Consumer<OperationalPhysicalToteReleaseRequest> observer =
                ignored -> { };
        private int callCount;
        private OperationalPhysicalToteReleaseRequest request;

        private RecordingTarget(String targetId, List<String> events) {
            this.targetId = targetId;
            this.events = events;
        }

        @Override
        public String targetId() {
            return targetId;
        }

        @Override
        public SchedulerCommandApplicationResult accept(
                OperationalPhysicalToteReleaseRequest request) {
            this.request = request;
            callCount++;
            events.add("target");
            observer.accept(request);
            return result;
        }
    }

    private record Fixture(
            Av02OperationalCommandHandler handler,
            Av02PhysicalToteInventory inventory,
            PhysicalToteLifecycleLedger ledger,
            MutableToteLoadPlanRegistry loadPlans,
            PhysicalToteId physicalToteId,
            Av02AllocatedTote allocatedTote,
            RecordingTarget target,
            ReleasePhysicalToteFromAv02Command command,
            List<String> events) {
    }
}
