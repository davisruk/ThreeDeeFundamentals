package online.davisfamily.warehouse.sim.sensor;

import java.util.ArrayList;
import java.util.List;

import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.behaviour.routing.transfer.RouteFollowerSnapshot;
import online.davisfamily.threedee.path.PathSegment3;
import online.davisfamily.threedee.sim.framework.SimulationContext;
import online.davisfamily.threedee.sim.objects.Sensor;

public class ProximitySensor implements Sensor {
	private String id;
	private final RouteSegment segment;
	private final PathSegment3 geometry;
	private final List<String> trackablesOnSegment = new ArrayList<>();
	public ProximitySensor(String id, RouteSegment segment) {
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
				if (snap.currentSegment().getLabel().equals(segment.getLabel())) {
					if (!trackablesOnSegment.contains(snap.followerId())) {
						trackablesOnSegment.add(snap.followerId());
						printTrackablesOnSegment();
					}
				} else if (trackablesOnSegment.contains(snap.followerId())) {
					trackablesOnSegment.remove(snap.followerId());
					printTrackablesOnSegment();
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