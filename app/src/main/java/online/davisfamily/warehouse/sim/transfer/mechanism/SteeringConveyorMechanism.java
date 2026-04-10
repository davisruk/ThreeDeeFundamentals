package online.davisfamily.warehouse.sim.transfer.mechanism;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation.Axis;
import online.davisfamily.threedee.rendering.RenderableObject;
import online.davisfamily.warehouse.sim.transfer.TransferOutcome;

public class SteeringConveyorMechanism implements TransferZoneMechanism {
    private final String id;
    private final List<RenderableObject> renderables;
    private final float continueYawRadians;
    private final float branchYawRadians;
    private final float angularSpeedRadiansPerSecond;
    private final float readyToleranceRadians;

    private float currentYawRadians;
    private TransferOutcome commandedOutcome = TransferOutcome.CONTINUE;
    private MechanismMotionState motionState = MechanismMotionState.IDLE;

    public SteeringConveyorMechanism(
            String id,
            List<RenderableObject> renderables,
            float continueYawRadians,
            float branchYawRadians,
            float angularSpeedRadiansPerSecond,
            float readyToleranceRadians) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (renderables == null || renderables.isEmpty()) {
            throw new IllegalArgumentException("renderables must not be empty");
        }
        if (angularSpeedRadiansPerSecond <= 0f) {
            throw new IllegalArgumentException("angularSpeedRadiansPerSecond must be > 0");
        }
        if (readyToleranceRadians < 0f) {
            throw new IllegalArgumentException("readyToleranceRadians must be >= 0");
        }

        this.id = id;
        this.renderables = Collections.unmodifiableList(new ArrayList<>(renderables));
        this.continueYawRadians = normalizeAngle(continueYawRadians);
        this.branchYawRadians = normalizeAngle(branchYawRadians);
        this.angularSpeedRadiansPerSecond = angularSpeedRadiansPerSecond;
        this.readyToleranceRadians = readyToleranceRadians;
        this.currentYawRadians = this.continueYawRadians;
        applyYawToRenderables();
        this.motionState = MechanismMotionState.READY_CONTINUE;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public void command(TransferOutcome outcome) {
        if (outcome == null) {
            throw new IllegalArgumentException("outcome must not be null");
        }
        commandedOutcome = outcome;
        motionState = isReadyFor(outcome)
                ? readyStateFor(outcome)
                : movingStateFor(outcome);
    }

    @Override
    public void update(double dtSeconds) {
        float targetYaw = targetYawFor(commandedOutcome);
        float delta = shortestAngleDelta(currentYawRadians, targetYaw);
        float maxStep = angularSpeedRadiansPerSecond * (float) dtSeconds;

        if (Math.abs(delta) <= readyToleranceRadians) {
            currentYawRadians = targetYaw;
            motionState = readyStateFor(commandedOutcome);
            applyYawToRenderables();
            return;
        }

        float step = Math.min(Math.abs(delta), maxStep);
        currentYawRadians = normalizeAngle(currentYawRadians + Math.signum(delta) * step);
        motionState = movingStateFor(commandedOutcome);
        applyYawToRenderables();
    }

    @Override
    public boolean isReadyFor(TransferOutcome outcome) {
        return Math.abs(shortestAngleDelta(currentYawRadians, targetYawFor(outcome))) <= readyToleranceRadians;
    }

    @Override
    public MechanismMotionState getMotionState() {
        return motionState;
    }

    @Override
    public List<RenderableObject> getRenderables() {
        return renderables;
    }

    private void applyYawToRenderables() {
        for (RenderableObject renderable : renderables) {
            renderable.transformation.setAxisRotation(Axis.Y, currentYawRadians);
        }
    }

    private float targetYawFor(TransferOutcome outcome) {
        return outcome == TransferOutcome.BRANCH ? branchYawRadians : continueYawRadians;
    }

    private MechanismMotionState movingStateFor(TransferOutcome outcome) {
        return outcome == TransferOutcome.BRANCH
                ? MechanismMotionState.MOVING_TO_BRANCH
                : MechanismMotionState.MOVING_TO_CONTINUE;
    }

    private MechanismMotionState readyStateFor(TransferOutcome outcome) {
        return outcome == TransferOutcome.BRANCH
                ? MechanismMotionState.READY_BRANCH
                : MechanismMotionState.READY_CONTINUE;
    }

    private static float shortestAngleDelta(float from, float to) {
        return normalizeAngle(to - from);
    }

    private static float normalizeAngle(float angle) {
        float twoPi = (float) (Math.PI * 2.0);
        float normalized = angle % twoPi;
        if (normalized > Math.PI) {
            normalized -= twoPi;
        }
        if (normalized < -Math.PI) {
            normalized += twoPi;
        }
        return normalized;
    }
}
