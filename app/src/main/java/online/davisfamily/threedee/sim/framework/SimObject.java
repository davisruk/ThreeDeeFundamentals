package online.davisfamily.threedee.sim.framework;

public interface SimObject {
	String getId();
	void update(SimulationContext context, double dtSeconds);
}
