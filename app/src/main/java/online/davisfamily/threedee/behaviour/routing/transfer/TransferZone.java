package online.davisfamily.threedee.behaviour.routing.transfer;

import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.sim.framework.objects.sensors.WindowSensorArea;
import online.davisfamily.warehouse.rendering.model.tracks.GuideSide;
import online.davisfamily.warehouse.sim.transfer.strategy.TransferDecisionStrategy;

public class TransferZone {
    private String id;
	private final float startDistance;
    private final float length;
    private final RouteSegment sourceSegment;
    private final RouteSegment targetSegment;
    private final float targetStartDistance;
    private final TransferDecisionStrategy decisionStrategy;
    private final GuideSide sourceOpenSide;
    private final GuideSide targetOpenSide;
    private final float centrePoint;
    
    public TransferZone(
            String id,
    		float startDistance,
            float length,
            RouteSegment sourceSegment,
            RouteSegment targetSegment,
            float targetStartDistance,
            GuideSide sourceOpenSide,
            GuideSide targetOpenSide,
            TransferDecisionStrategy decisionStrategy) {

        if (length <= 0f) {
            throw new IllegalArgumentException("length must be > 0");
        }
        if (targetSegment == null) {
            throw new IllegalArgumentException("targetSegment must not be null");
        }
        if (decisionStrategy == null) {
            throw new IllegalArgumentException("decisionStrategy must not be null");
        }
        if (sourceOpenSide == null) {
            throw new IllegalArgumentException("sourceOpenSide must not be null");
        }
        if (targetOpenSide == null) {
            throw new IllegalArgumentException("targetOpenSide must not be null");
        }
        this.id = id;
        this.startDistance = startDistance;
        this.length = length;
        this.sourceSegment = sourceSegment;
        this.targetSegment = targetSegment;
        this.targetStartDistance = targetStartDistance;
        this.sourceOpenSide = sourceOpenSide;
        this.targetOpenSide = targetOpenSide;
        this.decisionStrategy = decisionStrategy;
        this.centrePoint = startDistance + length * 0.5f;
    }

    
    public float getCentrePoint() {
    	return centrePoint;
    }
    public String getId() {
		return id;
	}


	public float getStartDistance() {
        return startDistance;
    }

    public float getLength() {
        return length;
    }

    public float getEndDistance() {
        return startDistance + length;
    }

    public RouteSegment getTargetSegment() {
        return targetSegment;
    }
    
    public RouteSegment getSourceSegment() {
		return sourceSegment;
	}

	public float getTargetStartDistance() {
        return targetStartDistance;
    }

    public TransferDecisionStrategy getDecisionStrategy() {
        return decisionStrategy;
    }

    public boolean contains(float distanceOnSegment) {
        return distanceOnSegment >= startDistance && distanceOnSegment <= getEndDistance();
    }
    
    public GuideSide getSourceOpenSide() {
        return sourceOpenSide;
    }

    public GuideSide getTargetOpenSide() {
        return targetOpenSide;
    }
    
	public RouteSegment getRouteSegment() {
		return sourceSegment;
	}
}