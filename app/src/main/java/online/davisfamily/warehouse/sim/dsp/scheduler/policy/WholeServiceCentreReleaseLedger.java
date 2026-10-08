package online.davisfamily.warehouse.sim.dsp.scheduler.policy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import online.davisfamily.warehouse.sim.dsp.io.LoadedDspData;
import online.davisfamily.warehouse.sim.dsp.lifecycle.InboundToteManifest;
import online.davisfamily.warehouse.sim.dsp.model.NotionalToteOrder;
import online.davisfamily.warehouse.sim.dsp.model.OrderSheetKey;
import online.davisfamily.warehouse.sim.dsp.model.OrderType;
import online.davisfamily.warehouse.sim.dsp.model.PhysicalToteId;
import online.davisfamily.warehouse.sim.dsp.osr.release.OperationalPhysicalToteReleaseCommand;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalPhysicalToteSource;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.dsp.outbound.P2pLineId;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pLineDefinition;
import online.davisfamily.warehouse.sim.dsp.p2p.lease.P2pPhysicalToteAssignment;
import online.davisfamily.warehouse.sim.dsp.schedule.DspServiceCentreTimetable;
import online.davisfamily.warehouse.sim.dsp.schedule.ServiceCentreSchedule;

/**
 * Simulation-thread-owned accounting for the entire executable day's inbound departures.
 * Workers receive {@link WholeServiceCentreReleaseSnapshot}, never this mutable owner.
 */
public final class WholeServiceCentreReleaseLedger {
    private final Map<PhysicalToteId, Obligation> osrObligations = new LinkedHashMap<>();
    private final Map<OrderSheetKey, Obligation> emptyObligations = new LinkedHashMap<>();
    private final Set<PhysicalToteId> committedAv02ToteIds = new HashSet<>();
    private final Map<P2pLineId, P2pLineDefinition> linesById = new LinkedHashMap<>();
    private final List<CentreCounts> orderedCentres;
    private final List<String> orderedServiceCentreIds;
    private int releaseCentreIndex;
    private long version;
    private WholeServiceCentreReleaseSnapshot cachedSnapshot;

    public WholeServiceCentreReleaseLedger(
            LoadedDspData executableData,
            DspServiceCentreTimetable timetable,
            List<P2pLineDefinition> lineDefinitions) {
        if (executableData == null || timetable == null
                || lineDefinitions == null || lineDefinitions.isEmpty()) {
            throw new IllegalArgumentException("executable data, timetable and configured lines are required");
        }
        Set<OperationalRouteDestination> destinations = new HashSet<>();
        for (P2pLineDefinition line : lineDefinitions) {
            if (line == null || linesById.putIfAbsent(line.lineId(), line) != null
                    || !destinations.add(line.destination())) {
                throw new IllegalArgumentException("configured line IDs and destinations must be distinct");
            }
        }
        Map<String, ServiceCentreSchedule> schedules = new LinkedHashMap<>();
        for (ServiceCentreSchedule schedule : timetable.serviceCentres()) {
            schedules.put(schedule.serviceCentreId(), schedule);
        }
        Map<OrderSheetKey, NotionalToteOrder> orders = new LinkedHashMap<>();
        Map<String, CentreCounts> centres = new LinkedHashMap<>();
        for (NotionalToteOrder order : executableData.orders()) {
            ServiceCentreSchedule schedule = schedules.get(order.serviceCentreId());
            if (schedule == null || schedule.priority() != order.orderPriority()) {
                throw new IllegalArgumentException("Invalid configured centre for sheet: " + order.orderSheetKey());
            }
            if (orders.putIfAbsent(order.orderSheetKey(), order) != null) {
                throw new IllegalArgumentException("Duplicate executable sheet: " + order.orderSheetKey());
            }
            CentreCounts centre = centres.computeIfAbsent(order.serviceCentreId(),
                    ignored -> new CentreCounts(schedule, linesById.keySet()));
            if (order.orderType() == OrderType.EMPTY) {
                emptyObligations.put(order.orderSheetKey(), new Obligation(order.orderSheetKey(), centre));
                centre.emptyCount = Math.incrementExact(centre.emptyCount);
            }
        }
        Set<OrderSheetKey> manifestedSheets = new HashSet<>();
        for (InboundToteManifest manifest : executableData.inboundToteManifests()) {
            NotionalToteOrder order = orders.get(manifest.orderSheetKey());
            if (order == null || order.orderType() != manifest.orderType()
                    || !order.serviceCentreId().equals(manifest.serviceCentreId())) {
                throw new IllegalArgumentException("Manifest does not match an executable sheet: "
                        + manifest.physicalToteId().value());
            }
            CentreCounts centre = centres.get(manifest.serviceCentreId());
            if (osrObligations.putIfAbsent(manifest.physicalToteId(),
                    new Obligation(manifest.orderSheetKey(), centre)) != null) {
                throw new IllegalArgumentException("Duplicate physical manifest ID: " + manifest.physicalToteId());
            }
            centre.osrCount = Math.incrementExact(centre.osrCount);
            manifestedSheets.add(manifest.orderSheetKey());
        }
        for (NotionalToteOrder order : orders.values()) {
            if (order.orderType() != OrderType.EMPTY && !manifestedSheets.contains(order.orderSheetKey())) {
                throw new IllegalArgumentException("Missing executable manifest for sheet: " + order.orderSheetKey());
            }
        }
        List<CentreCounts> ordered = new ArrayList<>(centres.values());
        ordered.sort(Comparator.comparingInt((CentreCounts centre) -> centre.schedule.priority())
                .reversed().thenComparing(centre -> centre.schedule.serviceCentreId()));
        orderedCentres = List.copyOf(ordered);
        orderedServiceCentreIds = ordered.stream().map(centre -> centre.schedule.serviceCentreId()).toList();
    }

    public WholeServiceCentreReleaseSnapshot snapshot() {
        if (cachedSnapshot == null) {
            Map<String, Integer> osrCounts = new LinkedHashMap<>();
            Map<String, Integer> emptyCounts = new LinkedHashMap<>();
            Map<String, Map<P2pLineId, Integer>> committedCounts = new LinkedHashMap<>();
            for (CentreCounts centre : orderedCentres) {
                String id = centre.schedule.serviceCentreId();
                osrCounts.put(id, centre.osrCount);
                emptyCounts.put(id, centre.emptyCount);
                committedCounts.put(id, centre.committedCounts);
            }
            cachedSnapshot = new WholeServiceCentreReleaseSnapshot(version, orderedServiceCentreIds,
                    releaseCentreIndex < orderedCentres.size()
                            ? Optional.of(orderedServiceCentreIds.get(releaseCentreIndex)) : Optional.empty(),
                    osrCounts, emptyCounts, committedCounts);
        }
        return cachedSnapshot;
    }

    /** Read-only pre-delegate validation; selecting/validating a command never counts a departure. */
    public void validateUnreleased(OperationalPhysicalToteReleaseCommand command) {
        validatedObligation(command);
    }

    /**
     * Call only after read-only validation and the underlying handler returns {@code applied()}.
     * Revalidation protects accounting invariants; it does not infer success from inventory/state.
     */
    public void recordApplied(OperationalPhysicalToteReleaseCommand command) {
        Obligation obligation;
        try {
            obligation = validatedObligation(command);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid applied release: " + exception.getMessage(), exception);
        }
        obligation.released = true;
        CentreCounts centre = obligation.centre;
        if (command.source() == OperationalPhysicalToteSource.OSR) {
            centre.osrCount--;
        } else {
            committedAv02ToteIds.add(command.physicalToteId());
            centre.emptyCount--;
        }
        command.proposedP2pAssignment().ifPresent(assignment ->
                centre.committedCounts.compute(assignment.lineId(), (line, count) -> count + 1));
        while (releaseCentreIndex < orderedCentres.size()
                && orderedCentres.get(releaseCentreIndex).allReleased()) {
            releaseCentreIndex++;
        }
        version++;
        cachedSnapshot = null;
    }

    private Obligation validatedObligation(OperationalPhysicalToteReleaseCommand command) {
        if (command == null || command.source() == null || command.physicalToteId() == null
                || command.orderSheetKey() == null || command.serviceCentreId() == null
                || command.proposedP2pAssignment() == null) {
            throw new IllegalArgumentException("release command identities must not be null");
        }
        Obligation obligation;
        if (command.source() == OperationalPhysicalToteSource.OSR) {
            obligation = osrObligations.get(command.physicalToteId());
        } else {
            obligation = emptyObligations.get(command.orderSheetKey());
            if (osrObligations.containsKey(command.physicalToteId())
                    || committedAv02ToteIds.contains(command.physicalToteId())) {
                throw new IllegalArgumentException("AV02 physical identity conflicts with existing work");
            }
        }
        if (obligation == null || obligation.released
                || !obligation.sheet.equals(command.orderSheetKey())
                || !obligation.centre.schedule.serviceCentreId().equals(command.serviceCentreId())
                || releaseCentreIndex >= orderedCentres.size()
                || orderedCentres.get(releaseCentreIndex) != obligation.centre) {
            throw new IllegalArgumentException("Command does not identify an unreleased current-centre obligation");
        }
        int remaining = command.source() == OperationalPhysicalToteSource.OSR
                ? obligation.centre.osrCount : obligation.centre.emptyCount;
        if (remaining <= 0 || version == Long.MAX_VALUE) {
            throw new IllegalArgumentException("Release accounting cannot advance");
        }
        command.proposedP2pAssignment().ifPresent(assignment -> validateAssignment(command, obligation, assignment));
        return obligation;
    }

    private void validateAssignment(OperationalPhysicalToteReleaseCommand command, Obligation obligation,
            P2pPhysicalToteAssignment assignment) {
        P2pLineDefinition line = linesById.get(assignment.lineId());
        if (line == null || !line.destination().equals(assignment.destination())
                || !assignment.physicalToteId().equals(command.physicalToteId())
                || !assignment.serviceCentreId().equals(command.serviceCentreId())
                || obligation.centre.committedCounts.get(assignment.lineId()) == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("P2P assignment must match the exact command and configured line");
        }
    }

    private static final class Obligation {
        private final OrderSheetKey sheet;
        private final CentreCounts centre;
        private boolean released;

        private Obligation(OrderSheetKey sheet, CentreCounts centre) {
            this.sheet = sheet;
            this.centre = centre;
        }
    }

    private static final class CentreCounts {
        private final ServiceCentreSchedule schedule;
        private final Map<P2pLineId, Integer> committedCounts = new LinkedHashMap<>();
        private int osrCount;
        private int emptyCount;

        private CentreCounts(ServiceCentreSchedule schedule, Set<P2pLineId> lineIds) {
            this.schedule = schedule;
            lineIds.forEach(line -> committedCounts.put(line, 0));
        }

        private boolean allReleased() {
            return osrCount == 0 && emptyCount == 0;
        }
    }
}
