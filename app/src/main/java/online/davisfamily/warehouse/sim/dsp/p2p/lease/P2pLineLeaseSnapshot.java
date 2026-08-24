package online.davisfamily.warehouse.sim.dsp.p2p.lease;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteSnapshot;

public record P2pLineLeaseSnapshot(
        P2pLineDefinition definition,
        Optional<String> serviceCentreId,
        P2pLineActivitySnapshot activity,
        List<P2pPhysicalToteAssignment> physicalAssignments) {

    public P2pLineLeaseSnapshot {
        if (definition == null) {
            throw new IllegalArgumentException("definition must not be null");
        }
        serviceCentreId = normalizedOptional(serviceCentreId, "serviceCentreId");
        if (activity == null) {
            throw new IllegalArgumentException("activity must not be null");
        }
        if (physicalAssignments == null) {
            throw new IllegalArgumentException("physicalAssignments must not be null");
        }
        Set<PhysicalToteId> physicalToteIds = new LinkedHashSet<>();
        for (P2pPhysicalToteAssignment assignment : physicalAssignments) {
            if (assignment == null) {
                throw new IllegalArgumentException("physicalAssignments must not contain null");
            }
            if (!assignment.lineId().equals(definition.lineId())
                    || !assignment.destination().equals(definition.destination())) {
                throw new IllegalArgumentException(
                        "physical assignment must match the lease line definition");
            }
            if (!physicalToteIds.add(assignment.physicalToteId())) {
                throw new IllegalArgumentException(
                        "physicalAssignments must contain distinct physical tote IDs");
            }
        }
        physicalAssignments = List.copyOf(physicalAssignments);

        Optional<OutboundToteSnapshot> openTote = activity.openOutboundTote();
        if (openTote.isPresent()) {
            OutboundToteSnapshot tote = openTote.orElseThrow();
            if (!tote.p2pLineId().equals(definition.lineId())) {
                throw new IllegalArgumentException("open outbound tote must match the lease line");
            }
            if (serviceCentreId.isEmpty()) {
                throw new IllegalArgumentException("an unleased line must not have an open outbound tote");
            }
            if (tote.serviceCentreId().isEmpty()
                    || !tote.serviceCentreId().equals(serviceCentreId)) {
                throw new IllegalArgumentException(
                        "open outbound tote must match the lease service centre");
            }
        }
    }

    public Optional<String> activePharmacyId() {
        return activity.openOutboundTote().flatMap(OutboundToteSnapshot::pharmacyId);
    }

    public boolean leased() {
        return serviceCentreId.isPresent();
    }

    private static Optional<String> normalizedOptional(Optional<String> value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        return value.map(item -> requireValue(item, fieldName));
    }

    private static String requireValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
