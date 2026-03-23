package online.davisfamily.threedee.behaviour.routing;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import online.davisfamily.threedee.behaviour.Behaviour;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.path.PathSegment3;
import online.davisfamily.threedee.rendering.RenderableObject;

/** follows one route segment at a time
 ** keeps local distance within the segment
 ** asks the provider when a branch is reached
 **/
public class GraphFollowerBehaviour implements Behaviour {

    public enum TravelDirection {
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
    private TravelDirection travelDirection;
    private final float startDistanceAlongSegment;
    private final TravelDirection startDirection;
    private final WrapMode wrapMode;

    public GraphFollowerBehaviour(
            RouteSegment startSegment,
            RouteDecisionProvider defaultDecisionProvider,
            float unitsPerSecond,
            WrapMode wrapMode,
            EnumSet<OrientationMode> orientationModes,
            float yawOffsetRadians) {

        this(startSegment,
                defaultDecisionProvider,
                unitsPerSecond,
                wrapMode,
                orientationModes,
                yawOffsetRadians,
                0f,
                TravelDirection.FORWARD);
    }

    public GraphFollowerBehaviour(
            RouteSegment startSegment,
            RouteDecisionProvider defaultDecisionProvider,
            float unitsPerSecond,
            WrapMode wrapMode,
            EnumSet<OrientationMode> orientationModes,
            float yawOffsetRadians,
            float startDistanceAlongSegment,
            TravelDirection startDirection) {

        if (startSegment == null) {
            throw new IllegalArgumentException("Start RouteSegment must not be null");
        }

        this.startSegment = startSegment;
        this.startDistanceAlongSegment = startDistanceAlongSegment;
        this.startDirection = startDirection;

        this.currentSegment = startSegment;
        this.distanceAlongCurrentSegment = startDistanceAlongSegment;
        this.travelDirection = startDirection;

        this.decisionProvider = defaultDecisionProvider;
        this.unitsPerSecond = unitsPerSecond;
        this.wrapMode = wrapMode;
        this.orientationModes = orientationModes;
        this.yawOffsetRadians = yawOffsetRadians;
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
            float segmentLength = geometry.getTotalLength();

            float distanceToBoundary = (travelDirection == TravelDirection.FORWARD)
                    ? (segmentLength - distanceAlongCurrentSegment)
                    : distanceAlongCurrentSegment;

            if (remainingDistance < distanceToBoundary) {
                if (travelDirection == TravelDirection.FORWARD) {
                    distanceAlongCurrentSegment += remainingDistance;
                } else {
                    distanceAlongCurrentSegment -= remainingDistance;
                }
                remainingDistance = 0f;
            } else {
                if (travelDirection == TravelDirection.FORWARD) {
                    distanceAlongCurrentSegment = segmentLength;
                } else {
                    distanceAlongCurrentSegment = 0f;
                }

                remainingDistance -= distanceToBoundary;

                RouteSegment adjacent = chooseAdjacentSegment(object);

                if (adjacent != null) {
                    currentSegment = adjacent;
                    distanceAlongCurrentSegment = (travelDirection == TravelDirection.FORWARD)
                            ? 0f
                            : currentSegment.getGeometry().getTotalLength();
                } else {
                    handleNoAdjacentSegment();
                    if (wrapMode == WrapMode.CLAMP) {
                        remainingDistance = 0f;
                    }
                }
            }
        }

        applyTransform(object);
    }

    private RouteSegment chooseAdjacentSegment(RenderableObject object) {
        List<RouteSegment> options = (travelDirection == TravelDirection.FORWARD)
                ? currentSegment.getNextSegments()
                : currentSegment.getPreviousSegments();

        if (options.isEmpty()) {
            return null;
        }

        if (options.size() == 1) {
            return options.get(0);
        }

        RouteDecisionProvider provider = currentSegment.getDecisionProvider();
        if (provider == null) {
            provider = decisionProvider;
        }

        if (provider == null) {
            return options.get(0);
        }

        RouteSegment chosen = provider.chooseNext(
                object,
                currentSegment,
                options,
                travelDirection
        );

        if (chosen == null || !options.contains(chosen)) {
            return options.get(0);
        }

        return chosen;
    }

    private void handleNoAdjacentSegment() {
        switch (wrapMode) {
            case CLAMP -> {
                // remain at the boundary
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
        travelDirection = (travelDirection == TravelDirection.FORWARD)
                ? TravelDirection.REVERSE
                : TravelDirection.FORWARD;
    }

    private void applyTransform(RenderableObject ro) {
		if (currentSegment == null) return;
		
		PathSegment3 ps = currentSegment.getGeometry();
		Vec3 pos = ps.sampleByDistance(distanceAlongCurrentSegment);
		ro.transformation.setTranslation(pos);
		if (orientationModes.contains(OrientationMode.NONE)) return;
		
		Vec3 tangent = ps.sampleTangentByDistance(distanceAlongCurrentSegment);
		if (travelDirection == TravelDirection.REVERSE) {
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
	}

    public List<RouteSegment> getReachableSegments() {
        List<RouteSegment> ordered = new ArrayList<>();
        Set<RouteSegment> visited = new HashSet<>();
        traverseGraph(startSegment, visited, ordered);
        return ordered;
    }

    private void traverseGraph(RouteSegment segment, Set<RouteSegment> visited, List<RouteSegment> ordered) {
        if (segment == null || !visited.add(segment)) {
            return;
        }

        ordered.add(segment);

        for (RouteSegment next : segment.getNextSegments()) {
            traverseGraph(next, visited, ordered);
        }

        for (RouteSegment previous : segment.getPreviousSegments()) {
            traverseGraph(previous, visited, ordered);
        }
    }

    public String describeGraph() {
        StringBuilder sb = new StringBuilder();
        sb.append("GraphPathFollowerBehaviour graph")
          .append(System.lineSeparator());

        List<RouteSegment> segments = getReachableSegments();

        for (RouteSegment segment : segments) {
            sb.append("segment=").append(segment.getLabel());

            if (segment == currentSegment) {
                sb.append(" [CURRENT]");
            }
            if (segment == startSegment) {
                sb.append(" [START]");
            }

            sb.append(System.lineSeparator());
            sb.append("  previous=").append(formatSegmentIds(segment.getPreviousSegments()))
              .append(System.lineSeparator());
            sb.append("  next=").append(formatSegmentIds(segment.getNextSegments()))
              .append(System.lineSeparator());
        }

        return sb.toString();
    }

    private String formatSegmentIds(List<RouteSegment> segments) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < segments.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(segments.get(i).getLabel());
        }
        sb.append("]");
        return sb.toString();
    }

    @Override
    public String toString() {
        return describeGraph();
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

	public TravelDirection getTravelDirection() {
		return travelDirection;
	}

	public void setTravelDirection(TravelDirection travelDirection) {
		this.travelDirection = travelDirection;
	}

	public RouteSegment getStartSegment() {
		return startSegment;
	}

}
