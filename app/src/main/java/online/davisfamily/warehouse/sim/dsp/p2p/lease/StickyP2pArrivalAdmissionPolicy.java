package online.davisfamily.warehouse.sim.dsp.p2p.lease;

import java.util.Optional;
import java.util.function.Supplier;

import online.davisfamily.warehouse.sim.dsp.p2p.arrival.P2pArrivalAdmissionDecision;
import online.davisfamily.warehouse.sim.dsp.p2p.arrival.P2pArrivalAdmissionPolicy;
import online.davisfamily.warehouse.sim.dsp.p2p.arrival.P2pArrivalAdmissionRequest;

public final class StickyP2pArrivalAdmissionPolicy implements P2pArrivalAdmissionPolicy {
    public static final String MISSING_ASSIGNMENT = "P2P assignment is missing";
    public static final String ASSIGNMENT_MISMATCH =
            "P2P assignment does not match the committed assignment";
    public static final String DESTINATION_MISMATCH =
            "P2P assignment destination does not match the arrival line";
    public static final String LINE_MISMATCH =
            "P2P assignment line does not match the arrival line";
    public static final String SERVICE_CENTRE_MISMATCH =
            "P2P assignment service centre does not match the arrival";
    public static final String INACTIVE_LEASE = "P2P arrival line has no active lease";
    public static final String LEASE_OWNER_MISMATCH =
            "P2P arrival line is leased to another service centre";

    private final P2pLineDefinition lineDefinition;
    private final Supplier<P2pLineLeaseCatalogSnapshot> leaseSnapshotSupplier;

    public StickyP2pArrivalAdmissionPolicy(
            P2pLineDefinition lineDefinition,
            Supplier<P2pLineLeaseCatalogSnapshot> leaseSnapshotSupplier) {
        if (lineDefinition == null) {
            throw new IllegalArgumentException("lineDefinition must not be null");
        }
        if (leaseSnapshotSupplier == null) {
            throw new IllegalArgumentException("leaseSnapshotSupplier must not be null");
        }
        this.lineDefinition = lineDefinition;
        this.leaseSnapshotSupplier = leaseSnapshotSupplier;
    }

    @Override
    public P2pArrivalAdmissionDecision evaluate(P2pArrivalAdmissionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        Optional<P2pPhysicalToteAssignment> proposed = request.p2pAssignment();
        if (proposed.isEmpty()) {
            return defer(MISSING_ASSIGNMENT);
        }
        P2pPhysicalToteAssignment assignment = proposed.orElseThrow();
        if (!request.destination().equals(lineDefinition.destination())
                || !assignment.destination().equals(lineDefinition.destination())) {
            return defer(DESTINATION_MISMATCH);
        }
        if (!assignment.lineId().equals(lineDefinition.lineId())) {
            return defer(LINE_MISMATCH);
        }
        if (!assignment.physicalToteId().equals(request.physicalToteId())) {
            return defer(ASSIGNMENT_MISMATCH);
        }
        if (!assignment.serviceCentreId().equals(request.serviceCentreId())) {
            return defer(SERVICE_CENTRE_MISMATCH);
        }

        P2pLineLeaseCatalogSnapshot catalog = leaseSnapshotSupplier.get();
        if (catalog == null) {
            throw new IllegalStateException("leaseSnapshotSupplier returned null");
        }
        Optional<P2pPhysicalToteAssignment> committed =
                catalog.findAssignment(request.physicalToteId());
        if (committed.isEmpty()) {
            return defer(MISSING_ASSIGNMENT);
        }
        if (!committed.orElseThrow().equals(assignment)) {
            return defer(ASSIGNMENT_MISMATCH);
        }

        Optional<P2pLineLeaseSnapshot> optionalLine =
                catalog.findLine(lineDefinition.lineId());
        if (optionalLine.isEmpty()) {
            return defer(INACTIVE_LEASE);
        }
        P2pLineLeaseSnapshot line = optionalLine.orElseThrow();
        if (!line.definition().equals(lineDefinition)) {
            return defer(DESTINATION_MISMATCH);
        }
        if (!line.leased()) {
            return defer(INACTIVE_LEASE);
        }
        if (!line.serviceCentreId().orElseThrow().equals(request.serviceCentreId())) {
            return defer(LEASE_OWNER_MISMATCH);
        }
        return P2pArrivalAdmissionDecision.permit();
    }

    private static P2pArrivalAdmissionDecision defer(String reason) {
        return P2pArrivalAdmissionDecision.defer(reason);
    }
}
