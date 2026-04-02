package online.davisfamily.threedee.sim.framework.objects;

import online.davisfamily.threedee.sim.framework.SimulationContext;

public interface SimObject {
	String getId();
	void update(SimulationContext context, double dtSeconds);
}
