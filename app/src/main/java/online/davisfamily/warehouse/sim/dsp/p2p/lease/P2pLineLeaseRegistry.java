package online.davisfamily.warehouse.sim.dsp.p2p.lease;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.outbound.OutboundToteSnapshot;

public final class P2pLineLeaseRegistry {
    private final List<P2pLineDefinition> definitions;
    private final Map<P2pLineId, MutableLineLease> leasesByLineId = new LinkedHashMap<>();
    private final Map<PhysicalToteId, P2pPhysicalToteAssignment> assignmentsByPhysicalToteId =
            new LinkedHashMap<>();
    private long transitionSequence;

    public P2pLineLeaseRegistry(List<P2pLineDefinition> definitions) {
        if (definitions == null) {
            throw new IllegalArgumentException("definitions must not be null");
        }
        Set<P2pLineId> lineIds = new LinkedHashSet<>();
        Set<OperationalRouteDestination> destinations = new LinkedHashSet<>();
        List<P2pLineDefinition> validatedDefinitions = new ArrayList<>(definitions.size());
        for (P2pLineDefinition definition : definitions) {
            if (definition == null) {
                throw new IllegalArgumentException("definitions must not contain null");
            }
            if (!lineIds.add(definition.lineId())) {
                throw new IllegalArgumentException("definition line IDs must be distinct");
            }
            if (!destinations.add(definition.destination())) {
                throw new IllegalArgumentException("definition destinations must be distinct");
            }
            validatedDefinitions.add(definition);
            leasesByLineId.put(definition.lineId(), new MutableLineLease(definition));
        }
        this.definitions = List.copyOf(validatedDefinitions);
    }

    public List<P2pLineDefinition> definitions() {
        return definitions;
    }

    public Optional<String> ownerFor(P2pLineId lineId) {
        return Optional.ofNullable(requireLine(lineId).serviceCentreId);
    }

    public Optional<P2pPhysicalToteAssignment> findAssignment(PhysicalToteId physicalToteId) {
        if (physicalToteId == null) {
            throw new IllegalArgumentException("physicalToteId must not be null");
        }
        return Optional.ofNullable(assignmentsByPhysicalToteId.get(physicalToteId));
    }

    public Optional<P2pLineLeaseTransitionSnapshot> lastTransitionFor(P2pLineId lineId) {
        return Optional.ofNullable(requireLine(lineId).lastTransition);
    }

    public void acquireLease(
            P2pLineId lineId,
            String serviceCentreId,
            P2pLineActivitySnapshot activity) {
        MutableLineLease line = requireLine(lineId);
        String normalizedServiceCentreId = requireValue(serviceCentreId, "serviceCentreId");
        if (activity == null) {
            throw new IllegalArgumentException("activity must not be null");
        }

        if (line.serviceCentreId != null
                && !line.serviceCentreId.equals(normalizedServiceCentreId)) {
            throw new IllegalStateException(
                    "P2P line " + lineId.value() + " is leased to " + line.serviceCentreId);
        }
        validateSnapshot(line, normalizedServiceCentreId, activity);
        if (line.serviceCentreId == null && !activity.quiescent()) {
            throw new IllegalStateException("An unleased P2P line must be quiescent before acquisition");
        }

        if (line.serviceCentreId == null) {
            line.serviceCentreId = normalizedServiceCentreId;
            recordTransition(line, "LEASE_ACQUIRED serviceCentre=" + normalizedServiceCentreId);
        }
    }

    public void commitAssignment(P2pPhysicalToteAssignment assignment) {
        if (assignment == null) {
            throw new IllegalArgumentException("assignment must not be null");
        }
        P2pPhysicalToteAssignment existing = assignmentsByPhysicalToteId.get(
                assignment.physicalToteId());
        if (existing != null) {
            if (existing.equals(assignment)) {
                return;
            }
            throw new IllegalStateException(
                    "Physical tote already has a different P2P assignment: "
                            + assignment.physicalToteId().value());
        }

        MutableLineLease line = requireLine(assignment.lineId());
        if (!line.definition.destination().equals(assignment.destination())) {
            throw new IllegalArgumentException(
                    "P2P assignment destination does not match the line definition");
        }
        if (line.serviceCentreId == null) {
            throw new IllegalStateException("P2P line must be leased before assignment");
        }
        if (!line.serviceCentreId.equals(assignment.serviceCentreId())) {
            throw new IllegalStateException(
                    "P2P assignment service centre does not own the selected line");
        }

        assignmentsByPhysicalToteId.put(assignment.physicalToteId(), assignment);
        recordTransition(
                line,
                "ASSIGNMENT_COMMITTED physicalTote="
                        + assignment.physicalToteId().value());
    }

    public void releaseLease(
            P2pLineId lineId,
            String expectedServiceCentreId,
            P2pLineActivitySnapshot activity) {
        MutableLineLease line = requireLine(lineId);
        String normalizedExpectedOwner = requireValue(
                expectedServiceCentreId, "expectedServiceCentreId");
        if (activity == null) {
            throw new IllegalArgumentException("activity must not be null");
        }
        if (line.serviceCentreId == null) {
            throw new IllegalStateException("P2P line is not leased");
        }
        if (!line.serviceCentreId.equals(normalizedExpectedOwner)) {
            throw new IllegalStateException("P2P line lease owner does not match the expected owner");
        }
        validateSnapshot(line, normalizedExpectedOwner, activity);
        if (!activity.quiescent()) {
            throw new IllegalStateException("P2P line must be quiescent before lease release");
        }

        line.serviceCentreId = null;
        recordTransition(line, "LEASE_RELEASED serviceCentre=" + normalizedExpectedOwner);
    }

    public void recordOutboundToteClosure(
            P2pLineId lineId,
            OutboundToteSnapshot closedTote) {
        MutableLineLease line = requireLine(lineId);
        if (closedTote == null) {
            throw new IllegalArgumentException("closedTote must not be null");
        }
        if (closedTote.open() || !closedTote.p2pLineId().equals(lineId)) {
            throw new IllegalArgumentException(
                    "closedTote must be closed and match the P2P line");
        }
        if (line.serviceCentreId == null
                || closedTote.serviceCentreId().isEmpty()
                || !closedTote.serviceCentreId().orElseThrow().equals(line.serviceCentreId)) {
            throw new IllegalStateException(
                    "closedTote must belong to the current P2P line lease owner");
        }
        recordTransition(
                line,
                "OUTBOUND_TOTE_CLOSED physicalTote="
                        + closedTote.physicalToteId().value()
                        + " reason="
                        + closedTote.closureReason().orElseThrow());
    }

    public P2pLineLeaseCatalogSnapshot snapshot(
            Map<P2pLineId, P2pLineActivitySnapshot> activityByLineId) {
        if (activityByLineId == null) {
            throw new IllegalArgumentException("activityByLineId must not be null");
        }
        if (activityByLineId.size() != definitions.size()) {
            throw new IllegalArgumentException(
                    "activityByLineId must contain exactly every configured P2P line");
        }

        List<P2pLineLeaseSnapshot> lineSnapshots = new ArrayList<>(definitions.size());
        for (P2pLineDefinition definition : definitions) {
            P2pLineActivitySnapshot activity = activityByLineId.get(definition.lineId());
            if (activity == null) {
                throw new IllegalArgumentException(
                        "Missing activity for P2P line " + definition.lineId().value());
            }
            MutableLineLease line = leasesByLineId.get(definition.lineId());
            lineSnapshots.add(snapshot(line, activity));
        }
        for (P2pLineId lineId : activityByLineId.keySet()) {
            if (lineId == null || !leasesByLineId.containsKey(lineId)) {
                throw new IllegalArgumentException("activityByLineId contains an unknown P2P line");
            }
        }
        return new P2pLineLeaseCatalogSnapshot(lineSnapshots);
    }

    private P2pLineLeaseSnapshot snapshot(
            MutableLineLease line,
            P2pLineActivitySnapshot activity) {
        return new P2pLineLeaseSnapshot(
                line.definition,
                Optional.ofNullable(line.serviceCentreId),
                activity,
                assignmentsByPhysicalToteId.values().stream()
                        .filter(assignment -> assignment.lineId().equals(line.definition.lineId()))
                        .toList());
    }

    private void validateSnapshot(
            MutableLineLease line,
            String serviceCentreId,
            P2pLineActivitySnapshot activity) {
        new P2pLineLeaseSnapshot(
                line.definition,
                Optional.of(serviceCentreId),
                activity,
                assignmentsByPhysicalToteId.values().stream()
                        .filter(assignment -> assignment.lineId().equals(line.definition.lineId()))
                        .toList());
    }

    private MutableLineLease requireLine(P2pLineId lineId) {
        if (lineId == null) {
            throw new IllegalArgumentException("lineId must not be null");
        }
        MutableLineLease line = leasesByLineId.get(lineId);
        if (line == null) {
            throw new IllegalArgumentException("Unknown P2P line: " + lineId.value());
        }
        return line;
    }

    private static String requireValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private void recordTransition(MutableLineLease line, String details) {
        line.lastTransition = new P2pLineLeaseTransitionSnapshot(
                ++transitionSequence, details);
    }

    private static final class MutableLineLease {
        private final P2pLineDefinition definition;
        private String serviceCentreId;
        private P2pLineLeaseTransitionSnapshot lastTransition;

        private MutableLineLease(P2pLineDefinition definition) {
            this.definition = definition;
        }
    }
}
