package online.davisfamily.threedee.sim.framework.events;

import online.davisfamily.threedee.sim.framework.SimulationContext;

public interface SimulationEventListener<S extends SimulationEvent> {
	void handleEvent(S event, SimulationContext context);
}
