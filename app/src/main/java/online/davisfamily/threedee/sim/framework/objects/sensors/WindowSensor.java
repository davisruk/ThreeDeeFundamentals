package online.davisfamily.threedee.sim.framework.objects.sensors;

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
	private boolean inside = false;
	private DetectionType detectionType = DetectionType.NOT_PRESENT;
	
	private final DetectionEvent cachedEvent;
	public WindowSensor(String id, WindowSensorArea area) {
		super();
		this.id = id;
		this.startDistance = area.startDistance();
		this.endDistance = area.endDistance();
		this.area = area;
		cachedEvent = new DetectionEvent();
		cachedEvent.set(id, 0, id, null, DetectionType.NOT_PRESENT);
	}

	@Override
	public String getId() {
		return id;
	}

	@Override
	public void update(SimulationContext context, double dtSeconds) {
		for(TrackableObject t: context.getTrackedObjects()) {
			RouteFollowerSnapshot last=t.getLastSnapshot();
			RouteSegment curr = last.currentSegment();
			if (!(curr == area.routeSegment())) continue;
			float distanceAlong = last.distanceAlongSegment(); 
			if (distanceAlong >= startDistance && distanceAlong <= endDistance) {
				if (inside == false) {
					inside = true;
					detectionType = DetectionType.ENTER;
				} else {
					detectionType = DetectionType.PRESENT;
				}
			} else if (inside == true) {
				inside = false;
				detectionType = DetectionType.EXIT;
			} else {
				detectionType = DetectionType.NOT_PRESENT;
			}
			
			if (detectionType != DetectionType.NOT_PRESENT) {
				cachedEvent.set(
				        context.getSimulationTimeSeconds(),
				        last.followerId(),
				        detectionType);
				context.publish(cachedEvent);
			}
		}
	}

}
