package online.davisfamily.threedee.sim.framework;

import online.davisfamily.threedee.behaviour.routing.transfer.RouteFollowerSnapshot;

public interface TrackableObject extends SimObject {
	public RouteFollowerSnapshot getLastSnapshot();
}
