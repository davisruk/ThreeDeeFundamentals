package online.davisfamily.warehouse.sim.sensor;

import java.util.HashSet;
import java.util.Set;

import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.behaviour.routing.transfer.RouteFollowerSnapshot;
import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.events.DetectionEvent;
import online.davisfamily.threedee.sim.framework.events.DetectionEvent.DetectionType;
import online.davisfamily.threedee.sim.framework.objects.sensors.Sensor;
import online.davisfamily.threedee.sim.framework.objects.sensors.WindowSensorArea;

public class MembershipSensor implements Sensor {
	private String id;
	private final RouteSegment segment;
	private final Set<String> trackablesOnSegment = new HashSet<>();
	private final DetectionEvent cachedEvent;
	private final WindowSensorArea area;
	public MembershipSensor(String id, WindowSensorArea area) {
		super();
		this.id = id;
		this.segment = area.routeSegment();
		this.area = area;
		cachedEvent = new DetectionEvent();
		cachedEvent.set(id, 0, id, null, DetectionType.ENTER);
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
					float distance = snap.distanceAlongSegment();
					if (distance >= area.startDistance() && distance <= area.endDistance()) {
						if (!trackablesOnSegment.contains(snap.followerId())) {
							trackablesOnSegment.add(snap.followerId());
							cachedEvent.set(
						        context.getSimulationTimeSeconds(),
						        snap.followerId(),
						        DetectionType.ENTER
							);
							context.publish(cachedEvent);
						} else {
							cachedEvent.set(
						        context.getSimulationTimeSeconds(),
						        snap.followerId(),
						        DetectionType.PRESENT);
							context.publish(cachedEvent);
						}
					}
				} else if (trackablesOnSegment.contains(snap.followerId())) {
					trackablesOnSegment.remove(snap.followerId());
					cachedEvent.set(
				        context.getSimulationTimeSeconds(),
				        snap.followerId(),
				        DetectionType.EXIT
				    );
					context.publish(cachedEvent);
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