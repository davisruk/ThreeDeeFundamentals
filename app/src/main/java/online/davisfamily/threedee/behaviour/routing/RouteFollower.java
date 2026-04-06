package online.davisfamily.threedee.behaviour.routing;

import java.util.List;

import online.davisfamily.threedee.behaviour.routing.transfer.RouteFollowerSnapshot;
import online.davisfamily.threedee.path.BezierSegment3;
import online.davisfamily.threedee.path.PathSegment3;

public class RouteFollower {
	private RouteSegment currentSegment;
	private float distanceAlongSegment;
	private double speedUnitsPerSecond;
	private TravelDirection travelDirection;
	private String owningId;
	
	public enum TravelDirection {
		FORWARD, REVERSE
	}
	public RouteFollower(String owningId, RouteSegment routeSegment, float distanceAlongSegment, double speedUnitsPerSecond) {
		super();
		this.currentSegment = routeSegment;
		this.speedUnitsPerSecond = speedUnitsPerSecond;
		this.distanceAlongSegment = distanceAlongSegment;
		this.travelDirection = TravelDirection.FORWARD;
		this.owningId = owningId;
	}

	public RouteFollowerSnapshot advance(double dtSeconds, boolean blocked) {
		if (!blocked) {
			advanceDistance(dtSeconds);
		}
		return buildSnapshot();
	}
	
	private void advanceDistance(double dtSeconds) {
		double distanceToMove = speedUnitsPerSecond * dtSeconds;
		float segmentLength = currentSegment.length();
		while (distanceToMove > 0 && currentSegment != null) {
			double remaining = segmentLength - distanceAlongSegment;
			if (distanceToMove < remaining) {
				distanceAlongSegment += distanceToMove;
				break;
			}
			
			distanceAlongSegment = segmentLength;
			distanceToMove -= remaining;
			RouteConnection next = chooseAdjacentConnection(currentSegment);
			if (next == null) {
				break;
			}
			currentSegment = next.getSegment();
			distanceAlongSegment = 0f;
		}
	}
	
    private RouteConnection chooseAdjacentConnection(RouteSegment segment) {
        List<RouteConnection> candidates = (travelDirection == TravelDirection.FORWARD)
                ? segment.getNextConnections()
                : segment.getPreviousConnections();

        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        return candidates.get(0);
    }
    
	public RouteFollowerSnapshot buildSnapshot() {
		PathSegment3 geometry = currentSegment.getGeometry(); 
		var pos = geometry.sampleByDistance(distanceAlongSegment);
		var forward = geometry.sampleOrientationDirectionByDistance(distanceAlongSegment);
		var up = geometry.sampleTangentByDistance(distanceAlongSegment);

		if (geometry instanceof BezierSegment3) {
	        if (travelDirection == TravelDirection.REVERSE) {
	            forward = forward.scale(-1f);
	        }
	    }
		
		if (travelDirection == TravelDirection.REVERSE) {
            up = up.scale(-1f);
        }

        float length = currentSegment.length();
		float remaining = length - distanceAlongSegment;
		return new RouteFollowerSnapshot(
				owningId,
				currentSegment,
				distanceAlongSegment,
				pos,
				forward,
				up,
				length,
				remaining
		);
	}
	
	public double getSpeedUnitsPerSecond() {
		return speedUnitsPerSecond;
	}

	public void setSpeedUnitsPerSecond(double speedUnitsPerSecond) {
		this.speedUnitsPerSecond = speedUnitsPerSecond;
	}

	public RouteSegment getCurrentSegment() {
		return currentSegment;
	}

	public double getDistanceAlongSegment() {
		return distanceAlongSegment;
	}

	public TravelDirection getTravelDirection() {
		return travelDirection;
	}

	public void setTravelDirection(TravelDirection travelDirection) {
		this.travelDirection = travelDirection;
	}

	public void setCurrentSegment(RouteSegment currentSegment) {
		this.currentSegment = currentSegment;
	}

	public void setDistanceAlongSegment(float distanceAlongSegment) {
		this.distanceAlongSegment = distanceAlongSegment;
	}
	

}
