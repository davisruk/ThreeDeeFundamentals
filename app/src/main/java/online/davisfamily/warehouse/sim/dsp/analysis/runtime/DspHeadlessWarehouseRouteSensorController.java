package online.davisfamily.warehouse.sim.dsp.analysis.runtime;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.SimulationController;
import online.davisfamily.threedee.sim.framework.events.DetectionEvent;
import online.davisfamily.threedee.sim.framework.events.DetectionEvent.DetectionType;
import online.davisfamily.warehouse.sim.dsp.transport.RoutedPhysicalTote;
import online.davisfamily.warehouse.sim.dsp.transport.routing.DspWarehouseTransportRuntime;
import online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseRouteCatalog;
import online.davisfamily.warehouse.sim.dsp.transport.routing.WarehouseRouteDefinition;

/** Direct, deterministic headless route completion for the source-neutral transport runtime. */
public final class DspHeadlessWarehouseRouteSensorController implements SimulationController {
    private final DspWarehouseTransportRuntime transportRuntime;
    private final WarehouseRouteCatalog routeCatalog;
    private final double routeSpeedUnitsPerSecond;
    private final Map<String, Double> dueSimulationTimes = new LinkedHashMap<>();
    private final Set<String> sensorTriggeredPhysicalToteIds = new LinkedHashSet<>();

    public DspHeadlessWarehouseRouteSensorController(
            DspWarehouseTransportRuntime transportRuntime,
            WarehouseRouteCatalog routeCatalog,
            double routeSpeedUnitsPerSecond) {
        if (transportRuntime == null) {
            throw new IllegalArgumentException("transportRuntime must not be null");
        }
        if (routeCatalog == null) {
            throw new IllegalArgumentException("routeCatalog must not be null");
        }
        if (!Double.isFinite(routeSpeedUnitsPerSecond) || routeSpeedUnitsPerSecond <= 0d) {
            throw new IllegalArgumentException(
                    "routeSpeedUnitsPerSecond must be finite and positive");
        }
        this.transportRuntime = transportRuntime;
        this.routeCatalog = routeCatalog;
        this.routeSpeedUnitsPerSecond = routeSpeedUnitsPerSecond;
    }

    @Override
    public void update(SimulationContext context, double dtSeconds) {
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        if (!Double.isFinite(dtSeconds) || dtSeconds < 0d) {
            throw new IllegalArgumentException("dtSeconds must be finite and >= 0");
        }

        Set<String> activeIds = new LinkedHashSet<>();
        for (RoutedPhysicalTote routedTote : transportRuntime.activeRoutedTotes()) {
            String physicalToteId = routedTote.physicalToteId().value();
            activeIds.add(physicalToteId);
            if (sensorTriggeredPhysicalToteIds.contains(physicalToteId)) {
                continue;
            }
            dueSimulationTimes.computeIfAbsent(
                    physicalToteId,
                    ignored -> context.getSimulationTimeSeconds()
                            + routeDurationSeconds(routedTote));
        }

        dueSimulationTimes.entrySet().removeIf(entry -> !activeIds.contains(entry.getKey()));
        sensorTriggeredPhysicalToteIds.removeIf(id -> !activeIds.contains(id));

        for (RoutedPhysicalTote routedTote : transportRuntime.activeRoutedTotes()) {
            String physicalToteId = routedTote.physicalToteId().value();
            if (sensorTriggeredPhysicalToteIds.contains(physicalToteId)
                    || context.getSimulationTimeSeconds()
                            < dueSimulationTimes.getOrDefault(physicalToteId, Double.POSITIVE_INFINITY)) {
                continue;
            }
            WarehouseRouteDefinition definition = routeCatalog.find(routedTote.destination())
                    .orElseThrow(() -> new IllegalStateException(
                            "No warehouse route is configured for " + routedTote.destination()));
            routedTote.tote().getRouteFollower().setCurrentSegment(definition.terminalSegment());
            routedTote.tote().getRouteFollower().setDistanceAlongSegment(
                    definition.terminalSegment().length());
            transportRuntime.arrivalController().handleDetection(
                    new DetectionEvent(
                            "dsp-headless-terminal-sensor",
                            context.getSimulationTimeSeconds(),
                            definition.terminalArrivalSensorId(),
                            physicalToteId,
                            DetectionType.ENTER),
                    context);
            sensorTriggeredPhysicalToteIds.add(physicalToteId);
        }
    }

    public Map<String, Double> scheduledArrivalTimes() {
        return Map.copyOf(dueSimulationTimes);
    }

    private double routeDurationSeconds(RoutedPhysicalTote routedTote) {
        WarehouseRouteDefinition definition = routeCatalog.find(routedTote.destination())
                .orElseThrow(() -> new IllegalStateException(
                        "No warehouse route is configured for " + routedTote.destination()));
        double entryRemaining = definition.entrySegment().length() - definition.entryDistance();
        double terminalDistance = definition.terminalSegment().length();
        return (entryRemaining + terminalDistance) / routeSpeedUnitsPerSecond;
    }
}
