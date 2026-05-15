package online.davisfamily.threedee.sim.framework.objects.sensors;

import java.util.HashSet;
import java.util.Set;

import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.behaviour.routing.transfer.RouteFollowerSnapshot;
import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.framework.events.DetectionEvent;
import online.davisfamily.threedee.sim.framework.events.DetectionEvent.DetectionType;
import online.davisfamily.threedee.sim.framework.objects.TrackableObject;

public class WindowSensor implements Sensor {
	
	private final float startDistance;
	private final float endDistance;
	private final String id;
	private final WindowSensorArea area;
	private final Set<String> trackablesInside = new HashSet<>();

	public WindowSensor(String id, WindowSensorArea area) {
		super();
		this.id = id;
		this.startDistance = area.startDistance();
		this.endDistance = area.endDistance();
		this.area = area;
	}

	@Override
	public String getId() {
		return id;
	}

	@Override
	public void update(SimulationContext context, double dtSeconds) {
		for(TrackableObject t: context.getTrackedObjects()) {
			RouteFollowerSnapshot last=t.getLastSnapshot();
			if (last == null) {
				continue;
			}

			String followerId = last.followerId();
			RouteSegment curr = last.currentSegment();
			boolean inside = false;
			if (curr == area.routeSegment()) {
				float distanceAlong = last.distanceAlongSegment();
				inside = distanceAlong >= startDistance && distanceAlong <= endDistance;
			}

			boolean wasInside = trackablesInside.contains(followerId);
			DetectionType detectionType;
			if (inside && !wasInside) {
				trackablesInside.add(followerId);
				detectionType = DetectionType.ENTER;
			} else if (inside) {
				detectionType = DetectionType.PRESENT;
			} else if (wasInside) {
				trackablesInside.remove(followerId);
				detectionType = DetectionType.EXIT;
			} else {
				detectionType = DetectionType.NOT_PRESENT;
			}

			if (detectionType != DetectionType.NOT_PRESENT) {
				context.publish(new DetectionEvent(
						id,
						context.getSimulationTimeSeconds(),
						id,
						followerId,
						detectionType));
			}
		}
	}

}
