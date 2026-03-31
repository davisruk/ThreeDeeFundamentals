package online.davisfamily.threedee.sim.framework;

public interface SimulationController {
	void handleEvent(SimulationEvent event, SimulationContext context);
}
