package online.davisfamily.warehouse.rendering.model.tracks;

import online.davisfamily.threedee.behaviour.Behaviour;
import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation;
import online.davisfamily.threedee.matrices.Mat4.ObjectTransformation.Axis;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.threedee.rendering.RenderableObject;

public class ConveyorMarkerBehaviour implements Behaviour {
    private static final Vec3 WORLD_UP = new Vec3(0f, 1f, 0f);

    private final RouteSegment segment;
    private final float startDistance;
    private final float endDistance;
    private final TrackSpec spec;
    private final float markerOffset;
    private final double speedUnitsPerSecond;

    private double travelledDistance;

    public ConveyorMarkerBehaviour(
            RouteSegment segment,
            float startDistance,
            float endDistance,
            TrackSpec spec,
            float markerOffset,
            double speedUnitsPerSecond) {
        this.segment = segment;
        this.startDistance = startDistance;
        this.endDistance = endDistance;
        this.spec = spec;
        this.markerOffset = markerOffset;
        this.speedUnitsPerSecond = speedUnitsPerSecond;
    }

    @Override
    public void update(RenderableObject object, double dtSeconds) {
        float topLength = Math.max(0.001f, endDistance - startDistance);
        float wrapRadius = ConveyorMeshFactory.getWrapRadius(spec);
        float wrapLength = (float) (Math.PI * wrapRadius);

        Vec3 startTop = sampleTopPosition(startDistance);
        Vec3 endTop = sampleTopPosition(endDistance);
        Vec3 startUnder = startTop.add(new Vec3(0f, -2f * wrapRadius, 0f));
        Vec3 endUnder = endTop.add(new Vec3(0f, -2f * wrapRadius, 0f));
        Vec3 underDirection = startUnder.subtract(endUnder);
        float returnLength = Math.max(0.001f, underDirection.length());
        underDirection.mutableNormalize();

        float loopLength = topLength + wrapLength + returnLength + wrapLength;
        travelledDistance = (travelledDistance + (speedUnitsPerSecond * dtSeconds)) % loopLength;

        float s = (float) ((travelledDistance + markerOffset) % loopLength);
        ObjectTransformation t = object.transformation;

        if (s < topLength) {
            float d = startDistance + s;
            Vec3 pos = sampleTopPosition(d);
            Vec3 tangent = segment.getGeometry().sampleOrientationDirectionByDistance(d).normalize();
            applyTransform(t, pos, tangent, 0f);
            return;
        }

        s -= topLength;
        Vec3 endForward = segment.getGeometry().sampleOrientationDirectionByDistance(endDistance).normalize();
        if (s < wrapLength) {
            float angle = s / wrapRadius;
            Vec3 pos = endTop
                    .add(WORLD_UP.scale(-wrapRadius))
                    .add(WORLD_UP.scale((float) Math.cos(angle) * wrapRadius))
                    .add(endForward.scale((float) Math.sin(angle) * wrapRadius));
            applyTransform(t, pos, endForward, -angle);
            return;
        }

        s -= wrapLength;
        if (s < returnLength) {
            Vec3 pos = endUnder.add(underDirection.scale(s));
            applyTransform(t, pos, underDirection, (float) Math.PI);
            return;
        }

        s -= returnLength;
        Vec3 startForward = segment.getGeometry().sampleOrientationDirectionByDistance(startDistance).normalize();
        float angle = s / wrapRadius;
        Vec3 pos = startTop
                .add(WORLD_UP.scale(-wrapRadius))
                .add(WORLD_UP.scale(-(float) Math.cos(angle) * wrapRadius))
                .add(startForward.scale(-(float) Math.sin(angle) * wrapRadius));
        applyTransform(t, pos, startForward.scale(-1f), (float) Math.PI - angle);
    }

    private Vec3 sampleTopPosition(float distance) {
        Vec3 sampled = segment.getGeometry().sampleByDistance(distance);
        return new Vec3(
                sampled.x,
                spec.deckTopY + (spec.conveyorBeltThickness + spec.conveyorMarkerThickness) * 0.5f,
                sampled.z);
    }

    private void applyTransform(ObjectTransformation transformation, Vec3 position, Vec3 forward, float rollRadians) {
        transformation.setTranslation(position);
        transformation.setAxisRotation(Axis.X, 0f);
        transformation.setAxisRotation(Axis.Y, Vec3.yawFromDirection(forward));
        transformation.setAxisRotation(Axis.Z, rollRadians);
    }
}
