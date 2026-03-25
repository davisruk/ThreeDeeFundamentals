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
	private RouteDecisionProvider decisionProvider;
	
    private final List<TransferZone> transferZones = new ArrayList<>();


	public RouteSegment(int id, String label, PathSegment3 geometry) {
		this.id = id;
		this.label = label;
		this.geometry = geometry;
	}

	public List<RouteSegment> getNextSegments() {
		return nextSegments;
	}
	
	public List<RouteSegment> getPreviousSegments() {
		return previousSegments;
	}

	public RouteSegment addNext(RouteSegment next) {
	    if (next == null) {
	        throw new IllegalArgumentException("Next RouteSegment must not be null");
	    }

	    if (!nextSegments.contains(next)) {
	        nextSegments.add(next);
	    }

	    if (!next.previousSegments.contains(this)) {
	        next.previousSegments.add(this);
	    }

	    return this;
	}
	
    public List<TransferZone> getTransferZones() {
        return transferZones;
    }
    
    public void addTransferZone(TransferZone tz) {
    	transferZones.add(tz);
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
	public RouteDecisionProvider getDecisionProvider() {
		return decisionProvider;
	}

	public void setDecisionProvider(RouteDecisionProvider decisionProvider) {
		this.decisionProvider = decisionProvider;
	}
    @Override
    public String toString() {
        return "RouteSegment{id='" + id + "', label='" + label + "'}";
    }	
}
