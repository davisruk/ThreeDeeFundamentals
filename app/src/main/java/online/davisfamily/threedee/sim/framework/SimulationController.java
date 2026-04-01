package online.davisfamily.threedee.sim.framework;

public interface SimulationController {
	void update(SimulationContext context, double dtSeconds);
	void handleEvent(SimulationEvent event, SimulationContext context);
}
