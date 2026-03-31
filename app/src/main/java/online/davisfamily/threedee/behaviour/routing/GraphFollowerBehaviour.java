package online.davisfamily.threedee.behaviour.routing;

import online.davisfamily.threedee.behaviour.Behaviour;
import online.davisfamily.threedee.behaviour.routing.transfer.RouteFollowerSnapshot;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.objects.SimTote;

public class GraphFollowerBehaviour implements Behaviour {

    public enum TravelDirection {
        FORWARD,
        REVERSE
    }
    
    private final SimTote tote;
    private final SimulationContext context;
	


	public GraphFollowerBehaviour(SimTote tote, SimulationContext context) {
		super();
		this.tote = tote;
		this.context = context;
	}



	@Override
	public void update(RenderableObject ro, double dtSeconds) {
		tote.update(context, dtSeconds);
	}
}