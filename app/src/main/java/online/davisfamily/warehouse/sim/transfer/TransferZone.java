package online.davisfamily.warehouse.sim.transfer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.warehouse.rendering.model.tracks.GuideSide;
import online.davisfamily.warehouse.sim.transfer.mechanism.TransferZoneMechanism;
import online.davisfamily.warehouse.sim.transfer.strategy.AlwaysTransferStrategy;
import online.davisfamily.warehouse.sim.transfer.strategy.LegacyTransferDecisionStrategyAdapter;
import online.davisfamily.warehouse.sim.transfer.strategy.TransferTargetDecisionStrategy;
import online.davisfamily.warehouse.sim.transfer.strategy.TransferDecisionStrategy;

public class TransferZone {
    private String id;
    private final float startDistance;
    private final float length;
    private final RouteSegment sourceSegment;
    private final RouteSegment targetSegment;
    private final float targetStartDistance;
    private final TransferDecisionStrategy legacyDecisionStrategy;
    private final TransferTargetDecisionStrategy targetDecisionStrategy;
    private final GuideSide sourceOpenSide;
    private final GuideSide targetOpenSide;
    private final float centrePoint;
    private final TransferMotionConfig motionConfig;
    private final List<TransferZoneMechanism> mechanisms = new ArrayList<>();

    public TransferZone(
            String id,
            float startDistance,
            float length,
            RouteSegment sourceSegment,
            RouteSegment targetSegment,
            float targetStartDistance,
            GuideSide sourceOpenSide,
            GuideSide targetOpenSide,
            TransferDecisionStrategy decisionStrategy,
            TransferMotionConfig motionConfig) {
        this(
                id,
                startDistance,
                length,
                sourceSegment,
                targetSegment,
                targetStartDistance,
                sourceOpenSide,
                targetOpenSide,
                decisionStrategy,
                new LegacyTransferDecisionStrategyAdapter(decisionStrategy),
                motionConfig);
    }

    public TransferZone(
            String id,
            float startDistance,
            float length,
            RouteSegment sourceSegment,
            RouteSegment targetSegment,
            float targetStartDistance,
            GuideSide sourceOpenSide,
            GuideSide targetOpenSide,
            TransferTargetDecisionStrategy decisionStrategy,
            TransferMotionConfig motionConfig) {
        this(
                id,
                startDistance,
                length,
                sourceSegment,
                targetSegment,
                targetStartDistance,
                sourceOpenSide,
                targetOpenSide,
                null,
                decisionStrategy,
                motionConfig);
    }

    private TransferZone(
            String id,
            float startDistance,
            float length,
            RouteSegment sourceSegment,
            RouteSegment targetSegment,
            float targetStartDistance,
            GuideSide sourceOpenSide,
            GuideSide targetOpenSide,
            TransferDecisionStrategy legacyDecisionStrategy,
            TransferTargetDecisionStrategy targetDecisionStrategy,
            TransferMotionConfig motionConfig) {

        if (length <= 0f) {
            throw new IllegalArgumentException("length must be > 0");
        }
        if (targetSegment == null) {
            throw new IllegalArgumentException("targetSegment must not be null");
        }
        if (targetDecisionStrategy == null) {
            throw new IllegalArgumentException("decisionStrategy must not be null");
        }
        if (sourceOpenSide == null) {
            throw new IllegalArgumentException("sourceOpenSide must not be null");
        }
        if (targetOpenSide == null) {
            throw new IllegalArgumentException("targetOpenSide must not be null");
        }
        if (motionConfig == null) {
            throw new IllegalArgumentException("motionConfig must not be null");
        }
        this.id = id;
        this.startDistance = startDistance;
        this.length = length;
        this.sourceSegment = sourceSegment;
        this.targetSegment = targetSegment;
        this.targetStartDistance = targetStartDistance;
        this.legacyDecisionStrategy = legacyDecisionStrategy;
        this.targetDecisionStrategy = targetDecisionStrategy;
        this.sourceOpenSide = sourceOpenSide;
        this.targetOpenSide = targetOpenSide;
        this.centrePoint = startDistance + length * 0.5f;
        this.motionConfig = motionConfig;
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

    /**
     * Legacy strategy accessor retained for interval/overlay transfer callers.
     * Prefer {@link #getTargetDecisionStrategy()} for standalone transfer windows.
     */
    @Deprecated
    public TransferDecisionStrategy getDecisionStrategy() {
        return legacyDecisionStrategy;
    }

    public TransferTargetDecisionStrategy getTargetDecisionStrategy() {
        return targetDecisionStrategy;
    }

    public boolean usesLegacyAlwaysTransferStrategy() {
        return legacyDecisionStrategy instanceof AlwaysTransferStrategy;
    }

    public String getPreferredStrategyName() {
        return legacyDecisionStrategy != null
                ? legacyDecisionStrategy.getClass().getSimpleName()
                : targetDecisionStrategy.getClass().getSimpleName();
    }

    public TransferMotionConfig getMotionConfig() {
        return motionConfig;
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

    public void addMechanism(TransferZoneMechanism mechanism) {
        if (mechanism == null) {
            throw new IllegalArgumentException("mechanism must not be null");
        }
        mechanisms.add(mechanism);
    }

    public List<TransferZoneMechanism> getMechanisms() {
        return Collections.unmodifiableList(mechanisms);
    }
}
