package online.davisfamily.warehouse.sim.dsp.p2p.bag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteSource;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.OperationalP2pReleaseAssignmentCommitter;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pPhysicalToteAssignment;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pReleaseAssignmentCommit;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pReleaseAssignmentRequest;

class BagCoherentOperationalP2pReleaseAssignmentCommitterTest {
    private static final OrderSheetKey SHEET = new OrderSheetKey("sheet-1", 1);
    private static final P2pBagCorrelationRequirement REQUIREMENT =
            new P2pBagCorrelationRequirement("bag-1", 2);

    @Test
    void shouldPinOnlyAfterTheAcceptedExistingCommitRuns() {
        P2pBagCorrelationAssignmentRegistry registry =
                new P2pBagCorrelationAssignmentRegistry();
        P2pPhysicalToteAssignment assignment = assignment("tote-1", "line-1");
        AtomicInteger existingCommits = new AtomicInteger();
        OperationalP2pReleaseAssignmentCommitter delegate = ignored ->
                () -> existingCommits.incrementAndGet();
        BagCoherentOperationalP2pReleaseAssignmentCommitter committer = committer(
                delegate, registry, assignment, REQUIREMENT);

        P2pReleaseAssignmentCommit pending = committer.prepare(request(assignment));

        assertTrue(registry.snapshot().assignments().isEmpty());
        pending.commit();

        assertEquals(1, existingCommits.get());
        assertEquals(Optional.of(new P2pLineId("line-1")), registry.lineFor("bag-1"));
    }

    @Test
    void shouldLeaveBothStatesUnchangedWhenTargetDoesNotAcceptThePreparedRelease() {
        P2pBagCorrelationAssignmentRegistry registry =
                new P2pBagCorrelationAssignmentRegistry();
        AtomicInteger existingCommits = new AtomicInteger();
        P2pPhysicalToteAssignment assignment = assignment("tote-1", "line-1");
        BagCoherentOperationalP2pReleaseAssignmentCommitter committer = committer(
                ignored -> () -> existingCommits.incrementAndGet(),
                registry,
                assignment,
                REQUIREMENT);

        committer.prepare(request(assignment));

        assertEquals(0, existingCommits.get());
        assertTrue(registry.snapshot().assignments().isEmpty());
    }

    @Test
    void shouldNotPinWhenTheExistingCommitterRejectsAStaleCommand() {
        P2pBagCorrelationAssignmentRegistry registry =
                new P2pBagCorrelationAssignmentRegistry();
        P2pPhysicalToteAssignment assignment = assignment("tote-1", "line-1");
        OperationalP2pReleaseAssignmentCommitter staleDelegate = ignored -> {
            throw new IllegalStateException("stale release");
        };
        BagCoherentOperationalP2pReleaseAssignmentCommitter committer = committer(
                staleDelegate, registry, assignment, REQUIREMENT);

        assertThrows(IllegalStateException.class, () -> committer.prepare(request(assignment)));
        assertTrue(registry.snapshot().assignments().isEmpty());
    }

    @Test
    void shouldRejectAConflictingLaterLineWithoutChangingTheOriginalPin() {
        P2pBagCorrelationAssignmentRegistry registry =
                new P2pBagCorrelationAssignmentRegistry();
        P2pPhysicalToteAssignment firstAssignment = assignment("tote-1", "line-1");
        AtomicInteger existingCommits = new AtomicInteger();
        OperationalP2pReleaseAssignmentCommitter delegate = ignored ->
                () -> existingCommits.incrementAndGet();
        BagCoherentOperationalP2pReleaseAssignmentCommitter committer = committer(
                delegate, registry, firstAssignment, REQUIREMENT);
        committer.prepare(request(firstAssignment)).commit();

        P2pPhysicalToteAssignment conflictingAssignment = assignment("tote-2", "line-2");
        assertThrows(
                IllegalStateException.class,
                () -> committer.prepare(request(conflictingAssignment)));

        assertEquals(1, existingCommits.get());
        assertEquals(firstAssignment, registry.find("bag-1").orElseThrow().p2pAssignment());
    }

    private static BagCoherentOperationalP2pReleaseAssignmentCommitter committer(
            OperationalP2pReleaseAssignmentCommitter delegate,
            P2pBagCorrelationAssignmentRegistry registry,
            P2pPhysicalToteAssignment assignment,
            P2pBagCorrelationRequirement requirement) {
        P2pBagCorrelationRequirementCatalog catalog =
                new P2pBagCorrelationRequirementCatalog(
                        Map.of(),
                        Map.of(SHEET, Set.of(requirement)));
        return new BagCoherentOperationalP2pReleaseAssignmentCommitter(
                delegate,
                catalog,
                registry);
    }

    private static P2pReleaseAssignmentRequest request(P2pPhysicalToteAssignment assignment) {
        return new P2pReleaseAssignmentRequest(
                assignment.physicalToteId(),
                SHEET,
                assignment.serviceCentreId(),
                assignment.destination().targetId(),
                OperationalPhysicalToteSource.AV02,
                Optional.of(assignment));
    }

    private static P2pPhysicalToteAssignment assignment(String toteId, String lineId) {
        P2pLineId line = new P2pLineId(lineId);
        return new P2pPhysicalToteAssignment(
                new PhysicalToteId(toteId),
                "SC-1",
                line,
                new OperationalRouteDestination(StationType.P2P, "target-" + lineId));
    }
}
