package online.davisfamily.warehouse.sim.dsp.p2p.lease;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteSnapshot;

public final class P2pLineLeaseSnapshot {
    private final P2pLineDefinition definition;
    private final Optional<String> serviceCentreId;
    private final P2pLineActivitySnapshot activity;
    private final List<P2pPhysicalToteAssignment> physicalAssignments;

    public P2pLineLeaseSnapshot(
            P2pLineDefinition definition,
            Optional<String> serviceCentreId,
            P2pLineActivitySnapshot activity,
            List<P2pPhysicalToteAssignment> physicalAssignments) {
        P2pLineDefinition validatedDefinition = requireDefinition(definition);
        Optional<String> normalizedServiceCentreId = normalizedOptional(
                serviceCentreId, "serviceCentreId");
        P2pLineActivitySnapshot validatedActivity = requireActivity(activity);
        List<P2pPhysicalToteAssignment> validatedAssignments = validateAssignments(
                validatedDefinition, physicalAssignments);
        validateOpenOutboundTote(
                validatedDefinition, normalizedServiceCentreId, validatedActivity);

        this.definition = validatedDefinition;
        this.serviceCentreId = normalizedServiceCentreId;
        this.activity = validatedActivity;
        this.physicalAssignments = List.copyOf(validatedAssignments);
    }

    static P2pLineLeaseSnapshot fromValidatedRegistryState(
            P2pLineDefinition definition,
            Optional<String> serviceCentreId,
            P2pLineActivitySnapshot activity,
            List<P2pPhysicalToteAssignment> physicalAssignments) {
        P2pLineDefinition validatedDefinition = requireDefinition(definition);
        Optional<String> normalizedServiceCentreId = normalizedOptional(
                serviceCentreId, "serviceCentreId");
        P2pLineActivitySnapshot validatedActivity = requireActivity(activity);
        if (physicalAssignments == null) {
            throw new IllegalArgumentException("physicalAssignments must not be null");
        }
        validateOpenOutboundTote(
                validatedDefinition, normalizedServiceCentreId, validatedActivity);
        return new P2pLineLeaseSnapshot(
                validatedDefinition,
                normalizedServiceCentreId,
                validatedActivity,
                physicalAssignments,
                true);
    }

    private P2pLineLeaseSnapshot(
            P2pLineDefinition definition,
            Optional<String> serviceCentreId,
            P2pLineActivitySnapshot activity,
            List<P2pPhysicalToteAssignment> physicalAssignments,
            boolean trustedAssignments) {
        this.definition = definition;
        this.serviceCentreId = serviceCentreId;
        this.activity = activity;
        this.physicalAssignments = physicalAssignments;
    }

    public P2pLineDefinition definition() {
        return definition;
    }

    public Optional<String> serviceCentreId() {
        return serviceCentreId;
    }

    public P2pLineActivitySnapshot activity() {
        return activity;
    }

    public List<P2pPhysicalToteAssignment> physicalAssignments() {
        return physicalAssignments;
    }

    public Optional<String> activePharmacyId() {
        return activity.openOutboundTote().flatMap(OutboundToteSnapshot::pharmacyId);
    }

    public boolean leased() {
        return serviceCentreId.isPresent();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof P2pLineLeaseSnapshot that)) {
            return false;
        }
        return definition.equals(that.definition)
                && serviceCentreId.equals(that.serviceCentreId)
                && activity.equals(that.activity)
                && physicalAssignments.equals(that.physicalAssignments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(definition, serviceCentreId, activity, physicalAssignments);
    }

    @Override
    public String toString() {
        return "P2pLineLeaseSnapshot[definition=" + definition
                + ", serviceCentreId=" + serviceCentreId
                + ", activity=" + activity
                + ", physicalAssignments=" + physicalAssignments
                + "]";
    }

    private static P2pLineDefinition requireDefinition(P2pLineDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("definition must not be null");
        }
        return definition;
    }

    private static P2pLineActivitySnapshot requireActivity(
            P2pLineActivitySnapshot activity) {
        if (activity == null) {
            throw new IllegalArgumentException("activity must not be null");
        }
        return activity;
    }

    private static List<P2pPhysicalToteAssignment> validateAssignments(
            P2pLineDefinition definition,
            List<P2pPhysicalToteAssignment> physicalAssignments) {
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
        return physicalAssignments;
    }

    private static void validateOpenOutboundTote(
            P2pLineDefinition definition,
            Optional<String> serviceCentreId,
            P2pLineActivitySnapshot activity) {
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
