package online.davisfamily.warehouse.sim.totebag.assembly;

import online.davisfamily.threedee.behaviour.routing.RouteSegment;
import online.davisfamily.threedee.matrices.Vec3;
import online.davisfamily.warehouse.rendering.model.tracks.TrackAppearance;
import online.davisfamily.warehouse.rendering.model.tracks.TrackSpec;
import online.davisfamily.warehouse.sim.totebag.layout.TipperEntryLayoutSpec;

public class TipperTrackSection {
    private final TipperEntryLayoutSpec layoutSpec;
    private final RouteSegment infeedSegment;
    private final RouteSegment tipperSegment;
    private final RouteSegment exitSegment;
    private final TrackSpec tipperTrackSpec;
    private final TrackAppearance trackAppearance;
    private final float tipperLength;
    private final float tipperStopDistance;
    private final float tippedAngleRadians;
    private final float toteYOffset;
    private final float tipperTrackOverallWidth;
    private final Vec3 tipperAssemblyLocalOrigin;

    public TipperTrackSection(
            TipperEntryLayoutSpec layoutSpec,
            RouteSegment infeedSegment,
            RouteSegment tipperSegment,
            RouteSegment exitSegment,
            TrackSpec tipperTrackSpec,
            TrackAppearance trackAppearance,
            float tipperLength,
            float tipperStopDistance,
            float tippedAngleRadians,
            float toteYOffset,
            float tipperTrackOverallWidth,
            Vec3 tipperAssemblyLocalOrigin) {
        if (layoutSpec == null
                || infeedSegment == null
                || tipperSegment == null
                || exitSegment == null
                || tipperTrackSpec == null
                || trackAppearance == null
                || tipperAssemblyLocalOrigin == null) {
            throw new IllegalArgumentException("Tipper track section inputs must not be null");
        }
        this.layoutSpec = layoutSpec;
        this.infeedSegment = infeedSegment;
        this.tipperSegment = tipperSegment;
        this.exitSegment = exitSegment;
        this.tipperTrackSpec = tipperTrackSpec;
        this.trackAppearance = trackAppearance;
        this.tipperLength = tipperLength;
        this.tipperStopDistance = tipperStopDistance;
        this.tippedAngleRadians = tippedAngleRadians;
        this.toteYOffset = toteYOffset;
        this.tipperTrackOverallWidth = tipperTrackOverallWidth;
        this.tipperAssemblyLocalOrigin = Vec3.copy(tipperAssemblyLocalOrigin);
    }

    public TipperEntryLayoutSpec getLayoutSpec() {
        return layoutSpec;
    }

    public RouteSegment getInfeedSegment() {
        return infeedSegment;
    }

    public RouteSegment getTipperSegment() {
        return tipperSegment;
    }

    public RouteSegment getExitSegment() {
        return exitSegment;
    }

    public TrackSpec getTipperTrackSpec() {
        return tipperTrackSpec;
    }

    public TrackAppearance getTrackAppearance() {
        return trackAppearance;
    }

    public float getTipperLength() {
        return tipperLength;
    }

    public float getTipperStopDistance() {
        return tipperStopDistance;
    }

    public float getTippedAngleRadians() {
        return tippedAngleRadians;
    }

    public float getToteYOffset() {
        return toteYOffset;
    }

    public float getTipperTrackOverallWidth() {
        return tipperTrackOverallWidth;
    }

    public Vec3 getTipperAssemblyLocalOrigin() {
        return Vec3.copy(tipperAssemblyLocalOrigin);
    }

    public Vec3 localToWorld(float localX, float localY, float localZ) {
        return localToWorld(new Vec3(localX, localY, localZ));
    }

    public Vec3 localToWorld(Vec3 localPoint) {
        Vec3 rotated = Vec3.rotateY(localPoint, rigYaw());
        rotated.mutableAdd(layoutSpec.origin());
        return rotated;
    }

    public float rigYaw() {
        return layoutSpec.yawRadians();
    }
}
