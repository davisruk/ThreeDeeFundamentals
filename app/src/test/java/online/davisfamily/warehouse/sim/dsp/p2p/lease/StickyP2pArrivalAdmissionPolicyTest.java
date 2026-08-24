package online.davisfamily.warehouse.sim.dsp.p2p.lease;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.arrival.P2pArrivalAdmissionDecision;
import online.davisfamily.warehouse.sim.dsp.p2p.arrival.P2pArrivalAdmissionRequest;

class StickyP2pArrivalAdmissionPolicyTest {

    @Test
    void shouldPermitOnlyTheExactCommittedAssignmentAndCurrentOwner() {
        P2pLineDefinition line = line("line-1", "p2p-1");
        P2pPhysicalToteAssignment assignment = assignment("tote-1", "104", line);
        P2pLineLeaseCatalogSnapshot snapshot = catalog(line, "104", assignment);
        AtomicInteger snapshotReads = new AtomicInteger();
        StickyP2pArrivalAdmissionPolicy policy = new StickyP2pArrivalAdmissionPolicy(
                line,
                () -> {
                    snapshotReads.incrementAndGet();
                    return snapshot;
                });

        assertEquals(
                P2pArrivalAdmissionDecision.permit(),
                policy.evaluate(request("tote-1", "104", line.destination(), assignment)));
        assertEquals(1, snapshotReads.get());
        assertEquals(Optional.of(assignment),
                snapshot.findAssignment(assignment.physicalToteId()));
        assertEquals(Optional.of("104"),
                snapshot.findLine(line.lineId()).orElseThrow().serviceCentreId());
    }

    @Test
    void shouldReturnStableReasonsForInvalidArrivalIdentity() {
        P2pLineDefinition line = line("line-1", "p2p-1");
        P2pLineDefinition otherLine = line("line-2", "p2p-2");
        P2pPhysicalToteAssignment assignment = assignment("tote-1", "104", line);
        StickyP2pArrivalAdmissionPolicy policy = new StickyP2pArrivalAdmissionPolicy(
                line, () -> catalog(line, "104", assignment));

        assertDeferred(
                StickyP2pArrivalAdmissionPolicy.MISSING_ASSIGNMENT,
                policy.evaluate(request("tote-1", "104", line.destination(), null)));
        assertDeferred(
                StickyP2pArrivalAdmissionPolicy.DESTINATION_MISMATCH,
                policy.evaluate(request(
                        "tote-1", "104", otherLine.destination(), assignment)));
        assertDeferred(
                StickyP2pArrivalAdmissionPolicy.LINE_MISMATCH,
                policy.evaluate(request(
                        "tote-1",
                        "104",
                        line.destination(),
                        new P2pPhysicalToteAssignment(
                                new PhysicalToteId("tote-1"),
                                "104",
                                otherLine.lineId(),
                                line.destination()))));
        assertDeferred(
                StickyP2pArrivalAdmissionPolicy.ASSIGNMENT_MISMATCH,
                policy.evaluate(request("other-tote", "104", line.destination(), assignment)));
        assertDeferred(
                StickyP2pArrivalAdmissionPolicy.SERVICE_CENTRE_MISMATCH,
                policy.evaluate(request("tote-1", "108", line.destination(), assignment)));
    }

    @Test
    void shouldReturnStableReasonsForMissingOrMismatchedCommittedState() {
        P2pLineDefinition line = line("line-1", "p2p-1");
        P2pPhysicalToteAssignment assignment = assignment("tote-1", "104", line);
        P2pArrivalAdmissionRequest request =
                request("tote-1", "104", line.destination(), assignment);

        assertDeferred(
                StickyP2pArrivalAdmissionPolicy.MISSING_ASSIGNMENT,
                new StickyP2pArrivalAdmissionPolicy(
                        line, () -> catalog(line, "104"))
                        .evaluate(request));
        assertDeferred(
                StickyP2pArrivalAdmissionPolicy.ASSIGNMENT_MISMATCH,
                new StickyP2pArrivalAdmissionPolicy(
                        line,
                        () -> catalog(line, "104", assignment("tote-1", "108", line)))
                        .evaluate(request));
        assertDeferred(
                StickyP2pArrivalAdmissionPolicy.INACTIVE_LEASE,
                new StickyP2pArrivalAdmissionPolicy(
                        line, () -> catalog(line, null, assignment))
                        .evaluate(request));
        assertDeferred(
                StickyP2pArrivalAdmissionPolicy.LEASE_OWNER_MISMATCH,
                new StickyP2pArrivalAdmissionPolicy(
                        line, () -> catalog(line, "108", assignment))
                        .evaluate(request));
    }

    @Test
    void shouldRejectInvalidPolicyInputsAndBrokenSnapshotSupplier() {
        P2pLineDefinition line = line("line-1", "p2p-1");
        P2pPhysicalToteAssignment assignment = assignment("tote-1", "104", line);

        assertThrows(IllegalArgumentException.class, () ->
                new StickyP2pArrivalAdmissionPolicy(null, () -> catalog(line, "104")));
        assertThrows(IllegalArgumentException.class, () ->
                new StickyP2pArrivalAdmissionPolicy(line, null));
        StickyP2pArrivalAdmissionPolicy policy =
                new StickyP2pArrivalAdmissionPolicy(line, () -> null);
        assertThrows(IllegalArgumentException.class, () -> policy.evaluate(null));
        assertThrows(IllegalStateException.class, () -> policy.evaluate(
                request("tote-1", "104", line.destination(), assignment)));
    }

    private static void assertDeferred(
            String reason,
            P2pArrivalAdmissionDecision decision) {
        assertEquals(P2pArrivalAdmissionDecision.defer(reason), decision);
    }

    private static P2pArrivalAdmissionRequest request(
            String physicalToteId,
            String serviceCentreId,
            OperationalRouteDestination destination,
            P2pPhysicalToteAssignment assignment) {
        return new P2pArrivalAdmissionRequest(
                new PhysicalToteId(physicalToteId),
                destination,
                serviceCentreId,
                new OrderSheetKey("order-1", 1),
                OrderType.FULL_PACK,
                List.of("pharmacy-1"),
                Optional.ofNullable(assignment));
    }

    private static P2pLineDefinition line(String lineId, String targetId) {
        return new P2pLineDefinition(
                new P2pLineId(lineId),
                new OperationalRouteDestination(StationType.P2P, targetId));
    }

    private static P2pPhysicalToteAssignment assignment(
            String physicalToteId,
            String serviceCentreId,
            P2pLineDefinition line) {
        return new P2pPhysicalToteAssignment(
                new PhysicalToteId(physicalToteId),
                serviceCentreId,
                line.lineId(),
                line.destination());
    }

    private static P2pLineLeaseCatalogSnapshot catalog(
            P2pLineDefinition line,
            String owner,
            P2pPhysicalToteAssignment... assignments) {
        return new P2pLineLeaseCatalogSnapshot(List.of(new P2pLineLeaseSnapshot(
                line,
                Optional.ofNullable(owner),
                P2pLineActivitySnapshot.idle(),
                List.of(assignments))));
    }
}
