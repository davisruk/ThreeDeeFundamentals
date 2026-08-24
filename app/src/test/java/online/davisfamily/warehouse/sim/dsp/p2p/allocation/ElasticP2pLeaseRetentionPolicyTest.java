package online.davisfamily.warehouse.sim.dsp.p2p.allocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.warehouse.sim.dsp.bagging.BagKey;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedBag;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleLedger;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.outbound.DeterministicOutboundToteIdSource;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteAllocator;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteClosureReason;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteConfig;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteSnapshot;
import online.davisfamily.warehouse.sim.dsp.outbound.OutputSheetAllocator;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pBaggingActivitySnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pInputActivitySnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLeaseReleaseController;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLeaseRetentionAction;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineActivityProbe;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineActivitySnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineDefinition;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineLeaseCatalogSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineLeaseRegistry;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineLeaseSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pPackPathActivitySnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pPhysicalToteAssignment;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pServiceCentreWorkSnapshot;
import online.davisfamily.warehouse.sim.dsp.schedule.ServiceCentreDeadlineSnapshot;

class ElasticP2pLeaseRetentionPolicyTest {

    private static final String OWNER = "SC-104";
    private static final LocalDateTime EVALUATED_AT = LocalDateTime.of(2026, 8, 24, 6, 0);

    @Test
    void shouldRetainSurplusLeaseWhileCommittedAssignmentRemainsPinned() {
        P2pLineDefinition definition = definition("line-1");
        P2pPhysicalToteAssignment assignment = assignment(definition, "physical-1");
        P2pLineLeaseCatalogSnapshot leases = catalog(leased(
                definition, P2pLineActivitySnapshot.idle(), List.of(assignment)));
        ElasticP2pLeaseRetentionPolicy policy = new ElasticP2pLeaseRetentionPolicy(
                () -> allocation(leases, List.of(), List.of("line-1")));

        assertTrue(policy.firstTransition(
                leases, work("physical-1", "physical-unassigned")).isEmpty());

        var transition = policy.firstTransition(leases, work("physical-unassigned"))
                .orElseThrow();
        assertEquals(P2pLeaseRetentionAction.RELEASE_LEASE, transition.action());
        assertEquals(new P2pLineId("line-1"), transition.lineId());
    }

    @Test
    void shouldRetainSurplusLeaseUntilProcessingDrainsAndCancelWhenDemandRises() {
        P2pLineDefinition definition = definition("line-1");
        P2pLineActivitySnapshot busy = new P2pLineActivitySnapshot(
                new P2pInputActivitySnapshot(1, 0, false, 0),
                P2pPackPathActivitySnapshot.idle(),
                P2pBaggingActivitySnapshot.idle(),
                Optional.empty());
        P2pLineLeaseCatalogSnapshot busyLeases = catalog(leased(definition, busy, List.of()));
        AtomicReference<P2pElasticAllocationSnapshot> allocation = new AtomicReference<>(
                allocation(busyLeases, List.of(), List.of("line-1")));
        ElasticP2pLeaseRetentionPolicy policy = new ElasticP2pLeaseRetentionPolicy(allocation::get);

        assertTrue(policy.firstTransition(busyLeases, work("remaining-1")).isEmpty());

        P2pLineLeaseCatalogSnapshot idleLeases = catalog(leased(
                definition, P2pLineActivitySnapshot.idle(), List.of()));
        allocation.set(allocation(idleLeases, List.of("line-1"), List.of()));
        assertTrue(policy.firstTransition(idleLeases, work("remaining-1")).isEmpty());
    }

    @Test
    void shouldCloseSurplusOutputThenReleaseOnLaterUpdateDespiteAssignmentHistory() {
        P2pLineDefinition definition = definition("line-1");
        P2pLineLeaseRegistry registry = new P2pLineLeaseRegistry(List.of(definition));
        registry.acquireLease(definition.lineId(), OWNER, P2pLineActivitySnapshot.idle());
        registry.commitAssignment(assignment(definition, "consumed-history"));
        OutboundToteAllocator allocator = allocator();
        allocator.allocate(definition.lineId(), bag(), Duration.ofSeconds(1));
        P2pLineActivityProbe probe = () -> new P2pLineActivitySnapshot(
                P2pInputActivitySnapshot.idle(),
                P2pPackPathActivitySnapshot.idle(),
                P2pBaggingActivitySnapshot.idle(),
                allocator.snapshot().openToteFor(definition.lineId()));
        AtomicReference<P2pElasticAllocationSnapshot> allocation = new AtomicReference<>();
        ElasticP2pLeaseRetentionPolicy policy = new ElasticP2pLeaseRetentionPolicy(allocation::get);
        P2pLeaseReleaseController controller = new P2pLeaseReleaseController(
                () -> work("different-remaining-tote"),
                List.of(probe),
                registry,
                allocator,
                policy);
        allocation.set(allocation(
                registry.snapshot(Map.of(definition.lineId(), probe.snapshot())),
                List.of(),
                List.of("line-1")));
        SimulationContext context = new SimulationContext();
        context.setSimulationTimeSeconds(2d);

        controller.update(context, 0.1d);

        assertEquals(Optional.of(OWNER), registry.ownerFor(definition.lineId()));
        assertEquals(OutboundToteClosureReason.SERVICE_CENTRE_CHANGED,
                allocator.snapshot().closedTotes().getFirst().closureReason().orElseThrow());

        allocation.set(allocation(
                registry.snapshot(Map.of(definition.lineId(), probe.snapshot())),
                List.of(),
                List.of("line-1")));
        controller.update(context, 0.1d);

        assertTrue(registry.ownerFor(definition.lineId()).isEmpty());
    }

    @Test
    void shouldPreserveApplicableWorkCompletionClosureForCompletedOwner() {
        P2pLineDefinition definition = definition("line-1");
        P2pLineActivitySnapshot open = openActivity(definition.lineId());
        P2pLineLeaseCatalogSnapshot leases = catalog(leased(definition, open, List.of()));
        ElasticP2pLeaseRetentionPolicy policy = new ElasticP2pLeaseRetentionPolicy(
                () -> allocation(leases, List.of(), List.of("line-1")));

        var transition = policy.firstTransition(
                leases, P2pServiceCentreWorkSnapshot.empty()).orElseThrow();

        assertEquals(P2pLeaseRetentionAction.CLOSE_FOR_APPLICABLE_WORK_COMPLETION,
                transition.action());
    }

    @Test
    void shouldValidatePolicyInputsAndAllocationCatalog() {
        P2pLineLeaseCatalogSnapshot leases = catalog(leased(
                definition("line-1"), P2pLineActivitySnapshot.idle(), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new ElasticP2pLeaseRetentionPolicy(
                        (java.util.function.Supplier<P2pElasticAllocationSnapshot>) null));
        ElasticP2pLeaseRetentionPolicy nullAllocation =
                new ElasticP2pLeaseRetentionPolicy(() -> null);
        assertThrows(IllegalStateException.class, () -> nullAllocation.firstTransition(
                leases, work("remaining-1")));

        P2pLineLeaseCatalogSnapshot different = catalog(leased(
                definition("different"), P2pLineActivitySnapshot.idle(), List.of()));
        ElasticP2pLeaseRetentionPolicy mismatch = new ElasticP2pLeaseRetentionPolicy(
                () -> allocation(different, List.of(), List.of("different")));
        assertThrows(IllegalArgumentException.class,
                () -> mismatch.firstTransition(leases, work("remaining-1")));
    }

    private static P2pElasticAllocationSnapshot allocation(
            P2pLineLeaseCatalogSnapshot leases,
            List<String> feedingIds,
            List<String> drainingIds) {
        List<P2pLineId> feeding = feedingIds.stream().map(P2pLineId::new).toList();
        List<P2pLineId> draining = drainingIds.stream().map(P2pLineId::new).toList();
        int desired = feeding.size();
        P2pServiceCentreLineDemandSnapshot demand = new P2pServiceCentreLineDemandSnapshot(
                OWNER,
                999,
                Duration.ZERO,
                deadline(),
                new P2pServiceCentreWorkloadSnapshot(
                        OWNER,
                        List.of(new PhysicalToteId("remaining-work")),
                        0,
                        List.of(),
                        List.of(),
                        Duration.ofHours(1)),
                Duration.ofHours(1),
                1,
                1,
                desired,
                feeding,
                draining,
                0,
                Math.max(0, 1 - desired),
                true,
                List.of());
        return new P2pElasticAllocationSnapshot(
                P2pElasticAllocationSnapshot.DEADLINE_AWARE_ELASTIC_STICKY_LEASES,
                P2pElasticAllocationCalibrationStatus.UNCALIBRATED,
                EVALUATED_AT,
                leases.lines().stream().map(line -> line.definition().lineId()).toList(),
                1,
                List.of(demand),
                List.of());
    }

    private static ServiceCentreDeadlineSnapshot deadline() {
        return new ServiceCentreDeadlineSnapshot(
                OWNER,
                "Letchworth",
                999,
                EVALUATED_AT,
                EVALUATED_AT.plusHours(11),
                EVALUATED_AT.plusHours(10),
                EVALUATED_AT.plusHours(10),
                EVALUATED_AT.plusHours(10),
                Duration.ofHours(10),
                false,
                false);
    }

    private static P2pServiceCentreWorkSnapshot work(String... physicalToteIds) {
        return new P2pServiceCentreWorkSnapshot(
                Map.of(OWNER, java.util.Arrays.stream(physicalToteIds)
                        .map(PhysicalToteId::new)
                        .toList()),
                Map.of());
    }

    private static P2pLineLeaseCatalogSnapshot catalog(P2pLineLeaseSnapshot... lines) {
        return new P2pLineLeaseCatalogSnapshot(List.of(lines));
    }

    private static P2pLineLeaseSnapshot leased(
            P2pLineDefinition definition,
            P2pLineActivitySnapshot activity,
            List<P2pPhysicalToteAssignment> assignments) {
        return new P2pLineLeaseSnapshot(
                definition, Optional.of(OWNER), activity, assignments);
    }

    private static P2pPhysicalToteAssignment assignment(
            P2pLineDefinition definition,
            String physicalToteId) {
        return new P2pPhysicalToteAssignment(
                new PhysicalToteId(physicalToteId),
                OWNER,
                definition.lineId(),
                definition.destination());
    }

    private static P2pLineDefinition definition(String lineId) {
        return new P2pLineDefinition(
                new P2pLineId(lineId),
                new OperationalRouteDestination(StationType.P2P, "target-" + lineId));
    }

    private static P2pLineActivitySnapshot openActivity(P2pLineId lineId) {
        return new P2pLineActivitySnapshot(
                P2pInputActivitySnapshot.idle(),
                P2pPackPathActivitySnapshot.idle(),
                P2pBaggingActivitySnapshot.idle(),
                Optional.of(new OutboundToteSnapshot(
                        new PhysicalToteId("outbound-1"),
                        lineId,
                        Optional.of(OWNER),
                        Optional.of("pharmacy-1"),
                        10,
                        List.of(),
                        Optional.empty())));
    }

    private static OutboundToteAllocator allocator() {
        return new OutboundToteAllocator(
                new PhysicalToteLifecycleLedger(),
                new DeterministicOutboundToteIdSource(),
                new OutputSheetAllocator(List.of(new OrderSheetKey("order-1", 1))),
                new OutboundToteConfig(10));
    }

    private static PlannedBag bag() {
        return new PlannedBag(
                new BagKey("rx-1", 1),
                OWNER,
                "pharmacy-1",
                "patient-1",
                "rx-1",
                List.of("pack-1"),
                List.of(new OrderSheetKey("order-1", 1)));
    }
}
