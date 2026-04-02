package online.davisfamily.threedee.sim.framework;

public interface SimulationEventListener<S extends SimulationEvent<?>> {
	void handleEvent(S event, SimulationContext context);
}
