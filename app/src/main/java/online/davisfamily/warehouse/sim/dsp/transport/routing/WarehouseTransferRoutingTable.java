package online.davisfamily.warehouse.sim.dsp.transport.routing;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;

import online.davisfamily.threedee.behaviour.routing.RouteConnection;
import online.davisfamily.threedee.behaviour.routing.RouteFollower.TravelDirection;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.warehouse.sim.dsp.osr.release.launch.OperationalRouteDestination;
import online.davisfamily.warehouse.sim.transfer.TransferRoutingDecision;
import online.davisfamily.warehouse.sim.transfer.TransferTarget;
import online.davisfamily.warehouse.sim.transfer.TransferZone;
import online.davisfamily.warehouse.sim.transfer.TransferZoneMachine;

public final class WarehouseTransferRoutingTable {

    public record Entry(
            String transferMachineId,
            String destinationTargetId,
            TransferRoutingDecision decision) {

        public Entry {
            transferMachineId = requireId(transferMachineId, "transferMachineId");
            destinationTargetId = requireId(destinationTargetId, "destinationTargetId");
            if (decision == null) {
                throw new IllegalArgumentException("decision must not be null");
            }
        }
    }

    private final WarehouseRouteCatalog routeCatalog;
    private final List<TransferZoneMachine> transferMachines;
    private final Map<RoutingKey, TransferRoutingDecision> decisionsByKey;
    private final Map<String, TransferZoneMachine> machinesById;

    public WarehouseTransferRoutingTable(
            WarehouseRouteCatalog routeCatalog,
            List<TransferZoneMachine> transferMachines,
            List<Entry> entries) {
        if (routeCatalog == null) {
            throw new IllegalArgumentException("routeCatalog must not be null");
        }
        if (transferMachines == null) {
            throw new IllegalArgumentException("transferMachines must not be null");
        }
        if (entries == null) {
            throw new IllegalArgumentException("entries must not be null");
        }
        this.routeCatalog = routeCatalog;
        this.machinesById = validateMachines(transferMachines);
        this.transferMachines = List.copyOf(transferMachines);
        this.decisionsByKey = validateEntries(entries);
        validateDestinationPaths();
    }

    public Optional<TransferRoutingDecision> findDecision(
            String transferMachineId,
            OperationalRouteDestination destination) {
        if (destination == null) {
            throw new IllegalArgumentException("destination must not be null");
        }
        String machineId = requireId(transferMachineId, "transferMachineId");
        WarehouseRouteDefinition definition = routeCatalog
                .find(destination)
                .orElse(null);
        if (definition == null || !machinesById.containsKey(machineId)) {
            return Optional.empty();
        }
        return Optional.ofNullable(decisionsByKey.get(
                new RoutingKey(machineId, destination.targetId())));
    }

    private Map<String, TransferZoneMachine> validateMachines(
            List<TransferZoneMachine> configuredMachines) {
        Map<String, TransferZoneMachine> result = new LinkedHashMap<>();
        for (TransferZoneMachine machine : configuredMachines) {
            if (machine == null) {
                throw new IllegalArgumentException(
                        "transferMachines must not contain null");
            }
            String machineId = requireId(machine.getId(), "transfer machine ID");
            if (result.putIfAbsent(machineId, machine) != null) {
                throw new IllegalArgumentException(
                        "Duplicate transfer machine ID: " + machineId);
            }
            TransferZone zone = machine.getTransferZone();
            if (zone == null || zone.getSourceSegment() == null
                    || zone.getTargetSegment() == null) {
                throw new IllegalArgumentException(
                        "Transfer machine must expose source and target topology: "
                                + machineId);
            }
        }
        return Map.copyOf(result);
    }

    private Map<RoutingKey, TransferRoutingDecision> validateEntries(List<Entry> entries) {
        Map<RoutingKey, TransferRoutingDecision> result = new HashMap<>();
        for (Entry entry : entries) {
            if (entry == null) {
                throw new IllegalArgumentException("entries must not contain null");
            }
            TransferZoneMachine machine = machinesById.get(entry.transferMachineId());
            if (machine == null) {
                throw new IllegalArgumentException(
                        "Unknown transfer machine: " + entry.transferMachineId());
            }
            if (routeCatalog.findByTargetId(entry.destinationTargetId()).isEmpty()) {
                throw new IllegalArgumentException(
                        "Unknown warehouse destination target: "
                                + entry.destinationTargetId());
            }
            RoutingKey key = new RoutingKey(
                    entry.transferMachineId(), entry.destinationTargetId());
            if (result.putIfAbsent(key, entry.decision()) != null) {
                throw new IllegalArgumentException(
                        "Duplicate transfer routing decision for "
                                + entry.transferMachineId() + "/"
                                + entry.destinationTargetId());
            }
            validateDecisionTarget(machine, entry.decision());
        }
        return Map.copyOf(result);
    }

    private static void validateDecisionTarget(
            TransferZoneMachine machine,
            TransferRoutingDecision decision) {
        if (!decision.isContinueOnCurrentRoute() && !decision.isTransfer()) {
            throw new IllegalArgumentException(
                    "Transfer routing decision must continue or transfer");
        }
        if (!decision.isTransfer()) {
            return;
        }
        TransferTarget target = decision.transferTarget();
        if (target.segment() != machine.getTransferZone().getTargetSegment()) {
            throw new IllegalArgumentException(
                    "Transfer target segment is not part of machine topology: "
                            + machine.getId());
        }
    }

    private void validateDestinationPaths() {
        for (WarehouseRouteDefinition definition : routeCatalog.definitions()) {
            if (!canReachTerminal(definition)) {
                throw new IllegalArgumentException(
                        "Warehouse destination terminal is unreachable through configured "
                                + "transfer topology: "
                                + definition.destination().targetId());
            }
        }
    }

    private boolean canReachTerminal(WarehouseRouteDefinition definition) {
        Queue<PathState> pending = new ArrayDeque<>();
        Set<PathState> visited = new HashSet<>();
        pending.add(new PathState(
                definition.entrySegment(), definition.entryDirection(), 0));

        while (!pending.isEmpty()) {
            PathState state = pending.remove();
            if (!visited.add(state)) {
                continue;
            }
            if (state.segment == definition.terminalSegment()) {
                return true;
            }

            int machineIndex = nextMachineIndex(state.segment, state.nextMachineIndex);
            if (machineIndex >= 0) {
                TransferZoneMachine machine = transferMachines.get(machineIndex);
                TransferRoutingDecision decision = decisionsByKey.get(new RoutingKey(
                        machine.getId(), definition.destination().targetId()));
                if (decision == null) {
                    throw new IllegalArgumentException(
                            "Missing transfer routing decision for reachable machine "
                                    + machine.getId() + "/"
                                    + definition.destination().targetId());
                }
                if (decision.isTransfer()) {
                    TransferTarget target = decision.transferTarget();
                    pending.add(new PathState(
                            target.segment(), target.travelDirection(), 0));
                } else {
                    pending.add(new PathState(
                            state.segment,
                            state.direction,
                            machineIndex + 1));
                }
                continue;
            }

            List<RouteConnection> connections = state.direction == TravelDirection.FORWARD
                    ? state.segment.getNextConnections()
                    : state.segment.getPreviousConnections();
            for (RouteConnection connection : connections) {
                pending.add(new PathState(connection.getSegment(), state.direction, 0));
            }
        }
        return false;
    }

    private int nextMachineIndex(RouteSegment segment, int startingIndex) {
        for (int i = startingIndex; i < transferMachines.size(); i++) {
            if (transferMachines.get(i).getTransferZone().getSourceSegment() == segment) {
                return i;
            }
        }
        return -1;
    }

    private static String requireId(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }

    private record RoutingKey(String machineId, String destinationTargetId) {
    }

    private record PathState(
            RouteSegment segment,
            TravelDirection direction,
            int nextMachineIndex) {
    }
}
