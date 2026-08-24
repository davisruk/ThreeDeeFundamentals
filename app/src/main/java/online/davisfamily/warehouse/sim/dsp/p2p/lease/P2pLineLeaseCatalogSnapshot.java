package online.davisfamily.warehouse.sim.dsp.p2p.lease;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;

public record P2pLineLeaseCatalogSnapshot(List<P2pLineLeaseSnapshot> lines) {

    public P2pLineLeaseCatalogSnapshot {
        if (lines == null) {
            throw new IllegalArgumentException("lines must not be null");
        }
        Set<P2pLineId> lineIds = new LinkedHashSet<>();
        Set<OperationalRouteDestination> destinations = new LinkedHashSet<>();
        Set<PhysicalToteId> physicalToteIds = new LinkedHashSet<>();
        for (P2pLineLeaseSnapshot line : lines) {
            if (line == null) {
                throw new IllegalArgumentException("lines must not contain null");
            }
            if (!lineIds.add(line.definition().lineId())) {
                throw new IllegalArgumentException("line IDs must be distinct");
            }
            if (!destinations.add(line.definition().destination())) {
                throw new IllegalArgumentException("line destinations must be distinct");
            }
            for (P2pPhysicalToteAssignment assignment : line.physicalAssignments()) {
                if (!physicalToteIds.add(assignment.physicalToteId())) {
                    throw new IllegalArgumentException(
                            "physical assignment IDs must be distinct across lines");
                }
            }
        }
        lines = List.copyOf(lines);
    }

    public Optional<P2pLineLeaseSnapshot> findLine(P2pLineId lineId) {
        if (lineId == null) {
            throw new IllegalArgumentException("lineId must not be null");
        }
        return lines.stream()
                .filter(line -> line.definition().lineId().equals(lineId))
                .findFirst();
    }

    public Optional<P2pLineLeaseSnapshot> findDestination(
            OperationalRouteDestination destination) {
        if (destination == null) {
            throw new IllegalArgumentException("destination must not be null");
        }
        return lines.stream()
                .filter(line -> line.definition().destination().equals(destination))
                .findFirst();
    }

    public Optional<P2pPhysicalToteAssignment> findAssignment(PhysicalToteId physicalToteId) {
        if (physicalToteId == null) {
            throw new IllegalArgumentException("physicalToteId must not be null");
        }
        return lines.stream()
                .flatMap(line -> line.physicalAssignments().stream())
                .filter(assignment -> assignment.physicalToteId().equals(physicalToteId))
                .findFirst();
    }
}
