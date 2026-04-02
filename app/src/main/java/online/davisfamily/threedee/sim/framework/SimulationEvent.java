package online.davisfamily.threedee.sim.framework;

public interface SimulationEvent<P extends SimulationEventPayload> {
		P getPayload();
}
