package online.davisfamily.warehouse.sim.dsp.p2p.lease;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.model.StationType;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteSource;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.bag.P2pBagCorrelationAssignmentSnapshot;
import online.davisfamily.warehouse.sim.dsp.p2p.bag.P2pBagCorrelationRequirementCatalog;

/** Synthetic request/lease-boundary state, not a simulated lifecycle journey. */
final class P2pHistoryScalingFixtures {
    private static final int LINE_COUNT = 5;
    private static final int CURRENT_ASSIGNMENT_COUNT = 40;

    private P2pHistoryScalingFixtures() {
    }

    static Fixture create(int historicalCount, int pendingCount) {
        if (historicalCount < 0 || pendingCount <= 0) {
            throw new IllegalArgumentException(
                    "historicalCount must be nonnegative and pendingCount must be positive");
        }

        List<P2pLineDefinition> definitions = new ArrayList<>(LINE_COUNT);
        List<List<P2pPhysicalToteAssignment>> assignmentsByLine = new ArrayList<>(LINE_COUNT);
        for (int index = 0; index < LINE_COUNT; index++) {
            definitions.add(new P2pLineDefinition(
                    new P2pLineId("diagnostic-line-" + (index + 1)),
                    new OperationalRouteDestination(
                            StationType.P2P, "diagnostic-target-" + (index + 1))));
            assignmentsByLine.add(new ArrayList<>());
        }
        appendAssignments("history-", historicalCount, "104", definitions, assignmentsByLine);
        appendAssignments("current-", CURRENT_ASSIGNMENT_COUNT, "125", definitions, assignmentsByLine);

        List<P2pLineLeaseSnapshot> lines = new ArrayList<>(LINE_COUNT);
        Map<OperationalRouteDestination, Boolean> admissions = new LinkedHashMap<>();
        for (int index = 0; index < LINE_COUNT; index++) {
            P2pLineDefinition definition = definitions.get(index);
            lines.add(new P2pLineLeaseSnapshot(
                    definition,
                    Optional.of("125"),
                    new P2pLineActivitySnapshot(
                            new P2pInputActivitySnapshot(6, 1, true, 0),
                            P2pPackPathActivitySnapshot.idle(),
                            P2pBaggingActivitySnapshot.idle(),
                            Optional.empty()),
                    assignmentsByLine.get(index)));
            admissions.put(definition.destination(), true);
        }
        P2pLineLeaseCatalogSnapshot catalog = new P2pLineLeaseCatalogSnapshot(lines);
        Map<PhysicalToteId, P2pPhysicalToteAssignment> indexedAssignments = new LinkedHashMap<>();
        for (P2pLineLeaseSnapshot line : catalog.lines()) {
            for (P2pPhysicalToteAssignment assignment : line.physicalAssignments()) {
                if (indexedAssignments.putIfAbsent(assignment.physicalToteId(), assignment) != null) {
                    throw new IllegalArgumentException("Duplicate fixture assignment physical tote ID");
                }
            }
        }

        List<Candidate> candidates = new ArrayList<>(pendingCount);
        for (int index = 0; index < pendingCount; index++) {
            candidates.add(new Candidate(
                    index % 2 == 0 ? OperationalPhysicalToteSource.OSR : OperationalPhysicalToteSource.AV02,
                    new PhysicalToteId("pending-" + index),
                    new OrderSheetKey("pending-order-" + index, 1)));
        }
        P2pLineAllocationRequestFactory requestFactory = new P2pLineAllocationRequestFactory(
                catalog,
                admissions,
                Optional.empty(),
                P2pBagCorrelationAssignmentSnapshot.empty(),
                P2pBagCorrelationRequirementCatalog.empty());
        return new Fixture(
                historicalCount, catalog.lines(), catalog, requestFactory, indexedAssignments, candidates);
    }

    private static void appendAssignments(
            String prefix,
            int count,
            String serviceCentreId,
            List<P2pLineDefinition> definitions,
            List<List<P2pPhysicalToteAssignment>> assignmentsByLine) {
        for (int index = 0; index < count; index++) {
            int lineIndex = index % LINE_COUNT;
            P2pLineDefinition definition = definitions.get(lineIndex);
            assignmentsByLine.get(lineIndex).add(new P2pPhysicalToteAssignment(
                    new PhysicalToteId(prefix + index),
                    serviceCentreId,
                    definition.lineId(),
                    definition.destination()));
        }
    }

    record Candidate(OperationalPhysicalToteSource source, PhysicalToteId toteId,
            OrderSheetKey sheetKey) {
    }

    record Fixture(int historicalCount, List<P2pLineLeaseSnapshot> lines,
            P2pLineLeaseCatalogSnapshot catalog,
            P2pLineAllocationRequestFactory requestFactory,
            Map<PhysicalToteId, P2pPhysicalToteAssignment> indexedAssignments,
            List<Candidate> candidates) {
        Fixture {
            lines = List.copyOf(lines);
            indexedAssignments = Collections.unmodifiableMap(new LinkedHashMap<>(indexedAssignments));
            candidates = List.copyOf(candidates);
        }
    }
}
