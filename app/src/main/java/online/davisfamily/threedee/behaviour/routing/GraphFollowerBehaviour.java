package online.davisfamily.threedee.behaviour.routing;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import online.davisfamily.threedee.behaviour.Behaviour;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.path.BezierSegment3;
import online.davisfamily.threedee.path.PathSegment3;
import online.davisfamily.threedee.rendering.RenderableObject;

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
    private final float yOffset;

    /**
     * Length of the travelling object used to determine when its rear edge reaches
     * the end of the transfer zone.
     */
    private final float transferObjectLength;

    private Vec3 transferSourceAnchor;
    private Vec3 transferTargetAnchor;
    private float lateralTransferProgress;
    private float lateralTransferDistance;
    private boolean lateralTransferInProgress;

    private TransferZone activeTransferZone;
    private boolean transferCommitted;
    private TransferZone lastDeclinedTransferZone;

    // Yaw state
    private float currentYawRadians;
    private boolean yawInitialised;
    private Float frozenTransferYawRadians;

    public GraphFollowerBehaviour(
            RouteSegment startSegment,
            RouteDecisionProvider defaultDecisionProvider,
            float unitsPerSecond,
            WrapMode wrapMode,
            EnumSet<OrientationMode> orientationModes,
            float yawOffsetRadians,
            float yOffset) {

        this(
                startSegment,
                defaultDecisionProvider,
                unitsPerSecond,
                wrapMode,
                orientationModes,
                yawOffsetRadians,
                yOffset,
                0f,
                TravelDirection.FORWARD,
                0f
        );
    }

    public GraphFollowerBehaviour(
            RouteSegment startSegment,
            RouteDecisionProvider defaultDecisionProvider,
            float unitsPerSecond,
            WrapMode wrapMode,
            EnumSet<OrientationMode> orientationModes,
            float yawOffsetRadians,
            float yOffset,
            float startDistanceAlongSegment,
            TravelDirection startDirection) {

        this(
                startSegment,
                defaultDecisionProvider,
                unitsPerSecond,
                wrapMode,
                orientationModes,
                yawOffsetRadians,
                yOffset,
                startDistanceAlongSegment,
                startDirection,
                0f
        );
    }

    public GraphFollowerBehaviour(
            RouteSegment startSegment,
            RouteDecisionProvider defaultDecisionProvider,
            float unitsPerSecond,
            WrapMode wrapMode,
            EnumSet<OrientationMode> orientationModes,
            float yawOffsetRadians,
            float yOffset,
            float startDistanceAlongSegment,
            TravelDirection startDirection,
            float transferObjectLength) {

        if (startSegment == null) {
            throw new IllegalArgumentException("Start RouteSegment must not be null");
        }

        this.yOffset = yOffset;
        this.startSegment = startSegment;
        this.startDistanceAlongSegment = startDistanceAlongSegment;
        this.startDirection = startDirection;
        this.transferObjectLength = Math.max(0f, transferObjectLength);

        this.currentSegment = startSegment;
        this.distanceAlongCurrentSegment = startDistanceAlongSegment;
        this.travelDirection = startDirection;

        this.decisionProvider = defaultDecisionProvider;
        this.unitsPerSecond = unitsPerSecond;
        this.wrapMode = wrapMode;
        this.orientationModes = orientationModes;
        this.yawOffsetRadians = yawOffsetRadians;

        initialiseYaw();
    }

    @Override
    public void update(RenderableObject object, double dtSeconds) {
        if (currentSegment == null) {
            return;
        }

        if (unitsPerSecond <= 0f || dtSeconds <= 0d) {
            object.transformation.setTranslation(computeDisplayPosition());
            applyOrientation(object);
            return;
        }

        float remainingDistance = unitsPerSecond * (float) dtSeconds;

        while (remainingDistance > 0f && currentSegment != null) {

            // If we are in lateral transfer, consume distance only into the lateral motion.
            if (lateralTransferInProgress) {
                float remainingLateralDistance = (1f - lateralTransferProgress) * lateralTransferDistance;
                float lateralStep = Math.min(remainingDistance, remainingLateralDistance);

                if (lateralTransferDistance > 0f) {
                    lateralTransferProgress += lateralStep / lateralTransferDistance;
                } else {
                    lateralTransferProgress = 1f;
                }

                lateralTransferProgress = clamp(lateralTransferProgress, 0f, 1f);
                remainingDistance -= lateralStep;

                updateYawForCurrentState();

                if (maybeCompleteTransfer()) {
                    continue;
                }

                if (lateralStep == 0f) {
                    remainingDistance = 0f;
                }

                continue;
            }

            maybeStartTransfer(object);

            PathSegment3 geometry = currentSegment.getGeometry();
            float segmentLength = geometry.getTotalLength();

            float distanceToBoundary = (travelDirection == TravelDirection.FORWARD)
                    ? (segmentLength - distanceAlongCurrentSegment)
                    : distanceAlongCurrentSegment;

            float distanceToLateralStart = Float.POSITIVE_INFINITY;
            if (shouldUseTwoPhaseTransfer()) {
                float triggerDistance = getLateralTransferTriggerDistance(activeTransferZone);
                distanceToLateralStart = triggerDistance - distanceAlongCurrentSegment;
            }

            float stepDistance = Math.min(remainingDistance, Math.min(distanceToBoundary, distanceToLateralStart));
            if (stepDistance < 0f) {
                stepDistance = 0f;
            }

            if (travelDirection == TravelDirection.FORWARD) {
                distanceAlongCurrentSegment += stepDistance;
            } else {
                distanceAlongCurrentSegment -= stepDistance;
            }

            remainingDistance -= stepDistance;

            updateYawForCurrentState();

            if (shouldUseTwoPhaseTransfer() && shouldStartLateralTransferNow()) {
                beginLateralTransfer();
                continue;
            }

            geometry = currentSegment.getGeometry();
            segmentLength = geometry.getTotalLength();

            boolean atBoundary =
                    (travelDirection == TravelDirection.FORWARD && distanceAlongCurrentSegment >= segmentLength)
                 || (travelDirection == TravelDirection.REVERSE && distanceAlongCurrentSegment <= 0f);

            if (atBoundary) {
                if (travelDirection == TravelDirection.FORWARD) {
                    distanceAlongCurrentSegment = segmentLength;
                } else {
                    distanceAlongCurrentSegment = 0f;
                }

                RouteConnection connection = chooseAdjacentConnection(object);

                if (connection != null) {
                    currentSegment = connection.getSegment();
                    distanceAlongCurrentSegment = connection.getEntryDistance();

                    clearTransferState();
                    onEnterSegment(currentSegment);
                } else {
                    handleNoAdjacentSegment();
                    if (wrapMode == WrapMode.CLAMP) {
                        remainingDistance = 0f;
                    }
                }
            } else if (stepDistance == 0f) {
                remainingDistance = 0f;
            }
        }

        object.transformation.setTranslation(computeDisplayPosition());
        applyOrientation(object);
    }

    private void initialiseYaw() {
        if (yawInitialised || currentSegment == null) {
            return;
        }

        PathSegment3 ps = currentSegment.getGeometry();
        Vec3 dir = ps.sampleOrientationDirectionByDistance(distanceAlongCurrentSegment);
        currentYawRadians = Vec3.yawFromDirection(dir) + yawOffsetRadians;
        yawInitialised = true;
    }

    private void onEnterSegment(RouteSegment segment) {
        if (segment == null) {
            return;
        }

        PathSegment3 ps = segment.getGeometry();

        if (ps instanceof BezierSegment3) {
            Vec3 dir = ps.sampleOrientationDirectionByDistance(distanceAlongCurrentSegment);
            currentYawRadians = Vec3.yawFromDirection(dir) + yawOffsetRadians;
        }
        // LinearSegment3 and other non-bezier segments preserve currentYawRadians
    }

    private void updateYawForCurrentState() {
        if (!yawInitialised || currentSegment == null) {
            initialiseYaw();
        }

        if ((transferCommitted && activeTransferZone != null) || lateralTransferInProgress) {
            if (frozenTransferYawRadians == null) {
                frozenTransferYawRadians = currentYawRadians;
            }
            return;
        }

        PathSegment3 ps = currentSegment.getGeometry();

        if (ps instanceof BezierSegment3) {
            Vec3 dir = ps.sampleOrientationDirectionByDistance(distanceAlongCurrentSegment);
            if (travelDirection == TravelDirection.REVERSE) {
                dir = dir.scale(-1f);
            }
            currentYawRadians = Vec3.yawFromDirection(dir) + yawOffsetRadians;
        }
        // LinearSegment3 preserves yaw
    }

    private void maybeStartTransfer(RenderableObject object) {
        if (activeTransferZone != null) {
            return;
        }

        TransferZone currentZone = findContainingTransferZone(currentSegment, distanceAlongCurrentSegment);

        if (currentZone != null && currentZone != lastDeclinedTransferZone) {
            boolean shouldTransfer = currentZone.getDecisionStrategy().shouldTransfer(
                    currentSegment,
                    currentZone,
                    object
            );

            if (shouldTransfer) {
                activeTransferZone = currentZone;
                transferCommitted = true;
                lastDeclinedTransferZone = null;
                frozenTransferYawRadians = currentYawRadians;
            } else {
                lastDeclinedTransferZone = currentZone;
            }
        }

        if (currentZone == null && activeTransferZone == null) {
            lastDeclinedTransferZone = null;
        }
    }

    private boolean shouldUseTwoPhaseTransfer() {
        return transferCommitted
                && activeTransferZone != null
                && travelDirection == TravelDirection.FORWARD
                && !lateralTransferInProgress;
    }

    private float getLateralTransferTriggerDistance(TransferZone zone) {
        // Trigger when the rear edge of the object reaches the end of the zone.
        // If object length is zero, this degenerates to the zone end.
        float halfObjectLength = transferObjectLength * 0.5f;
        return zone.getEndDistance() - halfObjectLength;
    }

    private boolean shouldStartLateralTransferNow() {
        if (!shouldUseTwoPhaseTransfer()) {
            return false;
        }
        return distanceAlongCurrentSegment >= getLateralTransferTriggerDistance(activeTransferZone);
    }

    private void beginLateralTransfer() {
        lateralTransferInProgress = true;
        lateralTransferProgress = 0f;

        Vec3 sourcePos = currentSegment.getGeometry().sampleByDistance(distanceAlongCurrentSegment);
        Vec3 targetPos = activeTransferZone.getTargetSegment()
                .getGeometry()
                .sampleByDistance(activeTransferZone.getTargetStartDistance());

        transferSourceAnchor = sourcePos;
        transferTargetAnchor = targetPos;
        lateralTransferDistance = sourcePos.distanceTo(targetPos);

        if (frozenTransferYawRadians == null) {
            frozenTransferYawRadians = currentYawRadians;
        }
    }

    private boolean maybeCompleteTransfer() {
        if (!lateralTransferInProgress || activeTransferZone == null) {
            return false;
        }

        if (lateralTransferProgress >= 1f) {
            currentSegment = activeTransferZone.getTargetSegment();
            distanceAlongCurrentSegment = activeTransferZone.getTargetStartDistance();

            if (frozenTransferYawRadians != null) {
                currentYawRadians = frozenTransferYawRadians;
            }

            clearTransferState();
            onEnterSegment(currentSegment);
            return true;
        }

        return false;
    }

    private Vec3 computeDisplayPosition() {
        if (currentSegment == null) {
            return new Vec3(0f, 0f, 0f);
        }

        if (lateralTransferInProgress && transferSourceAnchor != null && transferTargetAnchor != null) {
            float blend = smoothStep(clamp(lateralTransferProgress, 0f, 1f));
            Vec3 blended = lerp(transferSourceAnchor, transferTargetAnchor, blend);
            return new Vec3(blended.x, blended.y + yOffset, blended.z);
        }

        Vec3 sourcePos = currentSegment.getGeometry().sampleByDistance(distanceAlongCurrentSegment);
        sourcePos.y += yOffset;
        return sourcePos;
    }

    private void applyOrientation(RenderableObject ro) {
        if (currentSegment == null) return;
        if (orientationModes.contains(OrientationMode.NONE)) return;

        float yawToApply = currentYawRadians;
        if ((transferCommitted && activeTransferZone != null || lateralTransferInProgress) && frozenTransferYawRadians != null) {
            yawToApply = frozenTransferYawRadians;
        }

        if (orientationModes.contains(OrientationMode.YAW)) {
            ro.transformation.angleY = yawToApply;
        }

        if (orientationModes.contains(OrientationMode.PITCH)) {
            if (lateralTransferInProgress) {
                // Hold pitch steady during lateral transfer.
                return;
            }

            PathSegment3 ps = currentSegment.getGeometry();
            Vec3 tangent = ps.sampleTangentByDistance(distanceAlongCurrentSegment);
            if (travelDirection == TravelDirection.REVERSE) {
                tangent = tangent.scale(-1f);
            }

            float pitch = tangent.pitch();
            ro.transformation.angleX = -pitch;
        }
    }

    private TransferZone findContainingTransferZone(RouteSegment segment, float distanceOnSegment) {
        for (TransferZone zone : segment.getTransferZones()) {
            if (zone.contains(distanceOnSegment)) {
                return zone;
            }
        }
        return null;
    }

    private RouteConnection chooseAdjacentConnection(RenderableObject object) {
        List<RouteConnection> candidates = (travelDirection == TravelDirection.FORWARD)
                ? currentSegment.getNextConnections()
                : currentSegment.getPreviousConnections();

        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        if (candidates.size() == 1) {
            return candidates.get(0);
        }

        // Later, use decisionProvider properly here.
        return candidates.get(0);
    }

    private void handleNoAdjacentSegment() {
        switch (wrapMode) {
            case CLAMP -> {
                // remain at boundary
            }
            case LOOP -> resetToStart();
            case PING_PONG -> reverseDirectionAtBoundary();
        }
    }

    private void resetToStart() {
        currentSegment = startSegment;
        distanceAlongCurrentSegment = startDistanceAlongSegment;
        travelDirection = startDirection;

        clearTransferState();

        initialiseYaw();
        onEnterSegment(currentSegment);
    }

    private void reverseDirectionAtBoundary() {
        travelDirection = (travelDirection == TravelDirection.FORWARD)
                ? TravelDirection.REVERSE
                : TravelDirection.FORWARD;

        clearTransferState();
    }

    private void clearTransferState() {
        activeTransferZone = null;
        transferCommitted = false;
        lastDeclinedTransferZone = null;
        frozenTransferYawRadians = null;
        transferSourceAnchor = null;
        transferTargetAnchor = null;
        lateralTransferInProgress = false;
        lateralTransferProgress = 0f;
        lateralTransferDistance = 0f;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private static float smoothStep(float t) {
        return t * t * (3f - 2f * t);
    }

    private static Vec3 lerp(Vec3 a, Vec3 b, float t) {
        return new Vec3(
                a.x + (b.x - a.x) * t,
                a.y + (b.y - a.y) * t,
                a.z + (b.z - a.z) * t
        );
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

        for (RouteConnection next : segment.getNextConnections()) {
            traverseGraph(next.getSegment(), visited, ordered);
        }

        for (RouteConnection previous : segment.getPreviousConnections()) {
            traverseGraph(previous.getSegment(), visited, ordered);
        }
    }

    public String describeGraph() {
        StringBuilder sb = new StringBuilder();
        sb.append("GraphFollowerBehaviour graph")
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

            sb.append("  previous=").append(formatConnections(segment.getPreviousConnections()))
              .append(System.lineSeparator());
            sb.append("  next=").append(formatConnections(segment.getNextConnections()))
              .append(System.lineSeparator());
        }

        return sb.toString();
    }

    private String formatConnections(List<RouteConnection> connections) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < connections.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            RouteConnection c = connections.get(i);
            sb.append(c.getSegment().getLabel())
              .append("@")
              .append(c.getEntryDistance());
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