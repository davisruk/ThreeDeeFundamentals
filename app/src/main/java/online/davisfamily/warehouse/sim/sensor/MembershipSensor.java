package online.davisfamily.warehouse.sim.sensor;

import java.util.HashSet;
import java.util.Set;

import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.behaviour.routing.transfer.RouteFollowerSnapshot;
import online.davisfamily.threedee.path.PathSegment3;
import online.davisfamily.threedee.sim.framework.DetectionEvent;
import online.davisfamily.threedee.sim.framework.DetectionType;
import online.davisfamily.threedee.sim.framework.Sensor;
import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.SimulationEvent;

public class MembershipSensor implements Sensor {
	private String id;
	private final RouteSegment segment;
	private final PathSegment3 geometry;
	private final Set<String> trackablesOnSegment = new HashSet<>();
	
	public MembershipSensor(String id, RouteSegment segment) {
		super();
		this.id = id;
		this.segment = segment;
		this.geometry = segment.getGeometry();
	}
	
	@Override
	public String getId() {
		return id;
	}

	@Override
	public void update(SimulationContext context, double dtSeconds) {
		// update this to detect approaching, inside, leaving, left
		context.getTrackedObjects().stream().forEach(t -> {
			RouteFollowerSnapshot snap = t.getLastSnapshot();
			if (snap != null) {
				if (snap.currentSegment() == segment) {
					if (!trackablesOnSegment.contains(snap.followerId())) {
						trackablesOnSegment.add(snap.followerId());
						context.publish(new DetectionEvent(
						        getId(),
						        context.getSimulationTimeSeconds(),
						        getId(),
						        snap.followerId(),
						        DetectionType.ENTER
						));
					} else {
						context.publish(new DetectionEvent(
						        getId(),
						        context.getSimulationTimeSeconds(),
						        getId(),
						        snap.followerId(),
						        DetectionType.PRESENT));
					}
					
				} else if (trackablesOnSegment.contains(snap.followerId())) {
					trackablesOnSegment.remove(snap.followerId());
					context.publish(new DetectionEvent(
					        getId(),
					        context.getSimulationTimeSeconds(),
					        getId(),
					        snap.followerId(),
					        DetectionType.EXIT
					));

				}
			}
		});
	}
	
	private void printTrackablesOnSegment() {
		StringBuffer buff = new StringBuffer("Trackables on segment: ");
		if (trackablesOnSegment.size() == 0) {
			buff.append("None");
		} else {
			for(String s:trackablesOnSegment)
				buff.append(s + " ");
		}
		System.out.println(buff);
	}
}