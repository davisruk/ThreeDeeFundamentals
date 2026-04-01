package online.davisfamily.threedee.sim.framework;

public record DetectionEvent(
        String sourceId,
        double simulationTimeSeconds,
        String sensorId,
        String objectId,
        DetectionType type
) implements SimulationEvent {
}