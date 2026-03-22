package online.davisfamily.threedee.behaviour.routing;

import java.util.EnumSet;
import java.util.List;

import online.davisfamily.threedee.behaviour.Behaviour;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.path.PathSegment3;
import online.davisfamily.threedee.rendering.RenderableObject;

/** follows one route segment at a time
 ** keeps local distance within the segment
 ** asks the provider when a branch is reached
 **/
public class GraphFollowerBehaviour implements Behaviour {

    public enum DirectionOfTravel {
        FORWARD,
        REVERSE
    }
    
    private final EnumSet<OrientationMode> orientationModes;
    private final RouteDecisionProvider decisionProvider;
	private final float unitsPerSecond;
    private final float yawOffsetRadians;
    private final RouteSegment startSegment;
    private RouteSegment currentSegment;
    private float distanceAlongCurrentSegment;
    private DirectionOfTravel travelDirection;
    private final float startDistanceAlongSegment;
    private final DirectionOfTravel startDirection;

    private final WrapMode wrapMode;
    public GraphFollowerBehaviour(RouteSegment startSegment,
            RouteDecisionProvider decisionProvider,
            float unitsPerSecond,
            EnumSet<OrientationMode> orientationModes,
            float yawOffsetRadians,
            DirectionOfTravel travelDirection,
            WrapMode wrapMode,
            float startDistanceAlongSegment) {
		super();
        if (startSegment == null) {
            throw new IllegalArgumentException("Start RouteSegment must not be null");
        }
        if (decisionProvider == null) {
            throw new IllegalArgumentException("RouteDecisionProvider must not be null");
        }
		this.orientationModes = orientationModes;
		this.decisionProvider = decisionProvider;
		this.unitsPerSecond = unitsPerSecond;
		this.yawOffsetRadians = yawOffsetRadians;
		this.travelDirection = travelDirection;
		this.currentSegment = startSegment;
		this.startSegment = startSegment;
		this.distanceAlongCurrentSegment = 0f;
		this.wrapMode = wrapMode;
		this.startDistanceAlongSegment = startDistanceAlongSegment;
		this.startDirection = travelDirection;
	}

	@Override
	public void update(RenderableObject object, double dtSeconds) {
        if (currentSegment == null) {
            return;
        }

        if (unitsPerSecond <= 0f || dtSeconds <= 0d) {
            applyTransform(object);
            return;
        }

        float remainingDistance = unitsPerSecond * (float) dtSeconds;

        while (remainingDistance > 0f && currentSegment != null) {
            PathSegment3 geometry = currentSegment.getGeometry();
            float segmentLength = geometry.getLength();

            float distanceToBoundary = (travelDirection == DirectionOfTravel.FORWARD)
                    ? (segmentLength - distanceAlongCurrentSegment)
                    : distanceAlongCurrentSegment;

            if (remainingDistance < distanceToBoundary) {
                if (travelDirection == DirectionOfTravel.FORWARD) {
                    distanceAlongCurrentSegment += remainingDistance;
                } else {
                    distanceAlongCurrentSegment -= remainingDistance;
                }
                remainingDistance = 0f;
            } else {
                // Move exactly to the boundary
                if (travelDirection == DirectionOfTravel.FORWARD) {
                    distanceAlongCurrentSegment = segmentLength;
                } else {
                    distanceAlongCurrentSegment = 0f;
                }

                remainingDistance -= distanceToBoundary;

                RouteSegment nextSegment = chooseAdjacentSegment(object);

                if (nextSegment != null) {
                    currentSegment = nextSegment;
                    distanceAlongCurrentSegment = (travelDirection == DirectionOfTravel.FORWARD)
                            ? 0f
                            : currentSegment.getGeometry().getLength();
                } else {
                    handleNoAdjacentSegment();
                    // if STOP, current segment remains at boundary and we stop consuming
                    // if LOOP or PING_PONG, continue with the updated state
                    if (wrapMode == WrapMode.CLAMP) {
                        remainingDistance = 0f;
                    }
                }
            }
        }

        applyTransform(object);        
	}

    private RouteSegment chooseAdjacentSegment(RenderableObject object) {
        List<RouteSegment> options = (travelDirection == DirectionOfTravel.FORWARD)
                ? currentSegment.getNextSegments()
                : currentSegment.getPreviousSegments();

        if (options.isEmpty()) {
            return null;
        }

        if (options.size() == 1) {
            return options.get(0);
        }

        RouteSegment chosen = decisionProvider.chooseNext(object, currentSegment, options, travelDirection);

        if (chosen == null || !options.contains(chosen)) {
            return options.get(0);
        }

        return chosen;
    }

    private void handleNoAdjacentSegment() {
        switch (wrapMode) {
            case CLAMP -> {
                // stay at current boundary
            }
            case LOOP -> resetToStart();
            case PING_PONG -> reverseDirectionAtBoundary();
        }
    }

    private void resetToStart() {
        currentSegment = startSegment;
        distanceAlongCurrentSegment = startDistanceAlongSegment;
        travelDirection = startDirection;
    }

    private void reverseDirectionAtBoundary() {
        travelDirection = (travelDirection == DirectionOfTravel.FORWARD)
                ? DirectionOfTravel.REVERSE
                : DirectionOfTravel.FORWARD;
    }
    
	private void applyTransform(RenderableObject ro) {
		if (currentSegment == null) return;
		
		PathSegment3 ps = currentSegment.getGeometry();
		Vec3 pos = ps.sampleByDistance(distanceAlongCurrentSegment);
		ro.transformation.setTranslation(pos);
		if (orientationModes.contains(OrientationMode.NONE)) return;
		
		Vec3 tangent = ps.sampleTangentByDistance(distanceAlongCurrentSegment);
		if (travelDirection == DirectionOfTravel.REVERSE) {
            tangent = tangent.scale(-1f);
        }
		float yaw = Vec3.yawFromDirection(tangent);
		
		if (orientationModes.contains(OrientationMode.YAW)) {
            ro.transformation.angleY = yaw + yawOffsetRadians;
            ro.transformation.angleX = 0f;
		}
		if (orientationModes.contains(OrientationMode.PITCH)) {
            float pitch = tangent.pitch();
            ro.transformation.angleX = -pitch;
        }

        if (currentSegment == null) {
            return;
        }
	}

	public RouteDecisionProvider getDecisionProvider() {
		return decisionProvider;
	}

	public RouteSegment getCurrentSegment() {
		return currentSegment;
	}

	public float getDistanceAlongCurrentSegment() {
		return distanceAlongCurrentSegment;
	}

	public DirectionOfTravel getTravelDirection() {
		return travelDirection;
	}

	public void setTravelDirection(DirectionOfTravel travelDirection) {
		this.travelDirection = travelDirection;
	}

	public RouteSegment getStartSegment() {
		return startSegment;
	}

}
