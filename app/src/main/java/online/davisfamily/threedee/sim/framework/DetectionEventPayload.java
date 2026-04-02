package online.davisfamily.threedee.sim.framework;

public record DetectionEventPayload(
        String sourceId,
        double simulationTimeSeconds,
        String sensorId,
        String objectId,
        DetectionType type
) implements SimulationEventPayload {
	public enum DetectionType {
		ENTER, EXIT, PRESENT 
	}
}
