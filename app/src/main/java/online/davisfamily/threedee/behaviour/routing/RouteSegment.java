package online.davisfamily.threedee.behaviour.routing;

import java.util.ArrayList;
import java.util.List;

import online.davisfamily.threedee.path.PathSegment3;

public class RouteSegment {
	private final int id;
	private final String label;
	private final List<RouteSegment> nextSegments = new ArrayList<>();
	private final List<RouteSegment> previousSegments = new ArrayList<>();
	private final PathSegment3 geometry;
	
	public RouteSegment(int id, String label, PathSegment3 geometry) {
		this.id = id;
		this.label = label;
		this.geometry = geometry;
	}

	public List<RouteSegment> getNextSegments() {
		return nextSegments;
	}
	public List<RouteSegment> getPreviousSegments() {
		return nextSegments;
	}
	public RouteSegment addNext(RouteSegment next) {
		if (!this.nextSegments.contains(next)) this.nextSegments.add(next);
		if (!next.previousSegments.contains(this)) next.previousSegments.add(this);
		return this;
	}

	public int getId() {
		return id;
	}

	public String getLabel() {
		return label;
	}

	public PathSegment3 getGeometry() {
		return geometry;
	}
}
