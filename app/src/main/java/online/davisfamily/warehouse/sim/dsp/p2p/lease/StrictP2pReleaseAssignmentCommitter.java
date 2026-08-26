package online.davisfamily.warehouse.sim.dsp.p2p.lease;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.ReleasePhysicalToteFromOsrCommand;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.routing.RouteRequirements;
import online.davisfamily.warehouse.sim.dsp.scheduler.operational.OperationalRouteEntrySelector;

public final class StrictP2pReleaseAssignmentCommitter
        implements P2pReleaseAssignmentCommitter, OperationalP2pReleaseAssignmentCommitter {
    private final P2pLineLeaseRegistry leaseRegistry;
    private final P2pReleaseRequirementResolver requirementResolver;
    private final Map<P2pLineId, P2pLineActivityProbe> activityProbes;
    private final OperationalRouteEntrySelector routeEntrySelector =
            new OperationalRouteEntrySelector();

    public StrictP2pReleaseAssignmentCommitter(
            P2pLineLeaseRegistry leaseRegistry,
            P2pReleaseRequirementResolver requirementResolver,
            Map<P2pLineId, P2pLineActivityProbe> activityProbes) {
        if (leaseRegistry == null || requirementResolver == null || activityProbes == null) {
            throw new IllegalArgumentException("P2P release committer inputs must not be null");
        }
        Map<P2pLineId, P2pLineActivityProbe> probeCopy = new LinkedHashMap<>();
        activityProbes.forEach((lineId, probe) -> {
            if (lineId == null || probe == null) {
                throw new IllegalArgumentException("activityProbes must not contain null");
            }
            probeCopy.put(lineId, probe);
        });
        if (!probeCopy.keySet().equals(leaseRegistry.definitions().stream()
                .map(P2pLineDefinition::lineId)
                .collect(java.util.stream.Collectors.toSet()))) {
            throw new IllegalArgumentException(
                    "activityProbes must contain exactly every configured P2P line");
        }
        this.leaseRegistry = leaseRegistry;
        this.requirementResolver = requirementResolver;
        this.activityProbes = Map.copyOf(probeCopy);
    }

    @Override
    public P2pReleaseAssignmentCommit prepare(
            ReleasePhysicalToteFromOsrCommand command,
            InboundToteManifest manifest) {
        if (command == null || manifest == null) {
            throw new IllegalArgumentException("command and manifest must not be null");
        }
        validateIdentity(command, manifest);
        return prepare(P2pReleaseAssignmentRequest.from(command));
    }

    @Override
    public P2pReleaseAssignmentCommit prepare(P2pReleaseAssignmentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request must not be null");
        }
        RouteRequirements requirements = requirementResolver.resolve(request.orderSheetKey());
        if (requirements == null) {
            throw new IllegalStateException("requirementResolver returned null");
        }
        Optional<P2pPhysicalToteAssignment> optionalAssignment = request.proposedP2pAssignment();
        if (!requirements.requiresP2p()) {
            if (optionalAssignment.isPresent()) {
                throw new IllegalStateException("Non-P2P route must not carry a P2P assignment");
            }
            return P2pReleaseAssignmentCommit.NO_OP;
        }
        P2pPhysicalToteAssignment assignment = optionalAssignment.orElseThrow(() ->
                new IllegalStateException("P2P-required route must carry exactly one assignment"));
        validateRouteTarget(request, requirements, assignment);

        Optional<P2pPhysicalToteAssignment> existing = leaseRegistry.findAssignment(
                assignment.physicalToteId());
        if (existing.isPresent()) {
            if (!existing.orElseThrow().equals(assignment)) {
                throw new IllegalStateException(
                        "Physical tote already has a conflicting P2P assignment");
            }
        }

        Map<P2pLineId, P2pLineActivitySnapshot> activities = snapshotActivities();
        P2pLineLeaseSnapshot line = leaseRegistry.snapshot(activities)
                .findLine(assignment.lineId())
                .orElseThrow(() -> new IllegalStateException(
                        "P2P assignment references an unknown line"));
        if (!line.definition().destination().equals(assignment.destination())) {
            throw new IllegalStateException(
                    "P2P assignment destination does not match the configured line");
        }
        if (line.leased()
                && !line.serviceCentreId().orElseThrow().equals(assignment.serviceCentreId())) {
            throw new IllegalStateException("P2P line is leased to another service centre");
        }
        if (existing.isPresent()) {
            if (!line.leased()) {
                throw new IllegalStateException(
                        "Existing P2P assignment has no active line lease");
            }
            return P2pReleaseAssignmentCommit.NO_OP;
        }
        if (!line.leased() && !line.activity().quiescent()) {
            throw new IllegalStateException("Unleased P2P line is not quiescent");
        }

        P2pLineActivitySnapshot validatedActivity = line.activity();
        boolean acquireLease = !line.leased();
        return () -> {
            if (acquireLease) {
                leaseRegistry.acquireLease(
                        assignment.lineId(), assignment.serviceCentreId(), validatedActivity);
            }
            leaseRegistry.commitAssignment(assignment);
        };
    }

    private Map<P2pLineId, P2pLineActivitySnapshot> snapshotActivities() {
        Map<P2pLineId, P2pLineActivitySnapshot> activities = new LinkedHashMap<>();
        for (P2pLineDefinition definition : leaseRegistry.definitions()) {
            P2pLineActivitySnapshot activity = activityProbes.get(definition.lineId()).snapshot();
            if (activity == null) {
                throw new IllegalStateException("P2P line activity probe returned null");
            }
            activities.put(definition.lineId(), activity);
        }
        return activities;
    }

    private static void validateIdentity(
            ReleasePhysicalToteFromOsrCommand command,
            InboundToteManifest manifest) {
        if (!command.physicalToteId().equals(manifest.physicalToteId())
                || !command.orderSheetKey().equals(manifest.orderSheetKey())
                || !command.serviceCentreId().equals(manifest.serviceCentreId())) {
            throw new IllegalStateException(
                    "P2P release command identity does not match the inbound manifest");
        }
        command.proposedP2pAssignment().ifPresent(assignment -> {
            if (!assignment.physicalToteId().equals(manifest.physicalToteId())
                    || !assignment.serviceCentreId().equals(manifest.serviceCentreId())) {
                throw new IllegalStateException(
                        "P2P assignment identity does not match the inbound manifest");
            }
        });
    }

    private void validateRouteTarget(
            P2pReleaseAssignmentRequest request,
            RouteRequirements requirements,
            P2pPhysicalToteAssignment assignment) {
        StationType firstStation = routeEntrySelector.firstStation(requirements)
                .orElseThrow(() -> new IllegalStateException(
                        "P2P-required route has no first station"));
        boolean sameTarget = request.releaseTargetId().equals(
                assignment.destination().targetId());
        if (firstStation == StationType.P2P && !sameTarget) {
            throw new IllegalStateException(
                    "Direct P2P release target must match its assigned line destination");
        }
        if (firstStation != StationType.P2P && sameTarget) {
            throw new IllegalStateException(
                    "Multi-station release target must remain distinct from its P2P destination");
        }
    }
}
