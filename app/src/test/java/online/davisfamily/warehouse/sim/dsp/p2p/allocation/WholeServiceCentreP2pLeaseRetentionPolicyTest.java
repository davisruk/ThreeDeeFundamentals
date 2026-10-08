package online.davisfamily.warehouse.sim.dsp.p2p.allocation;

import static org.junit.jupiter.api.Assertions.*;
import static online.davisfamily.warehouse.sim.dsp.p2p.allocation.WholeServiceCentreP2pAllocationPlannerTest.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.warehouse.sim.dsp.bagging.BagKey;
import online.davisfamily.warehouse.sim.dsp.bagging.PlannedBag;
import online.davisfamily.warehouse.sim.dsp.lifecycle.PhysicalToteLifecycleLedger;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.outbound.DeterministicOutboundToteIdSource;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteAllocator;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteClosureReason;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteConfig;
import online.davisfamily.warehouse.sim.dsp.outbound.OutputSheetAllocator;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pBaggingActivitySnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pInputActivitySnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLeaseReleaseController;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLeaseRetentionAction;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineActivityProbe;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineActivitySnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineLeaseCatalogSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineLeaseRegistry;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineLeaseSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pPackPathActivitySnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pPhysicalToteAssignment;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pServiceCentreWorkSnapshot;

class WholeServiceCentreP2pLeaseRetentionPolicyTest {
    @Test
    void shouldHoldUnreleasedCentreAndUpstreamPinnedToteButLetAnotherLineDrain() {
        var assignment = assignment(0, "A", "upstream");
        var leases = catalog(line(0, "A", P2pLineActivitySnapshot.idle(), List.of(assignment)),
                line(1, "A", P2pLineActivitySnapshot.idle(), List.of()));
        var work = work("A", "upstream");
        assertTrue(new WholeServiceCentreP2pLeaseRetentionPolicy(() -> releases(1, 1, Map.of()))
                .firstTransition(leases, work).isEmpty());
        var policy = new WholeServiceCentreP2pLeaseRetentionPolicy(() -> releases(0, 1, Map.of()));
        var decision = policy.firstTransition(leases, work).orElseThrow();
        assertEquals(LINES.get(1).lineId(), decision.lineId());
        assertEquals(P2pLeaseRetentionAction.RELEASE_LEASE, decision.action());
        var unpinned = policy.firstTransition(leases, P2pServiceCentreWorkSnapshot.empty()).orElseThrow();
        assertEquals(LINES.get(0).lineId(), unpinned.lineId());
    }

    @Test
    void shouldHoldEveryFreshInputPackAndBaggingOccupancy() {
        var policy = new WholeServiceCentreP2pLeaseRetentionPolicy(() -> releases(0, 1, Map.of()));
        var activities = new ArrayList<P2pLineActivitySnapshot>();
        for (var input : List.of(new P2pInputActivitySnapshot(1, 0, false, 0),
                new P2pInputActivitySnapshot(0, 1, false, 0), new P2pInputActivitySnapshot(0, 0, true, 0),
                new P2pInputActivitySnapshot(0, 0, false, 1))) {
            activities.add(new P2pLineActivitySnapshot(input, P2pPackPathActivitySnapshot.idle(),
                    P2pBaggingActivitySnapshot.idle(), Optional.empty()));
        }
        activities.add(new P2pLineActivitySnapshot(P2pInputActivitySnapshot.idle(),
                new P2pPackPathActivitySnapshot(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1),
                P2pBaggingActivitySnapshot.idle(), Optional.empty()));
        for (var bagging : List.of(
                new P2pBaggingActivitySnapshot(true, false, false, 0, false, false, false, 0),
                new P2pBaggingActivitySnapshot(false, true, false, 0, false, false, false, 0),
                new P2pBaggingActivitySnapshot(false, false, true, 0, false, false, false, 0),
                new P2pBaggingActivitySnapshot(false, false, false, 1, false, false, false, 0),
                new P2pBaggingActivitySnapshot(false, false, false, 0, true, false, false, 0),
                new P2pBaggingActivitySnapshot(false, false, false, 0, false, true, false, 0),
                new P2pBaggingActivitySnapshot(false, false, false, 0, false, false, true, 0),
                new P2pBaggingActivitySnapshot(false, false, false, 0, false, false, false, 1))) {
            activities.add(new P2pLineActivitySnapshot(P2pInputActivitySnapshot.idle(),
                    P2pPackPathActivitySnapshot.idle(), bagging, Optional.empty()));
        }
        var work = P2pServiceCentreWorkSnapshot.empty();
        for (var activity : activities) {
            assertTrue(policy.firstTransition(catalog(line(0, "A", activity, List.of())), work).isEmpty());
        }
        assertEquals(P2pLeaseRetentionAction.RELEASE_LEASE, policy.firstTransition(
                catalog(line(0, "A", P2pLineActivitySnapshot.idle(), List.of())), work).orElseThrow().action());
    }

    @Test
    void shouldCloseThenReleaseOnSeparateControllerUpdatesWhileOtherLineStaysBusy() {
        var registry = new P2pLineLeaseRegistry(LINES);
        registry.acquireLease(LINES.get(0).lineId(), "A", P2pLineActivitySnapshot.idle());
        registry.acquireLease(LINES.get(1).lineId(), "A", P2pLineActivitySnapshot.idle());
        registry.commitAssignment(assignment(0, "A", "consumed-history"));
        var sheet = new OrderSheetKey("order", 1);
        var allocator = new OutboundToteAllocator(new PhysicalToteLifecycleLedger(),
                new DeterministicOutboundToteIdSource(), new OutputSheetAllocator(List.of(sheet)), new OutboundToteConfig(10));
        allocator.allocate(LINES.get(0).lineId(), new PlannedBag(new BagKey("rx", 1), "A", "pharmacy", "patient", "rx",
                List.of("pack"), List.of(sheet)), Duration.ofSeconds(1));
        List<P2pLineActivityProbe> probes = new ArrayList<>();
        for (int index = 0; index < LINES.size(); index++) {
            int ordinal = index;
            probes.add(() -> new P2pLineActivitySnapshot(
                    ordinal == 1 ? new P2pInputActivitySnapshot(1, 0, false, 0) : P2pInputActivitySnapshot.idle(),
                    P2pPackPathActivitySnapshot.idle(), P2pBaggingActivitySnapshot.idle(),
                    allocator.snapshot().openToteFor(LINES.get(ordinal).lineId())));
        }
        var releases = releases(0, 1, Map.of());
        var work = P2pServiceCentreWorkSnapshot.empty();
        var policy = new WholeServiceCentreP2pLeaseRetentionPolicy(() -> releases);
        var controller = new P2pLeaseReleaseController(() -> work, probes, registry, allocator, policy);
        var context = new SimulationContext();
        context.setSimulationTimeSeconds(2);
        controller.update(context, 0.1);
        assertEquals(Optional.of("A"), registry.ownerFor(LINES.get(0).lineId()));
        assertEquals(OutboundToteClosureReason.APPLICABLE_WORK_COMPLETE,
                allocator.snapshot().closedTotes().getFirst().closureReason().orElseThrow());
        controller.update(context, 0.1);
        assertTrue(registry.ownerFor(LINES.get(0).lineId()).isEmpty());
        assertEquals(Optional.of("A"), registry.ownerFor(LINES.get(1).lineId()));
        assertEquals(1, allocator.snapshot().closedTotes().size());
    }

    @Test
    void shouldReusePendingProjectionAcrossActivityAndOwnerChangesButInvalidateWorkAndLists() throws Exception {
        var releases = releases(0, 0, Map.of());
        AtomicInteger reads = new AtomicInteger();
        var policy = new WholeServiceCentreP2pLeaseRetentionPolicy(() -> { reads.incrementAndGet(); return releases; });
        var historical = List.of(assignment(0, "A", "old-owner-tote"));
        var work = work("A", "old-owner-tote");
        var field = WholeServiceCentreP2pLeaseRetentionPolicy.class.getDeclaredField("pendingProjection");
        field.setAccessible(true);
        assertTrue(policy.firstTransition(catalog(line(0, "A", P2pLineActivitySnapshot.idle(), historical)), work).isEmpty());
        Object first = field.get(policy);
        var newOwner = catalog(line(0, "B", P2pLineActivitySnapshot.idle(), historical));
        // An earlier owner's remaining assignment is not an arrival due to the current owner.
        assertEquals("B", policy.firstTransition(newOwner, work).orElseThrow().serviceCentreId());
        assertSame(first, field.get(policy));
        var active = new P2pLineActivitySnapshot(new P2pInputActivitySnapshot(0, 0, true, 0),
                P2pPackPathActivitySnapshot.idle(), P2pBaggingActivitySnapshot.idle(), Optional.empty());
        assertTrue(policy.firstTransition(catalog(line(0, "B", active, historical)), work).isEmpty());
        assertSame(first, field.get(policy));
        var changedWork = P2pServiceCentreWorkSnapshot.empty();
        assertTrue(policy.firstTransition(catalog(line(0, "B", active, historical)), changedWork).isEmpty());
        Object second = field.get(policy);
        assertNotSame(first, second);
        var changedList = List.of(assignment(0, "B", "new-owner-tote"));
        policy.firstTransition(catalog(line(0, "B", active, changedList)), changedWork);
        Object third = field.get(policy);
        assertNotSame(second, third);
        policy.firstTransition(catalog(line(0, "B", active, changedList)), changedWork);
        assertSame(third, field.get(policy));
        assertEquals(6, reads.get());
    }

    @Test
    void shouldFailNullInputsAndSupplierResultsWithoutChangingRegistry() {
        assertThrows(IllegalArgumentException.class, () -> new WholeServiceCentreP2pLeaseRetentionPolicy(null));
        var policy = new WholeServiceCentreP2pLeaseRetentionPolicy(() -> null);
        var leases = catalog(line(0, "A", P2pLineActivitySnapshot.idle(), List.of()));
        assertThrows(IllegalStateException.class, () -> policy.firstTransition(leases, P2pServiceCentreWorkSnapshot.empty()));
        assertThrows(IllegalArgumentException.class, () -> policy.firstTransition(null, P2pServiceCentreWorkSnapshot.empty()));
        assertThrows(IllegalArgumentException.class, () -> policy.firstTransition(leases, null));
        assertEquals(Optional.of("A"), leases.lines().getFirst().serviceCentreId());
    }

    private static P2pLineLeaseSnapshot line(int index, String owner, P2pLineActivitySnapshot activity,
            List<P2pPhysicalToteAssignment> assignments) {
        return new P2pLineLeaseSnapshot(LINES.get(index), Optional.of(owner), activity, assignments);
    }

    private static P2pLineLeaseCatalogSnapshot catalog(P2pLineLeaseSnapshot... lines) {
        return new P2pLineLeaseCatalogSnapshot(List.of(lines));
    }

    private static P2pPhysicalToteAssignment assignment(int index, String owner, String physical) {
        return new P2pPhysicalToteAssignment(new PhysicalToteId(physical), owner,
                LINES.get(index).lineId(), LINES.get(index).destination());
    }

    private static P2pServiceCentreWorkSnapshot work(String owner, String physical) {
        return new P2pServiceCentreWorkSnapshot(Map.of(owner, List.of(new PhysicalToteId(physical))), Map.of());
    }
}
